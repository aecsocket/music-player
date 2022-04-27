package com.github.aecsocket.player

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.aecsocket.player.databinding.ActivitySettingsBinding
import com.github.aecsocket.player.fragment.SettingsFragment

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
    }
}
