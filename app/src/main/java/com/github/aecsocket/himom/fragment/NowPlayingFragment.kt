package com.github.aecsocket.himom.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.github.aecsocket.himom.*
import com.github.aecsocket.himom.databinding.FragmentNowPlayingBinding
import com.github.aecsocket.himom.media.*
import com.google.android.exoplayer2.C
import java.util.*

class NowPlayingFragment : Fragment() {
    private lateinit var vTrack: TextView
    private lateinit var vArtist: TextView
    private lateinit var vArt: ImageView
    private lateinit var vPlayPause: ImageButton
    private lateinit var vShuffle: ImageButton
    private lateinit var vRepeat: ImageButton
    private lateinit var vSeek: SeekBar
    private lateinit var vPosition: TextView
    private lateinit var vDuration: TextView
    private var seekDragging = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentNowPlayingBinding.inflate(inflater, container, false)
        val context = context ?: return binding.root
        val player = (context.applicationContext as App).player

        vTrack = binding.nowPlayingTrack
        vArtist = binding.nowPlayingArtist
        vArt = binding.nowPlayingArt
        vPlayPause = binding.nowPlayingPlayPause
        vShuffle = binding.nowPlayingShuffle
        vRepeat = binding.nowPlayingRepeat
        vSeek = binding.nowPlayingSeek
        vPosition = binding.nowPlayingPosition
        vDuration = binding.nowPlayingDuration

        setupBasicBindings(context, player, viewLifecycleOwner,
            vTrack, vArtist, vArt, vPlayPause, binding.nowPlayingSkipNext)

        binding.nowPlayingSkipPrevious.setOnClickListener { context.sendBroadcast(Intent(ACTION_SKIP_PREVIOUS)) }

        vSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(view: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser)
                    return
                vPosition.text = formatTime(progress.toLong())
            }

            override fun onStartTrackingTouch(view: SeekBar) {
                seekDragging = true
            }

            override fun onStopTrackingTouch(view: SeekBar) {
                seekDragging = false
                player.seekTo(view.progress.toLong())
            }
        })

        binding.nowPlayingRepeat.setOnClickListener { player.nextRepeatMode() }
        player.repeatMode.observe(viewLifecycleOwner) { state ->
            binding.nowPlayingRepeat.setImageResource(when (state) {
                REPEAT_MODE_OFF -> R.drawable.ic_repeat
                REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                REPEAT_MODE_ALL -> R.drawable.ic_repeat_all
                else -> throw IndexOutOfBoundsException()
            })
        }
        player.getState().observe(viewLifecycleOwner) {
            val handle = player.handle ?: return@observe
            val duration = handle.exo.duration
            if (duration == C.TIME_UNSET) {
                vSeek.max = 1
                vDuration.text = getString(R.string.unknown_time)
            } else {
                vSeek.max = duration.toInt()
                vSeek.keyProgressIncrement = 10_000
                vDuration.text = formatTime(duration)
            }
        }
        player.getPosition().observe(viewLifecycleOwner) { position ->
            if (!seekDragging) {
                vSeek.progress = position.toInt()
                vPosition.text = formatTime(position)
            }
        }
        player.getBuffered().observe(viewLifecycleOwner) { buffered ->
            vSeek.secondaryProgress = buffered.toInt()
        }

        return binding.root
    }

    companion object {
        fun formatTime(ms: Long): String {
            return "%02d:%02d".format(Locale.ROOT, ms / (1000 * 60), (ms / 1000) % 60)
        }

        fun setupBasicBindings(
            context: Context,
            player: QueuedPlayer,
            lifecycleOwner: LifecycleOwner,
            track: TextView,
            artist: TextView,
            art: ImageView,
            playPause: ImageButton,
            skipNext: ImageButton
        ) {
            track.isSelected = true // enable marquee
            skipNext.setOnClickListener { context.sendBroadcast(Intent(ACTION_SKIP_NEXT)) }

            player.getCurrent().observe(lifecycleOwner) { stream ->
                println("RECV stream")
                track.text = stream.getPrimaryText(context)
                artist.text = stream.getSecondaryText(context)
                stream.art?.let { App.setupRequest(it).into(art) }
            }
            player.getState().observe(lifecycleOwner) { state ->
                when (state) {
                    STATE_PLAYING -> {
                        playPause.setImageResource(R.drawable.ic_pause)
                        playPause.setOnClickListener { context.sendBroadcast(Intent(ACTION_PAUSE)) }
                    }
                    STATE_PAUSED -> {
                        playPause.setImageResource(R.drawable.ic_play)
                        playPause.setOnClickListener { context.sendBroadcast(Intent(ACTION_PLAY)) }
                    }
                }
            }
        }
    }
}