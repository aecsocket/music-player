package com.github.aecsocket.himom.fragment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.aecsocket.himom.App
import com.github.aecsocket.himom.R
import com.github.aecsocket.himom.data.ArtistData
import com.github.aecsocket.himom.data.DataItem
import com.github.aecsocket.himom.data.StreamData
import java.lang.IllegalStateException

const val ITEM_TYPE_SKELETON = 0
const val ITEM_TYPE_STREAM = 1
const val ITEM_TYPE_ARTIST = 2

class GenericItemAdapter : ListAdapter<DataItem, GenericItemAdapter.BaseHolder>(DataItem.itemCallback()) {
    init {
        setHasStableIds(true)
    }

    open class BaseHolder(view: View) : RecyclerView.ViewHolder(view) {
        val base: View = view.findViewById(R.id.item)
        val primary: TextView = view.findViewById(R.id.itemPrimary)
        val secondary: TextView = view.findViewById(R.id.itemSecondary)
        val art: ImageView = view.findViewById(R.id.itemArt)

        open fun bindTo(item: DataItem) {
            val context = itemView.context
            primary.text = item.getPrimaryText(context)
            secondary.text = item.getSecondaryText(context)
            item.getArt(context)?.placeholder(R.drawable.placeholder)?.into(art)
        }
    }

    class StreamHolder(view: View) : BaseHolder(view) {
        private val player = (view.context.applicationContext as App).player
        private val addToQueue: ImageButton = view.findViewById(R.id.itemAddToQueue)

        override fun bindTo(item: DataItem) {
            super.bindTo(item)
            val stream = item as StreamData
            base.setOnClickListener {
                player.queue.addOrSelect(stream)
                addedToQueue()
            }
            if (player.queue.indexOf(stream) == null) {
                addToQueue.setOnClickListener {
                    player.queue.addUnique(stream)
                    addedToQueue()
                }
            } else {
                addedToQueue()
            }
        }

        private fun addedToQueue() {
            addToQueue.setOnClickListener(null)
            addToQueue.setImageResource(R.drawable.ic_list_added)
        }

        companion object {
            fun from(parent: ViewGroup): StreamHolder = StreamHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_stream, parent, false))
        }
    }

    class ArtistHolder(view: View) : BaseHolder(view) {
        companion object {
            fun from(parent: ViewGroup): ArtistHolder = ArtistHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_artist, parent, false))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseHolder {
        return when (viewType) {
            ITEM_TYPE_STREAM -> StreamHolder.from(parent)
            ITEM_TYPE_ARTIST -> ArtistHolder.from(parent)
            else -> throw IllegalStateException("cannot create ViewHolder of $viewType")
        }
    }

    override fun onBindViewHolder(holder: BaseHolder, position: Int) {
        holder.bindTo(getItem(position))
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is StreamData -> ITEM_TYPE_STREAM
            is ArtistData -> ITEM_TYPE_ARTIST
        }
    }

    override fun getItemId(position: Int): Long = getItem(position).id
}