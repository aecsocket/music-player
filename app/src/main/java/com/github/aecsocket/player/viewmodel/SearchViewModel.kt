package com.github.aecsocket.player.viewmodel

import androidx.lifecycle.ViewModel
import com.github.aecsocket.player.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SearchViewModel : ViewModel() {
    sealed class Results {
        object Fetching : Results()
        object None : Results()
        data class Success(val results: List<ItemCategory> = emptyList(), val exs: List<Throwable> = emptyList()) : Results()
        data class Error(val exs: List<Throwable>) : Results()
    }

    class NoServicesException : RuntimeException()

    private val _results = MutableStateFlow<Results>(Results.Success())
    val results: StateFlow<Results> = _results

    private var resultsJob: Job? = null

    fun query(
        scope: CoroutineScope,
        defServices: Collection<ItemService>,
        query: String
    ) {
        fun complete(results: List<ItemCategory>, exs: List<Throwable> = emptyList()) {
            _results.value = if (results.isEmpty()) {
                if (exs.isEmpty()) Results.None
                else Results.Error(exs)
            } else Results.Success(results, exs)
        }

        fun error(exs: List<Throwable>) {
            _results.value = Results.Error(exs)
        }

        if (query.isEmpty()) {
            _results.value = Results.Success(emptyList())
        } else {
            _results.value = Results.Fetching
            resultsJob?.cancel()
            resultsJob = scope.launch {
                ItemService.byUrl(query)?.let { service ->
                    scope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, ex ->
                        error(listOf(ex))
                    }) {
                        complete(service.fetchStreams(scope, query).asCategories())
                    }
                } ?: run {
                    if (defServices.isEmpty()) {
                        error(listOf(NoServicesException()))
                    } else {
                        val results = ArrayList<ItemCategory>()
                        val exs = ArrayList<Throwable>()
                        defServices.map { service ->
                            async {
                                launch(Dispatchers.IO + CoroutineExceptionHandler { _, ex ->
                                    exs.add(ex)
                                }) {
                                    results.addAll(service.fetchSearch(this, query))
                                }
                            }
                        }.map { it.await() }
                        complete(results, exs)
                    }
                }
            }
        }
    }

    fun cancelQuery() {
        _results.value = Results.Success()
        resultsJob?.cancel()
    }
}
