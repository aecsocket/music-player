package com.github.aecsocket.player.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.recyclerview.widget.DiffUtil
import com.github.aecsocket.player.R
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import kotlinx.coroutines.*
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.concurrent.ThreadLocalRandom

sealed class DataItem(
    val url: String,
    val id: Long = ThreadLocalRandom.current().nextLong()
) {
    abstract fun getPrimaryText(context: Context): String
    abstract fun getSecondaryText(context: Context): String
    abstract fun getArt(context: Context): RequestCreator?

    fun isSame(other: DataItem) = url == other.url

    companion object {
        inline fun <reified T : DataItem> itemCallback() = object : DiffUtil.ItemCallback<T>() {
            override fun areItemsTheSame(oldItem: T, newItem: T) = oldItem.id == newItem.id
            @SuppressLint("DiffUtilEquals")
            override fun areContentsTheSame(oldItem: T, newItem: T) = oldItem.isSame(newItem)
        }

        fun requestStream(url: String, service: StreamingService) =
            StreamInfo.getInfo(service, url).asData(service)


        suspend fun requestStreams(url: String, service: StreamingService, scope: CoroutineScope): List<StreamData>? {
            return when (service.getLinkTypeByUrl(url)) {
                StreamingService.LinkType.STREAM -> listOf(requestStream(url, service))
                StreamingService.LinkType.PLAYLIST -> {
                    var playlist = PlaylistInfo.getInfo(service, url)
                    // keep it ordered by using an array
                    val streams = Array<StreamData?>(playlist.streamCount.toInt()) { null }
                    val jobs = ArrayList<Job>()
                    var idx = 0
                    while (playlist != null) {
                        playlist.relatedItems.forEach { elem ->
                            val i = idx
                            jobs.add(scope.launch(Dispatchers.IO) {
                                streams[i] = requestStream(elem.url, service)
                            })
                            idx++
                        }
                        playlist = if (playlist.hasNextPage())
                            PlaylistInfo.getInfo(playlist.nextPage.url)
                        else null
                    }
                    jobs.joinAll()
                    // if we failed to get some entries, they'll stay null - just filter them out
                    // TODO maybe log these errors
                    streams.filterNotNull()
                }
                else -> null
            }
        }

        suspend fun requestStreams(url: String, scope: CoroutineScope): List<StreamData>? {
            return try {
                requestStreams(url, NewPipe.getServiceByUrl(url), scope)
            } catch (ex: ExtractionException) {
                null
            }
        }
    }
}

class StreamData(
    url: String,
    val name: String,
    val artist: String? = null,
    val art: RequestCreator? = null,
    val type: StreamType,
    val service: StreamingService
) : DataItem(url) {
    override fun getPrimaryText(context: Context) = name
    override fun getSecondaryText(context: Context) = artist ?: context.getString(R.string.unknown_artist)
    override fun getArt(context: Context) = art

    fun request(): StreamInfo {
        return StreamInfo.getInfo(service, url)
    }
}

class ArtistData(
    url: String,
    val name: String,
    val art: RequestCreator? = null
) : DataItem(url) {
    override fun getPrimaryText(context: Context) = name
    override fun getSecondaryText(context: Context) = context.getString(R.string.artist)
    override fun getArt(context: Context) = art

    override fun toString(): String {
        return """ArtistData('$name' ${if (art != null) " (has art)" else ""})"""
    }
}

// TODO distinction between user playlists and artist albums
// even though they act the same, they should be visually different
class ListData(
    url: String,
    val name: String,
    val amount: Long,
    val art: RequestCreator? = null
) : DataItem(url) {
    override fun getPrimaryText(context: Context) = name
    override fun getSecondaryText(context: Context) = context.getString(R.string.playlist)
    override fun getArt(context: Context) = art

    override fun toString(): String {
        return """ListData('$name' ${if (art != null) " (has art)" else ""})"""
    }
}

fun StreamInfo.asData(service: StreamingService) =
    StreamData(url, name, uploaderName, Picasso.get().load(thumbnailUrl), streamType, service)

fun PlaylistInfo.asData(service: StreamingService) =
    ListData(url, name, streamCount, Picasso.get().load(thumbnailUrl))

fun ChannelInfo.asData(service: StreamingService) =
    ArtistData(url, name, Picasso.get().load(avatarUrl))
