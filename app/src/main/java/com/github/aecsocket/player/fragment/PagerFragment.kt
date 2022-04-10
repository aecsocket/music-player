package com.github.aecsocket.player.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.MarginLayoutParamsCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.github.aecsocket.player.*
import com.github.aecsocket.player.databinding.FragmentPagerBinding
import com.github.aecsocket.player.media.DURATION_LIVE
import com.github.aecsocket.player.media.DURATION_UNKNOWN
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayoutMediator
import java.lang.IndexOutOfBoundsException

class PagerFragment : Fragment() {
    private lateinit var navTabs: View
    private lateinit var mediaBarCoordinator: View
    private lateinit var sheetBar: View
    private lateinit var sheetContent: View
    private lateinit var sheetTabs: View
    private lateinit var sheetPager: ViewPager2
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

        val navTabs = binding.navTabs.also { navTabs = it }
        TabLayoutMediator(navTabs, contentPager) { tab, position ->
            tab.setIcon(when(position) {
                CONTENT_HOME -> R.drawable.ic_home
                CONTENT_SEARCH -> R.drawable.ic_search
                CONTENT_LIBRARY -> R.drawable.ic_library
                CONTENT_HISTORY -> R.drawable.ic_history
                else -> throw IndexOutOfBoundsException()
            })
        }.attach()

        val sheetTabs = binding.sheetTabs.also { sheetTabs = it }
        sheetPager = binding.sheetPager.apply {
            isUserInputEnabled = false
            adapter = PagerAdapter.Sheet(this@PagerFragment)
        }
        sheetPager.currentItem = SHEET_NOW_PLAYING
        TabLayoutMediator(sheetTabs, sheetPager) { tab, position ->
            tab.text = getString(when(position) {
                SHEET_QUEUE -> R.string.queue
                SHEET_NOW_PLAYING -> R.string.now_playing
                SHEET_LYRICS -> R.string.lyrics
                else -> throw IndexOutOfBoundsException()
            })
        }.attach()

        val player = (context.applicationContext as App).player

        mediaBarCoordinator = binding.mediaBarCoordinator
        sheetBar = binding.sheetBar
        sheetContent = binding.sheetContent
        sheet = BottomSheetBehavior.from(binding.mediaSheet)
        sheet.state = BottomSheetBehavior.STATE_HIDDEN
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

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()

        val window = requireActivity().window
        // we don't use the window inset listener here because it will make us return 0 for view
        // heights, since they haven't been laid out yet
        sheetContent.post {
            val insets = window.decorView.rootWindowInsets
            val inset = WindowInsetsCompat.toWindowInsetsCompat(insets).getInsets(WindowInsetsCompat.Type.systemBars())

            // this is really complicated don't mess with it
            mediaBarCoordinator.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                height = view.height - navTabs.height + sheetBar.height
                bottomMargin = sheetBar.height
            }

            sheetTabs.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = inset.top
            }

            sheet.peekHeight = sheetBar.height

            // updates the height to match the screen
            sheetPager.updateLayoutParams {
                height = view.height - inset.top - navTabs.height - sheetTabs.height
                Log.d(TAG,
                    "Insets changed { view=${view.height} top=${inset.top} bottom=${inset.bottom} navTabs=${navTabs.height} sheetTabs=${sheetTabs.height} } = $height")
            }
        }
    }
}