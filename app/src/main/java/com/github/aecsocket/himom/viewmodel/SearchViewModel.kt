package com.github.aecsocket.himom.viewmodel

import androidx.lifecycle.*
import com.github.aecsocket.himom.data.DataItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    private val results = MutableLiveData<List<DataItem>>()

    fun getResults(): LiveData<List<DataItem>> = results

    fun loadResults(url: String, lifecycleScope: LifecycleCoroutineScope) {
        lifecycleScope.launch(Dispatchers.IO) {
            DataItem.fromUrl(url).collect {
                results.postValue((results.value ?: emptyList()) + it)
            }
        }
    }
}