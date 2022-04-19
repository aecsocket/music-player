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
    private val liveConfig = MediaItem.LiveConfiguration.Builder()
        .setTargetOffsetMs(STREAM_EDGE_GAP)
        .build()

    class NoStreamsException : Exception()

    fun resolve(stream: StreamData, info: StreamInfo): LoadedStreamData {
        val art = Picasso.get().load(info.thumbnailUrl)
        if (info.streamType.isLive()) {
            fun create(factory: MediaSource.Factory, url: String) = LoadedStreamData(
                stream, art,
                factory.createMediaSource(MediaItem.Builder()
                    .setUri(url)
                    .setLiveConfiguration(liveConfig)
                    .build()))

            return if (info.hlsUrl.isNotEmpty()) create(sources.hlsSourceFactory, info.hlsUrl)
                else create(sources.dashSourceFactory, info.dashMpdUrl)
        }

        val streams = info.audioStreams
        if (streams.isEmpty())
            throw NoStreamsException()

        // TODO highest quality or lowest data
        val best = mostEfficient(streams)
        // TODO diff types of factories?? idk

        return LoadedStreamData(stream, art,
            sources.progressiveSourceFactory.createMediaSource(MediaItem.Builder()
                .setUri(best.url)
                .build()))
    }

    companion object {
        // 0 = lowest quality
        val FORMAT_HIGHEST_QUALITY = listOf(MediaFormat.MP3, MediaFormat.WEBMA, MediaFormat.M4A)
        // 0 = most efficient
        val FORMAT_MOST_EFFICIENT = listOf(MediaFormat.WEBMA, MediaFormat.M4A, MediaFormat.MP3)

        fun compareByBitrate(a: AudioStream, b: AudioStream, ranks: List<MediaFormat>) =
            if (a.averageBitrate < b.averageBitrate) -1
            else if (a.averageBitrate > b.averageBitrate) 1
            // if they have the same bitrate, sort by our format ranking
            else ranks.indexOf(a.getFormat()) - ranks.indexOf(b.getFormat())

        fun highestQuality(streams: List<AudioStream>): AudioStream {
            return streams.stream()
                .max { a, b -> compareByBitrate(a, b, FORMAT_HIGHEST_QUALITY) }
                .get()
        }

        fun mostEfficient(streams: List<AudioStream>): AudioStream {
            return streams.stream()
                .max { a, b -> -compareByBitrate(a, b, FORMAT_MOST_EFFICIENT) }
                .get()
        }
    }
}

fun StreamType.isLive() =
    this == StreamType.LIVE_STREAM || this == StreamType.AUDIO_LIVE_STREAM