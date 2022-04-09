package com.github.aecsocket.player

import android.app.Application
import android.content.res.Resources
import android.util.TypedValue
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.github.aecsocket.player.media.MediaPlayer
import com.squareup.picasso.RequestCreator
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.*
import java.util.concurrent.TimeUnit

const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:78.0) Gecko/20100101 Firefox/78.0"
const val PKG = BuildConfig.APPLICATION_ID
const val NOTIF_CHAN_MEDIA = "media"
const val TAG = "MPlayer"

class App : Application() {
    lateinit var player: MediaPlayer

    override fun onCreate() {
        super.onCreate()
        player = MediaPlayer(this)
        val downloader = object : Downloader() {
            val client = OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            // TODO do we need to "enableModernTLS : DownloaderImpl"?

            override fun execute(req: Request): Response {
                val url = req.url()
                val reqBuilder = okhttp3.Request.Builder()
                    .method(req.httpMethod(), req.dataToSend()?.let { RequestBody.create(null, it) })
                    .url(url)
                    .addHeader("User-Agent", USER_AGENT)

                req.headers().forEach { (name, values) ->
                    if (values.size > 1) {
                        reqBuilder.removeHeader(name)
                        values.forEach { reqBuilder.addHeader(name, it) }
                    } else {
                        reqBuilder.header(name, values[0])
                    }
                }

                val rsp = client.newCall(reqBuilder.build()).execute()
                if (rsp.code() == 429) {
                    rsp.close()
                    throw ReCaptchaException("reCaptcha Challenge requested", url)
                }

                return Response(
                    rsp.code(), rsp.message(), rsp.headers().toMultimap(),
                    rsp.body()?.string(), rsp.request().url().toString())
            }
        }
        NewPipe.init(downloader,
            Localization.fromLocale(Locale.getDefault()),
            ContentCountry.DEFAULT)

        NotificationManagerCompat.from(this).apply {
            createNotificationChannel(
                NotificationChannelCompat.Builder(NOTIF_CHAN_MEDIA, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(getString(R.string.notif_media))
                    .setDescription(getString(R.string.notif_media_desc))
                    .build()
            )
        }
    }

    companion object {
        fun setupRequest(req: RequestCreator): RequestCreator =
            req.placeholder(R.drawable.placeholder)
    }
}

fun Resources.Theme.resolve(resid: Int): Int? {
    val value = TypedValue()
    return if (resolveAttribute(resid, value, true)) value.data else null
}