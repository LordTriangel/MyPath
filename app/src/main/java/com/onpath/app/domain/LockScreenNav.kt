package com.onpath.app.domain

/**
 * Navigate shows over the lock screen (owner) only while a tour runs and the app is in Navigate – never Plan,
 * My routes or Settings without unlocking.
 */
object LockScreenNav {
    fun shows(navigate: Boolean, touring: Boolean): Boolean = navigate && touring
}
