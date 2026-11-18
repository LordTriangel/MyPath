package com.onpath.app.ui.suggest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onpath.app.data.Format
import com.onpath.app.routing.Activity
import com.onpath.app.suggest.SuggestCard
import com.onpath.app.suggest.SuggestPill
import com.onpath.app.ui.ActionButtons
import com.onpath.app.ui.OnPathColors
import com.onpath.app.ui.OnPathShadow
import com.onpath.app.ui.warmShadow
import com.onpath.strings.*
import kotlin.math.roundToInt

/*
 * The round-trip suggestions' screens (docs/design/tour-suggestions.md §7 stage 4; the planner's web/app/src/suggest.ts
 * is the model): the wish sheet of sketch 5 B as a Material 3 dialog – activity as plain labels (no icons, owner
 * 2026-11-17), time and ascent as two-handle range sliders, the Ziel chips, Abbrechen · OK – and the panel in the plan
 * sheet: the wait, then the cards of sketch 4 A with the pills of sketch 7 (≤ 2 green + ≤ 2 yellow, no bars).
 */

/** Time text of the wish: „2–3 h“, halves with the language's mark („2,5–4 h“). */
fun hoursText(h: ClosedFloatingPointRange<Float>): String {
    fun one(v: Float) = if (v % 1f == 0f) v.toInt().toString() else Format.number(v.toDouble(), 1)
    return Txt.s(Res.string.suggest_ui_hours, one(h.start), one(h.endInclusive))
}

/** Ascent text of the wish: „bis 400 m“, „300–600 m“, „ab 300 m“, „egal“ (an end at the scale's end is open). */
fun ascentText(up: ClosedFloatingPointRange<Float>): String {
    val lo = up.start.roundToInt(); val hi = up.endInclusive.roundToInt()
    val open = hi >= SuggestWish.UP_MAX.roundToInt()
    return when {
        lo <= 0 && open -> Txt.s(Res.string.suggest_ui_ascent_any)
        lo <= 0 -> Txt.s(Res.string.suggest_ui_ascent_upto, hi.toString())
        open -> Txt.s(Res.string.suggest_ui_ascent_from, lo.toString())
        else -> Txt.s(Res.string.suggest_ui_ascent_range, lo.toString(), hi.toString())
    }
}

/** The wish in one line for the panel's head: „Wandern · T2 · 2–3 h · ↑ bis 400 m“. */
fun wishSummary(w: SuggestWish): String =
    listOfNotNull(w.activity.label, if (w.activity == Activity.HIKE) "T${w.sac}" else null, hoursText(w.hours), "↑ " + ascentText(w.ascent))
        .joinToString(" · ") { it.replace(' ', '\u00A0') } // breaks only between the parts

private fun zielText(z: String): String = Txt.s(when (z) {
    "gipfel" -> Res.string.suggest_ziel_gipfel
    "wasser" -> Res.string.suggest_ziel_wasser
    "aussicht" -> Res.string.suggest_ziel_aussicht
    "einkehr" -> Res.string.suggest_ziel_einkehr
    else -> Res.string.suggest_ziel_kultur
})

/** A range slider's value snapped to [step] and kept inside its scale. */
private fun snap(r: ClosedFloatingPointRange<Float>, step: Float, min: Float, max: Float): ClosedFloatingPointRange<Float> {
    fun s(v: Float) = ((v / step).roundToInt() * step).coerceIn(min, max)
    val a = s(r.start); val b = s(r.endInclusive)
    return a..maxOf(a, b)
}

