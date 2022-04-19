package com.github.aecsocket.player.error

import android.content.Context
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.PrintWriter
import java.io.StringWriter

@Parcelize
class ErrorInfo(
    val parts: List<ErrorPart>
) : Parcelable {
    constructor(context: Context, exs: Collection<Throwable>) :
        this(exs.map { ErrorPart(context, it) })

    constructor(context: Context, ex: Throwable) :
        this(listOf(ErrorPart(context, ex)))
}

@Parcelize
class ErrorPart(
    val message: String,
    val stackTrace: String
) : Parcelable {
    constructor(message: String, ex: Throwable) :
        this(message, StringWriter().use { writer ->
            ex.printStackTrace(PrintWriter(writer))
            writer.buffer.toString()
        })

    constructor(context: Context, ex: Throwable) :
        this(ErrorHandler.getMessage(context, ex), ex)
}
