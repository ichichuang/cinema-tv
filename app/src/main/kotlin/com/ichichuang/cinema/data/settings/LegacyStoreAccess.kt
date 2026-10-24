package com.ichichuang.cinema.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.ichichuang.cinema.data.userdata.LegacyHandoffRuntime

/** The lock covers actual edit completion, including IO, rather than only launching its Job. */
internal suspend fun DataStore<Preferences>.editLegacy(context: Context,
    transform: suspend (MutablePreferences) -> Unit): Preferences =
    LegacyHandoffRuntime.legacyWrite(context) { edit(transform) }
