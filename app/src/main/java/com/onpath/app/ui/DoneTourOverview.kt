package com.onpath.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onpath.app.data.Format
import com.onpath.app.data.LastActivity
import com.onpath.app.data.Tour
import com.onpath.app.routing.Activity
import com.onpath.app.time.TourReview
import com.onpath.strings.*
import kotlin.math.roundToInt

/**
 * The words and values of a done tour's look back (owner 2026-11-15, round 3 of the tour analysis,
 * https://claude.ai/artifact/4QQF66CN7f8L2pJ15xY5fm). Pure: a value the review does not have gives null, and the screen
 * leaves its line out.
 */
internal object ReviewText {
    /** "4:35 h", under an hour "50 min". */
    fun time(s: Double): String = Format.durationShort(s)

    /** "↑620 m" */
    fun ascent(m: Double): String = "↑${Format.meters(m)}"

    /** "↓640 m" */
    fun descent(m: Double): String = "↓${Format.meters(m)}"

    /** "6,1" – where along the tour, one decimal. */
    fun km(m: Double): String = Format.number(m / 1000.0, 1)

    /** A hike's pace "11:40 min/km"; a ride's speed "32,4 km/h" (what each reads on a road sign). */
    fun kmTime(s: Double, walking: Boolean): String =
        if (walking) (s.roundToInt()).let { "%d:%02d min/km".format(it / 60, it % 60) }
        else "${Format.number(3600.0 / s, 1)} km/h"

    fun kmh(v: Double): String = "${Format.number(v, 1)} km/h"

    fun perHour(m: Double): String = "${m.roundToInt()} m/h"

