package com.github.aecsocket.himom.fragment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.aecsocket.himom.App
import com.github.aecsocket.himom.R
import com.github.aecsocket.himom.data.DataItem
import com.github.aecsocket.himom.data.StreamData

class QueueItemAdapter : ListAdapter<StreamData, QueueItemAdapter.ViewHolder>(DataItem.itemCallback()) {
    init {
        setHasStableIds(true)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val player = (view.context.applicationContext as App).player
        val base: View = view.findViewById(R.id.item)
        val primary: TextView = view.findViewById(R.id.itemPrimary)
        val secondary: TextView = view.findViewById(R.id.itemSecondary)
        val art: ImageView = view.findViewById(R.id.itemArt)

        fun bindTo(item: StreamData) {
            val context = itemView.context
            primary.text = item.getPrimaryText(context)
            secondary.text = item.getSecondaryText(context)
            item.getArt(context)?.placeholder(R.drawable.placeholder)?.into(art)
            base.setOnClickListener {
                player.queue.setIndex(layoutPosition)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_queue, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindTo(getItem(position))
    }

    override fun getItemId(position: Int): Long = getItem(position).id
}