package com.onpath.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.onpath.app.data.LastActivity
import com.onpath.app.data.Tour
import com.onpath.app.data.TourKind
import com.onpath.app.routing.Activity
import com.onpath.app.time.TourReview
import com.onpath.strings.*
import kotlin.math.abs

/**
 * „Mehr“ of a route's overview (owner 2026-11-19: „we don't call it details, but more. And then we only show the values in
 * there that are really there in addition … some nicer graphical view“): only what the overview does not show – the clock
 * times as a time bar with the pauses as gaps, the descent, speed uphill / downhill as a pair of bars, planned vs. needed
 * as two bars of one scale with the difference – and a small line with the activity and the rank by length. The doubles
 * of the overview (moving time, pauses, Ø speed, ascent) are not here. Pure: what is not known is left out, and with
 * nothing left there is no „Mehr“ at all ([of] null).
 */
internal object More {
    /**
     * The time bar: start and end clock time, the pauses as gaps ([gaps]: from, to as parts of the way 0..1), [key] the
     * gaps' key („Pausen (4)“, only with gaps).
     */
    data class Clock(val from: String, val to: String, val gaps: List<Pair<Float, Float>>, val key: String?,
                     val description: String)

    /** Climbing / descending speed (m/h): each side only when known; [scale] = the larger, both bars measured on it. */
    data class Rates(val upMh: Double?, val downMh: Double?) {
        val scale: Double get() = maxOf(upMh ?: 0.0, downMh ?: 0.0)
        fun part(v: Double): Float = if (scale > 0) (v / scale).toFloat().coerceIn(0f, 1f) else 0f
        val description: String get() = listOfNotNull(
            upMh?.let { "${Txt.s(Res.string.routes_review_uphill)} ${ReviewText.perHour(it)}" },
            downMh?.let { "${Txt.s(Res.string.routes_review_downhill)} ${ReviewText.perHour(it)}" }).joinToString(", ")
    }

    /** Planned vs. needed moving time on the followed route (s), both measured on the longer one. */
    data class Plan(val plannedS: Double, val neededS: Double) {
        private val scale get() = maxOf(plannedS, neededS)
        val plannedPart: Float get() = (plannedS / scale).toFloat()
        val neededPart: Float get() = (neededS / scale).toFloat()
        /** „30 min schneller“ / „20 min länger“ / „Genau wie geplant“ (within a minute). */
        val difference: String get() {
            val d = neededS - plannedS
            return when {
                abs(d) < 60 -> Txt.s(Res.string.routes_review_as_planned)
                d > 0 -> Txt.s(Res.string.routes_more_longer, ReviewText.time(d))
                else -> Txt.s(Res.string.routes_more_faster, ReviewText.time(-d))
            }
        }
        val description: String get() = listOf(
            "${Txt.s(Res.string.routes_more_planned)} ${ReviewText.time(plannedS)}",
            "${Txt.s(Res.string.routes_more_needed)} ${ReviewText.time(neededS)}",
            difference, Txt.s(Res.string.routes_more_without_breaks)).joinToString(", ")
    }

    data class Parts(val clock: Clock?, val descent: String?, val rates: Rates?, val plan: Plan?, val note: String?)

