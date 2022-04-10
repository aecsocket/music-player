package com.github.aecsocket.player.fragment

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.facebook.shimmer.ShimmerFrameLayout
import com.github.aecsocket.player.App
import com.github.aecsocket.player.error.ErrorHandler
import com.github.aecsocket.player.R
import com.github.aecsocket.player.databinding.FragmentSearchBinding
import com.github.aecsocket.player.error.ErrorInfo
import com.github.aecsocket.player.viewmodel.SearchViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.NullPointerException

class SearchFragment : Fragment() {
    private val adapter = GenericItemAdapter()
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var shimmer: ShimmerFrameLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentSearchBinding.inflate(inflater, container, false)
        val context = context ?: return binding.root
        val player = (context.applicationContext as App).player
        binding.searchItems.adapter = adapter

        shimmer = binding.searchItemsShimmer
        stopShimmer()
        viewModel.getResults().observe(viewLifecycleOwner) { state ->
            if (state != null) {
                stopShimmer()
            }
            adapter.submitList(state)
        }
        // TODO observe queue changes here
        player.queue.getState().observe(viewLifecycleOwner) { state ->
            if (state != null) {
            }
        }

        val searchAction = binding.searchAction
        binding.searchField.apply {
            addTextChangedListener { text ->
                if (text?.length ?: 0 > 0) {
                    searchAction.setImageResource(R.drawable.ic_clear)
                    searchAction.setOnClickListener { this.text.clear() }
                } else {
                    searchAction.setImageResource(R.drawable.ic_search)
                    searchAction.setOnClickListener(null)
                }
            }
            setOnEditorActionListener { view, action, event ->
                if (
                    action == EditorInfo.IME_ACTION_SEARCH
                    || event?.keyCode == KeyEvent.KEYCODE_ENTER
                ) {
                    if (action == EditorInfo.IME_ACTION_SEARCH || event?.action == KeyEvent.ACTION_DOWN) {
                        val url = view.editableText.toString()
                        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                            .hideSoftInputFromWindow(view.windowToken, 0)
                        startShimmer()
                        viewModel.loadResults(url, lifecycleScope, Dispatchers.IO + CoroutineExceptionHandler { _, ex ->
                            lifecycleScope.launch(Dispatchers.Main) {
                                ErrorHandler.handle(this@SearchFragment, ErrorInfo(ErrorHandler.getMessage(context, ex), ex))
                                stopShimmer()
                            }
                        })
                    }
                    return@setOnEditorActionListener true
                }
                return@setOnEditorActionListener false
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.searchBarContainer) { view, windowInsets ->
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            }
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.searchItems) { view, windowInsets ->
            val padding = binding.searchBarContainer.height + windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            shimmer.setPadding(shimmer.paddingLeft, padding, shimmer.paddingRight, shimmer.paddingBottom)
            view.setPadding(view.paddingLeft, padding, view.paddingRight, view.paddingBottom)
            WindowInsetsCompat.CONSUMED
        }

        return binding.root
    }

    private fun startShimmer() {
        shimmer.visibility = View.VISIBLE
        shimmer.startShimmer()
    }

    private fun stopShimmer() {
        shimmer.visibility = View.INVISIBLE
        shimmer.stopShimmer()
    }
}