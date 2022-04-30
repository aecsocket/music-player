package com.github.aecsocket.player.data

import android.content.Context
import com.github.aecsocket.player.Errorable
import com.github.aecsocket.player.R
import com.github.aecsocket.player.media.*
import kotlinx.coroutines.*
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.services.media_ccc.MediaCCCService
import org.schabi.newpipe.extractor.services.peertube.PeertubeService
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import java.lang.IllegalStateException

interface ItemService {
    fun name(context: Context): String

    suspend fun fetchStream(scope: CoroutineScope, context: Context, sources: DataSources, url: String): LoadedStreamData

    suspend fun fetchStreams(scope: CoroutineScope, url: String): List<StreamData>

    suspend fun fetchSearch(scope: CoroutineScope, query: String): Errorable<List<ItemCategory>>

    companion object {
        val NEWPIPE = ServiceList.all().associateWith { NewPipeItemService(it, when (it) {
            is YoutubeService, is PeertubeService, is MediaCCCService -> TypeNames(ITEM_TYPE_VIDEO, ITEM_TYPE_CHANNEL)
            else -> TypeNames(ITEM_TYPE_SONG, ITEM_TYPE_ARTIST)
        }) }

        val ALL = listOf(
            LocalItemService,
            NewPipeItemService(ServiceList.YouTube,
                TypeNames(ITEM_TYPE_SONG, ITEM_TYPE_ARTIST),
                R.string.yt_music,
                listOf(
                    listOf("music_songs", "music_playlists", "music_albums", "music_artists"),
                    listOf("music_songs"),
                    listOf("music_playlists"),
                    listOf("music_albums"),
                    listOf("music_artists")
                )
            ),
            SpotifyItemService
        ) + NEWPIPE.values

        fun byUrl(url: String): ItemService? {
            return try {
                val newPipe = NewPipe.getServiceByUrl(url)
                NEWPIPE[newPipe]
                    // we throw here, rather than returning null, because every NP service
                    // should be mapped (as above)
                    ?: throw IllegalStateException("service ${newPipe.serviceInfo.name} does not have corresponding ItemService")
            } catch (ex: ExtractionException) {
                // todo
                null
            }
        }
    }
}

object LocalItemService : ItemService {
    override fun name(context: Context) = context.getString(R.string.local)

    override suspend fun fetchStream(
        scope: CoroutineScope,
        context: Context,
        sources: DataSources,
        url: String
    ): LoadedStreamData {
        TODO("Not yet implemented")
    }

    override suspend fun fetchStreams(scope: CoroutineScope, url: String) = emptyList<StreamData>()

    override suspend fun fetchSearch(scope: CoroutineScope, query: String) = Errorable<List<ItemCategory>>(emptyList())
}
