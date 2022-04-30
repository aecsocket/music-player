package com.github.aecsocket.player.viewmodel

import androidx.lifecycle.ViewModel
import com.github.aecsocket.player.Errorable
import com.github.aecsocket.player.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashSet

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

    private val _suggestions = MutableStateFlow<Errorable<List<Suggestion>>>(Errorable(emptyList()))
    val suggestions: StateFlow<Errorable<List<Suggestion>>> = _suggestions
    private var suggestionsJob: Job? = null

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

        suggestionsJob?.cancel()
        resultsJob?.cancel()
        if (query.isEmpty()) {
            _results.value = Results.Success(emptyList())
        } else {
            _results.value = Results.Fetching
            resultsJob = scope.launch {
                ItemService.byUrl(query)?.let { service ->
                    launch(Dispatchers.IO + CoroutineExceptionHandler { _, ex ->
                        error(listOf(ex))
                    }) {
                        complete(service.fetchStreams(this, query).asCategories())
                    }
                } ?: run {
                    if (defServices.isEmpty()) {
                        error(listOf(NoServicesException()))
                    } else {
                        val results = ArrayList<ItemCategory>()
                        val exs = ArrayList<Throwable>()
                        defServices.map { service ->
                            async {
                                launch(Dispatchers.IO) {
                                    val res = service.fetchSearch(this, query)
                                    results.addAll(res.result)
                                    exs.addAll(res.exs)
                                }
                            }
                        }.map { it.await() }
                        complete(results, exs)
                    }
                }
            }
        }
    }

    fun fetchSuggestions(
        scope: CoroutineScope,
        services: Collection<ItemService>,
        query: String
    ) {
        suggestionsJob?.cancel()
        if (query.isEmpty()) {
            _suggestions.value = Errorable(emptyList())
        } else {
            suggestionsJob = scope.launch {
                val queried = LinkedHashSet<String>() // ignore duplicates
                val exs = ArrayList<Throwable>()
                services.map { service -> async(Dispatchers.IO + CoroutineExceptionHandler { _, ex ->
                    exs.add(ex)
                }) {
                    queried.addAll(service.fetchSuggestions(this, query).map { it.lowercase() })
                } }.map { it.await() }
                // TODO add suggestions from history
                _suggestions.value = Errorable(queried.map { Suggestion(SUGGESTION_TYPE_QUERY, it) }, exs)
            }
        }
    }
}
