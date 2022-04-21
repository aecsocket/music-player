package com.github.aecsocket.player.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.marginBottom
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.github.aecsocket.player.App
import com.github.aecsocket.player.data.QueueAdapter
import com.github.aecsocket.player.databinding.FragmentQueueBinding
import com.github.aecsocket.player.media.StreamQueue
import com.github.aecsocket.player.modPadding

class QueueFragment : Fragment() {
    private lateinit var adapter: QueueAdapter
    private lateinit var queueListener: StreamQueue.Listener

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentQueueBinding.inflate(inflater, container, false)
        val context = requireContext()
        val player = App.player(context)

        val queueItems = binding.queueItems
        val queueClear = binding.queueClear
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.RIGHT
        ) {
            override fun onMove(view: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                player.queue.move(from.layoutPosition, to.layoutPosition)
                return true
            }

            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {
                player.queue.remove(holder.layoutPosition)
            }

            override fun isLongPressDragEnabled() = false
        })
        adapter = QueueAdapter(touchHelper, player.queue)
        queueItems.adapter = adapter
        touchHelper.attachToRecyclerView(queueItems)

        queueClear.setOnClickListener {
            player.queue.clear()
        }

        binding.root.post {
            queueItems.modPadding(bottom = queueClear.height + (queueClear.marginBottom * 2))
        }

        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter.dispose()
    }
}