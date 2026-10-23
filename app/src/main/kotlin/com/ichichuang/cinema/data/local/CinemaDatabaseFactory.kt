package com.ichichuang.cinema.data.local

import android.content.Context
import androidx.room.Room

/** Explicit candidate construction only; no singleton, legacy reads, or startup registration. */
internal object CinemaDatabaseFactory {
    fun open(context: Context, databaseName: String): CinemaDatabase {
        require(databaseName.matches(Regex("[A-Za-z0-9_.-]+")) && databaseName != "." && databaseName != "..") {
            "A private candidate database name is required"
        }
        return Room.databaseBuilder(context.applicationContext, CinemaDatabase::class.java, databaseName).build()
    }
}