    /** "10:12–14:47" in the phone's time zone. */
    fun clockRange(startMs: Long, endMs: Long): String {
        val f = java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT)
        return "${f.format(java.util.Date(startMs))}–${f.format(java.util.Date(endMs))}"
    }

    /** The one plan line: "12 % schneller als geplant" / "… langsamer …" / "Genau wie geplant"; null = no comparison. */
    fun plan(pct: Int?): String? = when {
        pct == null -> null
        pct > 0 -> Txt.s(Res.string.routes_review_faster, pct)
        pct < 0 -> Txt.s(Res.string.routes_review_slower, -pct)
        else -> Txt.s(Res.string.routes_review_as_planned)
    }

    fun pausesLabel(count: Int?): String =
        if (count != null && count > 0) Txt.s(Res.string.routes_review_pauses_n, count) else Txt.s(Res.string.routes_review_pauses)

    /** The small line under the Details numbers: planned vs needed · the activity · its place by length – what is known. */
    fun note(t: Tour, r: TourReview): String? {
        val act = com.onpath.app.debug.enumOrNull<Activity>(t.activity, "routes")
        return listOfNotNull(
            if (r.plannedS != null && r.neededS != null)
                Txt.s(Res.string.routes_review_plan_note, com.onpath.app.time.TimeEstimate.hm(r.plannedS), com.onpath.app.time.TimeEstimate.hm(r.neededS))
            else null,
            act?.let { LastActivity.name(it, t.sac) },
            r.rank?.let { Txt.s(Res.string.routes_review_rank, it.place, it.of) },
        ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /** A highlight row: icon, label, value, where (km). */
    data class Row(val icon: ImageVector, val label: String, val value: String, val where: String)

    /** The highlights the data gives, in the sketch's order: longest pause, highest point, fastest km. */
    fun highlights(t: Tour, r: TourReview): List<Row> = listOfNotNull(
        r.longestPause?.let { Row(Icons.Outlined.PauseCircle, Txt.s(Res.string.routes_review_longest_pause), time(it.value), at(it.atM)) },
        r.highest?.let { Row(Icons.Outlined.Landscape, Txt.s(Res.string.routes_review_highest), Format.meters(it.value), at(it.atM)) },
        r.fastestKm?.let {
            Row(Icons.Outlined.Speed, Txt.s(Res.string.routes_review_fastest_km), kmTime(it.value, t.activity == Activity.HIKE.name),
                Txt.s(Res.string.routes_review_at_km, (it.atM / 1000).roundToInt().toString()))
        },
    )

    private fun at(m: Double) = Txt.s(Res.string.routes_review_at_km, km(m))

    /** The Details sheet's numbers (label, value) – only the known ones. */
    fun details(t: Tour, r: TourReview): List<Pair<String, String>> = listOfNotNull(
        if (r.startMs != null && r.endMs != null) Txt.s(Res.string.routes_review_on_the_way) to clockRange(r.startMs, r.endMs) else null,
        r.movingS?.let { Txt.s(Res.string.routes_review_moving) to time(it) },
        r.pauseS?.let { pausesLabel(r.pauseCount) to time(it) },
        r.avgKmh?.let { Txt.s(Res.string.routes_review_avg) to kmh(it) },
        Txt.s(Res.string.routes_review_ascent) to ascent(t.ascentM),
        Txt.s(Res.string.routes_review_descent) to descent(t.descentM),
        r.upMh?.let { Txt.s(Res.string.routes_review_uphill) to perHour(it) },
        r.downMh?.let { Txt.s(Res.string.routes_review_downhill) to perHour(it) },
    )
}

/**
 * The large overview of a done tour (owner 2026-11-15, round 3 of the tour analysis – the compromise between the owner's
 * other proposal and the compact sketch, in the app's colours): title + share, three big values (distance, ascent, total
 * time), the map picture with the height profile under it in one card, moving | pauses, the plan line, the highlights,
 * „Details ›“ (a sheet with the further numbers) and the overview's foot. One screen at a normal font; at a large font the
 * page scrolls (nothing is cut). [review] null = still read / not known: the stored numbers only.
 */
@Composable
internal fun DoneTourOverview(
    t: Tour, review: TourReview?,
    picture: @Composable (Modifier) -> Unit,
    foot: @Composable () -> Unit,
    onExportTrack: (() -> Unit)?, onExportPlan: (() -> Unit)?,
) {
    var details by remember { mutableStateOf(false) }
    val r = review
    // a large font scrolls the page anyway: the picture keeps a size worth looking at
    val large = LocalDensity.current.fontScale >= DraftLook.STACK_FONT_SCALE
    FillOrScroll(minMiddle = if (large) MIN_PICTURE_LARGE else MIN_PICTURE,
        top = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReviewHead(t, onExportTrack, onExportPlan)
                BigValues(listOf(
                    Format.distance(t.distanceM) to str(Res.string.routes_review_distance),
                    ReviewText.ascent(t.ascentM) to str(Res.string.routes_review_ascent),
                    ReviewText.time(r?.totalS ?: t.durationS) to str(Res.string.routes_review_total),
                ), dividerBelow = true)
            }
        },
        middle = {
            Column(Modifier.fillMaxSize().padding(vertical = 8.dp).clip(OnPathShapes.Card).background(OnPathColors.PaperDim)) {
                picture(Modifier.weight(1f).fillMaxWidth())
                r?.profile?.takeIf { it.size >= 2 }?.let { MiniProfile(it, Modifier.fillMaxWidth().height(PROFILE_HEIGHT)) }
            }
        },
        bottom = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val two = listOfNotNull(
                    r?.movingS?.let { ReviewText.time(it) to str(Res.string.routes_review_moving) },
                    r?.pauseS?.let { ReviewText.time(it) to ReviewText.pausesLabel(r.pauseCount) },
                )
                if (two.isNotEmpty()) BigValues(two, dividerBelow = true, top = false)
                r?.let { ReviewText.plan(it.planPct) }?.let { PlanLine(it, faster = (r.planPct ?: 0) >= 0) }
                r?.let { ReviewText.highlights(t, it) }?.forEach { HighlightRow(it) }
                if (r != null && ReviewText.details(t, r).isNotEmpty()) DetailsRow { details = true }
                Spacer(Modifier.height(2.dp))
                foot()
            }
        })
    if (details && r != null) DetailsSheet(t, r) { details = false }
}

/** At least this much picture: below it the page scrolls instead (a large font). */
private val MIN_PICTURE = 220.dp
private val MIN_PICTURE_LARGE = 340.dp
private val PROFILE_HEIGHT = 64.dp

/**
 * [top], [middle], [bottom] one under the other: [middle] takes the height left (at least [minMiddle]); when that does not
 * fit (a large font, a small screen) the whole page scrolls with [middle] at [minMiddle] – nothing is cut.
 */
