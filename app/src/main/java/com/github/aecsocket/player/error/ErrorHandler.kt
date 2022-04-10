package com.github.aecsocket.player.error

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.View
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import com.github.aecsocket.player.NOTIF_CHAN_ERROR
import com.github.aecsocket.player.R
import com.github.aecsocket.player.TAG
import com.google.android.material.snackbar.Snackbar
import org.schabi.newpipe.extractor.exceptions.*
import java.io.IOException

const val NOTIF_ID = 16274

object ErrorHandler {
    fun openActivityIntent(context: Context, error: ErrorInfo) =
        Intent(context, ErrorActivity::class.java).apply {
            putExtra(ERROR_INFO, error)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun openActivity(context: Context, error: ErrorInfo) {
        context.startActivity(openActivityIntent(context, error))
    }

    fun getMessage(context: Context, ex: Throwable): String {
        return context.getString(when (ex) {
            is AgeRestrictedContentException -> R.string.error_age_restricted_content
            is GeographicRestrictionException -> R.string.error_georestricted_content
            is PaidContentException -> R.string.error_paid_content
            is PrivateContentException -> R.string.error_private_content
            is SoundCloudGoPlusContentException -> R.string.error_soundcloud_go_plus_content
            is YoutubeMusicPremiumContentException -> R.string.error_youtube_music_premium_content
            is ContentNotAvailableException -> R.string.error_content_not_available
            is ContentNotSupportedException -> R.string.error_content_not_supported
            is IOException -> R.string.error_network
            else -> R.string.error_generic
        })
    }

    fun handle(context: Context, view: View?, error: ErrorInfo) {
        if (view == null) {
            NotificationManagerCompat.from(context)
                .notify(NOTIF_ID, NotificationCompat.Builder(context, NOTIF_CHAN_ERROR)
                    .setSmallIcon(R.drawable.ic_bug_report)
                    .setContentTitle(context.getString(R.string.error_notif))
                    .setContentText(error.message)
                    .setAutoCancel(true)
                    .setContentIntent(PendingIntent.getActivity(
                        context, 0,
                        openActivityIntent(context, error),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    .build()
                )
        } else {
            Snackbar.make(view, context.getString(R.string.error_generic), Snackbar.LENGTH_LONG)
                .setActionTextColor(Color.YELLOW)
                .setAction(R.string.details) {
                    openActivity(context, error)
                }.show()
        }
    }

    fun handle(fragment: Fragment, error: ErrorInfo) {
        val view = fragment.view ?: fragment.activity?.findViewById(R.id.content)
        handle(fragment.requireContext(), view, error)
    }

    fun handle(context: Context, error: ErrorInfo) {
        handle(context, if (context is Activity) context.findViewById<View>(R.id.content) else null, error)
    }
}