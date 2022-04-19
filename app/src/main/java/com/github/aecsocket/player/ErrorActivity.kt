package com.github.aecsocket.player

import android.os.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.github.aecsocket.player.databinding.ActivityErrorBinding
import com.github.aecsocket.player.error.ErrorInfo

const val ERROR_INFO = "error_info"

class ErrorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityErrorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val error = intent.getParcelableExtra<ErrorInfo>(ERROR_INFO)
        if (error == null) {
            finish()
            return
        }

        binding.errorMessage.text = error.message ?: getString(R.string.no_message)
        binding.errorStackTrace.text = error.stackTrace
    }
}
