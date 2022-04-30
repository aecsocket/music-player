package com.github.aecsocket.player.data

import androidx.recyclerview.widget.DiffUtil

const val SUGGESTION_TYPE_QUERY = 0
const val SUGGESTION_TYPE_HISTORY = 1

data class Suggestion(
    val type: Int,
    val value: String
) {
    companion object {
        fun itemCallback() = object : DiffUtil.ItemCallback<Suggestion>() {
            override fun areItemsTheSame(oldItem: Suggestion, newItem: Suggestion) = oldItem == newItem
            override fun areContentsTheSame(oldItem: Suggestion, newItem: Suggestion) = oldItem == newItem
        }
    }
}
