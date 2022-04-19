package com.github.aecsocket.player.data

import android.content.Context
import androidx.recyclerview.widget.DiffUtil
import com.github.aecsocket.player.R
import com.github.aecsocket.player.media.LoadedStreamData
import com.github.aecsocket.player.media.SourceResolver
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import kotlinx.coroutines.*
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.concurrent.atomic.AtomicLong

sealed interface ItemData {
    val id: Long
    val art: RequestCreator?

    fun same(other: ItemData) = id == other.id

    fun primaryText(context: Context): String
    fun secondaryText(context: Context): String

    companion object {
        private val nextId = AtomicLong()
        fun nextId() = nextId.getAndIncrement()

        inline fun <reified T : ItemData> itemCallback() = object : DiffUtil.ItemCallback<T>() {
            override fun areItemsTheSame(oldItem: T, newItem: T) = oldItem.same(newItem)
            override fun areContentsTheSame(oldItem: T, newItem: T) = oldItem.same(newItem)
        }

        suspend fun requestStreams(
            url: String,
            service: StreamingService,
            scope: CoroutineScope
        ): List<StreamData>? {
            return when (service.getLinkTypeByUrl(url)) {
                StreamingService.LinkType.STREAM -> listOf(StreamInfo.getInfo(service, url).asData(service))
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
                                streams[i] = elem.asData(service)
                            })
                            idx++
                        }
                        playlist =
                            if (playlist.hasNextPage()) PlaylistInfo.getInfo(playlist.nextPage.url)
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

interface StreamData : ItemData {
    val name: String
    val artist: String?
    val type: StreamType

    override fun primaryText(context: Context) = name
    override fun secondaryText(context: Context) = artist ?: context.getString(R.string.unknown_artist)

    fun makeSource(resolver: SourceResolver): LoadedStreamData
}

data class ServiceStreamData(
    override val id: Long = ItemData.nextId(),
    override val name: String,
    override val artist: String? = null,
    override val type: StreamType,
    override val art: RequestCreator? = null,

    val service: StreamingService,
    val url: String
) : StreamData {
    override fun same(other: ItemData) =
        super.same(other) || other is ServiceStreamData && url == other.url

    override fun makeSource(resolver: SourceResolver) =
        resolver.resolve(this, StreamInfo.getInfo(service, url))
}

interface ArtistData : ItemData {
    val name: String

    override fun primaryText(context: Context) = name
    override fun secondaryText(context: Context) = context.getString(R.string.artist)
}

data class RemoteArtistData(
    override val id: Long = ItemData.nextId(),
    override val name: String,
    override val art: RequestCreator? = null
) : ArtistData

fun StreamInfo.asData(service: StreamingService) =
    ServiceStreamData(
        name = name,
        artist = uploaderName,
        art = Picasso.get().load(thumbnailUrl),
        type = streamType,
        service = service,
        url = url)

fun StreamInfoItem.asData(service: StreamingService) =
    ServiceStreamData(
        name = name,
        artist = uploaderName,
        art = Picasso.get().load(thumbnailUrl),
        type = streamType,
        service = service,
        url = url)
