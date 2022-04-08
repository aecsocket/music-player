package com.github.aecsocket.himom

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.aecsocket.himom.data.DataItem
import com.github.aecsocket.himom.data.StreamData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            lifecycleScope.launch(Dispatchers.IO) {
                DataItem.fromUrl(url, service) {
                    lifecycleScope.launch(Dispatchers.Main) { finish()  }
                }.collect {
                    if (it is StreamData) {
                        withContext(Dispatchers.Main) {
                            if (queue.getState().value?.items?.isNotEmpty() == true) {
                                queue.addUnique(it)
                            } else {
                                queue.addOrSelect(it)
                            }
                        }
                    }
                }
            }
        }

    }
}