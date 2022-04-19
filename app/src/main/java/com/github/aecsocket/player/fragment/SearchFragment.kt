package com.github.aecsocket.player.fragment

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.facebook.shimmer.ShimmerFrameLayout
import com.github.aecsocket.player.App
import com.github.aecsocket.player.R
import com.github.aecsocket.player.data.ItemDataAdapter
import com.github.aecsocket.player.databinding.FragmentSearchBinding
import com.github.aecsocket.player.error.ErrorHandler
import com.github.aecsocket.player.error.ErrorInfo
import com.github.aecsocket.player.modPadding
import com.github.aecsocket.player.viewmodel.SearchViewModel
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var adapter: ItemDataAdapter
    lateinit var searchBar: View
    lateinit var searchResults: RecyclerView
    lateinit var searchResultsShimmer: ShimmerFrameLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentSearchBinding.inflate(inflater, container, false)
        val context = requireContext()
        val player = App.player(context)
        val inputMethods = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        searchBar = binding.searchBar
        searchResults = binding.searchResults
        searchResultsShimmer = binding.searchResultsShimmer
        val searchBtn = binding.searchBtn
        val searchText = binding.searchText
        searchText.addTextChangedListener { text ->
            if (text?.length ?: 0 > 0) {
                searchBtn.setImageResource(R.drawable.ic_clear)
                searchBtn.setOnClickListener {
                    searchText.text.clear()
                }
            } else {
                searchBtn.setImageResource(R.drawable.ic_search)
                searchBtn.setOnClickListener(null)
            }
        }
        searchText.setOnEditorActionListener { view, action, event ->
            if (
                action == EditorInfo.IME_ACTION_SEARCH
                || (event?.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                if (event?.action != KeyEvent.ACTION_DOWN) {
                    inputMethods.hideSoftInputFromWindow(view.windowToken, 0)
                    val query = view.text.toString()
                    if (query.isEmpty()) {
                        viewModel.cancelQuery()
                    } else {
                        viewModel.query(lifecycleScope, query)
                    }
                }
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
        adapter = ItemDataAdapter(player.queue)
        searchResults.adapter = adapter

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.results.collect {
                    when (it) {
                        is SearchViewModel.Results.Fetching -> showFetching()
                        is SearchViewModel.Results.Success -> {
                            adapter.submitList(it.results)
                            showResults()
                        }
                        is SearchViewModel.Results.Error -> {
                            ErrorHandler.handle(this@SearchFragment, R.string.error_info_search, ErrorInfo(context, it.ex))
                            showError()
                        }
                    }
                }
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val window = requireActivity().window

        view.post {
            val insets = WindowInsetsCompat.toWindowInsetsCompat(window.decorView.rootWindowInsets)
            val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            searchBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = inset.top
            }

            listOf(searchResults, searchResultsShimmer).forEach {
                it.modPadding(top = inset.top + searchBar.height)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter.dispose()
    }

    fun showFetching() {
        searchResults.visibility = View.INVISIBLE
        searchResultsShimmer.visibility = View.VISIBLE
        searchResultsShimmer.startShimmer()
        adapter.submitList(emptyList())
    }

    fun showResults() {
        searchResults.visibility = View.VISIBLE
        searchResultsShimmer.visibility = View.INVISIBLE
        searchResultsShimmer.stopShimmer()
    }

    fun showError() {
        searchResults.visibility = View.INVISIBLE
        searchResultsShimmer.visibility = View.INVISIBLE
        searchResultsShimmer.stopShimmer()
    }
}