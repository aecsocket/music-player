package com.github.aecsocket.player.media

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.github.aecsocket.player.data.StreamData
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
            if (other.isSame(stream))
                return index
        }
        return null
    }

    private fun post(mapper: (State) -> State) {
        val cur = stateOr()
        val mapped = mapper(cur)
        val next = State(mapped.items, if (mapped.items.isEmpty()) NOTHING_PLAYING else mapped.index)
        // TODO: this is unsafe and whatever cause if [a, b] and [a, c] then not [a, b, c].
        // Cry.
        //state.postValue(next)
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

    fun add(element: StreamData): Int {
        indexOf(element)?.let { return it }
        var index: Int? = null
        post {
            index = it.index + 1
            State(
                it.items.toMutableList().apply { add(element) },
                it.index
            )
        }
        return index ?: throw IllegalStateException("added element without index")
    }

    fun remove(index: Int) {
        post { State(
            it.items.toMutableList().apply { removeAt(index) },
            if (
                it.index > index // if it is at or above the item we just removed
                || it.index == it.items.size - 1 // if it is the last item
            ) it.index - 1
            else it.index
        ) }
    }

    fun remove(element: StreamData) {
        indexOf(element)?.let { remove(it) }
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

    fun clear() {
        post { State(emptyList(), NOTHING_PLAYING) }
    }

    fun addOrSelect(element: StreamData) {
        setIndex(indexOf(element) ?: add(element))
    }

    fun addInitial(element: StreamData) {
        val state = stateOr()
        if (state.index == NOTHING_PLAYING) {
            addOrSelect(element)
        } else {
            add(element)
        }
    }
}