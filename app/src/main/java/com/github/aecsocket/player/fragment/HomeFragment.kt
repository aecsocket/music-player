package com.github.aecsocket.player.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.github.aecsocket.player.SettingsActivity
import com.github.aecsocket.player.databinding.FragmentHomeBinding
import com.github.aecsocket.player.modPadding

class HomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentHomeBinding.inflate(inflater, container, false)
        val context = requireContext()
        val window = requireActivity().window

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(context, SettingsActivity::class.java))
        }

        binding.root.post {
            val insets = WindowInsetsCompat.toWindowInsetsCompat(window.decorView.rootWindowInsets)
            val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.homeTop.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = inset.top
            }
        }

        return binding.root
    }
}