package com.github.aecsocket.player.media

import android.content.Context
import com.github.aecsocket.player.data.StreamData
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.MediaSource
import com.squareup.picasso.Picasso
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import java.lang.Exception

const val STREAM_EDGE_GAP = 10_000L

class SourceResolver(
    val context: Context,
    val sources: DataSources
) {
}

fun StreamType.isLive() =
    this == StreamType.LIVE_STREAM || this == StreamType.AUDIO_LIVE_STREAM