/** The wish sheet (sketch 5 B): a Material 3 dialog, Abbrechen · OK in one row at the bottom right. */
@Composable
fun SuggestWishDialog(wish: SuggestWish, onChange: (SuggestWish) -> Unit, onCancel: () -> Unit, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(str(Res.string.suggest_ui_from_here)) },
        text = { WishFields(wish, onChange) },
        confirmButton = {
            TextButton(colors = ActionButtons.editText(), onClick = onOk, modifier = Modifier.heightIn(min = 48.dp)) { Text(str(Res.string.common_ok)) }
        },
        dismissButton = {
            TextButton(colors = ActionButtons.editText(), onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(str(Res.string.common_cancel)) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WishFields(wish: SuggestWish, onChange: (SuggestWish) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // activity: plain labels, the plan's one pre-set; the T level beside it for a hike
        Column {
            FieldHead(str(Res.string.suggest_ui_activity), null)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(wish.activity.label, Activity.entries.map { it.label }) { i -> onChange(wish.copy(activity = Activity.entries[i])) }
                if (wish.activity == Activity.HIKE)
                    Choice("T${wish.sac}", (1..5).map { "T$it" }) { i -> onChange(wish.copy(sac = i + 1)) }
            }
        }
        Column {
            FieldHead(str(Res.string.suggest_ui_time), hoursText(wish.hours))
            Range(wish.hours, SuggestWish.H_MIN, SuggestWish.H_MAX, SuggestWish.H_STEP) { onChange(wish.copy(hours = it)) }
        }
        Column {
            FieldHead(str(Res.string.suggest_ui_ascent), ascentText(wish.ascent))
            Range(wish.ascent, 0f, SuggestWish.UP_MAX, SuggestWish.UP_STEP) { onChange(wish.copy(ascent = it)) }
        }
        Column {
            FieldHead(str(Res.string.suggest_ui_ziel), null)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (z in SuggestWish.ZIEL) {
                    val on = z in wish.ziel
                    FilterChip(selected = on, onClick = { onChange(wish.copy(ziel = if (on) wish.ziel - z else wish.ziel + z)) },
                        label = { Text(zielText(z)) })
                }
            }
        }
    }
}

/** A field's name (small, muted) and its value at the right („2–3 h“). */
@Composable
private fun FieldHead(name: String, value: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** A plain label with ▾ that opens its choices (no icons on the activity, owner 2026-11-17). */
@Composable
private fun Choice(label: String, choices: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = true, onClick = { open = true }, label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEachIndexed { i, c -> DropdownMenuItem(text = { Text(c) }, onClick = { open = false; onPick(i) }) }
        }
    }
}

@Composable
private fun Range(value: ClosedFloatingPointRange<Float>, min: Float, max: Float, step: Float, onChange: (ClosedFloatingPointRange<Float>) -> Unit) {
    RangeSlider(value = value, onValueChange = { onChange(snap(it, step, min, max)) }, valueRange = min..max,
        steps = ((max - min) / step).roundToInt() - 1,
        colors = SliderDefaults.colors(thumbColor = OnPathColors.ActionEdit, activeTrackColor = OnPathColors.ActionEdit,
            inactiveTrackColor = OnPathColors.EditTonal,
            activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent))
}

/** What the panel shows ([SuggestSlice]'s state, plain for screenshots). */
data class SuggestPanelUi(
    val phase: SuggestPhase,
    val summary: String,
    val cards: List<SuggestCard> = emptyList(),
    val notes: List<String> = emptyList(),
    val chosen: Int = -1,
    val taking: Boolean = false,
)

