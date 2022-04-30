package com.github.aecsocket.player.data

import android.content.Context
import com.github.aecsocket.player.Errorable
import com.github.aecsocket.player.R
import com.github.aecsocket.player.media.DataSources
import com.github.aecsocket.player.media.LoadedStreamData
import kotlinx.coroutines.CoroutineScope

object SpotifyItemService : ItemService {
    override fun name(context: Context) = context.getString(R.string.spotify)

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

    override suspend fun fetchSuggestions(scope: CoroutineScope, query: String) = emptyList<String>()
}
