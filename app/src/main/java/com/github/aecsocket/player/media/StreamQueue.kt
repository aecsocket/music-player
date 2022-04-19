package com.github.aecsocket.player.media

import com.github.aecsocket.player.data.StreamData
import java.util.concurrent.atomic.AtomicInteger

const val NOT_PLAYING = -1

class StreamQueue {
    interface Listener {
        fun onSelect(from: Int, to: Int) {}
        fun onAppend(index: Int, size: Int) {}
        fun onRemove(index: Int) {}
        fun onMove(from: Int, to: Int) {}
        fun onClear(size: Int) {}
    }

    private val items = ArrayList<StreamData>()
    private val index = AtomicInteger(-1)
    private val listeners = ArrayList<Listener>()

    fun getItems() = items.toList()
    fun getIndex() = index.get()

    fun addListener(listener: Listener) = listeners.add(listener)
    fun removeListener(listener: Listener) = listeners.remove(listener)

    fun getSize() = items.size

    operator fun get(index: Int) = items[index]

    fun getOr(index: Int) = if (index < 0 || index >= items.size) null else items[index]

    fun indexOf(elem: StreamData): Int? {
        items.forEachIndexed { index, other ->
            if (other.same(elem))
                return index
        }
        return null
    }

    fun contains(elem: StreamData) = indexOf(elem) != null

    fun setIndex(index: Int) {
        if (index != NOT_PLAYING && (index < 0 || index >= items.size))
            throw IndexOutOfBoundsException()
        val cur = this.index.get()
        if (index == cur)
            return
        this.index.set(index)
        listeners.forEach { it.onSelect(cur, index) }
    }

    fun offsetIndex(offset: Int) {
        val index = this.index.get() + offset
        setIndex(if (index >= items.size) 0 else if (index < 0) items.size - 1 else index)
    }

    fun resetIndex() {
        setIndex(0)
    }


    private fun forceAppend(elems: Collection<StreamData>): Int {
        val from = items.size
        if (elems.isEmpty())
            return from
        items.addAll(elems)
        listeners.forEach { it.onAppend(from, elems.size) }
        return from
    }

    fun append(elems: Collection<StreamData>): Int {
        val added = elems.filter { indexOf(it) == null }
        return forceAppend(added)
    }

    fun append(elem: StreamData): Int {
        return append(setOf(elem))
    }

    fun appendPlay(elem: StreamData) {
        indexOf(elem)?.let {
            setIndex(it)
        } ?: setIndex(forceAppend(setOf(elem)))
    }

    fun appendInitial(elem: StreamData) {
        if (index.get() == NOT_PLAYING) {
            appendPlay(elem)
        } else {
            append(elem)
        }
    }

    fun remove(index: Int) {
        val size = items.size
        items.removeAt(index)

        val cur = this.index.get()
        if (cur == size - 1 || cur > index)
            setIndex(cur - 1)
        listeners.forEach { it.onRemove(index) }
    }

    fun remove(elem: StreamData) {
        indexOf(elem)?.let { remove(it) }
    }

    fun move(from: Int, to: Int) {
        items.add(to, items.removeAt(from))
        listeners.forEach { it.onMove(from, to) }

        val cur = index.get()
        this.index.set(if (from == cur) to
        else if (cur in (from + 1)..to) cur - 1
        else if (cur in to until from) cur + 1
        else cur)
    }

    fun clear() {
        val size = items.size
        items.clear()
        listeners.forEach { it.onClear(size) }
        this.setIndex(NOT_PLAYING)
    }
}
