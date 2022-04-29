package com.github.aecsocket.player.data

import android.content.Context
import com.github.aecsocket.player.R
import com.github.aecsocket.player.media.DataSources
import com.github.aecsocket.player.media.LoadedStreamData
import kotlinx.coroutines.CoroutineScope

object SpotifyItemService : ItemService {
    override fun name(context: Context) = context.getString(R.string.spotify)

    override suspend fun fetchStream(
        scope: CoroutineScope,
        sources: DataSources,
        url: String
    ): LoadedStreamData {
        TODO("Not yet implemented")
    }

    override suspend fun fetchStreams(scope: CoroutineScope, url: String): List<StreamData> {
        TODO("Not yet implemented")
    }

    override suspend fun fetchSearch(scope: CoroutineScope, query: String): List<ItemCategory> {
        TODO("Not yet implemented")
    }
}
