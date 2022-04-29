package com.github.aecsocket.player

import android.content.Context
import androidx.preference.PreferenceManager

const val VOLUME_ANIM_TIME_DEF = 1000
const val VOLUME_ANIM_TIME_STEP = 100L
const val STREAM_QUALITY_QUALITY = "quality"
const val STREAM_QUALITY_EFFICIENCY = "efficiency"

object Prefs {
    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    fun volumeAnimTime(context: Context) =
        prefs(context).getInt(context.getString(R.string.pref_volume_anim_key), VOLUME_ANIM_TIME_DEF) * VOLUME_ANIM_TIME_STEP

    fun streamQuality(context: Context) =
        prefs(context).getString(context.getString(R.string.pref_stream_quality_key), STREAM_QUALITY_QUALITY)
}
