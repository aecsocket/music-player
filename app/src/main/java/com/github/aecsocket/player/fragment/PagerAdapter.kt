package com.github.aecsocket.player.fragment

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

const val CONTENT_HOME = 0
const val CONTENT_SEARCH = 1
const val CONTENT_LIBRARY = 2
const val CONTENT_HISTORY = 3

const val SHEET_QUEUE = 0
const val SHEET_NOW_PLAYING = 1
const val SHEET_LYRICS = 2

open class PagerAdapter(
    fragment: Fragment,
    val tabs: Map<Int, () -> Fragment>
) : FragmentStateAdapter(fragment) {
    override fun getItemCount() = tabs.size

    override fun createFragment(position: Int): Fragment {
        return tabs[position]?.invoke() ?: throw IndexOutOfBoundsException()
    }

    class Content(fragment: Fragment) : PagerAdapter(fragment, mapOf(
        CONTENT_HOME to { HomeFragment() },
        CONTENT_SEARCH to { SearchFragment() },
        CONTENT_LIBRARY to { LibraryFragment() },
        CONTENT_HISTORY to { HistoryFragment() }
    ))

    class Sheet(fragment: Fragment) : PagerAdapter(fragment, mapOf(
        SHEET_QUEUE to { QueueFragment() },
        SHEET_NOW_PLAYING to { NowPlayingFragment() },
        SHEET_LYRICS to { LyricsFragment() }
    ))
}