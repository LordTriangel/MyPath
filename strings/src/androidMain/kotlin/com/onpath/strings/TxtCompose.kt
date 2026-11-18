package com.onpath.strings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import java.util.Locale

/** A text in Compose; read again when the language changes (the activity is recreated then anyway). */
@Composable
fun str(res: StringResource, vararg args: Any): String {
    val lang = Locale.getDefault().language
    return remember(res, lang, *args) { Txt.s(res, *args) }
}

/** A plural text in Compose: "1 point" / "3 points"; [args] usually start with the count. */
@Composable
fun plural(res: PluralStringResource, count: Int, vararg args: Any): String {
    val lang = Locale.getDefault().language
    return remember(res, lang, count, *args) { Txt.p(res, count, *args) }
}
