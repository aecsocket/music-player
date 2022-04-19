package com.github.aecsocket.player.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.github.aecsocket.player.R
import com.github.aecsocket.player.data.ItemData
import com.github.aecsocket.player.data.RemoteArtistData
import com.github.aecsocket.player.data.ServiceStreamData
import com.github.aecsocket.player.data.asData
import com.github.aecsocket.player.error.ErrorHandler
import com.github.aecsocket.player.error.ErrorInfo
import com.squareup.picasso.Picasso
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class SearchViewModel : ViewModel() {
    sealed class Results {
        object Fetching : Results()
        data class Success(val results: List<ItemData>): Results()
        data class Error(val ex: Throwable): Results()
    }

    private val _results = MutableStateFlow<Results>(Results.Success(emptyList()))
    val results: StateFlow<Results> = _results

    private var resultsJob: Job? = null

    fun query(
        scope: CoroutineScope,
        query: String
    ) {
        val dispatcher = Dispatchers.IO + CoroutineExceptionHandler { _, ex ->
            _results.value = Results.Error(ex)
        }

        _results.value = Results.Fetching
        resultsJob?.cancel()
        resultsJob = scope.launch {
            try {
                val service = NewPipe.getServiceByUrl(query)
                scope.launch(dispatcher) {
                    ItemData.requestStreams(query, service, scope)?.let {
                        _results.value = Results.Success(it)
                    }
                }
            } catch (ex: ExtractionException) {
                scope.launch(dispatcher) {
                    val service = NewPipe.getService(0)
                    val response = SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(query))
                    _results.value = Results.Success(response.relatedItems.mapNotNull { when (it) {
                        is StreamInfoItem -> it.asData(service)
                        is ChannelInfoItem -> RemoteArtistData(
                            name = it.name,
                            art = Picasso.get().load(it.thumbnailUrl))
                        is PlaylistInfoItem -> null
                        else -> null
                    }})
                }
            }
        }
    }

    fun cancelQuery() {
        _results.value = Results.Success(emptyList())
        resultsJob?.cancel()
    }
}