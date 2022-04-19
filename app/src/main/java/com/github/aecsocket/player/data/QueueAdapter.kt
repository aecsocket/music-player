package com.github.aecsocket.player.data

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.github.aecsocket.player.App
import com.github.aecsocket.player.databinding.ItemQueueBinding
import com.github.aecsocket.player.media.StreamQueue

class QueueAdapter(
    private val touchHelper: ItemTouchHelper,
    private val queue: StreamQueue
) : RecyclerView.Adapter<QueueAdapter.ViewHolder>() {
    class ViewHolder(
        view: ItemQueueBinding,
        private val touchHelper: ItemTouchHelper
    ) : RecyclerView.ViewHolder(view.root) {
        private val base = view.item
        private val primary = view.itemPrimary
        private val secondary = view.itemSecondary
        private val art = view.itemArt
        private val dragHandle = view.itemDragHandle

        @SuppressLint("ClickableViewAccessibility")
        fun bindTo(item: StreamData, selected: Boolean) {
            val context = itemView.context
            val player = App.player(context)
            base.isSelected = selected
            primary.text = item.primaryText(context)
            secondary.text = item.secondaryText(context)
            item.art?.into(art)

            dragHandle.setOnTouchListener { view, event ->
                view.performClick()
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(this)
                }
                false
            }

            base.setOnClickListener {
                player.queue.setIndex(layoutPosition)
            }
        }

        companion object {
            fun from(parent: ViewGroup, touchHelper: ItemTouchHelper) = ViewHolder(
                ItemQueueBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                touchHelper)
        }
    }

    private val queueListener = object : StreamQueue.Listener {
        override fun onSelect(from: Int, to: Int) {
            notifyItemChanged(from)
            notifyItemChanged(to)
        }
        override fun onAppend(index: Int, size: Int) {
            notifyItemRangeInserted(index, size)
        }
        override fun onRemove(index: Int) {
            notifyItemRemoved(index)
            notifyItemChanged(queue.getIndex())
        }
        override fun onMove(from: Int, to: Int) {
            notifyItemMoved(from, to)
        }
        override fun onClear(size: Int) {
            notifyItemRangeRemoved(0, size)
        }
    }

    init {
        queue.addListener(queueListener)
    }

    fun dispose() {
        queue.removeListener(queueListener)
    }

    override fun getItemCount() = queue.getSize()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder.from(parent, touchHelper)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bindTo(queue[position], queue.getIndex() == position)
}
