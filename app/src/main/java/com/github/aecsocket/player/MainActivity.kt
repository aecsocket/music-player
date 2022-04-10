package com.github.aecsocket.player

import android.content.res.Configuration
import android.media.AudioManager
import android.os.*
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        volumeControlStream = AudioManager.STREAM_MUSIC
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(R.id.view)) { view, insets ->
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginEnd = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).right
                }
            }
            insets
        }
    }
}