    /**
     * What „Mehr“ shows for [t] with its look back [r] (null: a planned / imported route, or a done tour still read):
     * null = nothing the overview does not already show – no „Mehr“ row then.
     */
    fun of(t: Tour, r: TourReview?): Parts? {
        val done = t.kind == TourKind.RECORDED
        val review = r.takeIf { done }
        val clock = review?.let { clock(it) }
        val descent = t.descentM.takeIf { it >= 1 }?.let { ReviewText.descent(it) }
        val rates = review?.let { if (it.upMh != null || it.downMh != null) Rates(it.upMh, it.downMh) else null }
        val plan = review?.let { rv -> rv.plannedS?.let { p -> rv.neededS?.let { n -> Plan(p, n).takeIf { p > 0 && n > 0 } } } }
        val act = com.onpath.app.debug.enumOrNull<Activity>(t.activity, "routes")
        val note = listOfNotNull(
            act?.let { LastActivity.name(it, t.sac) },
            review?.rank?.let { Txt.s(Res.string.routes_review_rank, it.place, it.of) },
        ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
        if (clock == null && descent == null && rates == null && plan == null && note == null) return null
        return Parts(clock, descent, rates, plan, note)
    }

    private fun clock(r: TourReview): Clock? {
        val s = r.startMs ?: return null
        val e = r.endMs ?: return null
        if (e <= s) return null
        val span = (e - s).toDouble()
        val gaps = r.pauseSpans.mapNotNull { p ->
            val a = ((p.startMs - s) / span).toFloat().coerceIn(0f, 1f)
            val b = ((p.endMs - s) / span).toFloat().coerceIn(0f, 1f)
            (a to b).takeIf { b > a }
        }
        val from = clockText(s); val to = clockText(e)
        val pauses = r.pauseS?.takeIf { it >= 60 }?.let { "${ReviewText.pausesLabel(r.pauseCount)} ${ReviewText.time(it)}" }
        val desc = listOfNotNull(Txt.s(Res.string.routes_more_clock_desc, from, to), pauses).joinToString(", ")
        return Clock(from, to, gaps, ReviewText.pausesLabel(r.pauseCount).takeIf { gaps.isNotEmpty() }, desc)
    }

    /** "10:12" in the phone's time zone. */
    fun clockText(ms: Long): String = java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT).format(java.util.Date(ms))
}

/** The „Mehr ›“ row of an overview and its sheet – nothing when [More.of] has nothing to add. */
@Composable
internal fun MoreEntry(t: Tour, review: TourReview?) {
    val parts = More.of(t, review)
    var open by remember { mutableStateOf(false) }
    if (parts == null) return
    MoreRow { open = true }
    if (open) MoreSheet(parts) { open = false }
}

@Composable
private fun MoreRow(onClick: () -> Unit) {
    Column {
        HorizontalDivider(color = OnPathColors.Line)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            Text(str(Res.string.routes_review_more), style = MaterialTheme.typography.bodyLarge, color = OnPathColors.Ink,
                modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = OnPathColors.InkMuted)
        }
        HorizontalDivider(color = OnPathColors.Line)
    }
}

/** „Mehr ›“: the sheet from below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(parts: More.Parts, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = OnPathColors.Paper) { MoreContent(parts) }
}

/** The sheet's content (also drawn alone in the screenshots): one block per known part, calm, the app's colours. */
@Composable
internal fun MoreContent(parts: More.Parts) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(str(Res.string.routes_review_more), style = MaterialTheme.typography.titleLarge, color = OnPathColors.Ink)
        parts.clock?.let { ClockBar(it) }
        parts.descent?.let { ValueRow(str(Res.string.routes_review_descent), it) }
        parts.rates?.let { RatePair(it) }
        parts.plan?.let { PlanBars(it) }
        parts.note?.let { Text(whole(it), style = MaterialTheme.typography.bodyMedium, color = OnPathColors.InkMuted) }
    }
}

private val BAR_HEIGHT = 12.dp
private val MOVING = OnPathColors.ActionGo
private val TRACK = OnPathColors.PaperDeep

/** „Unterwegs“: start clock … end clock under a slim bar, the pauses as light gaps in it (a small key when there are some). */
@Composable
private fun ClockBar(c: More.Clock) {
    val body = MaterialTheme.typography.bodyMedium
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = c.description },
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(str(Res.string.routes_review_on_the_way), style = body, color = OnPathColors.Ink)
        Canvas(Modifier.fillMaxWidth().height(BAR_HEIGHT)) {
            val r = size.height / 2
            val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r))) }
            clipPath(shape) {
                drawRect(MOVING)
                val min = 2.dp.toPx()
                c.gaps.forEach { (a, b) ->
                    val x0 = a * size.width
                    drawRect(TRACK, Offset(x0, 0f), Size(maxOf((b - a) * size.width, min), size.height))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(instrument(c.from, body, OnPathColors.Ink), style = body, color = OnPathColors.Ink, maxLines = 1, softWrap = false)
            Row(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                c.key?.let {
                    Box(Modifier.size(width = 14.dp, height = 10.dp).clip(RoundedCornerShape(3.dp)).background(TRACK)
                        .border(1.dp, OnPathColors.Line, RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = OnPathColors.InkMuted, textAlign = TextAlign.Center)
                }
            }
            Text(instrument(c.to, body, OnPathColors.Ink), style = body, color = OnPathColors.Ink, maxLines = 1, softWrap = false)
        }
    }
}

