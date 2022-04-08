package com.github.aecsocket.himom

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Binder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import com.github.aecsocket.himom.data.StreamData
import com.github.aecsocket.himom.media.QueuedPlayer
import com.github.aecsocket.himom.media.STATE_BUFFERING
import com.github.aecsocket.himom.media.STATE_PAUSED
import com.github.aecsocket.himom.media.STATE_PLAYING
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
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
    private lateinit var player: QueuedPlayer
    private lateinit var receiver: BroadcastReceiver
    private var art: Bitmap? = null
    private var artTarget: Target? = null

    private fun getStream() = player.getCurrent().value
        ?: throw IllegalStateException("using media service with no stream running")

    override fun onCreate() {
        super.onCreate()
        player = (application as App).player
        val stream = getStream()
        player.getCurrent().observe(this) { updateStream(it) }
        player.getState().observe(this) { updateState(it) }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val streamNow = getStream()
                // TODO: Action media buttons dont actually update the notif
                // Cause #handleBroadcast updates the playback status
                // off this thread, so updateNotif -> actual playback pause
                // Fix pls
                player.handleBroadcast(intent)
                if (player.handle != null) {
                    updateNotif(streamNow)
                }
            }
        }
        registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_BUTTON)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(ACTION_PLAY)
            addAction(ACTION_PAUSE)
            addAction(ACTION_SKIP_PREVIOUS)
            addAction(ACTION_SKIP_NEXT)
            addAction(ACTION_STOP)
        })

        startForeground(NOTIF_ID, createNotif(stream))
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

    private fun createNotif(stream: StreamData): Notification {
        val playerHandle = player.handle ?: throw IllegalStateException("using media service with no player")
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val title = stream.track
        val builder = NotificationCompat.Builder(this, NOTIF_CHAN_MEDIA)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setSmallIcon(R.drawable.ic_library)
            .setShowWhen(false)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(stream.artist ?: getString(R.string.unknown_artist))
            .setLargeIcon(art)
            .addAction(R.drawable.ic_skip_previous, getString(R.string.skip_previous),
                PendingIntent.getBroadcast(this, 0, Intent(ACTION_SKIP_PREVIOUS), flags))
        when (player.getState().value) {
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
                .setMediaSession(playerHandle.session.sessionToken)
                .setShowActionsInCompactView(1, 2))
            .build()
    }

    private fun updateNotif(stream: StreamData) {
        NotificationManagerCompat.from(this).notify(NOTIF_ID, createNotif(stream))
    }

    private fun updateStream(stream: StreamData) {
        stream.art?.let { art ->
            artTarget?.let { Picasso.get().cancelRequest(it) }
            val artTarget = object : Target {
                override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
                    this@MediaService.art = bitmap
                    updateNotif(stream)
                }

                override fun onBitmapFailed(ex: Exception, error: Drawable?) {
                    // TODO snackbar
                    // TODO set art
                    this@MediaService.art = null
                    Toast.makeText(this@MediaService,
                        "Error getting image: $ex",
                        Toast.LENGTH_SHORT).show()
                    updateNotif(stream)
                }

                override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
            }
            this.artTarget = artTarget
            App.setupRequest(art).into(artTarget)
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