package com.github.aecsocket.himom.fragment

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.github.aecsocket.himom.*
import com.github.aecsocket.himom.databinding.FragmentPagerBinding
import com.github.aecsocket.himom.media.STATE_PAUSED
import com.github.aecsocket.himom.media.STATE_PLAYING
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayoutMediator
import java.lang.IndexOutOfBoundsException

class PagerFragment : Fragment() {
    private lateinit var vSheet: BottomSheetBehavior<*>
    private lateinit var vTrack: TextView
    private lateinit var vArtist: TextView
    private lateinit var vArt: ImageView
    private lateinit var vPlayPause: ImageButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentPagerBinding.inflate(inflater, container, false)
        val context = context ?: return binding.root

        val contentPager = binding.contentPager.apply {
            isUserInputEnabled = false // disable swipe to change tab
            adapter = PagerAdapter.Content(this@PagerFragment)
        }
        val navTabs = binding.navTabs
        TabLayoutMediator(navTabs, contentPager) { tab, position ->
            tab.setIcon(when(position) {
                CONTENT_HOME -> R.drawable.ic_home
                CONTENT_SEARCH -> R.drawable.ic_search
                CONTENT_LIBRARY -> R.drawable.ic_library
                CONTENT_HISTORY -> R.drawable.ic_history
                else -> throw IndexOutOfBoundsException()
            })
        }.attach()

        val sheetPager = binding.sheetPager.apply {
            adapter = PagerAdapter.Sheet(this@PagerFragment)
        }
        sheetPager.currentItem = SHEET_NOW_PLAYING
        val sheetTabs = binding.sheetTabs
        TabLayoutMediator(sheetTabs, sheetPager) { tab, position ->
            tab.text = getString(when(position) {
                SHEET_QUEUE -> R.string.queue
                SHEET_NOW_PLAYING -> R.string.now_playing
                SHEET_LYRICS -> R.string.lyrics
                else -> throw IndexOutOfBoundsException()
            })
        }.attach()

        val player = (context.applicationContext as App).player

        vSheet = BottomSheetBehavior.from(binding.mediaSheet)
        vSheet.state = BottomSheetBehavior.STATE_HIDDEN
        binding.sheetBar.post { vSheet.peekHeight = binding.sheetBar.height }
        vSheet.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(sheet: View, state: Int) {
                if (state == BottomSheetBehavior.STATE_HIDDEN) {
                    context.sendBroadcast(Intent(ACTION_STOP))
                }
            }

            override fun onSlide(sheet: View, offset: Float) {}
        })
        player.queue.getCurrent().observe(viewLifecycleOwner) { stream ->
            if (stream == null) {
                vSheet.state = BottomSheetBehavior.STATE_HIDDEN
            } else if (vSheet.state == BottomSheetBehavior.STATE_HIDDEN) {
                vSheet.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        vTrack = binding.mediaTrack
        vArtist = binding.mediaArtist
        vArt = binding.mediaArt
        vPlayPause = binding.mediaPlayPause

        NowPlayingFragment.setupBasicBindings(context, player, viewLifecycleOwner,
            vTrack, vArtist, vArt, vPlayPause, binding.mediaSkipNext)

        ViewCompat.setOnApplyWindowInsetsListener(navTabs) { view, insets ->
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            }
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.sheetContent) { view, insets ->
            // updates the height to match the screen...
            // ... - nav - status bars
            // ... - nav tabs
            // ... - sheet tabs
            // for this to work, the height must NOT be wrap_content!!!
            binding.sheetPager.updateLayoutParams {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val metrics = requireActivity().windowManager.currentWindowMetrics
                    val insetVals = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    height = metrics.bounds.height() - insetVals.bottom - insetVals.top - navTabs.height - sheetTabs.height
                } // todo
            }
            sheetTabs.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                val insetVals = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                topMargin = insetVals.top
            }
            WindowInsetsCompat.CONSUMED
        }

        return binding.root
    }
}