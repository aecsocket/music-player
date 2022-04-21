package com.github.aecsocket.player.fragment

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
import com.github.aecsocket.player.error.findView
import com.github.aecsocket.player.viewmodel.SearchViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.lang.IllegalStateException

class SearchFragment : Fragment() {
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var adapter: ItemDataAdapter
    lateinit var searchBar: View
    lateinit var searchService: ChipGroup
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
        searchService = binding.searchService
        searchResults = binding.searchResults
        searchResultsShimmer = binding.searchResultsShimmer
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
                        val services = selectedServices()
                        if (services.isEmpty()) {
                            snackbar(
                                findView() ?: throw IllegalStateException("could not find view"),
                                getString(R.string.must_specify_service), Snackbar.LENGTH_LONG).show()
                        }
                        viewModel.query(
                            lifecycleScope,
                            services,
                            query)
                    }
                }
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

        adapter = ItemDataAdapter(player.queue, lifecycleScope)
        searchResults.adapter = adapter

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.results.collect { results ->
                    when (results) {
                        is SearchViewModel.Results.Fetching -> showFetching()
                        is SearchViewModel.Results.Complete -> {
                            adapter.submitList(results.results)
                            if (results.exs.isNotEmpty()) {
                                ErrorHandler.handle(this@SearchFragment, R.string.error_info_search,
                                    ErrorInfo(context, results.exs))
                            }
                            showComplete()
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

            listOf(searchResults, searchResultsShimmer).forEach {
                it.modPadding(top = inset.top + searchBar.height)
            }
        }

        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter.dispose()
    }

    private fun selectedServices() = searchService.children
        .mapIndexed { idx, chip -> if ((chip as Chip).isChecked) ItemService.ALL[idx] else null }
        .filterNotNull()
        .toList()

    private fun showFetching() {
        searchResults.visibility = View.INVISIBLE
        searchResultsShimmer.visibility = View.VISIBLE
        searchResultsShimmer.startShimmer()
        adapter.submitList(emptyList())
    }

    private fun showComplete() {
        searchResults.visibility = View.VISIBLE
        searchResultsShimmer.visibility = View.INVISIBLE
        searchResultsShimmer.stopShimmer()
    }
}