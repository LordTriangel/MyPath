package com.onpath.app.ui

/** Whole MB, at least 1 for anything non-empty. */
fun mb(bytes: Long): Long = if (bytes <= 0) 0 else maxOf(1, (bytes + 524_288) / 1_048_576)
