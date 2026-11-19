package com.onpath.app.ui

import com.onpath.app.time.TimeBreakdown

/** What went into the time shown in the plan sheet. */
data class TimeDetailsUi(
    val result: TimeBreakdown.Result,
    val paceLabel: String,
    val paceFactor: Double,
    /** Settings → Diagnostics → Developer tools: the formulas and tables too; false = route sheet ⋯ (plain lines only). */
    val technical: Boolean = false,
)
