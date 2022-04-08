package com.github.aecsocket.himom

import android.content.Context
import android.widget.Toast
import org.schabi.newpipe.extractor.exceptions.*
import java.io.IOException
import java.lang.Exception

object ExceptionHandler {
    // todo make better lol
    fun handle(context: Context, ex: Exception) {
        Toast.makeText(context, context.getString(when (ex) {
            is AgeRestrictedContentException -> R.string.error_age_restricted_content
            is GeographicRestrictionException -> R.string.error_georestricted_content
            is PaidContentException -> R.string.error_paid_content
            is PrivateContentException -> R.string.error_private_content
            is SoundCloudGoPlusContentException -> R.string.error_soundcloud_go_plus_content
            is YoutubeMusicPremiumContentException -> R.string.error_youtube_music_premium_content
            is ContentNotAvailableException -> R.string.error_content_not_available
            is ContentNotSupportedException -> R.string.error_content_not_supported
            is IOException -> R.string.error_network
            //else -> R.string.error_generic
            else -> throw ex
        }, ex.toString()), Toast.LENGTH_LONG).show()
    }
}