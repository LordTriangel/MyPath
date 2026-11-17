package com.onpath.app.poi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.onpath.app.debug.Problems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The map's POI symbols for the UI (owner 2026-11-17: the details sheet shows the symbol the map draws – fast food was
 * ♨ in the sheet but a burger on the map): the badge "poi-<kind>" cut from the bundled sprite
 * (assets/sprites/onpath@2x, tools/make_sprite.py), read once, kept per kind. Never throws: a symbol that cannot be
 * read is reported to Problems and the caller shows the kind's glyph instead.
 */
object PoiSprite {
    const val JSON = "sprites/onpath@2x.json"
    const val PNG = "sprites/onpath@2x.png"

    class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

    /** Where the badge of [kind] lies in the sprite sheet described by [spriteJson]; null when the sprite has none. */
    fun rect(spriteJson: String, kind: String): Rect? {
        val o = JSONObject(spriteJson).optJSONObject("poi-$kind") ?: return null
        return Rect(o.getInt("x"), o.getInt("y"), o.getInt("width"), o.getInt("height"))
    }

    private var sheet: Pair<String, Bitmap>? = null
    private val badges = HashMap<String, ImageBitmap?>()

    /** The badge of [kind] (off the main thread: the first call reads the sprite); null = show the glyph. */
    fun badge(context: Context, kind: String): ImageBitmap? = synchronized(this) {
        if (kind in badges) return badges[kind]
        val image = try {
            val (json, png) = sheet ?: (context.assets.open(JSON).bufferedReader().use { it.readText() } to
                (context.assets.open(PNG).use { BitmapFactory.decodeStream(it) } ?: error("sprite image not decoded")))
                .also { sheet = it }
            // a kind the map draws without a symbol of its own (not in the sprite) keeps its glyph – nothing to report
            rect(json, kind)?.let { r -> Bitmap.createBitmap(png, r.x, r.y, r.w, r.h).asImageBitmap() }
        } catch (e: Exception) {
            Problems.warn("poi", "Map symbol of POI kind $kind not read from the sprite, the details show its glyph", e)
            null
        }
        badges[kind] = image
        image
    }
}

/** The symbol of a POI kind for a sheet: [ready] once the sprite was read; [image] null = show the glyph. */
class PoiSymbol(val image: ImageBitmap?, val ready: Boolean)

/** [PoiSprite.badge] read off the main thread; until then not [PoiSymbol.ready]. */
@Composable
fun rememberPoiSymbol(kind: String): PoiSymbol {
    val context = LocalContext.current
    val symbol by produceState(PoiSymbol(null, false), kind) {
        value = PoiSymbol(withContext(Dispatchers.IO) { PoiSprite.badge(context.applicationContext, kind) }, true)
    }
    return symbol
}
