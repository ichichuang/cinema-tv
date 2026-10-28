package com.ichichuang.cinema.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

/**
 * The read/write path of `CinemaSettings.reduceMotion`.
 *
 * Split out of the settings class so the default, the mapping and the round trip
 * are JVM-testable without a `Context`: only the DataStore handle and the legacy
 * write handoff need Android, and both stay in [CinemaSettings].
 */
internal object ReduceMotionSetting {
    /** Maps the raw preferences flow onto the flag the UI reads. */
    fun flow(data: Flow<Preferences>): Flow<Boolean> = data.map { ReduceMotionPreference.read(it) }

    /** The write transform applied inside the DataStore edit. */
    fun write(preferences: MutablePreferences, enabled: Boolean) =
        ReduceMotionPreference.write(preferences, enabled)
}
