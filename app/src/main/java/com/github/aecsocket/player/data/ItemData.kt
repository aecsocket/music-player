package com.github.aecsocket.player.data

import android.content.Context
import androidx.recyclerview.widget.DiffUtil
import com.github.aecsocket.player.R
import com.github.aecsocket.player.formatTime
import com.github.aecsocket.player.media.DataSources
import com.github.aecsocket.player.media.LoadedStreamData
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import kotlinx.coroutines.*
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.concurrent.atomic.AtomicLong

const val ITEM_TYPE_SONG = 0
const val ITEM_TYPE_VIDEO = 1
const val ITEM_TYPE_ARTIST = 2
const val ITEM_TYPE_CHANNEL = 3
const val ITEM_TYPE_ALBUM = 4
const val ITEM_TYPE_PLAYLIST = 5

data class ItemCategory(val type: Int, val items: List<ItemData>) {
    companion object {
        fun itemCallback() = object : DiffUtil.ItemCallback<ItemCategory>() {
            override fun areItemsTheSame(oldItem: ItemCategory, newItem: ItemCategory) = oldItem.type == newItem.type
            override fun areContentsTheSame(oldItem: ItemCategory, newItem: ItemCategory) = oldItem.items.same(newItem.items)
        }
    }
}

sealed interface ItemData {
    val id: Long
    val itemType: Int
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
    }
}

fun List<ItemData>.asCategories(): List<ItemCategory> {
    val categories = HashMap<Int, MutableList<ItemData>>()
    forEach { item -> categories.computeIfAbsent(item.itemType) { ArrayList() }.add(item) }
    return categories.map { (type, items) -> ItemCategory(type, items) }
}

fun List<ItemData>.same(other: List<ItemData>): Boolean {
    if (size != other.size)
        return false
    forEachIndexed { idx, item ->
        if (!item.same(other[idx]))
            return false
    }
    return true
}

interface StreamData : ItemData {
    val name: String
    val creator: String?
    val duration: Long
    val streamType: StreamType

    fun typeNameKey() = when (itemType) {
        ITEM_TYPE_SONG -> R.string.song
        ITEM_TYPE_VIDEO -> R.string.video
        else -> throw IllegalStateException("unknown stream type $itemType")
    }

    fun creator(context: Context) = creator ?: context.getString(R.string.unknown_artist)

    fun formatDuration(context: Context) = if (duration >= 0) formatTime(context, duration) else context.getString(R.string.live)

    override fun primaryText(context: Context) = name
    override fun secondaryText(context: Context) = context.getString(R.string.stream_info,
        context.getString(typeNameKey()),
        creator(context),
        formatDuration(context))

    fun shortSecondaryText(context: Context) = context.getString(R.string.stream_info_short,
        creator(context),
        formatDuration(context))

    suspend fun fetchSource(scope: CoroutineScope, context: Context, sources: DataSources): LoadedStreamData
}

data class ServiceStreamData(
    override val id: Long = ItemData.nextId(),
    override val name: String,
    override val creator: String? = null,
    override val art: RequestCreator? = null,
    override val duration: Long,
    override val itemType: Int,
    override val streamType: StreamType,

    val service: ItemService,
    val url: String
) : StreamData {
    override fun same(other: ItemData) =
        super.same(other) || other is ServiceStreamData && url == other.url

    override suspend fun fetchSource(
        scope: CoroutineScope,
        context: Context,
        sources: DataSources
    ) = service.fetchStream(scope, context, sources, url)
}

interface CreatorData : ItemData {
    val name: String

    override fun primaryText(context: Context) = name
    override fun secondaryText(context: Context) = context.getString(when (itemType) {
        ITEM_TYPE_ARTIST -> R.string.artist
        ITEM_TYPE_CHANNEL -> R.string.channel
        else -> throw IllegalStateException("unknown uploader type $itemType")
    })
}

data class ServiceCreatorData(
    override val id: Long = ItemData.nextId(),
    override val name: String,
    override val art: RequestCreator? = null,
    override val itemType: Int,

    val service: ItemService,
    val url: String
) : CreatorData

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
    override val itemType: Int,

    val service: ItemService,
    val url: String
) : StreamListData {
    override fun primaryText(context: Context) = name
    override fun secondaryText(context: Context): String {
        val nameType = context.getString(when (itemType) {
            ITEM_TYPE_ALBUM -> R.string.album
            ITEM_TYPE_PLAYLIST -> R.string.playlist
            else -> throw IllegalStateException("unknown stream list type $itemType")
        })
        return if (size > 0) context.resources.getQuantityString(R.plurals.list_info, size.toInt(),
            nameType, creator, size) else context.getString(R.string.list_info_no_size, nameType, creator)
    }

    override suspend fun fetchStreams(scope: CoroutineScope) =
        service.fetchStreams(scope, url)
}

fun StreamInfo.asData(service: ItemService, itemType: Int, creator: String? = uploaderName) =
    ServiceStreamData(
        name = name,
        creator = creator,
        art = Picasso.get().load(thumbnailUrl),
        duration = duration * 1000,
        itemType = itemType,
        streamType = streamType,
        service = service,
        url = url)

fun StreamInfoItem.asData(service: ItemService, itemType: Int, creator: String? = uploaderName) =
    ServiceStreamData(
        name = name,
        creator = creator,
        art = Picasso.get().load(thumbnailUrl),
        duration = duration * 1000,
        itemType = itemType,
        streamType = streamType,
        service = service,
        url = url)

fun ChannelInfo.asData(service: ItemService, itemType: Int, creator: String = this.name) =
    ServiceCreatorData(
        name = name,
        art = Picasso.get().load(avatarUrl),
        itemType = itemType,
        service = service,
        url = url)

fun ChannelInfoItem.asData(service: ItemService, itemType: Int, name: String = this.name) =
    ServiceCreatorData(
        name = name,
        art = Picasso.get().load(thumbnailUrl),
        itemType = itemType,
        service = service,
        url = url)

fun PlaylistInfo.asData(service: ItemService, itemType: Int, creator: String? = uploaderName) =
    ServiceStreamListData(
        name = name,
        creator = creator,
        art = Picasso.get().load(thumbnailUrl),
        size = streamCount,
        itemType = itemType,
        service = service,
        url = url)

fun PlaylistInfoItem.asData(service: ItemService, itemType: Int, creator: String? = uploaderName) =
    ServiceStreamListData(
        name = name,
        creator = uploaderName,
        art = Picasso.get().load(thumbnailUrl),
        size = streamCount,
        itemType = itemType,
        service = service,
        url = url)
