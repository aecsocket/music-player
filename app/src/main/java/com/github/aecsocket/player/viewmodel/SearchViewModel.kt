package com.github.aecsocket.player.viewmodel

import androidx.lifecycle.*
import com.github.aecsocket.player.data.*
import kotlinx.coroutines.*
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import kotlin.coroutines.CoroutineContext

class SearchViewModel : ViewModel() {
    private var results: MutableList<DataItem>? = null
    private val dResults = MutableLiveData<List<DataItem>?>()
    private var resultsJob: Job? = null

    fun getResults(): LiveData<List<DataItem>?> = dResults

    private fun postResults(results: MutableList<DataItem>?) {
        this.results = results
        dResults.postValue(this.results)
    }

    private fun addResult(item: DataItem?) {
        if (item == null)
            return
        results?.let {
            println("added, cur = $it")
            it.add(item)
            dResults.postValue(results)
        }
    }

    fun loadResults(
        query: String,
        scope: CoroutineScope,
        dispatcher: CoroutineContext
    ) {
        this.results = mutableListOf()
        dResults.postValue(null)
        resultsJob?.cancel()

        resultsJob = scope.launch {
            try {
                // try getting from a URL
                val service = NewPipe.getServiceByUrl(query)
                scope.launch(dispatcher) {
                    DataItem.requestStreams(query, service, scope)?.let {
                        postResults(it.toMutableList())
                    }
                }
            } catch (ex: ExtractionException) {
                // not a known service, do a search instead
                scope.launch(dispatcher) {
                    val service = NewPipe.getService(0) // TODO change service
                    val search = SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(query))
                    for (info in search.relatedItems) {
                        when (info.infoType) {
                            InfoItem.InfoType.STREAM -> {
                                scope.launch(dispatcher) {
                                    val stream = StreamInfo.getInfo(service, info.url)
                                    println("found a stream: $stream, cur size = ${results?.size}")
                                    addResult(stream.asData(service))
                                    /*val results = (results.value ?: emptyList())
                                    val next = results + StreamInfo.getInfo(service, info.url)
                                        .asData(service)
                                    println("NOW: ${results.size} NEXT: ${next.size}")
                                    this@SearchViewModel.results.postValue(next)*/
                                }
                            }
                            InfoItem.InfoType.PLAYLIST -> {
                                scope.launch(dispatcher) {
                                    addResult(PlaylistInfo.getInfo(service, info.url).asData(service))
                                    //results.postValue((results.value ?: emptyList()) + PlaylistInfo.getInfo(service, info.url)
                                    //    .asData(service))
                                }
                            }
                            InfoItem.InfoType.CHANNEL -> {
                                scope.launch(dispatcher) {
                                    addResult(ChannelInfo.getInfo(service, info.url).asData(service))
                                    //results.postValue((results.value ?: emptyList()) + ChannelInfo.getInfo(service, info.url)
                                    //    .asData(service))
                                }
                            }
                            else -> {
                                // todo unhandled
                            }
                        }
                    }
                }
            }
        }
    }
}