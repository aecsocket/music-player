package com.github.aecsocket.himom

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.aecsocket.himom.data.StreamData
import com.github.aecsocket.himom.databinding.DialogRouterBinding
import com.google.android.exoplayer2.MediaItem
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.*
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException

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
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val service = NewPipe.getServiceByUrl(url)
                val linkType = service.getLinkTypeByUrl(url)
                if (linkType == StreamingService.LinkType.NONE) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RouterActivity,
                            getString(R.string.error_unsupported_service), Toast.LENGTH_LONG).show()
                        finish()
                    }
                } else {
                    // todo stupid ass hack
                    val info = StreamInfo.getInfo(url)
                    if (info.audioStreams.size > 0) {
                        val stream = info.audioStreams[0]
                        withContext(Dispatchers.Main) {
                            val player = (application as App).player
                            val stream = StreamData(
                                media = MediaItem.fromUri(stream.url),
                                track = info.name,
                                artist = info.uploaderName,
                                art = Picasso.get().load(info.thumbnailUrl)
                            )
                            player.queue.addOrSelect(stream)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        finish()
                    }
                        /*val radioGroup = DialogRouterBinding.inflate(layoutInflater).list
                        val alertDialog = AlertDialog.Builder(this@RouterActivity)
                            .setTitle(R.string.app_name)
                            //.setView(radioGroup)
                            .setCancelable(true)
                            .setOnDismissListener { finish() }
                            .create().also { this@RouterActivity.alertDialog = it }

                        alertDialog.show()*/
                }
            } catch (ex: ExtractionException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RouterActivity, when (ex) {
                        is AgeRestrictedContentException -> getString(R.string.error_age_restricted_content)
                        is GeographicRestrictionException -> getString(R.string.error_georestricted_content)
                        is PaidContentException -> getString(R.string.error_paid_content)
                        is PrivateContentException -> getString(R.string.error_private_content)
                        is SoundCloudGoPlusContentException -> getString(R.string.error_soundcloud_go_plus_content)
                        is YoutubeMusicPremiumContentException -> getString(R.string.error_youtube_music_premium_content)
                        is ContentNotAvailableException -> getString(R.string.error_content_not_available)
                        is ContentNotSupportedException -> getString(R.string.error_content_not_supported)
                        is IOException -> getString(R.string.error_network)
                        else -> getString(R.string.error_generic, ex.toString())
                    }, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }
}