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
        /*val queue = (application as App).player.queue
        Toast.makeText(this@RouterActivity, "Collecting", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            DataItem.fromUrl(url) {
                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(this@RouterActivity, "finished", Toast.LENGTH_SHORT).show()
                }
            }.collect {
                if (it is StreamData) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RouterActivity, "Collected one", Toast.LENGTH_SHORT).show()
                        if (queue.getState().value?.items?.isNotEmpty() == true) {
                            queue.addUnique(it)
                        } else {
                            queue.addOrSelect(it)
                        }
                    }
                }
            }
        }*/
    }
}