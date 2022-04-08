package com.github.aecsocket.himom.media

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.github.aecsocket.himom.MainActivity
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.C.CONTENT_TYPE_MUSIC
import com.google.android.exoplayer2.C.USAGE_MEDIA
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector

class MediaPlayer(context: Context) {
    val exo: Player
    val session: MediaSessionCompat
    val connector: MediaSessionConnector
    val controller: MediaControllerCompat

    fun getState() = exo.playbackState

    init {
        exo = ExoPlayer.Builder(context)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(audioAttributes, false)
            .build()
        session = MediaSessionCompat(context, javaClass.simpleName).apply {
            //setFlags(MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS) // TODO meaning we need to do queue mgmt
            setSessionActivity(PendingIntent.getActivity(context, 0,
                Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            setPlaybackState(PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_SEEK_TO
                        or PlaybackStateCompat.ACTION_PLAY
                        or PlaybackStateCompat.ACTION_PAUSE
                        or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                .build())
            isActive = true
        }
        connector = MediaSessionConnector(session).apply {
            setPlayer(exo)
        }
        controller = session.controller
    }

    fun release() {
        if (!session.isActive)
            return
        exo.stop()
        exo.release()
        session.isActive = false
        session.release()
    }

    companion object {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(CONTENT_TYPE_MUSIC)
            .setUsage(USAGE_MEDIA)
            .build()
    }
}