package com.ichichuang.cinema.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * The "减弱动效" flag, stored next to the other settings.
 *
 * Split out of [CinemaSettings] so the read/write contract (key name, default,
 * round trip) is a pure function over a `Preferences` snapshot and can be
 * verified in a JVM test without an Android `Context`.
 *
 * The key is new: existing keys, their names and the on-disk migration path are
 * untouched. A device that never touched the switch reads [DEFAULT_VALUE].
 */
object ReduceMotionPreference {
    /** Stored key. Never rename: it is already user data once shipped. */
    const val KEY_NAME = "reduce_motion"

    /** Off by default: full motion is the product's intended feel. */
    const val DEFAULT_VALUE = false

    private val key = booleanPreferencesKey(KEY_NAME)

    /** Absent key means "never set" and must read as [DEFAULT_VALUE]. */
    fun read(preferences: Preferences): Boolean = preferences[key] ?: DEFAULT_VALUE

    /** Writes the explicit value; the caller decides when a write is needed. */
    fun write(preferences: MutablePreferences, enabled: Boolean) {
        preferences[key] = enabled
    }
}
