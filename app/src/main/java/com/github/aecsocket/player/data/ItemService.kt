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
        val NEWPIPE = ServiceList.all().associateWith { NewPipeItemService(it, LIST_TYPE_PLAYLIST) }

        val ALL = listOf(
            LocalItemService,
            NewPipeItemService(ServiceList.YouTube, LIST_TYPE_ALBUM, R.string.yt_music,
                listOf("music_songs", "music_playlists", "music_albums", "music_artists")),
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
    val listType: Int,
    val nameId: Int? = null,
    val filters: List<String> = emptyList()
) : ItemService {
    override fun name(context: Context) =
        nameId?.let { context.getString(it) } ?: handle.serviceInfo.name

    override suspend fun fetchStreams(url: String, scope: CoroutineScope): List<StreamData>? {
        return when (handle.getLinkTypeByUrl(url)) {
            StreamingService.LinkType.STREAM -> listOf(StreamInfo.getInfo(handle, url).asData(handle))
            StreamingService.LinkType.PLAYLIST -> ItemData.listToStreams(handle, PlaylistInfo.getInfo(handle, url), scope)
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
                is PlaylistInfoItem -> it.asData(handle, listType)
                else -> null
            }
        }
}
