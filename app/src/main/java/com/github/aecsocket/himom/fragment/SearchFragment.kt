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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.github.aecsocket.himom.App
import com.github.aecsocket.himom.data.StreamData
import com.github.aecsocket.himom.databinding.FragmentSearchBinding
import com.google.android.exoplayer2.MediaItem
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.lang.Exception
import java.net.UnknownHostException

class SearchFragment : Fragment() {
    private val adapter = GenericItemAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentSearchBinding.inflate(inflater, container, false)
        val context = context ?: return binding.root
        val player = (context.applicationContext as App).player
        binding.searchItems.adapter = adapter

        binding.searchField.setOnEditorActionListener { view, action, event ->
            if (
                action == EditorInfo.IME_ACTION_SEARCH
                || event?.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                if (action == EditorInfo.IME_ACTION_SEARCH || event?.action == KeyEvent.ACTION_DOWN) {
                    val url = view.editableText.toString()
                    view.editableText.clear()
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val info = StreamInfo.getInfo(url)
                            if (info.audioStreams.size > 0) {
                                val stream = info.audioStreams[0]
                                withContext(Dispatchers.Main) {
                                    adapter.submitList(listOf(StreamData(
                                        media = MediaItem.fromUri(stream.url),
                                        track = info.name,
                                        artist = info.uploaderName,
                                        art = Picasso.get().load(info.thumbnailUrl)
                                    )))
                                }
                            }
                        } catch (ex: Exception) {
                            when (ex) {
                                is ExtractionException, is IOException -> {
                                    // TODO smth more fancy
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@SearchFragment.context,
                                            "Error: $ex",
                                            Toast.LENGTH_SHORT).show()
                                    }
                                }
                                else -> throw ex
                            }
                        }
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