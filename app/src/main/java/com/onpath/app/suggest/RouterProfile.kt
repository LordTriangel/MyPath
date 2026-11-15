package com.onpath.app.suggest

import com.onpath.app.routing.Activity
import com.onpath.app.routing.BRouterEngine
import java.io.File

/**
 * The routing profile of an activity exactly as the app routes with it: the same .brf and the same parameters
 * ([BRouterEngine.profileFile], [BRouterEngine.keyValues] – SAC limit, e-bike power …), so the network's "allowed" is
 * the router's verdict (a suggestion must never use a way navigation refuses). Kept apart from [NetReader] so the
 * reader compiles without the app's routing code (bench tools).
 */
object RouterProfile {
    /** [profileDir] = where the app keeps lookups.dat and the .brf files (BRouterEngine copies them from the assets). */
    fun of(profileDir: File, activity: Activity, sacLimit: Int): NetReader.Profile =
        NetReader.Profile(File(profileDir, BRouterEngine.profileFile(activity)), BRouterEngine.keyValues(activity, sacLimit))
}
