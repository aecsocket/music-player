package com.github.aecsocket.himom.viewmodel

import androidx.lifecycle.*
import com.github.aecsocket.himom.data.DataItem
import com.github.aecsocket.himom.data.StreamData
import com.github.aecsocket.himom.data.asItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo

class SearchViewModel : ViewModel() {
    private val results = MutableLiveData<List<DataItem>>()

    fun getResults(): LiveData<List<DataItem>> = results

    fun loadResults(query: String, lifecycleScope: LifecycleCoroutineScope) {
        println("query = $query")
        results.postValue(emptyList())
        try {
            val service = NewPipe.getServiceByUrl(query)
            println("service = $service")
            lifecycleScope.launch(Dispatchers.IO) {
                DataItem.fromUrl(query, service).collect {
                    results.postValue((results.value ?: emptyList()) + it)
                }
            }
        } catch (ex: ExtractionException) {
            lifecycleScope.launch(Dispatchers.IO) {
                val service = NewPipe.getService(0)
                val search = SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(query))
                for (info in search.relatedItems) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val stream = StreamInfo.getInfo(service, info.url)
                            results.postValue((results.value ?: emptyList()) + stream.asItems())
                        } catch (ex: ParsingException) {
                            // todo this is some unhandled type like a channel
                        }
                    }
                }
            }
        }
    }
}