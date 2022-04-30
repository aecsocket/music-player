package com.github.aecsocket.player

data class Errorable<T>(val result: T, val exs: List<Throwable> = emptyList())
