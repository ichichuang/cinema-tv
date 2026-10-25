package com.ichichuang.cinema.feature.browse

import android.content.Context
import com.ichichuang.cinema.data.local.codec.BrowseSnapshotCodec
import com.ichichuang.cinema.domain.filter.*
import com.ichichuang.cinema.domain.model.catalog.*
import com.ichichuang.cinema.domain.model.userdata.BrowseSnapshot
import com.ichichuang.cinema.core.navigation.BrowseTopLevel
import com.ichichuang.cinema.feature.home.visual.HomeImageShape
import org.json.JSONObject
import java.io.File

/** No bundled catalog: this reader requires a private file supplied by the authorized native test. */
internal class BrowseProbeFixture(context: Context) {
    companion object { const val FILE = "round11-browse-fixture.json" }
    private val data = JSONObject(File(context.noBackupFilesDir, FILE).readText())
    val initial: BrowseSnapshot = BrowseSnapshotCodec.decode(data.getString("initial"))
    val alternative: BrowseSnapshot = BrowseSnapshotCodec.decode(data.getString("alternative"))
    val savedOrder = data.getJSONArray("order").let { array -> (0 until array.length()).map { WorkId(array.getString(it)) } }
    val complete = data.getBoolean("complete")
    val saveDelayMillis = data.optLong("saveDelayMillis", 0).also { require(it in 0..10_000) }
    data class Work(val id: WorkId, val title: String, val source: SourceId,
        val facts: StrictWorkFacts, val favorite: Boolean, val history: Boolean,
        /** R13 测试专用合成图（可选）；旧 fixture 无此字段即 null，生产数据无图即无图。 */
        val artworkUri: String? = null, val artworkShape: HomeImageShape = HomeImageShape.UNKNOWN)
    val works: List<Work> = data.getJSONArray("works").let { array -> (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        val source = SourceId(item.getString("source"))
        val reference = EvidenceReference(source, null, "测试私有文件", "round11-fixture")
        fun ids(name: String) = FactIds.of(item.getJSONArray(name).let { values ->
            (0 until values.length()).map { values.getString(it) }.toSet() })
        val languages = ids("languages")
        Work(WorkId(item.getString("id")), item.getString("title"), source,
            StrictWorkFacts(FieldEvidence.Confirmed(item.getInt("year"), reference),
                FieldEvidence.Confirmed(ids("countries"), reference),
                FieldEvidence.Confirmed(ids("genres"), reference),
                FieldEvidence.Confirmed(ContentKind.valueOf(item.getString("kind")), reference),
                FieldEvidence.Confirmed(languages, reference), FieldEvidence.Confirmed(languages, reference)),
            item.getBoolean("favorite"), item.getBoolean("history"),
            item.optString("artwork").takeIf { it.isNotBlank() },
            when (item.optString("artworkShape")) { "portrait" -> HomeImageShape.PORTRAIT; "landscape" -> HomeImageShape.LANDSCAPE; else -> HomeImageShape.UNKNOWN })
    } }.also { require(it.map(Work::id).distinct().size == it.size) }
    fun query(query: CatalogQuery, top: BrowseTopLevel, revision: Long): Pair<List<Work>, CatalogResult> {
        val scoped = works.filter { it.source in query.enabledSourceIds && when (top) {
            BrowseTopLevel.LIBRARY -> true; BrowseTopLevel.FAVORITES -> it.favorite; BrowseTopLevel.HISTORY -> it.history; BrowseTopLevel.CONTINUE -> false; BrowseTopLevel.HOME -> false
        } }
        val evaluated = scoped.map { it to StrictFilterEvaluator.evaluate(query, it.facts).verdict }
        val matches = evaluated.filter { it.second == MatchVerdict.MATCH }.map { it.first }
        fun year(work: Work) = (work.facts.year as FieldEvidence.Confirmed).value
        val sorted = when (query.sort) {
            CatalogSort.DEFAULT -> matches
            CatalogSort.YEAR_ASCENDING -> matches.sortedBy(::year)
            CatalogSort.YEAR_DESCENDING -> matches.sortedByDescending(::year)
        }
        val exhaustive = complete && evaluated.none { it.second == MatchVerdict.INDETERMINATE }
        val result = CatalogResult.create(query.queryId,
            sorted.map { CatalogItem(it.id, it.title, it.facts.year, it.facts.originalLanguages, it.facts.countries) },
            if (exhaustive) QueryCoverage.COMPLETE else QueryCoverage.PARTIAL,
            unknownFieldCount = evaluated.count { it.second == MatchVerdict.INDETERMINATE },
            knownMatchedCount = if (exhaustive) sorted.size.toLong() else null,
            hasMore = false, snapshotRevision = revision)
        return sorted to result
    }
}
