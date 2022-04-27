package com.github.aecsocket.player.fragment

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.github.aecsocket.player.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}