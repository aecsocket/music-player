package com.github.aecsocket.player.fragment

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.github.aecsocket.player.*
import com.github.aecsocket.player.databinding.FragmentPagerBinding
import com.github.aecsocket.player.media.DURATION_LIVE
import com.github.aecsocket.player.media.DURATION_UNKNOWN
import com.google.android.exoplayer2.C
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayoutMediator
import java.lang.IndexOutOfBoundsException

class PagerFragment : Fragment() {
    private lateinit var sheet: BottomSheetBehavior<*>
    private lateinit var track: TextView
    private lateinit var artist: TextView
    private lateinit var art: ImageView
    private lateinit var playPause: ImageButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentPagerBinding.inflate(inflater, container, false)
        val context = context ?: return binding.root

        val contentPager = binding.contentPager.apply {
            isUserInputEnabled = false
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
            isUserInputEnabled = false
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

        sheet = BottomSheetBehavior.from(binding.mediaSheet)
        sheet.state = BottomSheetBehavior.STATE_HIDDEN
        binding.sheetBar.post {
            sheet.peekHeight = binding.sheetBar.height
        }
        sheet.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(sheet: View, state: Int) {
                if (state == BottomSheetBehavior.STATE_HIDDEN) {
                    context.sendBroadcast(Intent(ACTION_STOP))
                }
            }

            override fun onSlide(sheet: View, offset: Float) {}
        })
        player.queue.getCurrent().observe(viewLifecycleOwner) { stream ->
            if (stream == null) {
                sheet.state = BottomSheetBehavior.STATE_HIDDEN
            } else if (sheet.state == BottomSheetBehavior.STATE_HIDDEN) {
                sheet.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        track = binding.mediaTrack
        artist = binding.mediaArtist
        art = binding.mediaArt
        playPause = binding.mediaPlayPause

        NowPlayingFragment.setupBasicBindings(context, player, viewLifecycleOwner,
            track, artist, art, playPause, binding.mediaSkipNext)

        val position = binding.mediaPosition
        val liveBar = binding.mediaLive
        player.getDuration().observe(viewLifecycleOwner) { duration ->
            fun showDeterminate() {
                liveBar.visibility = View.INVISIBLE
                position.visibility = View.VISIBLE
            }

            fun showLive() {
                liveBar.visibility = View.VISIBLE
                position.visibility = View.INVISIBLE
            }

            when (duration) {
                DURATION_UNKNOWN -> {
                    showDeterminate()
                    position.max = 1
                }
                DURATION_LIVE -> {
                    showLive()
                }
                else -> {
                    showDeterminate()
                    position.max = duration.toInt()
                }
            }
        }
        player.getPosition().observe(viewLifecycleOwner) { pos ->
            position.progress = pos.toInt()
        }

        ViewCompat.setOnApplyWindowInsetsListener(navTabs) { view, insets ->
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            }
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.sheetContent) { _, insets ->
            // updates the height to match the screen...
            // ... - nav - status bars
            // ... - nav tabs
            // ... - sheet tabs
            // for this to work, the height must NOT be wrap_content!!!
            // AND the peek height must NOT be defined manually!!!
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