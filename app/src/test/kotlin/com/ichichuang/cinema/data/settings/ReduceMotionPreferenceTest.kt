package com.ichichuang.cinema.data.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reduce-motion flag is a new key: it must default to "off" for devices
 * that never wrote it, survive a write/read round trip, and leave the keys that
 * were already stored untouched.
 */
class ReduceMotionPreferenceTest {

    @Test
    fun `missing key reads as the documented default`() {
        val preferences = emptyPreferences()
        assertEquals(false, ReduceMotionPreference.DEFAULT_VALUE)
        assertEquals(false, ReduceMotionPreference.read(preferences))
    }

    @Test
    fun `write then read round trips both values`() {
        val preferences = mutablePreferencesOf()

        ReduceMotionPreference.write(preferences, true)
        assertEquals(true, ReduceMotionPreference.read(preferences))

        ReduceMotionPreference.write(preferences, false)
        assertEquals(false, ReduceMotionPreference.read(preferences))
    }

    @Test
    fun `writing the flag does not disturb existing settings`() {
        val preferences = mutablePreferencesOf()
        val apiBase = stringPreferencesKey("api_base")
        preferences[apiBase] = "https://example.invalid/api.php/provide/vod/"

        ReduceMotionPreference.write(preferences, true)

        assertEquals("https://example.invalid/api.php/provide/vod/", preferences[apiBase])
        assertEquals(true, ReduceMotionPreference.read(preferences))
    }

    @Test
    fun `stored key name is stable`() {
        // Renaming this would silently drop the user's choice on upgrade.
        assertEquals("reduce_motion", ReduceMotionPreference.KEY_NAME)
    }
}
