package com.onpath.app.ui.suggest

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.onpath.app.ui.AppViewModel

/** The wish sheet while it is open ([SuggestSlice.wishOpen]); MapScreen composes it over the map. */
@Composable
fun SuggestWishHost(vm: AppViewModel) {
    val s = vm.suggest
    val w = s.wish
    if (!s.wishOpen || w == null) return
    SuggestWishDialog(w, onChange = { s.wish = it }, onCancel = s::cancelWish, onOk = s::ok)
}

/**
 * The suggestions in the plan sheet's place (MapScreen's mount point) while [SuggestSlice.phase] is set: Back closes
 * them; a point set on the map meanwhile (the plan is no longer empty) closes them too – the plan sheet comes back.
 */
@Composable
fun SuggestHostPanel(vm: AppViewModel) {
    val s = vm.suggest
    val phase = s.phase ?: return
    BackHandler { s.close() }
    val planned = vm.waypoints.isNotEmpty()
    LaunchedEffect(planned) { if (planned && !s.taking) s.close() }
    SuggestPanel(
        SuggestPanelUi(phase, s.wish?.let(::wishSummary).orEmpty(), s.cards, s.notes, s.chosen, s.taking),
        SuggestPanelCallbacks(onChoose = s::choose, onTake = s::take, onChange = s::change, onClose = s::close),
    )
}
