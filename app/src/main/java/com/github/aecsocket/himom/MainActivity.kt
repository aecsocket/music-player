package com.github.aecsocket.himom

import android.media.AudioManager
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        volumeControlStream = AudioManager.STREAM_MUSIC
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}