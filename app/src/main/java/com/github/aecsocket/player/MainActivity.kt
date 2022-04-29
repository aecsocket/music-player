package com.github.aecsocket.player

import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.os.*
import android.util.Log
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.github.aecsocket.player.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        volumeControlStream = AudioManager.STREAM_MUSIC

        ViewCompat.setOnApplyWindowInsetsListener(binding.view) { view, insets ->
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                val inset = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                bottomMargin = inset.bottom
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    rightMargin = inset.right
                }
            }
            insets
        }
    }
}
