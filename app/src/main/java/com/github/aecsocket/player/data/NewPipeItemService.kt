package com.github.aecsocket.player.data

import android.content.Context
import android.util.Log
import com.github.aecsocket.player.Prefs
import com.github.aecsocket.player.STREAM_QUALITY_EFFICIENCY
import com.github.aecsocket.player.STREAM_QUALITY_QUALITY
import com.github.aecsocket.player.TAG
import com.github.aecsocket.player.media.DataSources
import com.github.aecsocket.player.media.LoadedStreamData
import com.github.aecsocket.player.media.isLive
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.MediaSource
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

const val STREAM_EDGE_GAP = 10_000L
const val YT_ARTIST_SUFFIX = " - Topic"

class NoStreamsException : RuntimeException()

class NewPipeItemService(
    val handle: StreamingService,
    val streamType: Int,
    val creatorType: Int,
    val nameId: Int? = null,
    private val filterSets: List<List<String>> = listOf(
        emptyList(),
        listOf("videos", "sepia_videos", "music_videos", "tracks", "music_songs"),
        listOf("channels", "users", "music_artists"),
        listOf("playlists", "music_playlists", "music_albums")
    )
) : ItemService {
    override fun name(context: Context): String =
        nameId?.let { context.getString(it) } ?: handle.serviceInfo.name

    data class NameTransform(
        val type: Int,
        val name: String
    )

    // so scuffed but idc
    private fun streamType(creator: String): NameTransform {
        val suffix = creator.lastIndexOf(YT_ARTIST_SUFFIX)
        return if (suffix == -1) NameTransform(streamType, creator)
            else NameTransform(ITEM_TYPE_SONG, creator.substring(0, suffix))
    }

    private fun creatorType(name: String): NameTransform {
        val suffix = name.lastIndexOf(YT_ARTIST_SUFFIX)
        return if (suffix == -1) NameTransform(creatorType, name)
            else NameTransform(ITEM_TYPE_ARTIST, name.substring(0, suffix))
    }

    private fun listType(creator: String, size: Long) =
        if (creator.isEmpty() || size == -1L) NameTransform(ITEM_TYPE_ALBUM, creator)
        else NameTransform(ITEM_TYPE_PLAYLIST, creator)

    override suspend fun fetchStream(
        scope: CoroutineScope,
        context: Context,
        sources: DataSources,
        url: String
    ): LoadedStreamData {
        val info = StreamInfo.getInfo(handle, url)
        val transform = streamType(info.uploaderName)
        val stream = info.asData(this, transform.type, transform.name)
        val art = Picasso.get().load(info.thumbnailUrl)
        if (info.streamType.isLive()) {
            fun create(factory: MediaSource.Factory, url: String) = LoadedStreamData(
                stream, art,
                factory.createMediaSource(
                    MediaItem.Builder()
                    .setUri(url)
                    .setLiveConfiguration(LIVE_CONFIG)
                    .build()))

            return if (info.hlsUrl.isNotEmpty()) create(sources.hlsSourceFactory, info.hlsUrl)
            else create(sources.dashSourceFactory, info.dashMpdUrl)
        }

        val streams = info.audioStreams
        if (streams.isEmpty())
            throw NoStreamsException()

        // TODO highest quality or lowest data
        val best = when (val quality = Prefs.streamQuality(context)) {
            STREAM_QUALITY_QUALITY -> highestQuality(streams)
            STREAM_QUALITY_EFFICIENCY -> mostEfficient(streams)
            else -> throw IllegalStateException("unknown stream quality '$quality'")
        }
        Log.d(TAG, "Made loaded stream at ${best.averageBitrate}kbps")
        // TODO diff types of factories?? idk

        return LoadedStreamData(stream, art,
            sources.progressiveSourceFactory.createMediaSource(
                MediaItem.Builder()
                .setUri(best.url)
                .build()))
    }

    override suspend fun fetchStreams(
        scope: CoroutineScope,
        url: String
    ): List<StreamData> {
        return when (handle.getLinkTypeByUrl(url)) {
            StreamingService.LinkType.STREAM -> {
                val info = StreamInfo.getInfo(handle, url)
                val transform = streamType(info.uploaderName)
                listOf(info.asData(this, transform.type, transform.name))
            }
            StreamingService.LinkType.PLAYLIST -> {
                var playlist = PlaylistInfo.getInfo(handle, url)
                val streams = Array<StreamData?>(playlist.streamCount.toInt()) { null }
                var idx = 0
                while (playlist != null) {
                    playlist.relatedItems.forEach { elem ->
                        val i = idx
                        val transform = streamType(elem.uploaderName)
                        scope.launch {
                            streams[i] = elem.asData(this@NewPipeItemService, transform.type, transform.name)
                        }
                        idx++
                    }
                    playlist =
                        if (playlist.hasNextPage()) PlaylistInfo.getInfo(playlist.nextPage.url)
                        else null
                }
                // if we failed to get some entries, they'll stay null - just filter them out
                // TODO maybe log these errors
                return streams.filterNotNull()
            }
            else -> emptyList()
        }
    }

    override suspend fun fetchSearch(scope: CoroutineScope, query: String): List<ItemCategory> {
        val result = HashMap<Int, MutableList<ItemData>>()

        fun doFetch(filters: List<String>) {
            SearchInfo.getInfo(handle, handle.searchQHFactory.fromQuery(query, filters, ""))
                .relatedItems.mapNotNull {
                    fun add(transform: NameTransform, itemFactory: (Int, String) -> ItemData) {
                        val item = itemFactory(transform.type, transform.name)
                        val list = result.computeIfAbsent(transform.type) { ArrayList() }
                        synchronized(list) {
                            if (list.indexOfFirst { data -> data.same(item) } == -1) {
                                list.add(itemFactory(transform.type, transform.name))
                            }
                        }
                    }

                    when (it) {
                        is StreamInfoItem -> add(streamType(it.uploaderName)) { type, creator ->
                            it.asData(this, type, creator)
                        }
                        is ChannelInfoItem -> add(creatorType(it.name)) { type, name ->
                            it.asData(this, type, name)
                        }
                        is PlaylistInfoItem -> add(listType(it.uploaderName, it.streamCount)) { type, creator ->
                            it.asData(this, type, null)
                        }
                        else -> null
                    }
                }
        }

        // NewPipe/ServiceHelper @ 55
        filterSets.map { filters -> scope.async { doFetch(filters) } }.map { it.await() }
        return result.map { (type, items) -> ItemCategory(type, items) }
    }

    companion object {
        // 0 = lowest quality
        val FORMAT_HIGHEST_QUALITY = listOf(MediaFormat.MP3, MediaFormat.WEBMA, MediaFormat.M4A)
        // 0 = most efficient
        val FORMAT_MOST_EFFICIENT = listOf(MediaFormat.WEBMA, MediaFormat.M4A, MediaFormat.MP3)

        val LIVE_CONFIG = MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(STREAM_EDGE_GAP)
            .build()

        fun compareByBitrate(a: AudioStream, b: AudioStream, ranks: List<MediaFormat>) =
            if (a.averageBitrate < b.averageBitrate) -1
            else if (a.averageBitrate > b.averageBitrate) 1
            // if they have the same bitrate, sort by our format ranking
            else ranks.indexOf(a.getFormat()) - ranks.indexOf(b.getFormat())

        fun highestQuality(streams: List<AudioStream>): AudioStream {
            return streams.stream()
                .max { a, b -> compareByBitrate(a, b, FORMAT_HIGHEST_QUALITY) }
                .get()
        }

        fun mostEfficient(streams: List<AudioStream>): AudioStream {
            return streams.stream()
                .max { a, b -> -compareByBitrate(a, b, FORMAT_MOST_EFFICIENT) }
                .get()
        }
    }
}
