package com.github.aecsocket.player

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Binder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.aecsocket.player.error.ErrorHandler
import com.github.aecsocket.player.error.ErrorInfo
import com.github.aecsocket.player.media.*
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import kotlinx.coroutines.launch
import java.lang.Exception
import java.lang.IllegalStateException

const val NOTIF_ID = 84927
const val ACTION_PLAY = "$PKG.MediaService.PLAY"
const val ACTION_PAUSE = "$PKG.MediaService.PAUSE"
const val ACTION_SKIP_PREVIOUS = "$PKG.MediaService.SKIP_PREVIOUS"
const val ACTION_SKIP_NEXT = "$PKG.MediaService.SKIP_NEXT"
const val ACTION_STOP = "$PKG.MediaService.STOP"

class MediaService : LifecycleService() {
    private val binder = MediaBinder()
    private lateinit var player: MediaPlayer
    private lateinit var receiver: BroadcastReceiver
    private var art: Bitmap? = null
    private var artTarget: Target? = null

    private fun getStream() = player.stream.value
        ?: throw IllegalStateException("using MediaService with no stream")

    override fun onCreate() {
        super.onCreate()
        player = (application as App).player

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { player.state.collect { updateState(it) } }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { player.stream.collect { updateStream(it) } }
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_MEDIA_BUTTON) {
                    Log.e(TAG, "Media button")
                } else {
                    player.handleBroadcast(intent)
                    // if we've just released the player, conn is now null
                    if (player.conn != null)
                        updateNotif(getStream())
                }
            }
        }
        registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_BUTTON)
            //addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(ACTION_PLAY)
            addAction(ACTION_PAUSE)
            addAction(ACTION_SKIP_PREVIOUS)
            addAction(ACTION_SKIP_NEXT)
            addAction(ACTION_STOP)
        })

        startForeground(NOTIF_ID, createNotif(getStream()))
        Log.d(TAG, "Service started")
    }

    override fun onBind(intent: Intent): MediaBinder {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
        player.release()
        Log.d(TAG, "Service destroyed")
    }

    private fun createNotif(stream: ActiveStreamData): Notification {
        val conn = player.conn ?: throw IllegalStateException("using media service with no player")
        val data = stream.data
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val builder = NotificationCompat.Builder(this, NOTIF_CHAN_MEDIA)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setSmallIcon(R.drawable.ic_library)
            .setShowWhen(false)
            .setContentTitle(data.name)
            .setTicker(data.name)
            .setContentText(data.creator ?: getString(R.string.unknown_artist))
            .setLargeIcon(art)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags))
            .addAction(R.drawable.ic_skip_previous, getString(R.string.skip_previous),
                PendingIntent.getBroadcast(this, 0, Intent(ACTION_SKIP_PREVIOUS), flags))
        when (player.state.value) {
            STATE_PAUSED -> builder.addAction(R.drawable.ic_play, getString(R.string.play),
                PendingIntent.getBroadcast(this, 0, Intent(ACTION_PLAY), flags))
            STATE_PLAYING -> builder.addAction(R.drawable.ic_pause, getString(R.string.pause),
                PendingIntent.getBroadcast(this, 0, Intent(ACTION_PAUSE), flags))
            STATE_BUFFERING -> builder.addAction(R.drawable.ic_buffering, getString(R.string.buffering), null)
        }
        return builder
            .addAction(R.drawable.ic_skip_next, getString(R.string.skip_next),
                PendingIntent.getBroadcast(this, 0, Intent(ACTION_SKIP_NEXT), flags))
            .addAction(R.drawable.ic_stop, getString(R.string.stop),
                PendingIntent.getBroadcast(this, 0, Intent(ACTION_STOP), flags))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(conn.session.sessionToken)
                .setShowActionsInCompactView(1, 2))
            .build()
    }

    private fun updateNotif(stream: ActiveStreamData) {
        NotificationManagerCompat.from(this).notify(NOTIF_ID, createNotif(stream))
    }

    private fun updateStream(stream: ActiveStreamData?) {
        val stream = stream ?: return
        stream.art?.let { art ->
            artTarget?.let { Picasso.get().cancelRequest(it) }
            val artTarget = object : Target {
                override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
                    this@MediaService.art = bitmap
                    updateNotif(stream)
                }

                override fun onBitmapFailed(ex: Exception, placeholder: Drawable?) {
                    val context = applicationContext
                    ErrorHandler.handle(context, R.string.error_info_art, ErrorInfo(context, ex))
                    updateNotif(stream)
                }

                override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
            }
            this.artTarget = artTarget
            art.into(artTarget)
            updateNotif(stream)
        }
    }

    private fun updateState(state: Int) {
        updateNotif(getStream())
    }

    inner class MediaBinder : Binder() {
        val service = this@MediaService
    }
}
