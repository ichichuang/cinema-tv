package com.ichichuang.cinema.data.local.codec

import com.ichichuang.cinema.core.navigation.*
import kotlinx.serialization.json.*
import com.ichichuang.cinema.data.local.entity.BrowseSnapshotEntity
import com.ichichuang.cinema.data.userdata.RoomBrowseStateStore

/** A typed payload in the existing v5 browse table; no executable/modal destinations. */
internal object BrowseNavigationCodec : JsonStorageCodec<RestoreNavigationState>("browse_navigation") {
    override fun toJson(value: RestoreNavigationState) = buildJsonObject {
        put("snapshot", BrowseSnapshotCodec.encode(value.snapshot))
        put("returnChain", JsonArray(value.returnChain.map { JsonPrimitive(it.name) }))
    }
    override fun fromJson(value: JsonObject) = RestoreNavigationState(
        BrowseSnapshotCodec.decode(value.required("snapshot").text()),
        value.required("returnChain").jsonArray.map { BrowseChildRoute.valueOf(it.text()) },
    )
}

internal fun browseNavigationRecords(state: RestoreNavigationState): List<BrowseSnapshotEntity> {
    val activeKey = RoomBrowseStateStore.ACTIVE_NAVIGATION_KEY
    require(!state.snapshot.contextKey.startsWith(activeKey))
    return listOf(
        BrowseSnapshotEntity(state.snapshot.contextKey, BrowseSnapshotCodec.encode(state.snapshot)),
        BrowseSnapshotEntity(activeKey, BrowseNavigationCodec.encode(state)),
        BrowseSnapshotEntity("$activeKey.${state.snapshot.route}", BrowseNavigationCodec.encode(state)),
    )
}

internal fun decodeBrowseNavigationPointer(payload: String, topLevel: BrowseTopLevel? = null): RestoreNavigationState =
    BrowseNavigationCodec.decode(payload).also { state ->
        if ((topLevel != null && state.snapshot.route != topLevel.route) ||
            state.snapshot.contextKey.startsWith(RoomBrowseStateStore.ACTIVE_NAVIGATION_KEY))
            throw StorageReadException(StorageReadFailure.MALFORMED)
    }
