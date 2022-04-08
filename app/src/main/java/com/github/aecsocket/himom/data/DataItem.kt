package com.github.aecsocket.himom.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.recyclerview.widget.DiffUtil
import com.github.aecsocket.himom.R
import com.google.android.exoplayer2.MediaItem
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.lang.IllegalStateException
import java.lang.RuntimeException
import java.util.concurrent.ThreadLocalRandom

sealed class DataItem(
    val id: Long = ThreadLocalRandom.current().nextLong()
) {
    abstract fun getPrimaryText(context: Context): String
    abstract fun getSecondaryText(context: Context): String
    abstract fun getArt(context: Context): RequestCreator?

    companion object {
        inline fun <reified T : DataItem> itemCallback() = object : DiffUtil.ItemCallback<T>() {
            override fun areItemsTheSame(oldItem: T, newItem: T): Boolean = oldItem.id == newItem.id
            @SuppressLint("DiffUtilEquals")
            override fun areContentsTheSame(oldItem: T, newItem: T): Boolean = oldItem == newItem
        }

        @Suppress("BlockingMethodInNonBlockingContext")
        fun fromUrl(url: String, service: StreamingService, onComplete: () -> Unit = {}): Flow<DataItem> = flow {
            when (service.getLinkTypeByUrl(url)) {
                StreamingService.LinkType.NONE, StreamingService.LinkType.CHANNEL ->
                    throw UnsupportedServiceException()
                // todo make better
                StreamingService.LinkType.STREAM -> {
                    StreamInfo.getInfo(url).asItems().forEach { emit(it) }
                    onComplete()
                }
                StreamingService.LinkType.PLAYLIST -> {
                    var curUrl: String? = url
                    while (curUrl != null) {
                        val playlist = PlaylistInfo.getInfo(curUrl)
                        playlist.relatedItems.forEach { stream ->
                            StreamInfo.getInfo(stream.url).asItems().forEach { emit(it) }
                        }
                        curUrl = if (playlist.hasNextPage()) playlist.nextPage.url else null
                    }
                    onComplete()
                }
                else -> throw IllegalStateException()
            }
        }
    }
}

data class StreamData(
    val media: MediaItem,
    val track: String,
    val artist: String? = null,
    val art: RequestCreator? = null
) : DataItem() {
    override fun getPrimaryText(context: Context) = track
    override fun getSecondaryText(context: Context) = artist ?: context.getString(R.string.unknown_artist)
    override fun getArt(context: Context) = art

    fun equalMeta(other: StreamData): Boolean {
        return track == other.track && artist == other.artist
    }

    override fun toString(): String {
        return """StreamData('$track' by '${artist ?: "unknown"}'${if (art != null) " (has art)" else ""})"""
    }
}

data class ArtistData(
    val name: String,
    val art: RequestCreator? = null
) : DataItem() {
    override fun getPrimaryText(context: Context) = name
    override fun getSecondaryText(context: Context) = context.getString(R.string.artist)
    override fun getArt(context: Context) = art

    override fun toString(): String {
        return """ArtistData('$name' ${if (art != null) " (has art)" else ""})"""
    }
}

class UnsupportedServiceException : RuntimeException()

// TODO maybe have multiple items from one Info
fun StreamInfo.asItems(): List<StreamData> {
    if (audioStreams.size > 0) {
        val stream = audioStreams[0]
        return listOf(StreamData(
            MediaItem.fromUri(stream.url), name, uploaderName, Picasso.get().load(thumbnailUrl)
        ))
    }
    return emptyList()
}