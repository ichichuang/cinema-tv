package com.ichichuang.cinema.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The settings class needs a Context, so the read/write path it exposes as
 * `CinemaSettings.reduceMotion` is verified here over a plain `Preferences`
 * flow: the absent-key default, the round trip and following later writes.
 */
class ReduceMotionSettingTest {

    @Test
    fun `an absent key reads as the documented default`() = runBlocking {
        assertEquals(false, ReduceMotionPreference.DEFAULT_VALUE)
        assertEquals(false, ReduceMotionSetting.flow(flowOf(mutablePreferencesOf())).first())
    }

    @Test
    fun `a written value is what the settings flow reports`() = runBlocking {
        val on = mutablePreferencesOf()
        ReduceMotionSetting.write(on, true)
        assertEquals(true, ReduceMotionSetting.flow(flowOf(on)).first())

        val off = mutablePreferencesOf()
        ReduceMotionSetting.write(off, false)
        assertEquals(false, ReduceMotionSetting.flow(flowOf(off)).first())
    }

    @Test
    fun `the flow follows later writes`() = runBlocking {
        val store = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val reduceMotion = ReduceMotionSetting.flow(store)
        assertEquals(false, reduceMotion.first())

        val next = mutablePreferencesOf()
        ReduceMotionSetting.write(next, true)
        store.value = next
        assertEquals(true, reduceMotion.first())
    }

    @Test
    fun `unrelated keys are left alone`() = runBlocking {
        val apiBase = stringPreferencesKey("api_base")
        val preferences = mutablePreferencesOf()
        preferences[apiBase] = "https://example.invalid/api.php/provide/vod/"

        ReduceMotionSetting.write(preferences, true)

        assertEquals("https://example.invalid/api.php/provide/vod/", preferences[apiBase])
        assertEquals(true, ReduceMotionSetting.flow(flowOf(preferences)).first())
    }
}
