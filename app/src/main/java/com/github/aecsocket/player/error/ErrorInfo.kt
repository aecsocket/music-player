package com.github.aecsocket.player.error

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.PrintWriter
import java.io.StringWriter


@Parcelize
class ErrorInfo(
    val message: String?,
    val stackTrace: String
) : Parcelable {
    constructor(message: String, ex: Throwable) :
        this(ex.message, StringWriter().use { writer ->
            ex.printStackTrace(PrintWriter(writer))
            writer.buffer.toString()
        })
}
