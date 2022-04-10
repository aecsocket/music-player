package com.github.aecsocket.player.error

import android.os.*
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.github.aecsocket.player.R

const val ERROR_INFO = "error_info"

class ErrorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_error)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val error = intent.getParcelableExtra<ErrorInfo>(ERROR_INFO)
        if (error == null) {
            finish()
            return
        }

        findViewById<TextView>(R.id.errorMessage).text = error.message ?: getString(R.string.no_message)
        findViewById<TextView>(R.id.errorStackTrace).text = error.stackTrace
    }
}