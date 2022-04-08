package com.github.aecsocket.himom

import android.media.AudioManager
import android.os.*
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainActivity : AppCompatActivity() {
    private val viewModel: Model by viewModels()
    /*private val viewModel by viewModels<Model> {
        {
            return object : ViewModelProvider.Factory {

            }
        }
    }*/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        volumeControlStream = AudioManager.STREAM_MUSIC
        WindowCompat.setDecorFitsSystemWindows(window, false)

        /*val mediaBar = findViewById<View>(R.id.mediaBar)
        val textView = findViewById<TextView>(R.id.textView)
        val textTrack = findViewById<TextView>(R.id.mediaTrack)
        val textArtist = findViewById<TextView>(R.id.mediaArtist)
        val textProgress = findViewById<TextView>(R.id.mediaProgress)
        val btnPlayPause = findViewById<ImageButton>(R.id.mediaPlayPause)
        val btnSkipNext = findViewById<ImageButton>(R.id.mediaSkipNext)

        mediaBar.visibility = View.GONE
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, raw: IBinder) {
                val binder = raw as MediaService.MediaBinder
                val media = binder.service
                mediaBar.visibility = View.VISIBLE
                println("!!! SERVICE created")
                viewModel.run {  }
                /*val viewModel = Model by viewModels {
                    object : ViewModelProvider.Factory
                }
                viewModel.mediaTrack.observe(this@MainActivity) { track -> textTrack.text = track }
                viewModel.mediaArtist.observe(this@MainActivity) { artist -> textArtist.text = artist }*/
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Toast.makeText(this@MainActivity, "Stopped", Toast.LENGTH_SHORT).show()
                mediaBar.visibility = View.GONE
            }
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            recreate()
        }
        bindService(Intent(this, MediaService::class.java), conn, Context.BIND_AUTO_CREATE)*/
    }

    class Model : ViewModel() {
        init {
            println(">>> MODEL created")
        }
    }

    class Model2(controller: MediaControllerCompat) : ViewModel() {
        /*class Factory(

        ) : ViewModelProvider.NewInstanceFactory() {
            @Suppress("unchecked_cast")
            override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                return Model() as T
            }
        }*/

        var active = true

        override fun onCleared() {
            active = false
        }

        private val controller = controller.apply {
            registerCallback(object : MediaControllerCompat.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
                    super.onPlaybackStateChanged(state)
                }

                override fun onMetadataChanged(metadata: MediaMetadataCompat) {
                }
            })
            updatePosition()
        }

        private fun updatePosition() {
        }

        val mediaTrack = MutableLiveData<String>().apply {
            postValue("Track name")
        }
        val mediaArtist = MutableLiveData<String>()
        val mediaPosition = MutableLiveData<Long>().apply {
            postValue(0)
        }

        private val handler = Handler(Looper.getMainLooper())
    }
}

inline val PlaybackStateCompat.truePosition: Long
    get() = if (state == PlaybackStateCompat.STATE_PLAYING) {
        val delta = SystemClock.elapsedRealtime() - lastPositionUpdateTime
        (position + (delta * playbackSpeed)).toLong()
    } else {
        position
    }