@Composable
private fun FillOrScroll(minMiddle: Dp, top: @Composable () -> Unit, middle: @Composable () -> Unit, bottom: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val avail = constraints.maxHeight
        val minPx = with(LocalDensity.current) { minMiddle.roundToPx() }
        Layout(contents = listOf(top, middle, bottom), modifier = Modifier.verticalScroll(rememberScrollState())) { (tm, mm, bm), c ->
            val w = c.maxWidth
            val loose = Constraints(maxWidth = w)
            val tp = tm.map { it.measure(loose) }
            val bp = bm.map { it.measure(loose) }
            val th = tp.sumOf { it.height }; val bh = bp.sumOf { it.height }
            val midH = (avail - th - bh).coerceAtLeast(minPx)
            val mp = mm.map { it.measure(Constraints.fixed(w, midH)) }
            layout(w, th + midH + bh) {
                var y = 0
                tp.forEach { it.place(0, y); y += it.height }
                mp.forEach { it.place(0, y) }; y += midH
                bp.forEach { it.place(0, y); y += it.height }
            }
        }
    }
}

@Composable
private fun ReviewHead(t: Tour, onExportTrack: (() -> Unit)?, onExportPlan: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        if (onExportTrack != null || onExportPlan != null) Box {
            IconButton({ menu = true }) { Icon(Icons.Outlined.IosShare, str(Res.string.routes_review_share), tint = OnPathColors.Ink) }
            DropdownMenu(menu, { menu = false }) {
                onExportTrack?.let { go ->
                    ShareItem(Icons.Filled.Timeline, str(Res.string.common_export_gpx),
                        str(Res.string.routes_review_export_track_line, com.onpath.app.gpx.GpxWriter.fileName(t.name,
                            com.onpath.app.gpx.GpxWriter.kindSuffix(planned = false, recorded = true)))) { menu = false; go() }
                }
                onExportPlan?.let { go ->
                    ShareItem(Icons.Filled.Route, str(Res.string.routes_review_export_plan),
                        str(Res.string.routes_review_export_plan_line, com.onpath.app.gpx.PlanGpx.fileName(t.name))) { menu = false; go() }
                }
            }
        }
    }
}

@Composable
private fun ShareItem(icon: ImageVector, title: String, line: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(line, style = MaterialTheme.typography.bodySmall, color = OnPathColors.InkMuted)
            }
        },
        leadingIcon = { Icon(icon, null, tint = OnPathColors.InkMuted) },
        onClick = onClick, modifier = Modifier.heightIn(min = 56.dp).widthIn(max = 320.dp))
}

/** Big values side by side with thin lines between (the calm row of the owner's proposal); [top] = a line above too. */
@Composable
private fun BigValues(values: List<Pair<String, String>>, dividerBelow: Boolean, top: Boolean = true) {
    // a large font: one value per row, the label left and the value right – side by side they would break inside words
    if (LocalDensity.current.fontScale >= DraftLook.STACK_FONT_SCALE) {
        Column(Modifier.fillMaxWidth()) {
            if (top) HorizontalDivider(color = OnPathColors.Line)
            values.forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = OnPathColors.InkMuted, modifier = Modifier.weight(1f))
                    val style = MaterialTheme.typography.titleLarge
                    Text(instrument(value, style, OnPathColors.Ink), style = style, color = OnPathColors.Ink, maxLines = 1, softWrap = false)
                }
            }
            if (dividerBelow) HorizontalDivider(color = OnPathColors.Line)
        }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        if (top) HorizontalDivider(color = OnPathColors.Line)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 6.dp)) {
            values.forEachIndexed { i, (value, label) ->
                if (i > 0) VerticalDivider(color = OnPathColors.Line)
                Column(Modifier.weight(1f).padding(start = if (i == 0) 0.dp else 10.dp, end = 4.dp)) {
                    val style = MaterialTheme.typography.titleLarge
                    Text(instrument(value, style, OnPathColors.Ink), style = style, color = OnPathColors.Ink, maxLines = 2)
                    Text(label, style = MaterialTheme.typography.bodySmall, color = OnPathColors.InkMuted, maxLines = 2)
                }
            }
        }
        if (dividerBelow) HorizontalDivider(color = OnPathColors.Line)
    }
}

/** "✓ 12 % schneller als geplant" – a calm check when faster or on time, a clock when slower (never red). */
@Composable
private fun PlanLine(text: String, faster: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 28.dp)) {
        Icon(if (faster) Icons.Filled.CheckCircle else Icons.Outlined.Schedule, null,
            tint = if (faster) OnPathColors.Good else OnPathColors.InkMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = OnPathColors.Ink)
    }
}

