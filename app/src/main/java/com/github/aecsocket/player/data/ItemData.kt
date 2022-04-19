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
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.lang.IllegalStateException
import java.util.concurrent.atomic.AtomicLong

const val LIST_TYPE_ALBUM = 0
const val LIST_TYPE_PLAYLIST = 1

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

        suspend fun listToStreams(
            service: StreamingService,
            original: PlaylistInfo,
            scope: CoroutineScope
        ): List<StreamData> {
            var playlist: PlaylistInfo? = original
            val streams = Array<StreamData?>(original.streamCount.toInt()) { null }
            val jobs = ArrayList<Job>()
            var idx = 0
            while (playlist != null) {
                original.relatedItems.forEach { elem ->
                    val i = idx
                    jobs.add(scope.launch(Dispatchers.IO) {
                        streams[i] = elem.asData(service)
                    })
                    idx++
                }
                playlist =
                    if (original.hasNextPage()) PlaylistInfo.getInfo(original.nextPage.url)
                    else null
            }
            jobs.joinAll()
            // if we failed to get some entries, they'll stay null - just filter them out
            // TODO maybe log these errors
            return streams.filterNotNull()
        }

        suspend fun requestStreams(
            url: String,
            service: StreamingService,
            scope: CoroutineScope
        ): List<StreamData>? {
            return when (service.getLinkTypeByUrl(url)) {
                StreamingService.LinkType.STREAM -> listOf(StreamInfo.getInfo(service, url).asData(service))
                StreamingService.LinkType.PLAYLIST -> listToStreams(service, PlaylistInfo.getInfo(service, url), scope)
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

    fun fetchSource(resolver: SourceResolver): LoadedStreamData
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

    override fun fetchSource(resolver: SourceResolver) =
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

interface StreamListData : ItemData {
    val name: String
    val creator: String?
    val size: Long

    suspend fun fetchStreams(scope: CoroutineScope): List<StreamData>
}

data class ServiceStreamListData(
    override val id: Long = ItemData.nextId(),
    override val name: String,
    override val creator: String? = null,
    override val art: RequestCreator? = null,
    override val size: Long,

    val listType: Int,
    val service: StreamingService,
    val url: String
) : StreamListData {
    override fun primaryText(context: Context) = name
    override fun secondaryText(context: Context) = context.getString(when (listType) {
        LIST_TYPE_ALBUM -> R.string.album_info
        LIST_TYPE_PLAYLIST -> R.string.playlist_info
        else -> throw IllegalStateException("unknown stream list type $listType")
    }, size, creator)

    override suspend fun fetchStreams(scope: CoroutineScope) =
        ItemData.listToStreams(service, PlaylistInfo.getInfo(service, url), scope)
}

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

fun PlaylistInfo.asData(service: StreamingService, listType: Int) =
    ServiceStreamListData(
        name = name,
        creator = uploaderName,
        art = Picasso.get().load(thumbnailUrl),
        size = streamCount,
        listType = listType,
        service = service,
        url = url)

fun PlaylistInfoItem.asData(service: StreamingService, listType: Int) =
    ServiceStreamListData(
        name = name,
        creator = uploaderName,
        art = Picasso.get().load(thumbnailUrl),
        size = streamCount,
        listType = listType,
        service = service,
        url = url)
