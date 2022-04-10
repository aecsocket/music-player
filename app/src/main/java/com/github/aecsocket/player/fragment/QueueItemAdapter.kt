package com.github.aecsocket.player.fragment

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.aecsocket.player.App
import com.github.aecsocket.player.R
import com.github.aecsocket.player.data.DataItem
import com.github.aecsocket.player.data.StreamData
import com.github.aecsocket.player.media.StreamQueue
import com.github.aecsocket.player.resolve

class QueueItemAdapter(var index: Int = -1, val touchHelper: ItemTouchHelper) : ListAdapter<StreamData, QueueItemAdapter.ViewHolder>(DataItem.itemCallback()) {
    init {
        setHasStableIds(true)
    }

    class ViewHolder(view: View, val touchHelper: ItemTouchHelper) : RecyclerView.ViewHolder(view) {
        private val player = (view.context.applicationContext as App).player
        val base: View = view.findViewById(R.id.item)
        val primary: TextView = view.findViewById(R.id.itemPrimary)
        val secondary: TextView = view.findViewById(R.id.itemSecondary)
        val art: ImageView = view.findViewById(R.id.itemArt)
        val dragHandle: ImageView = view.findViewById(R.id.itemDragHandle)

        @SuppressLint("ClickableViewAccessibility")
        fun bindTo(item: StreamData) {
            val context = itemView.context
            primary.text = item.getPrimaryText(context)
            secondary.text = item.getSecondaryText(context)
            item.getArt(context)?.into(art)

            dragHandle.setOnTouchListener { view, event ->
                view.performClick()
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(this)
                }
                false
            }

            base.setOnClickListener { player.queue.setIndex(layoutPosition) }
        }

        fun updateSelected(selected: Int) {
            val context = itemView.context
            if (selected == layoutPosition) {
                // currently playing
                context.theme.resolve(R.attr.accent_alt)?.let { accent ->
                    listOf(primary, secondary).forEach { it.setTextColor(accent) }
                }
                base.setOnClickListener(null)
            } else {
                // not playing
                context.theme.resolve(R.attr.fg)?.let { primary.setTextColor(it) }
                context.theme.resolve(R.attr.fg_alt)?.let { secondary.setTextColor(it) }
                base.setOnClickListener { player.queue.setIndex(layoutPosition) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_queue, parent, false), touchHelper)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindTo(getItem(position))
        holder.updateSelected(index)
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    fun submitState(state: StreamQueue.State) {
        submitList(state.items)
        index = state.index
    }
}