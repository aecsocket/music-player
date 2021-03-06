package com.github.aecsocket.player.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.github.aecsocket.player.App
import com.github.aecsocket.player.R
import com.github.aecsocket.player.databinding.FragmentPagerBinding
import com.github.aecsocket.player.media.isLive
import com.github.aecsocket.player.modPadding
import com.github.aecsocket.player.view.PagerAdapter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

const val CONTENT_HOME = 0
const val CONTENT_SEARCH = 1

const val MEDIA_QUEUE = 0
const val MEDIA_NOW_PLAYING = 1
const val MEDIA_LYRICS = 2

class PagerFragment : Fragment() {
    lateinit var mediaCoordinator: CoordinatorLayout
    lateinit var mediaSheet: BottomSheetBehavior<*>
    lateinit var mediaBar: View
    lateinit var mediaTabs: TabLayout
    lateinit var navTabs: TabLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentPagerBinding.inflate(inflater, container, false)
        val context = requireContext()
        val player = App.player(context)

        mediaCoordinator = binding.mediaCoordinator
        mediaSheet = BottomSheetBehavior.from(binding.mediaSheet).apply {
            state = BottomSheetBehavior.STATE_HIDDEN
        }
        mediaSheet.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(sheet: View, state: Int) {
                if (state == BottomSheetBehavior.STATE_HIDDEN) {
                    player.release()
                }
            }

            override fun onSlide(sheet: View, offset: Float) {}
        })
        mediaBar = binding.mediaBar
        mediaTabs = binding.mediaTabs
        navTabs = binding.navTabs

        val viewPager = binding.viewPager.apply {
            isUserInputEnabled = false
            adapter = PagerAdapter(this@PagerFragment, mapOf(
                CONTENT_HOME to { HomeFragment() },
                CONTENT_SEARCH to { SearchFragment() }
            ))
        }
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (mediaSheet.state == BottomSheetBehavior.STATE_EXPANDED) {
                    mediaSheet.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            }
        })
        TabLayoutMediator(navTabs, viewPager) { tab, index ->
            tab.setIcon(when (index) {
                CONTENT_HOME -> R.drawable.ic_home
                CONTENT_SEARCH -> R.drawable.ic_search
                else -> throw IndexOutOfBoundsException()
            })
        }.attach()

        val mediaPager = binding.mediaPager.apply {
            isUserInputEnabled = false
            adapter = PagerAdapter(this@PagerFragment, mapOf(
                MEDIA_QUEUE to { QueueFragment() },
                MEDIA_NOW_PLAYING to { NowPlayingFragment() },
                MEDIA_LYRICS to { LyricsFragment() }
            ))
            currentItem = MEDIA_NOW_PLAYING
        }
        TabLayoutMediator(mediaTabs, mediaPager) { tab, index ->
            tab.text = context.getString(when (index) {
                MEDIA_QUEUE -> R.string.queue
                MEDIA_NOW_PLAYING -> R.string.now_playing
                MEDIA_LYRICS -> R.string.lyrics
                else -> throw IndexOutOfBoundsException()
            })
        }.attach()

        NowPlayingFragment.setupBindings(context, player, lifecycleScope, viewLifecycleOwner,
            binding.mediaTrack, binding.mediaArtist, binding.mediaArt, binding.mediaPlayPause, binding.mediaSkipNext)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                player.stream.collect { stream ->
                    if (stream == null) {
                        mediaSheet.state = BottomSheetBehavior.STATE_HIDDEN
                    } else if (mediaSheet.state == BottomSheetBehavior.STATE_HIDDEN) {
                        mediaSheet.state = BottomSheetBehavior.STATE_COLLAPSED
                    }
                }
            }
        }

        val positionDeterminate = binding.mediaPositionDeterminate
        val positionLive = binding.mediaPositionLive

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                player.stream.collect {
                    val stream = it ?: return@collect
                    if (stream.data.streamType.isLive()) {
                        positionDeterminate.visibility = View.INVISIBLE
                        positionLive.visibility = View.VISIBLE
                    } else {
                        positionDeterminate.visibility = View.VISIBLE
                        positionLive.visibility = View.INVISIBLE
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                player.position.collect { position ->
                    positionDeterminate.progress = position.position.toInt()
                }
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                player.duration.collect { duration ->
                    positionDeterminate.max = duration.toInt()
                }
            }
        }

        val window = requireActivity().window

        binding.root.post {
            val insets = WindowInsetsCompat.toWindowInsetsCompat(window.decorView.rootWindowInsets)
            val inset = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            mediaTabs.modPadding(top = inset.top)
            mediaSheet.peekHeight = mediaBar.height
            mediaCoordinator.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                height = binding.root.height - navTabs.height + mediaBar.height + mediaTabs.height
            }
        }

        return binding.root
    }
}
