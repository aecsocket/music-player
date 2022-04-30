package com.github.aecsocket.player.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.aecsocket.player.*
import com.github.aecsocket.player.databinding.FragmentNowPlayingBinding
import com.github.aecsocket.player.media.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class NowPlayingFragment : Fragment() {
    private var seekDragging = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentNowPlayingBinding.inflate(inflater, container, false)
        val context = requireContext()
        val player = App.player(context)

        setupBindings(context, player, lifecycleScope, viewLifecycleOwner,
            binding.playingTrack, binding.playingArtist, binding.playingArt, binding.playingPlayPause, binding.playingSkipNext)

        val timeDeterminate = binding.playingTimeDeterminate
        val timeLive = binding.playingTimeLive
        val timePosition = binding.playingTimePosition
        val timeDuration = binding.playingTimeDuration
        val timeSeek = binding.playingTimeSeek

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                player.stream.collect { stream ->
                    val stream = stream ?: return@collect
                    if (stream.data.streamType.isLive()) {
                        timeDeterminate.visibility = View.INVISIBLE
                        timeLive.visibility = View.VISIBLE
                    } else {
                        timeDeterminate.visibility = View.VISIBLE
                        timeLive.visibility = View.INVISIBLE
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                player.duration.collect { duration ->
                    timeDuration.text = if (duration < 0) getString(R.string.unknown_time)
                    else formatTime(context, duration)
                    timeSeek.max = duration.toInt()
                }
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                player.position.collect { position ->
                    if (!seekDragging) {
                        timeSeek.progress = position.position.toInt()
                        timePosition.text = formatTime(context, position.position)
                    }
                    timeSeek.secondaryProgress = position.buffered.toInt()
                }
            }
        }

        binding.playingSkipPrevious.setOnClickListener {
            player.restartOrSkipPrevious()
        }

        timeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(view: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser)
                    return
                timePosition.text = formatTime(context, progress.toLong())
            }

            override fun onStartTrackingTouch(view: SeekBar) {
                seekDragging = true
            }

            override fun onStopTrackingTouch(view: SeekBar) {
                seekDragging = false
                player.seekTo(view.progress.toLong())
            }
        })

        return binding.root
    }

    companion object {
        fun setupBindings(
            context: Context,
            player: MediaPlayer,
            scope: CoroutineScope,
            lifecycleOwner: LifecycleOwner,
            track: TextView,
            artist: TextView,
            art: ImageView,
            playPause: ImageButton,
            skipNext: ImageButton
        ) {
            track.isSelected = true // enable marquee

            skipNext.setOnClickListener {
                player.skipNext()
            }

            scope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    player.state.collect { state ->
                        when (state) {
                            STATE_PAUSED -> {
                                playPause.setImageResource(R.drawable.ic_play)
                                playPause.setOnClickListener { player.play() }
                            }
                            STATE_PLAYING -> {
                                playPause.setImageResource(R.drawable.ic_pause)
                                playPause.setOnClickListener { player.pause() }
                            }
                        }
                    }
                }
            }

            scope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    player.stream.collect { stream ->
                        val stream = stream ?: return@collect
                        track.text = stream.data.primaryText(context)
                        artist.text = stream.data.creator ?: context.getString(R.string.unknown_artist)
                        stream.art?.into(art)
                    }
                }
            }
        }
    }
}