@Composable
private fun HighlightRow(row: ReviewText.Row) {
    val body = MaterialTheme.typography.bodyMedium
    val value = @Composable { Text(instrument(row.value, body, OnPathColors.Ink), style = body, color = OnPathColors.Ink, maxLines = 1, softWrap = false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(row.icon, null, tint = OnPathColors.InkMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        if (LocalDensity.current.fontScale >= DraftLook.STACK_FONT_SCALE) {
            // a large font: the label on its own line, value and where under it – side by side they would break inside words
            Column(Modifier.weight(1f)) {
                Text(row.label, style = body, color = OnPathColors.Ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    value()
                    Text("  ·  ${row.where}", style = body, color = OnPathColors.InkMuted, maxLines = 1, softWrap = false)
                }
            }
            return@Row
        }
        Text(row.label, style = body, color = OnPathColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        value()
        Text(row.where, style = body, color = OnPathColors.InkMuted, maxLines = 1, softWrap = false,
            textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 72.dp).padding(start = 10.dp))
    }
}

@Composable
private fun DetailsRow(onClick: () -> Unit) {
    Column {
        HorizontalDivider(color = OnPathColors.Line)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            Text(str(Res.string.routes_review_details), style = MaterialTheme.typography.bodyLarge, color = OnPathColors.Ink,
                modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = OnPathColors.InkMuted)
        }
        HorizontalDivider(color = OnPathColors.Line)
    }
}

/** „Details ›“: the further numbers in a sheet from below, three to a row (two at a large font), the small line under them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsSheet(t: Tour, r: TourReview, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = OnPathColors.Paper) {
        DetailsContent(t, r)
    }
}

/** The sheet's content (also drawn alone in the screenshots). */
@Composable
internal fun DetailsContent(t: Tour, r: TourReview) {
    val perRow = if (LocalDensity.current.fontScale >= DraftLook.STACK_FONT_SCALE) 2 else 3
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(str(Res.string.routes_review_details), style = MaterialTheme.typography.titleLarge, color = OnPathColors.Ink)
        ReviewText.details(t, r).chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = OnPathColors.InkMuted, maxLines = 2)
                        Text(instrument(value, MaterialTheme.typography.bodyLarge, OnPathColors.Ink), style = MaterialTheme.typography.bodyLarge,
                            color = OnPathColors.Ink, maxLines = 2)
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        ReviewText.note(t, r)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = OnPathColors.InkMuted) }
    }
}

/**
 * The small height profile under the map picture: the done tour's green, a light fill, a dot on the highest point,
 * "0 km" and the length under it (profile axes: the caption step).
 */
@Composable
private fun MiniProfile(profile: List<TourReview.ProfilePoint>, modifier: Modifier) {
    val green = OnPathColors.ActionGo
    val fill = OnPathColors.GreenLight
    Column(modifier.padding(horizontal = 12.dp).padding(top = 6.dp, bottom = 2.dp)) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val x0 = profile.first().atM; val x1 = profile.last().atM
            val lo = profile.minOf { it.ele }; val hi = profile.maxOf { it.ele }
            val span = (hi - lo).coerceAtLeast(MIN_SPAN_M)
            fun px(p: TourReview.ProfilePoint) = Offset(
                ((p.atM - x0) / (x1 - x0)).toFloat() * size.width,
                size.height - ((p.ele - lo) / span).toFloat() * (size.height - 6.dp.toPx()) - 2.dp.toPx())
            val line = Path().apply { profile.forEachIndexed { i, p -> px(p).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } } }
            val area = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
            drawPath(area, fill)
            drawPath(line, green, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
            val top = px(profile.maxBy { it.ele })
            drawCircle(Color.White, 4.5.dp.toPx(), top)
            drawCircle(green, 3.dp.toPx(), top)
        }
        Row(Modifier.fillMaxWidth()) {
            Text("0 km", style = MaterialTheme.typography.labelSmall, color = OnPathColors.InkMuted, modifier = Modifier.weight(1f))
            Text(Format.distance(profile.last().atM), style = MaterialTheme.typography.labelSmall, color = OnPathColors.InkMuted)
        }
    }
}

/** A flat tour still shows some relief: at least this height range fills the profile. */
private const val MIN_SPAN_M = 40.0
