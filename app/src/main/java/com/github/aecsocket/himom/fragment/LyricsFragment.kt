package com.github.aecsocket.himom.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.aecsocket.himom.databinding.FragmentLyricsBinding

class LyricsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentLyricsBinding.inflate(inflater, container, false).root
    }
}