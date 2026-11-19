package com.onpath.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wiring of the UX logic rules that live in Compose
 * screens / the ViewModel and so cannot be run on the JVM: the source is checked instead (like PlanStatusTest).
 */
class UxReviewFixesTest {
    private val map = File("src/main/java/com/onpath/app/ui/MapScreen.kt").readText()

    /** Problem 3 (owner Q1): the activity in Plan during a tour opens the same check-and-switch sheet as Navigate. */
    @Test fun activityInPlanDuringATourOpensTheCheckSheet() {
        val plan = map.substringAfter("PlanMode(vm, rec,").substringBefore("navigate = {")
        assertTrue(plan, plan.contains("onActivity = { if (rec != null) vm.activityCheckSlice.openActivityCheck() else showActivity = true }"))
        assertTrue(map.contains("ActivitySwitchSheet(it, vm.activityCheckSlice::chooseActivity, vm.activityCheckSlice::closeActivityCheck"))
        // a proposal chosen in Plan is answered in Navigate (Take new route / Keep) = ActivityCheckFlowTest
        // .chosenInPlanDuringATourTheQuestionIsAskedInNavigateAfterTheCommand (the slice's own lines, test clean-up G09, 2026-11-19)
    }

    /** Problem 10: on a first start the restore offer waits for the welcome / phone setup guide and the loading modal. */
    @Test fun firstStartDialogsComeOneAfterTheOther() {
        assertTrue(RestoreOfferRule.show(offer = true, recording = false, locationAsking = false, loadModalOpen = false))
        assertFalse("location request open", RestoreOfferRule.show(true, recording = false, locationAsking = true, loadModalOpen = false))
        assertFalse("loading modal open", RestoreOfferRule.show(true, recording = false, locationAsking = false, loadModalOpen = true))
        assertFalse("during a tour", RestoreOfferRule.show(true, recording = true, locationAsking = false, loadModalOpen = false))
        assertFalse("nothing to offer", RestoreOfferRule.show(false, recording = false, locationAsking = false, loadModalOpen = false))
        val main = File("src/main/java/com/onpath/app/MainActivity.kt").readText()
        assertTrue(main.contains("RestoreOfferRule.show(vm.restoreOffer"))
        assertTrue(main.contains("vm.locationAsking || vm.setup.setupOpen, vm.mapData.loadModalOpen"))
        // no system dialog at the first screen: the welcome page, location only from the guide after its explanation
        assertTrue(main.contains("else vm.setup.setupWelcome = true"))
        assertTrue(main.contains("vm.locationAsking = true\n                    permissionLauncher.launch"))
    }

}
