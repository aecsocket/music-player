package com.github.aecsocket.player.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.github.aecsocket.player.*
import com.github.aecsocket.player.databinding.FragmentNowPlayingBinding
import com.github.aecsocket.player.media.*
import com.google.android.exoplayer2.Player
import java.util.*

class NowPlayingFragment : Fragment() {
    private lateinit var track: TextView
    private lateinit var artist: TextView
    private lateinit var art: ImageView
    private lateinit var playPause: ImageButton
    private lateinit var shuffle: ImageButton
    private lateinit var repeat: ImageButton
    private lateinit var seek: SeekBar
    private lateinit var position: TextView
    private lateinit var duration: TextView
    private var seekDragging = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentNowPlayingBinding.inflate(inflater, container, false)
        val context = context ?: return binding.root
        val player = (context.applicationContext as App).player

        track = binding.nowPlayingTrack
        artist = binding.nowPlayingArtist
        art = binding.nowPlayingArt
        playPause = binding.nowPlayingPlayPause
        shuffle = binding.nowPlayingShuffle
        repeat = binding.nowPlayingRepeat
        seek = binding.nowPlayingSeek
        position = binding.nowPlayingPosition
        duration = binding.nowPlayingDuration

        setupBasicBindings(context, player, viewLifecycleOwner,
            track, artist, art, playPause, binding.nowPlayingSkipNext)

        binding.nowPlayingSkipPrevious.setOnClickListener { context.sendBroadcast(Intent(ACTION_SKIP_PREVIOUS)) }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(view: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser)
                    return
                position.text = formatTime(progress.toLong())
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
                Player.REPEAT_MODE_OFF -> R.drawable.ic_repeat
                Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_all
                else -> throw IndexOutOfBoundsException()
            })
        }
        binding.nowPlayingShuffle.setOnClickListener { player.toggleShuffleMode() }
        player.shuffleMode.observe(viewLifecycleOwner) { state ->
            binding.nowPlayingShuffle.setImageResource(
                if (state) R.drawable.ic_shuffle_on
                else R.drawable.ic_shuffle_off)
        }

        player.getCurrent().observe(viewLifecycleOwner) { stream ->
            if (stream.type.isLive()) {
                binding.nowPlayingTimeDeterminate?.visibility = View.INVISIBLE
                binding.nowPlayingLive?.visibility = View.VISIBLE
            } else {
                binding.nowPlayingTimeDeterminate?.visibility = View.VISIBLE
                binding.nowPlayingLive?.visibility = View.INVISIBLE
            }
        }

        player.getPosition().observe(viewLifecycleOwner) { position ->
            if (!seekDragging) {
                seek.progress = position.toInt()
                this.position.text = formatTime(position)
            }
        }
        player.getBuffered().observe(viewLifecycleOwner) { buffered ->
            seek.secondaryProgress = buffered.toInt()
        }
        player.getDuration().observe(viewLifecycleOwner) { duration ->
            if (duration == DURATION_UNKNOWN) {
                seek.max = 1
                seek.progress = 0
                seek.secondaryProgress = 0
                this.duration.text = getString(R.string.unknown_time)
            } else if (duration >= 0) {
                seek.max = duration.toInt()
                seek.keyProgressIncrement = 10_000
                this.duration.text = formatTime(duration)
            }
        }

        return binding.root
    }

    companion object {
        fun formatTime(ms: Long): String {
            return "%02d:%02d".format(Locale.ROOT, ms / (1000 * 60), (ms / 1000) % 60)
        }

        fun setupBasicBindings(
            context: Context,
            player: MediaPlayer,
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