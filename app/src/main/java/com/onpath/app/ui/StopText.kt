package com.onpath.app.ui

import com.onpath.strings.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * [text] with its `{n}` tokens drawn as the map's plan point circles ([StopTokens]); plain text otherwise. The circle
 * is a little taller than the letters (1.25 em), so it reads at the button / card size.
 */
@Composable
fun StopText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, fontWeight: FontWeight? = null,
             textAlign: TextAlign? = null, maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip) {
    if (!StopTokens.has(text)) {
        Text(text, modifier, color = color, style = style, fontWeight = fontWeight, textAlign = textAlign, maxLines = maxLines,
            overflow = overflow)
        return
    }
    val inline = mutableMapOf<String, InlineTextContent>()
    val annotated: AnnotatedString = buildAnnotatedString {
        StopTokens.parts(text).forEachIndexed { i, p ->
            if (p is StopTokens.Token) {
                val id = "stop$i"
                appendInlineContent(id, StopTokens.spoken(p))
                inline[id] = InlineTextContent(Placeholder(1.25.em, 1.25.em, PlaceholderVerticalAlign.TextCenter)) { StopCircle(p) }
            } else append(p as String)
        }
    }
    Text(annotated, modifier, color = color, style = style, fontWeight = fontWeight, textAlign = textAlign, maxLines = maxLines,
        overflow = overflow, inlineContent = inline)
}

/** The circle itself: white ring (reads on green and on white), the point's colour, its number or a flag. */
@Composable
private fun StopCircle(t: StopTokens.Token) {
    val fill = when (t.kind) {
        StopTokens.Kind.STOP -> OnPathColors.PointStop
        StopTokens.Kind.START -> OnPathColors.PointStart
        StopTokens.Kind.GOAL -> OnPathColors.PointGoal
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(OnPathColors.CardWhite, CircleShape)
        .padding(1.5.dp).background(fill, CircleShape), contentAlignment = Alignment.Center) {
        // the number scales with the circle (an em size inside inline content rendered as a dot – no number)
        val num = with(androidx.compose.ui.platform.LocalDensity.current) { (maxHeight * 0.68f).toSp() }
        if (t.kind == StopTokens.Kind.GOAL) Icon(Icons.Filled.Flag, null, tint = OnPathColors.CardWhite, modifier = Modifier.fillMaxSize().padding(3.dp))
        else Text(t.number.toString(), color = OnPathColors.CardWhite, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
            style = TextStyle(fontSize = num, lineHeight = num, textAlign = TextAlign.Center,
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)))
    }
}
