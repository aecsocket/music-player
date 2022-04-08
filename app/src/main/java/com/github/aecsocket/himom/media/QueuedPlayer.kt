package com.github.aecsocket.himom.media

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media.AudioAttributesCompat
import androidx.media.AudioAttributesCompat.CONTENT_TYPE_MUSIC
import androidx.media.AudioAttributesCompat.USAGE_MEDIA
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.github.aecsocket.himom.*
import com.github.aecsocket.himom.data.StreamData
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import kotlin.math.max
import kotlin.math.min

const val RESTART_THRESHOLD = 1000
const val PROGRESS_UPDATE_INTERVAL = 10L
const val STATE_PAUSED = 0
const val STATE_PLAYING = 1
const val STATE_BUFFERING = 2
const val REPEAT_MODE_OFF = 0
const val REPEAT_MODE_ONE = 1
const val REPEAT_MODE_ALL = 2

class QueuedPlayer(private val context: Context) : AudioManager.OnAudioFocusChangeListener {
    var handle: MediaPlayer? = null
    val queue = StreamQueue().apply {
        getCurrent().observeForever { onStreamChange(it) }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener(this)
        .build()
    private val handler = Handler(Looper.getMainLooper())
    private val state = MutableLiveData<Int>().apply { postValue(STATE_PAUSED) }
    private val current = MutableLiveData<StreamData>()
    private val position = MutableLiveData<Long>().apply { postValue(0) }
    private val buffered = MutableLiveData<Long>().apply { postValue(0) }
    val repeatMode = MutableLiveData<Int>().apply { postValue(REPEAT_MODE_OFF) }
    private var exoListener: Player.Listener? = null

    fun getState(): LiveData<Int> = state
    fun getCurrent(): LiveData<StreamData> = current
    fun getPosition(): LiveData<Long> = position
    fun getBuffered(): LiveData<Long> = buffered

    fun requirePlayer(): MediaPlayer {
        return handle ?: MediaPlayer(context).also { handle ->
            val exoListener = object : Player.Listener {
                fun playOrPaused(playWhenReady: Boolean) =
                    if (playWhenReady) STATE_PLAYING else STATE_PAUSED

                override fun onPlaybackStateChanged(playbackState: Int) {
                    state.postValue(when (playbackState) {
                        Player.STATE_IDLE, Player.STATE_BUFFERING -> STATE_BUFFERING
                        else -> playOrPaused(handle.exo.playWhenReady)
                    })
                    if (playbackState == Player.STATE_ENDED) {
                        skipNext()
                    }
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    state.postValue(playOrPaused(playWhenReady))
                }

                // NewPipe: player/Player.java
                override fun onPlayerError(ex: PlaybackException) {
                    val exo = this@QueuedPlayer.handle?.exo ?: return
                    // TODO signal error
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
            }.also { this.exoListener = it }
            handle.exo.addListener(exoListener)
            this.handle = handle
            postUpdateProgress()
            ContextCompat.startForegroundService(context, Intent(context, MediaService::class.java))
            Log.d(TAG, "Player created")
        }
    }

    fun release() {
        if (handle == null)
            return
        context.stopService(Intent(context, MediaService::class.java))
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, focusRequest)
        handle?.let {
            it.exo.removeListener(exoListener!!)
            it.release()
        }
        handle = null
        queue.resetIndex()
        Log.d(TAG, "Player released")
    }

    private fun requestAudioFocus() = AudioManagerCompat.requestAudioFocus(audioManager, focusRequest)
    private fun abandonAudioFocus() = AudioManagerCompat.abandonAudioFocusRequest(audioManager, focusRequest)

    override fun onAudioFocusChange(focus: Int) {
        val handle = handle ?: return
        when (focus) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                handle.exo.volume = 1f
                handle.exo.play()
                // anim volume to 1
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                handle.exo.volume = 0.2f
                // duck
            }
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                handle.exo.pause()
            }
        }
    }

    private fun onStreamChange(stream: StreamData?) {
        if (stream == null) {
            release()
        } else {
            val cur = this.current.value
            if (handle != null && cur?.id == stream.id) {
                // we just restarted the current stream AND we're still playing
                seekTo(0)
            } else {
                // we post a value before anything else, so our service can get the current track
                // before we create it, and avoiding an illegal state
                this.current.postValue(stream)
                val handle = requirePlayer()
                handle.exo.setMediaItem(stream.media)
                handle.exo.prepare()
                handle.exo.play()
            }
        }
    }

    private fun postUpdateProgress() {
        handler.postDelayed({
            updateProgress()
            if (handle != null)
                postUpdateProgress()
        }, PROGRESS_UPDATE_INTERVAL)
    }

    private fun updateProgress() {
        val handle = handle ?: return
        position.postValue(handle.exo.currentPosition)
        buffered.postValue(handle.exo.bufferedPosition)
    }

    fun handleBroadcast(intent: Intent) {
        when (intent.action) {
            // todo ? Intent.ACTION_MEDIA_BUTTON -> MediaButtonReceiver.handleIntent(player.session, intent)
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> pause()
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_SKIP_NEXT -> skipNext()
            ACTION_SKIP_PREVIOUS -> restartOrSkipPrevious()
            ACTION_STOP -> release()
        }
    }

    fun play() {
        val handle = handle ?: return
        requestAudioFocus()
        if (handle.getState() == Player.STATE_ENDED) {
            if (queue.getState().value?.index == 0) {
                handle.exo.seekToDefaultPosition()
            } else {
                queue.setIndex(0)
            }
        }
        handle.exo.play()
    }

    fun pause() {
        val handle = handle ?: return
        abandonAudioFocus()
        handle.exo.pause()
    }

    fun skipNext() {
        val state = queue.getState().value ?: return
        if (state.index < state.items.size - 1) {
            queue.offsetIndex(1)
        } else {
            when (val repeat = repeatMode.value) {
                REPEAT_MODE_ALL, REPEAT_MODE_ONE -> {
                    queue.setIndex(0)
                    if (repeat == REPEAT_MODE_ONE) {
                        repeatMode.postValue(REPEAT_MODE_OFF)
                    }
                }
                else -> {}
            }
        }
    }

    fun skipPrevious() {
        queue.offsetIndex(-1)
    }

    fun restartOrSkipPrevious() {
        val handle = handle ?: return
        if (handle.exo.contentPosition > RESTART_THRESHOLD) {
            handle.exo.seekToDefaultPosition()
            updateProgress()
        } else {
            skipPrevious()
        }
    }

    fun seekTo(position: Long) {
        val handle = handle ?: return
        handle.exo.seekTo(max(0, min(handle.exo.duration, position)))
        updateProgress()
    }

    fun nextRepeatMode() {
        repeatMode.postValue(((repeatMode.value ?: 0) + 1) % 3)
    }

    companion object {
        val audioAttributes: AudioAttributesCompat = AudioAttributesCompat.Builder()
            .setContentType(CONTENT_TYPE_MUSIC)
            .setUsage(USAGE_MEDIA)
            .build()
    }
}