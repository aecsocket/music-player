package com.github.aecsocket.himom.media

import android.content.Context
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.FileDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSink
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache

class CacheDataSourceFactory(
    val context: Context,
    val maxFileSize: Long = 64 * 1024 * 1024,
    val maxCacheSize: Long = 2 * 1024 * 1024
) : DataSource.Factory {
    private val sourceFactory = DefaultDataSource.Factory(context)

    override fun createDataSource(): DataSource {
        val evictor = LeastRecentlyUsedCacheEvictor(maxCacheSize)
        val cache = SimpleCache(context.cacheDir.resolve("media"), evictor, StandaloneDatabaseProvider(context))
        return CacheDataSource(
            cache, sourceFactory.createDataSource(), FileDataSource(), CacheDataSink(cache, maxFileSize),
            CacheDataSource.FLAG_BLOCK_ON_CACHE or CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
            null
        )
    }
}