package com.github.aecsocket.player

import android.os.*
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.RecyclerView
import com.github.aecsocket.player.databinding.ActivityErrorBinding
import com.github.aecsocket.player.databinding.ItemErrorBinding
import com.github.aecsocket.player.error.ErrorInfo
import com.github.aecsocket.player.error.ErrorPart

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

        binding.errorParts.adapter = ErrorAdapter(error.parts)
    }

    class ErrorAdapter(val parts: List<ErrorPart>) : RecyclerView.Adapter<ErrorAdapter.ViewHolder>() {
        class ViewHolder(binding: ItemErrorBinding) : RecyclerView.ViewHolder(binding.root) {
            private val message = binding.errorMessage
            private val stackTrace = binding.errorStackTrace

            fun bindTo(part: ErrorPart) {
                message.text = part.message
                stackTrace.text = part.stackTrace
            }

            companion object {
                fun from(parent: ViewGroup) = ViewHolder(
                    ItemErrorBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
        }

        override fun getItemCount() = parts.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder.from(parent)

        override fun onBindViewHolder(holder: ViewHolder, position: Int) =
            holder.bindTo(parts[position])
    }
}