class SuggestPanelCallbacks(
    val onChoose: (Int) -> Unit = {},
    val onTake: (Int) -> Unit = {},
    val onChange: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/**
 * The suggestions in the plan sheet's place (like the place card): the wait, or the cards under a head „3 Rundtouren“ ·
 * Wunsch ändern · ✕. At most ~58 % of the screen high, so the chosen loop stays visible above it; the cards scroll.
 */
@Composable
fun SuggestPanel(ui: SuggestPanelUi, cb: SuggestPanelCallbacks, modifier: Modifier = Modifier) {
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.58f).dp
    Surface(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).navigationBarsPadding()
            .warmShadow(OnPathShadow.HIGH, MaterialTheme.shapes.extraLarge),
        shape = MaterialTheme.shapes.extraLarge, color = OnPathColors.Paper,
    ) {
        Column(Modifier.heightIn(max = maxH).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val head = when (ui.phase) {
                    SuggestPhase.CARDS -> plural(Res.plurals.suggest_ui_count, ui.cards.size, ui.cards.size.toString())
                    else -> str(Res.string.suggest_ui_from_here)
                }
                Text(head, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = cb.onClose, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = str(Res.string.common_close))
                }
            }
            Column(Modifier.padding(end = 10.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // the wish in one line, „Wunsch ändern“ beside it (the head keeps the count whole at a large font)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ui.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    if (ui.phase == SuggestPhase.CARDS || ui.phase == SuggestPhase.FAILED)
                        TextButton(colors = ActionButtons.editText(), onClick = cb.onChange, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(str(Res.string.suggest_ui_change))
                        }
                }
                when (ui.phase) {
                    SuggestPhase.WAIT, SuggestPhase.LOADING -> Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp, color = OnPathColors.ActionEdit)
                        Spacer(Modifier.width(12.dp))
                        Text(str(if (ui.phase == SuggestPhase.LOADING) Res.string.suggest_ui_loading_routing else Res.string.suggest_ui_wait),
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                    SuggestPhase.FAILED -> Note(str(Res.string.suggest_ui_failed))
                    SuggestPhase.CARDS -> {
                        ui.notes.forEach { Note(it) }
                        ui.cards.forEachIndexed { i, c -> SuggestCardView(c, chosen = i == ui.chosen, taking = ui.taking && i == ui.chosen,
                            onClick = { cb.onChoose(i) }, onTake = { cb.onTake(i) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Surface(color = OnPathColors.WarnContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = OnPathColors.OnWarnContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
    }
}

/** „8,4 km · 2:10 h · ↑ 180 m“ */
fun cardStats(c: SuggestCard): String =
    "${Format.number(c.km, 1)} km · ${Format.durationShort(c.hours * 3600)} · ↑ ${c.ascent} m"

/** One card (sketch 4 A): badge, title, numbers, the part on the signed loop, the text line (whole when chosen), the pills. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestCardView(c: SuggestCard, chosen: Boolean, taking: Boolean = false, onClick: () -> Unit = {}, onTake: () -> Unit = {}) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = OnPathColors.CardPaper,
        border = if (chosen) BorderStroke(2.dp, OnPathColors.ActionEdit) else BorderStroke(1.dp, OnPathColors.Line),
        modifier = Modifier.fillMaxWidth().semantics { selected = chosen }.clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Badge(c.badge)
            Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(cardStats(c), style = MaterialTheme.typography.bodyMedium)
            c.loopKm?.let {
                Text(str(Res.string.suggest_ui_on_loop, Format.number(it, 1)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (c.line.isNotBlank()) Text(c.line, style = MaterialTheme.typography.bodySmall,
                maxLines = if (chosen) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
            if (c.pills.isNotEmpty()) FlowRow(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) { c.pills.forEach { Pill(it) } }
            if (chosen) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                Button(colors = ActionButtons.edit(), onClick = onTake, enabled = !taking, modifier = Modifier.heightIn(min = 48.dp)) {
                    if (taking) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(str(Res.string.suggest_ui_taking))
                    } else Text(str(Res.string.suggest_ui_take))
                }
            }
        }
    }
}

/** „Ausgeschildert · Traumpfad“ (blue) or „Vorschlag“ (green), small. */
@Composable
private fun Badge(badge: String?) {
    val (bg, fg) = if (badge != null) SuggestColours.SignedBg to SuggestColours.SignedInk else SuggestColours.OwnBg to SuggestColours.OwnInk
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(if (badge != null) str(Res.string.suggest_ui_badge_signed, badge) else str(Res.string.suggest_ui_badge_own),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = fg,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A sketch-7 pill: light green (strength) or light yellow (weakness / hint), three intensities. */
@Composable
private fun Pill(p: SuggestPill) {
    val bg = SuggestColours.pill(p.strength, p.level)
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(p.text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            color = if (p.strength) SuggestColours.GreenInk else SuggestColours.YellowInk,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), maxLines = 1)
    }
}

/** The colours of the planner's cards (suggest.css), the same on the phone. */
object SuggestColours {
    val SignedBg = Color(0xFFE3E9F7)
    val SignedInk = Color(0xFF1E3FAE)
    val OwnBg = Color(0xFFDDE9DB)
    val OwnInk = Color(0xFF1F4F2A)
    val GreenInk = Color(0xFF1C3F1F)
    val YellowInk = Color(0xFF4A3A00)
    private val GREEN = listOf(Color(0xFFDCEDD4), Color(0xFFCDE6C3), Color(0xFFA9D49A))
    private val YELLOW = listOf(Color(0xFFF7E8B8), Color(0xFFF6E4A6), Color(0xFFEED073))
    fun pill(strength: Boolean, level: Int): Color = (if (strength) GREEN else YELLOW)[(level - 1).coerceIn(0, 2)]
}
