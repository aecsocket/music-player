package com.github.aecsocket.player.data

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.aecsocket.player.R
import com.github.aecsocket.player.databinding.ItemSearchCategoryBinding
import com.github.aecsocket.player.media.StreamQueue
import kotlinx.coroutines.*

class ItemCategoryAdapter(
    val queue: StreamQueue,
    val scope: CoroutineScope
) : ListAdapter<ItemCategory, ItemCategoryAdapter.ViewHolder>(ItemCategory.itemCallback()) {
    class ViewHolder(
        val queue: StreamQueue,
        val scope: CoroutineScope,
        binding: ItemSearchCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        val title = binding.categoryTitle
        val items = binding.categoryItems
        var adapter: ItemDataAdapter? = null

        fun bindTo(category: ItemCategory) {
            val context = itemView.context
            title.text = context.getString(when (category.type) {
                ITEM_TYPE_SONG -> R.string.songs
                ITEM_TYPE_VIDEO -> R.string.videos
                ITEM_TYPE_ARTIST -> R.string.artists
                ITEM_TYPE_CHANNEL -> R.string.channels
                ITEM_TYPE_ALBUM -> R.string.albums
                ITEM_TYPE_PLAYLIST -> R.string.playlists
                else -> throw IllegalArgumentException("unknown category ${category.type}")
            })
            adapter = ItemDataAdapter(queue, scope).also {
                items.adapter = it
                it.submitList(category.items)
            }
        }

        fun dispose() {
            adapter?.dispose()
            items.adapter = null
        }

        companion object {
            fun from(parent: ViewGroup, queue: StreamQueue, scope: CoroutineScope) = ViewHolder(
                queue, scope,
                ItemSearchCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder.from(parent, queue, scope)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindTo(getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.dispose()
    }

    override fun onDetachedFromRecyclerView(recycler: RecyclerView) {
        for (i in 0 until recycler.childCount) {
            (recycler.getChildViewHolder(recycler.getChildAt(i)) as ViewHolder)
                .dispose()
        }
    }
}
