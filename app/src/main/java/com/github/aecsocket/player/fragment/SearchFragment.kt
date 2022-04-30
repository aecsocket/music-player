package com.github.aecsocket.player.fragment

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.facebook.shimmer.ShimmerFrameLayout
import com.github.aecsocket.player.*
import com.github.aecsocket.player.data.*
import com.github.aecsocket.player.databinding.FragmentSearchBinding
import com.github.aecsocket.player.error.ErrorHandler
import com.github.aecsocket.player.error.ErrorInfo
import com.github.aecsocket.player.viewmodel.SearchViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import kotlin.math.min

class SearchFragment : Fragment() {
    private val viewModel: SearchViewModel by viewModels()
    private var lastQuery: String? = null
    private lateinit var adapter: ItemCategoryAdapter
    lateinit var searchBar: View
    lateinit var searchService: ChipGroup
    lateinit var searchResults: RecyclerView
    lateinit var searchResultsShimmer: ShimmerFrameLayout
    lateinit var searchNoResults: View
    lateinit var searchError: View
    lateinit var searchErrorDetails: Button

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
        searchService = binding.searchService
        searchResults = binding.searchResults
        searchResultsShimmer = binding.searchResultsShimmer
        searchNoResults = binding.searchNoResults
        searchError = binding.searchError
        searchErrorDetails = binding.searchErrorDetails
        val searchBtn = binding.searchBtn
        val searchText = binding.searchText

        for (service in ItemService.ALL) {
            searchService.addView(Chip(context).apply {
                text = service.name(context)
                isCheckable = true
                // check the first non-local service
                if (service !is LocalItemService && selectedServices().isEmpty()) {
                    isChecked = true
                }
            })
        }

        binding.searchErrorRetry.setOnClickListener {
            lastQuery?.let { query(it) }
        }

        searchText.addTextChangedListener { text ->
            if (text?.length ?: 0 > 0) {
                searchBtn.setImageResource(R.drawable.ic_clear)
                searchBtn.setOnClickListener {
                    searchText.text.clear()
                    viewModel.cancelQuery()
                    adapter.submitList(null)
                    showComplete()
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
                    query(view.text.toString())
                }
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

        adapter = ItemCategoryAdapter(player.queue, lifecycleScope)
        searchResults.adapter = adapter

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.results.collect { results ->
                    when (results) {
                        is SearchViewModel.Results.Fetching -> showFetching()
                        is SearchViewModel.Results.None -> showNoResults()
                        is SearchViewModel.Results.Success -> {
                            showComplete()
                            adapter.submitList(results.results.map {
                                // limit to first 3 items
                                ItemCategory(it.type, it.items.subList(0, min(3, it.items.size)))
                            })
                            if (results.exs.isNotEmpty()) {
                                ErrorHandler.handle(
                                    this@SearchFragment, R.string.error_info_search,
                                    ErrorInfo(context, results.exs))
                            }
                        }
                        is SearchViewModel.Results.Error -> {
                            showError(results.exs)
                        }
                    }
                }
            }
        }

        val window = requireActivity().window

        binding.root.post {
            val insets = WindowInsetsCompat.toWindowInsetsCompat(window.decorView.rootWindowInsets)
            val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            searchBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = inset.top
            }

            listOf(searchResults, searchResultsShimmer, searchNoResults, searchError).forEach {
                it.modPadding(top = inset.top + searchBar.height)
            }
        }

        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        for (i in 0 until searchResults.childCount) {
            (searchResults.getChildViewHolder(searchResults.getChildAt(i)) as ItemCategoryAdapter.BaseHolder)
                .dispose()
        }
    }

    private fun selectedServices() = searchService.children
        .mapIndexed { idx, chip -> if ((chip as Chip).isChecked) ItemService.ALL[idx] else null }
        .filterNotNull()
        .toList()

    private fun query(query: String) {
        lastQuery = query
        val services = selectedServices()
        viewModel.query(
            lifecycleScope,
            services,
            query)
    }

    private fun resetUi() {
        adapter.submitList(null)
        searchResults.visibility = View.INVISIBLE

        searchResultsShimmer.visibility = View.INVISIBLE
        searchResultsShimmer.stopShimmer()

        searchNoResults.visibility = View.INVISIBLE

        searchError.visibility = View.INVISIBLE
    }

    private fun showFetching() {
        resetUi()
        searchResultsShimmer.visibility = View.VISIBLE
        searchResultsShimmer.startShimmer()
    }

    private fun showComplete() {
        resetUi()
        searchResults.visibility = View.VISIBLE
    }

    private fun showNoResults() {
        resetUi()
        searchNoResults.visibility = View.VISIBLE
    }

    private fun showError(ex: List<Throwable>) {
        val context = requireContext()
        resetUi()
        searchError.visibility = View.VISIBLE
        searchErrorDetails.setOnClickListener {
            ErrorHandler.openActivity(context, ErrorInfo(context, ex))
        }
    }
}
