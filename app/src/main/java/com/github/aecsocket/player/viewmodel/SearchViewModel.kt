package com.github.aecsocket.player.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.github.aecsocket.player.TAG
import com.github.aecsocket.player.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SearchViewModel : ViewModel() {
    sealed class Results {
        object Fetching : Results()
        data class Complete(val results: List<ItemData> = emptyList(), val exs: List<Throwable> = emptyList()) : Results()
    }

    private val _results = MutableStateFlow<Results>(Results.Complete(results = emptyList()))
    val results: StateFlow<Results> = _results

    private var resultsJob: Job? = null

    fun query(
        scope: CoroutineScope,
        defServices: Collection<ItemService>,
        query: String
    ) {
        _results.value = Results.Fetching
        resultsJob?.cancel()
        resultsJob = scope.launch {
            ItemService.byUrl(query)?.let { service ->
                scope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, ex ->
                    _results.value = Results.Complete(exs = listOf(ex))
                }) {
                    service.fetchStreams(query, scope)?.let {
                        _results.value = Results.Complete(it)
                    }
                }
            } ?: run {
                val allResults = Array<List<ItemData>?>(defServices.size) { null }
                val allExs = Array<Throwable?>(defServices.size) { null }
                val allJobs = Array<Job?>(defServices.size) { null }
                defServices.forEachIndexed { index, service ->
                    allJobs[index] = scope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, ex ->
                        allExs[index] = ex
                    }) {
                        allResults[index] = service.fetchSearch(query)
                    }
                }
                allJobs.filterNotNull().joinAll()
                _results.value = Results.Complete(
                    allResults.filterNotNull().flatten(), allExs.filterNotNull())
            }
        }
    }

    fun cancelQuery() {
        _results.value = Results.Complete()
        resultsJob?.cancel()
    }
}