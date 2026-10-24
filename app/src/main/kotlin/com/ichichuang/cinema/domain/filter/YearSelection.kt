package com.ichichuang.cinema.domain.filter

import com.ichichuang.cinema.domain.model.catalog.CatalogQuery

/** Selection intent only: no indexed-inventory promise or current-year cutoff. */
sealed interface YearSelection {
    val startInclusive: Int
    val endInclusive: Int

    data class Exact(val year: Int) : YearSelection {
        init { positive(year) }
        override val startInclusive: Int get() = year
        override val endInclusive: Int get() = year
    }

    data class Decade(val startYear: Int) : YearSelection {
        init {
            positive(startYear)
            if (startYear % 10 != 0) throw InvalidYearSelection(YearSelectionFailure.INVALID_DECADE)
            if (startYear > Int.MAX_VALUE - 9) throw InvalidYearSelection(YearSelectionFailure.OVERFLOW)
        }
        override val startInclusive: Int get() = startYear
        override val endInclusive: Int get() = startYear + 9
    }

    data class Range(override val startInclusive: Int, override val endInclusive: Int) : YearSelection {
        init {
            positive(startInclusive)
            positive(endInclusive)
            if (startInclusive > endInclusive) throw InvalidYearSelection(YearSelectionFailure.INVERTED_RANGE)
        }
    }
}

enum class YearSelectionFailure { NON_POSITIVE, INVERTED_RANGE, INVALID_DECADE, OVERFLOW, QUERY_ID_REUSED }
class InvalidYearSelection(val reason: YearSelectionFailure) : IllegalArgumentException(reason.name)

private fun positive(year: Int) {
    if (year <= 0) throw InvalidYearSelection(YearSelectionFailure.NON_POSITIVE)
}

/** A changed selection needs a new session ID; explicit relaxation audit belongs to its caller. */
fun CatalogQuery.withYearSelection(selection: YearSelection?, queryId: String): CatalogQuery {
    val start = selection?.startInclusive
    val end = selection?.endInclusive
    if (queryId == this.queryId && (start != yearStart || end != yearEnd)) {
        throw InvalidYearSelection(YearSelectionFailure.QUERY_ID_REUSED)
    }
    return CatalogQuery.create(queryId, contentKind, languageScope, countryIds, genreIds,
        start, end, sort, enabledSourceIds)
}
