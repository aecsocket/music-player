package com.github.aecsocket.player.data

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.aecsocket.player.App
import com.github.aecsocket.player.R
import com.github.aecsocket.player.databinding.ItemArtistBinding
import com.github.aecsocket.player.databinding.ItemGenericBinding
import com.github.aecsocket.player.error.ErrorHandler
import com.github.aecsocket.player.error.ErrorInfo
import com.github.aecsocket.player.media.StreamQueue
import kotlinx.coroutines.*
import java.lang.IllegalStateException

const val ITEM_TYPE_STREAM = 0
const val ITEM_TYPE_LIST = 1
const val ITEM_TYPE_ARTIST = 2

class ItemDataAdapter(
    private val queue: StreamQueue,
    private val scope: CoroutineScope
) : ListAdapter<ItemData, ItemDataAdapter.BaseHolder>(ItemData.itemCallback()) {
    sealed class BaseHolder(
        val view: View,
        val primary: TextView,
        val secondary: TextView,
        val art: ImageView
    ) : RecyclerView.ViewHolder(view) {
        open fun bindTo(item: ItemData) {
            val context = itemView.context
            primary.text = item.primaryText(context)
            secondary.text = item.secondaryText(context)
            item.art?.into(art)
        }
    }

    class StreamHolder(
        val queue: StreamQueue,
        binding: ItemGenericBinding
    ) : BaseHolder(
        binding.root, binding.itemPrimary, binding.itemSecondary, binding.itemArt
    ) {
        val base = binding.item
        val add = binding.itemAdd

        override fun bindTo(item: ItemData) {
            super.bindTo(item)
            val player = App.player(view.context)
            if (item !is StreamData)
                throw IllegalStateException("binding ${item.javaClass.simpleName} to StreamHolder")

            // todo we could compute the index once and use that, but idk
            if (queue.contains(item)) {
                add.setImageResource(R.drawable.ic_check)
                add.setOnClickListener {
                    queue.remove(item)
                }
            } else {
                add.setImageResource(R.drawable.ic_list_add)
                add.setOnClickListener {
                    queue.append(item)
                }
            }

            base.setOnClickListener {
                player.queue.appendPlay(item)
                player.seekToDefault()
                player.play()
            }
            add.setOnClickListener {
                player.queue.append(item)
            }
        }

        companion object {
            fun from(parent: ViewGroup, queue: StreamQueue) = StreamHolder(
                queue,
                ItemGenericBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    class StreamListHolder(
        val queue: StreamQueue,
        binding: ItemGenericBinding,
        val scope: CoroutineScope
    ) : BaseHolder(
        binding.root, binding.itemPrimary, binding.itemSecondary, binding.itemArt
    ) {
        val base = binding.item
        val add = binding.itemAdd

        override fun bindTo(item: ItemData) {
            super.bindTo(item)
            val context = view.context
            if (item !is StreamListData)
                throw IllegalStateException("binding ${item.javaClass.simpleName} to StreamListHolder")

            add.setOnClickListener {
                scope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, ex ->
                    scope.launch(Dispatchers.Main) {
                        ErrorHandler.handle(context, R.string.error_info_list,
                            ErrorInfo(context, ex))
                    }
                }) {
                    val streams = item.fetchStreams(scope)
                    scope.launch(Dispatchers.Main) {
                        streams.forEach { queue.appendInitial(it) }
                    }
                }
            }
        }

        companion object {
            fun from(parent: ViewGroup, queue: StreamQueue, scope: CoroutineScope) = StreamListHolder(
                queue,
                ItemGenericBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                scope)
        }
    }

    class ArtistHolder(val binding: ItemArtistBinding) : BaseHolder(
        binding.root, binding.itemPrimary, binding.itemSecondary, binding.itemArt
    ) {
        override fun bindTo(item: ItemData) {
            super.bindTo(item)

        }

        companion object {
            fun from(parent: ViewGroup) = ArtistHolder(
                ItemArtistBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    private val queueListener = object : StreamQueue.Listener {
    }

    init {
        queue.addListener(queueListener)
    }

    fun dispose() {
        queue.removeListener(queueListener)
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is StreamData -> ITEM_TYPE_STREAM
        is StreamListData -> ITEM_TYPE_LIST
        is ArtistData -> ITEM_TYPE_ARTIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        ITEM_TYPE_STREAM -> StreamHolder.from(parent, queue)
        ITEM_TYPE_LIST -> StreamListHolder.from(parent, queue, scope)
        ITEM_TYPE_ARTIST -> ArtistHolder.from(parent)
        else -> throw IllegalStateException("cannot create view holder from $viewType")
    }

    override fun onBindViewHolder(holder: BaseHolder, position: Int) =
        holder.bindTo(getItem(position))
}
