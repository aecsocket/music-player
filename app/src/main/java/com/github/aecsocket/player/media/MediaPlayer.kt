package com.github.aecsocket.player.media

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.github.aecsocket.player.*
import com.github.aecsocket.player.data.StreamData
import com.github.aecsocket.player.error.ErrorHandler
import com.github.aecsocket.player.error.ErrorInfo
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.squareup.picasso.RequestCreator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.Exception

const val STATE_PAUSED = 0
const val STATE_PLAYING = 1
const val STATE_BUFFERING = 2

const val DURATION_UNKNOWN = -1L

const val PROGRESS_UPDATE_INTERVAL = 10L
const val RESTART_THRESHOLD = 1000L

const val VOLUME_DUCK_DURATION = 500L
const val VOLUME_DUCK = 0.2f

sealed class ActiveStreamData(
    val data: StreamData,
    val art: RequestCreator? = null
)

class LoadingStreamData(
    data: StreamData,
    art: RequestCreator? = null
) : ActiveStreamData(data, art)

class LoadedStreamData(
    data: StreamData,
    art: RequestCreator? = null,
    val source: MediaSource
) : ActiveStreamData(data, art)

class MediaPlayer(
    private val context: Context
) : AudioManager.OnAudioFocusChangeListener {
    val queue = StreamQueue().apply {
        addListener(object : StreamQueue.Listener {
            override fun onSelect(from: Int, to: Int) {
                this@MediaPlayer.onChangeStream(getOr(to))
            }
        })
    }
    var conn: PlayerConnection? = null
    val sources = DataSources(context, DefaultBandwidthMeter.Builder(context).build())

    private val scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener(this)
        .build()

    data class Position(val position: Long, val buffered: Long)

    private val _stream = MutableStateFlow<ActiveStreamData?>(null)
    val stream: StateFlow<ActiveStreamData?> = _stream
    private val _state = MutableStateFlow(STATE_BUFFERING)
    val state: StateFlow<Int> = _state
    private val _duration = MutableStateFlow(DURATION_UNKNOWN)
    val duration: StateFlow<Long> = _duration
    private val _position = MutableStateFlow(Position(0, 0))
    val position: StateFlow<Position> = _position

    private fun requestAudioFocus() = AudioManagerCompat.requestAudioFocus(audioManager, focusRequest)
    private fun abandonAudioFocus() = AudioManagerCompat.abandonAudioFocusRequest(audioManager, focusRequest)

    fun requireConn(): PlayerConnection {
        return conn ?: PlayerConnection(context, { exoListener(it) }, { queueNavigator(it) }).also { conn ->
            this.conn = conn
            postUpdatePosition()
            ContextCompat.startForegroundService(context, Intent(context, MediaService::class.java))
            Log.d(TAG, "Player created")
        }
    }

    fun release() {
        val conn = conn ?: return
        context.stopService(Intent(context, MediaService::class.java))
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, focusRequest)
        conn.release()
        this.conn = null
        queue.resetIndex()
        Log.d(TAG, "Player released")
    }

    override fun onAudioFocusChange(focus: Int) {
        val conn = conn ?: return
        when (focus) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                animateVolume(VOLUME_DUCK, 1f, VOLUME_DUCK_DURATION)
                conn.exo.play()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                animateVolume(1f, VOLUME_DUCK, VOLUME_DUCK_DURATION)
            }
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                conn.exo.pause()
            }
        }
    }

    private fun onChangeStream(stream: StreamData?) {
        if (stream == null) {
            _stream.value = stream
            release()
        } else {
            if (_stream.value?.data?.same(stream) != true) {
                _stream.value = LoadingStreamData(stream, stream.art)
                _duration.value = DURATION_UNKNOWN
                _state.value = STATE_BUFFERING
                val conn = requireConn()
                conn.exo.clearMediaItems()

                scope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, ex ->
                    scope.launch(Dispatchers.Main) {
                        ErrorHandler.handle(context, R.string.error_info_stream,
                            ErrorInfo(context, ex))
                        skipNext()
                    }
                }) {
                    val source = stream.fetchSource(this, context, sources)
                    withContext(Dispatchers.Main) {
                        _stream.value = source
                        conn.exo.setMediaSource(source.source)
                        conn.exo.prepare()
                    }
                }
            }
        }
    }

    private fun exoListener(conn: PlayerConnection) = object : Player.Listener {
        fun playOrPaused(playWhenReady: Boolean) =
            if (playWhenReady) STATE_PLAYING else STATE_PAUSED

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE, Player.STATE_BUFFERING -> _state.value = STATE_BUFFERING
                Player.STATE_READY -> {
                    requestAudioFocus()
                    _state.value = playOrPaused(conn.exo.playWhenReady)
                    _duration.value = conn.exo.duration
                }
                Player.STATE_ENDED -> {
                    // if we've just cleared all media items
                    // (we're about to buffer the next item)
                    if (_state.value == STATE_BUFFERING)
                        return
                    _state.value = STATE_PAUSED
                    skipNext()
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                requestAudioFocus()
                _state.value = STATE_PLAYING
                if (_stream.value?.data?.streamType?.isLive() == true) {
                    conn.exo.seekToDefaultPosition()
                }
            } else {
                _state.value = STATE_PAUSED
            }
        }

        // NewPipe: player/Player.java
        override fun onPlayerError(ex: PlaybackException) {
            val exo = conn.exo
            Log.w(TAG, "Playback error", ex)
            when (ex.errorCode) {
                PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                    exo.seekToDefaultPosition()
                    exo.prepare()
                }
                PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
                PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
                PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> {
                    // TODO exception handler here
                    skipNext()
                }
                PlaybackException.ERROR_CODE_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_UNSPECIFIED -> {
                    exo.prepare()
                }
                else -> release()
            }
        }
    }

    private fun queueNavigator(conn: PlayerConnection) = object : MediaSessionConnector.QueueNavigator {
        override fun getSupportedQueueNavigatorActions(player: Player) =
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM

        override fun onTimelineChanged(player: Player) {}
        override fun onCurrentMediaItemIndexChanged(player: Player) {}
        override fun getActiveQueueItemId(player: Player?) =
            queue.getIndex().toLong()
        override fun onSkipToPrevious(player: Player) = skipPrevious()
        override fun onSkipToQueueItem(player: Player, id: Long) {
            queue.safeSetIndex(id.toInt())
        }
        override fun onSkipToNext(player: Player) = skipNext()

        override fun onCommand(
            player: Player,
            command: String,
            extras: Bundle?,
            cb: ResultReceiver?
        ) = false
    }

    private fun postUpdatePosition() {
        handler.postDelayed({
            updatePosition()
            if (conn != null)
                postUpdatePosition()
        }, PROGRESS_UPDATE_INTERVAL)
    }

    private fun updatePosition() {
        val conn = conn ?: return
        _position.value = Position(conn.exo.currentPosition, conn.exo.bufferedPosition)
    }

    fun handleBroadcast(intent: Intent) {
        when (intent.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_SKIP_NEXT -> skipNext()
            ACTION_SKIP_PREVIOUS -> restartOrSkipPrevious()
            ACTION_STOP -> release()
        }
    }

    fun play() {
        val conn = conn ?: return
        if (_state.value == STATE_PLAYING) return
        if (conn.exo.playbackState == Player.STATE_ENDED || _stream.value?.data?.streamType?.isLive() == true) {
            seekToDefault()
        }
        requestAudioFocus()
        conn.exo.play()
        animateVolume(0f, 1f, Prefs.volumeAnimTime(context))
    }

    fun pause() {
        val conn = conn ?: return
        if (_state.value == STATE_PAUSED) return
        abandonAudioFocus()
        // change value now, since if not, the state won't change until anim ended
        _state.value = STATE_PAUSED
        animateVolume(1f, 0f, Prefs.volumeAnimTime(context)) {
            conn.exo.pause()
        }
    }

    // explicit transport actions will start playback
    // BUT if e.g. an item is removed from queue, and a new stream is queued
    // the playback state will NOT change
    fun skipNext() {
        if (queue.offsetIndex(1)) {
            play()
        }
    }

    fun skipPrevious() {
        if (queue.offsetIndex(-1)) {
            play()
        }
    }

    fun restartOrSkipPrevious() {
        val conn = conn ?: return
        if (conn.exo.currentPosition > RESTART_THRESHOLD) {
            conn.exo.seekToDefaultPosition()
            // todo update position
        } else {
            skipPrevious()
        }
    }

    fun seekTo(position: Long) {
        val conn = conn ?: return
        conn.exo.seekTo(position)
        updatePosition()
    }

    fun seekToDefault() {
        val conn = conn ?: return
        conn.exo.seekToDefaultPosition()
        updatePosition()
    }

    fun animateVolume(from: Float, to: Float, duration: Long, onEnd: (() -> Unit)? = null) {
        ValueAnimator().apply {
            setFloatValues(from, to)
            this.duration = duration
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    conn?.exo?.volume = from
                }

                override fun onAnimationCancel(animation: Animator?) {
                    conn?.exo?.volume = to
                }

                override fun onAnimationEnd(animation: Animator?) {
                    conn?.exo?.volume = to
                    onEnd?.invoke()
                }
            })
            addUpdateListener { conn?.exo?.volume = it.animatedValue as Float }
            start()
        }
    }

    companion object {
        val audioAttributes: AudioAttributesCompat = AudioAttributesCompat.Builder()
            .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributesCompat.USAGE_MEDIA)
            .build()
    }
}
