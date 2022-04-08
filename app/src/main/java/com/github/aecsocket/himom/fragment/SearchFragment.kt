package com.github.aecsocket.himom.fragment

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.github.aecsocket.himom.App
import com.github.aecsocket.himom.ExceptionHandler
import com.github.aecsocket.himom.data.DataItem
import com.github.aecsocket.himom.data.StreamData
import com.github.aecsocket.himom.databinding.FragmentSearchBinding
import com.github.aecsocket.himom.viewmodel.SearchViewModel
import com.google.android.exoplayer2.MediaItem
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.lang.Exception
import java.net.UnknownHostException

class SearchFragment : Fragment() {
    private val adapter = GenericItemAdapter()
    private val viewModel: SearchViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentSearchBinding.inflate(inflater, container, false)
        val context = context ?: return binding.root
        binding.searchItems.adapter = adapter

        viewModel.getResults().observe(viewLifecycleOwner) { adapter.submitList(it) }

        binding.searchField.setOnEditorActionListener { view, action, event ->
            if (
                action == EditorInfo.IME_ACTION_SEARCH
                || event?.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                if (action == EditorInfo.IME_ACTION_SEARCH || event?.action == KeyEvent.ACTION_DOWN) {
                    val url = view.editableText.toString()
                    view.editableText.clear()
                    try {
                        viewModel.loadResults(url, lifecycleScope)
                    } catch (ex: Exception) {
                        ExceptionHandler.handle(context, ex)
                    }
                }
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.searchBarContainer) { view, windowInsets ->
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            }
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.searchItems) { view, windowInsets ->
            view.setPadding(view.paddingLeft,
                binding.searchBarContainer.height + windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top,
                view.paddingRight, view.paddingBottom)
            WindowInsetsCompat.CONSUMED
        }

        return binding.root
    }
}