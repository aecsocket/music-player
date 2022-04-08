package com.github.aecsocket.himom.media

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.github.aecsocket.himom.data.StreamData
import com.google.android.exoplayer2.MediaItem
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.*
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.lang.IllegalStateException
import kotlin.math.max
import kotlin.math.min

const val NOTHING_PLAYING = -1

class StreamQueue {
    data class State(val items: List<StreamData>, val index: Int)

    private val empty = State(emptyList(), NOTHING_PLAYING)

    private val state = MutableLiveData<State>().apply {
        postValue(State(emptyList(), NOTHING_PLAYING))
    }
    private val current = MutableLiveData<StreamData?>()

    fun getState(): LiveData<State> = state
    fun getCurrent(): LiveData<StreamData?> = current

    private fun stateOr(): State = state.value ?: state.value ?: empty

    private fun resetPlaying() {
        if (current.value != null) {
            current.postValue(null)
        }
    }

    fun getIndex(): Int = stateOr().index
    fun setIndex(index: Int) {
        val cur = stateOr()
        if (cur.items.isEmpty())
            return
        val mIndex = max(0, min(cur.items.size - 1, index))
        state.postValue(State(cur.items, mIndex))
        current.postValue(cur.items[mIndex])
    }

    fun offsetIndex(offset: Int) {
        val cur = stateOr()
        val index = cur.index + offset
        val size = cur.items.size
        setIndex(if (index >= size) 0 else if (index < 0) size - 1 else index)
    }

    fun resetIndex() {
        val cur = stateOr()
        state.postValue(State(cur.items, NOTHING_PLAYING))
        resetPlaying()
    }

    fun indexOf(stream: StreamData): Int? {
        (state.value ?: return null).items.forEachIndexed { index, other ->
            if (other.equalMeta(stream))
                return index
        }
        return null
    }

    fun indexTo(stream: StreamData): Boolean {
        return indexOf(stream)?.let {
            setIndex(it)
            true
        } ?: false
    }

    private fun post(mapper: (State) -> State) {
        val cur = stateOr()
        val mapped = mapper(cur)
        val next = State(mapped.items, if (mapped.items.isEmpty()) NOTHING_PLAYING else mapped.index)
        state.value = next

        fun <T> safeGet(index: Int, list: List<T>): T? =
            if (index < 0) null else if (index >= list.size) null else list[index]

        val curStream = current.value
        val nextStream = safeGet(next.index, next.items)
        if (next.items.isEmpty()) {
            resetPlaying()
        } else if (curStream != null && nextStream != null && (curStream.id != nextStream.id)) {
            // we just switched to a new stream
            current.postValue(next.items[next.index])
        }
    }

    fun insert(index: Int, elements: Collection<StreamData>) {
        if (elements.isEmpty())
            return
        post { State(
            it.items.toMutableList().apply { addAll(index, elements) },
            if (it.index >= index) it.index + elements.size else it.index
        ) }
    }

    fun insert(index: Int, element: StreamData) {
        post { State(
            it.items.toMutableList().apply { add(index, element) },
            if (it.index >= index) it.index + 1 else it.index
        ) }
    }

    fun add(elements: Collection<StreamData>) {
        if (elements.isEmpty())
            return
        post { State(
            it.items.toMutableList().apply { addAll(elements) },
            it.index
        ) }
    }

    fun add(element: StreamData): Int {
        var index: Int? = null
        post {
            index = it.index + 1
            State(
                it.items.toMutableList().apply { add(element) },
                it.index
            )
        }
        return index ?: throw IllegalStateException("added without getting index")
    }

    fun addUnique(element: StreamData): Int? {
        return if (indexOf(element) == null) add(element) else null
    }

    fun remove(from: Int, to: Int) {
        post { State(
            it.items.toMutableList().apply { for (i in from..to) { removeAt(i) } },
            if (it.index > from) if (it.index <= to) from else it.index - (to - from) else it.index
        ) }
    }

    fun remove(index: Int) {
        post { State(
            it.items.toMutableList().apply { removeAt(index) },
            if (it.index > index || index == it.items.size - 1) index - 1 else it.index
        ) }
    }

    fun move(from: Int, to: Int) {
        post { State(
            it.items.toMutableList().apply {
                add(to, removeAt(from))
            },
            if (from == it.index) to
            else if (it.index in (from + 1)..to) it.index - 1
            else if (it.index in to until from) it.index + 1
            else it.index
        ) }
    }

    fun addOrSelect(element: StreamData) {
        if (!indexTo(element)) {
            val index = add(element)
            setIndex(index)
        }
    }

    private suspend fun addStream(
        url: String,
        callback: () -> Unit
    ) {
        val info = StreamInfo.getInfo(url)
        if (info.audioStreams.size > 0) {
            val stream = info.audioStreams[0]
            withContext(Dispatchers.Main) {
                val added = StreamData(
                    media = MediaItem.fromUri(stream.url),
                    track = info.name,
                    artist = info.uploaderName,
                    art = Picasso.get().load(info.thumbnailUrl)
                )
                if (state.value?.items?.isNotEmpty() == true) {
                    addUnique(added)
                } else {
                    addOrSelect(added)
                }
                callback()
            }
        }
    }
}