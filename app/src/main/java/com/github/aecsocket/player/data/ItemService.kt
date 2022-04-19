package com.github.aecsocket.player.data

import android.content.Context
import com.github.aecsocket.player.R
import com.squareup.picasso.Picasso
import kotlinx.coroutines.*
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.lang.IllegalStateException

interface ItemService {
    fun name(context: Context): String

    // we *really* pass the scope here?? todo
    suspend fun fetchStreams(url: String, scope: CoroutineScope): List<StreamData>?

    suspend fun fetchSearch(query: String): List<ItemData>

    companion object {
        val NEWPIPE = ServiceList.all().associateWith { NewPipeItemService(it) }

        val ALL = listOf(
            LocalItemService,
            NewPipeItemService(ServiceList.YouTube, R.string.yt_music,
                listOf("music_songs", "music_albums", "music_artists")),
            SpotifyItemService
        ) + NEWPIPE.values

        fun byUrl(url: String): ItemService? {
            try {
                val newPipe = NewPipe.getServiceByUrl(url)
                return NEWPIPE[newPipe]
                    ?: throw IllegalStateException("service ${newPipe.serviceInfo.name} does not have corresponding ItemService")
            } catch (ex: ExtractionException) {
                // todo
                return null
            }
        }
    }
}

object LocalItemService : ItemService {
    override fun name(context: Context) = context.getString(R.string.local)

    override suspend fun fetchStreams(url: String, scope: CoroutineScope): List<StreamData>? = null

    override suspend fun fetchSearch(query: String): List<ItemData> {
        TODO("Not yet implemented")
    }
}

object SpotifyItemService : ItemService {
    override fun name(context: Context) = context.getString(R.string.spotify)

    override suspend fun fetchStreams(url: String, scope: CoroutineScope): List<StreamData>? = null

    override suspend fun fetchSearch(query: String): List<ItemData> {
        TODO("Not yet implemented")
    }
}

class NewPipeItemService(
    val handle: StreamingService,
    val nameId: Int? = null,
    val filters: List<String> = emptyList()
) : ItemService {
    override fun name(context: Context) =
        nameId?.let { context.getString(it) } ?: handle.serviceInfo.name

    override suspend fun fetchStreams(url: String, scope: CoroutineScope): List<StreamData>? {
        return when (handle.getLinkTypeByUrl(url)) {
            StreamingService.LinkType.STREAM -> listOf(StreamInfo.getInfo(handle, url).asData(handle))
            StreamingService.LinkType.PLAYLIST -> {
                var playlist = PlaylistInfo.getInfo(handle, url)
                // keep it ordered by using an array
                val streams = Array<StreamData?>(playlist.streamCount.toInt()) { null }
                val jobs = ArrayList<Job>()
                var idx = 0
                while (playlist != null) {
                    playlist.relatedItems.forEach { elem ->
                        val i = idx
                        jobs.add(scope.launch(Dispatchers.IO) {
                            streams[i] = elem.asData(handle)
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

    override suspend fun fetchSearch(query: String) =
        SearchInfo.getInfo(handle, handle.searchQHFactory.fromQuery(query, filters, ""))
            .relatedItems.mapNotNull {
            when (it) {
                is StreamInfoItem -> it.asData(handle)
                is ChannelInfoItem -> RemoteArtistData(
                    name = it.name,
                    art = Picasso.get().load(it.thumbnailUrl)
                )
                is PlaylistInfoItem -> null
                else -> null
            }
        }
}
