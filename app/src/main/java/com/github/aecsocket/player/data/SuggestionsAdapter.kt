package com.github.aecsocket.player.data

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.aecsocket.player.R
import com.github.aecsocket.player.databinding.ItemSearchSuggestionBinding

class SuggestionsAdapter(
    val callback: (Suggestion) -> Unit
) : ListAdapter<Suggestion, SuggestionsAdapter.ViewHolder>(Suggestion.itemCallback()) {
    class ViewHolder(
        val callback: (Suggestion) -> Unit,
        binding: ItemSearchSuggestionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        val base = binding.item
        val type = binding.searchSuggestionType
        val value = binding.searchSuggestionValue

        fun bindTo(suggestion: Suggestion) {
            type.setImageResource(when (val type = suggestion.type) {
                SUGGESTION_TYPE_QUERY -> R.drawable.ic_search
                SUGGESTION_TYPE_HISTORY -> R.drawable.ic_history
                else -> throw IllegalStateException("unknown suggestion type $type")
            })
            value.text = suggestion.value

            base.setOnClickListener {
                callback(suggestion)
            }
        }

        companion object {
            fun from(parent: ViewGroup, callback: (Suggestion) -> Unit) = ViewHolder(
                callback,
                ItemSearchSuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder.from(parent, callback)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bindTo(getItem(position))
}
