package com.github.aecsocket.player.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.github.aecsocket.player.App
import com.github.aecsocket.player.databinding.FragmentQueueBinding

class QueueFragment : Fragment() {
    private val adapter = QueueItemAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentQueueBinding.inflate(inflater, container, false)
        val context = context ?: return binding.root
        val player = (context.applicationContext as App).player

        val items = binding.queueItems
        items.adapter = adapter
        player.queue.getState().observe(viewLifecycleOwner) {
            adapter.submitState(it)
            val childCount = items.childCount
            for (i in 0..childCount) {
                val child = items.getChildAt(i) ?: continue
                val holder = items.getChildViewHolder(child) as QueueItemAdapter.ViewHolder
                holder.updateSelected(it.index)
            }
        }

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(view: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                player.queue.move(from.layoutPosition, to.layoutPosition)
                return true
            }

            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {
                val position = holder.layoutPosition
                player.queue.remove(position)
            }
        }).attachToRecyclerView(binding.queueItems)

        binding.queueClearAll.setOnClickListener {
            player.queue.clear()
        }
        binding.queueJump.setOnClickListener {
            items.layoutManager!!.scrollToPosition(player.queue.getState().value?.index ?: 0)
        }

        return binding.root
    }
}