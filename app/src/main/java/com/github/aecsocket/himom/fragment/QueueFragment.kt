package com.github.aecsocket.himom.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.github.aecsocket.himom.App
import com.github.aecsocket.himom.databinding.FragmentQueueBinding

class QueueFragment : Fragment() {
    private val adapter = QueueItemAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentQueueBinding.inflate(inflater, container, false)
        val context = context ?: return binding.root
        val player = (context.applicationContext as App).player

        binding.queueItems.adapter = adapter
        player.queue.getState().observe(viewLifecycleOwner) { adapter.submitList(it.items) }

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

        return binding.root
    }
}