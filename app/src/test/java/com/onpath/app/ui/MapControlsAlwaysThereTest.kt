package com.onpath.app.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-11-06: in both map columns (Plan, Navigate) compass, locate, eye and whole route are always there – greyed
 * when they cannot act (no route yet), never hidden: nothing jumps, and one sees what one could do. Undo / Redo left the
 * column (owner 2026-11-08): their own pill under it, only while there is something to undo or redo.
 */
class MapControlsAlwaysThereTest {
    @Test fun aDisabledControlIsGreyNotGone() {
        val t = OnPathColors.ActionEdit
        assertEquals(t, MapControlState.tint(t, true))
        assertEquals(MapControlState.DISABLED_ALPHA, MapControlState.tint(t, false).alpha, 0.001f)
    }
}
