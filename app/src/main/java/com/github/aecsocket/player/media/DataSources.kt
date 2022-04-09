package com.github.aecsocket.player.media

import android.content.Context
import com.github.aecsocket.player.USER_AGENT
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistTracker
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.TransferListener

// NewPipe/PlayerDataSource

const val PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT = 15.0
const val MANIFEST_MINIMUM_RETRY = 5

class DataSources(
    val context: Context,
    val txListener: TransferListener
) {
    private val cacheData = CacheDataSourceFactory(context)

    private val cachelessData = DefaultDataSource.Factory(context,
        DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT))
        .setTransferListener(txListener)

    private val errorHandling = DefaultLoadErrorHandlingPolicy(MANIFEST_MINIMUM_RETRY)
    private val progErrorHandling = DefaultLoadErrorHandlingPolicy(Int.MAX_VALUE)

    val hlsSourceFactory = HlsMediaSource.Factory(cachelessData)
        .setAllowChunklessPreparation(true)
        .setLoadErrorHandlingPolicy(errorHandling)
        .setPlaylistTrackerFactory { sourceFactory, errorPolicy, parserFactory ->
            DefaultHlsPlaylistTracker(sourceFactory, errorPolicy, parserFactory, PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT)
        }

    val dashSourceFactory = DashMediaSource.Factory(
        DefaultDashChunkSource.Factory(cachelessData), cachelessData)
        .setLoadErrorHandlingPolicy(errorHandling)

    val progressiveSourceFactory = ProgressiveMediaSource.Factory(cacheData)
        .setContinueLoadingCheckIntervalBytes(1024 * 1024)
        .setLoadErrorHandlingPolicy(progErrorHandling)
}