/** A plain label – value row („Abstieg ↓640 m“). */
@Composable
private fun ValueRow(label: String, value: String) {
    val body = MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = body, color = OnPathColors.Ink, modifier = Modifier.weight(1f))
        Text(instrument(value, MaterialTheme.typography.bodyLarge, OnPathColors.Ink), style = MaterialTheme.typography.bodyLarge,
            color = OnPathColors.Ink, maxLines = 1, softWrap = false)
    }
}

/** Bergauf / Bergab as two bars measured on each other, each with its m/h. */
@Composable
private fun RatePair(r: More.Rates) {
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = r.description },
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        r.upMh?.let { BarRow("↑ " + str(Res.string.routes_review_uphill), r.part(it), ReviewText.perHour(it), MOVING) }
        r.downMh?.let { BarRow("↓ " + str(Res.string.routes_review_downhill), r.part(it), ReviewText.perHour(it), MOVING) }
    }
}

/** Geplant (plan blue) over Gebraucht (the done tour's green), one scale, the difference under them. */
@Composable
private fun PlanBars(p: More.Plan) {
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = p.description },
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BarRow(str(Res.string.routes_more_planned), p.plannedPart, ReviewText.time(p.plannedS), OnPathColors.ActionEdit)
        BarRow(str(Res.string.routes_more_needed), p.neededPart, ReviewText.time(p.neededS), MOVING)
        val without = str(Res.string.routes_more_without_breaks)
        Text(buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = OnPathColors.Ink)) { append(whole(p.difference)) }
            append(" · ${whole(without)}")
        }, style = MaterialTheme.typography.bodyMedium, color = OnPathColors.InkMuted, textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth())
    }
}

/**
 * label | bar (filled to [part]) | value. At a large font the label goes on its own line and the bar with its value under
 * it – side by side the label would break inside the word.
 */
@Composable
private fun BarRow(label: String, part: Float, value: String, color: Color) {
    val body = MaterialTheme.typography.bodyMedium
    val large = LocalDensity.current.fontScale >= DraftLook.STACK_FONT_SCALE
    val bar = @Composable { m: Modifier ->
        Box(m.height(BAR_HEIGHT).clip(RoundedCornerShape(BAR_HEIGHT / 2)).background(TRACK)) {
            if (part > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(part).clip(RoundedCornerShape(BAR_HEIGHT / 2)).background(color))
        }
    }
    val valueText = @Composable {
        Text(instrument(value, MaterialTheme.typography.bodyLarge, OnPathColors.Ink), style = MaterialTheme.typography.bodyLarge,
            color = OnPathColors.Ink, maxLines = 1, softWrap = false, textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 76.dp).padding(start = 10.dp))
    }
    if (large) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = body, color = OnPathColors.Ink)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { bar(Modifier.weight(1f)); valueText() }
        }
        return
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = body, color = OnPathColors.Ink, maxLines = 1, modifier = Modifier.width(LABEL_WIDTH))
        bar(Modifier.weight(1f))
        valueText()
    }
}

/** A line of parts („Wandern T2 · 3. längste von 14“) that wraps only between its parts, never inside one. */
private fun whole(text: String): String = text.split(" · ").joinToString(" · ") { it.replace(' ', ' ') }

/** The bar rows' label column: „Gebraucht“ / „↓ Downhill“ fit at the normal font. */
private val LABEL_WIDTH = 100.dp
