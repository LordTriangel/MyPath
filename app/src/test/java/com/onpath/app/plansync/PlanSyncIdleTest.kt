package com.onpath.app.plansync

import com.onpath.app.sync.SyncRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** C16, what stays in :app of the old SyncPollTest (it moved to :core with the poll rules): [PlanSync.idle] and [PlanSync.shown]. */
class PlanSyncIdleTest {
    @Test fun anIdlePassStaysOutOfTheEventLog() {
        assertTrue(PlanSync.idle(SyncPassResult(SyncRound.Outcome.Done(0, 0, 0))))
        assertFalse(PlanSync.idle(SyncPassResult(SyncRound.Outcome.Done(1, 0, 0))))
        assertFalse(PlanSync.idle(SyncPassResult(SyncRound.Outcome.Done(0, 0, 0), added = 1, toursChanged = true)))
        assertFalse(PlanSync.idle(SyncPassResult(SyncRound.Outcome.Offline)))
        assertFalse(PlanSync.idle(null))
    }

    /**
     * Field bug 2026-11-12 (re-pairing, 429 / 500 from one home IP): a busy server is no red „nicht angenommen“ in
     * Settings – a quiet retry; a real refusal (401, 507 …) is still said.
     */
    @Test fun busyServerShowsNoRefusal() {
        for (status in listOf(429, 500, 503)) assertNull("$status", PlanSync.shown(SyncRound.Outcome.Refused(status, "x")))
        assertEquals(SyncProblem.Refused(401, "x"), PlanSync.shown(SyncRound.Outcome.Refused(401, "x")))
        assertEquals(SyncProblem.Refused(507, null), PlanSync.shown(SyncRound.Outcome.Refused(507, null)))
    }
}
