package com.github.aecsocket.himom.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.aecsocket.himom.App
import com.github.aecsocket.himom.StreamQueue
import com.github.aecsocket.himom.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {
    val queueAdapter = GenericItemAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentHomeBinding.inflate(inflater, container, false)
        binding.queue.adapter = queueAdapter
        //App.queue.queue.observe(viewLifecycleOwner) { queueAdapter.submitList(it) }

        return binding.root
    }
}