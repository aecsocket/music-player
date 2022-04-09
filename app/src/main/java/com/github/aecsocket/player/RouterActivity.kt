package com.github.aecsocket.player

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.aecsocket.player.data.DataItem
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe

class RouterActivity : AppCompatActivity() {
    private var url = ""
    private var alertDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (url.isEmpty()) {
            // todo parse search : NewPipe/RouterActivity
            url = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.data?.toString() ?: ""
        }
    }

    override fun onStop() {
        super.onStop()
        alertDialog?.dismiss()
    }

    override fun onStart() {
        super.onStart()
        val queue = (application as App).player.queue
        val service = NewPipe.getServiceByUrl(url)
        if (service == null) {
            Toast.makeText(this, getString(R.string.error_unsupported_service), Toast.LENGTH_LONG).show()
        } else {
            lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, ex ->
                lifecycleScope.launch(Dispatchers.Main) {
                    ExceptionHandler.handle(findViewById<View>(android.R.id.content).rootView, ex)
                }
            }) {
                DataItem.requestStreams(url, this)?.let { streams ->
                    launch(Dispatchers.Main) {
                        streams.forEach { queue.addInitial(it) }
                        finish()
                    }
                }
            }
        }

    }
}