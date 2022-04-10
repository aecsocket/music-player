package com.github.aecsocket.player.media

import android.content.Context
import android.net.Uri
import com.github.aecsocket.player.data.DataItem
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.common.base.Ascii
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import java.lang.Exception
import java.util.*

const val STREAM_EDGE_GAP = 10_000L

class SourceResolver(
    val context: Context,
    val sources: DataSources
) {
    private val liveConfig = MediaItem.LiveConfiguration.Builder()
        .setTargetOffsetMs(STREAM_EDGE_GAP)
        .build()

    fun resolve(info: StreamInfo): MediaSource? {
        if (info.streamType.isLive()) {
            fun create(factory: MediaSource.Factory, url: String) = factory.createMediaSource(MediaItem.Builder()
                .setUri(url)
                .setLiveConfiguration(liveConfig)
                .build())

            return if (info.hlsUrl.isNotEmpty()) create(sources.hlsSourceFactory, info.hlsUrl)
                else create(sources.dashSourceFactory, info.dashMpdUrl)
        }

        val streams = info.audioStreams
        if (streams.isEmpty())
            return null

        // TODO highest quality or lowest data
        val best = highestQuality(streams)
        // TODO diff types of factories?? idk

        return sources.progressiveSourceFactory.createMediaSource(MediaItem.Builder()
            .setUri(best.url)
            .build())
    }

    fun fromUrl(url: String, service: StreamingService, onComplete: () -> Unit = {}): Flow<MediaSource> = flow {
        when (service.getLinkTypeByUrl(url)) {
            StreamingService.LinkType.NONE, StreamingService.LinkType.CHANNEL ->
                throw UnsupportedServiceException()
            // todo make better
            StreamingService.LinkType.STREAM -> {
                resolve(StreamInfo.getInfo(url))?.let { emit(it) }
                onComplete()
            }
            StreamingService.LinkType.PLAYLIST -> {
                var curUrl: String? = url
                while (curUrl != null) {
                    val playlist = PlaylistInfo.getInfo(curUrl)
                    playlist.relatedItems.forEach { stream ->
                        resolve(StreamInfo.getInfo(stream.url))?.let { emit(it) }
                    }
                    curUrl = if (playlist.hasNextPage()) playlist.nextPage.url else null
                }
                onComplete()
            }
            else -> throw IllegalStateException()
        }
    }

    class UnsupportedServiceException : Exception()

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