package com.github.aecsocket.player.view

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

open class PagerAdapter(
    fragment: Fragment,
    val tabs: Map<Int, () -> Fragment>
) : FragmentStateAdapter(fragment) {
    override fun getItemCount() = tabs.size

    override fun createFragment(position: Int): Fragment {
        return tabs[position]?.invoke() ?: throw IndexOutOfBoundsException()
    }
}
