package com.ichichuang.cinema.data.catalog

import com.ichichuang.cinema.data.local.codec.*
import com.ichichuang.cinema.domain.model.catalog.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CatalogCaptureCodecTest {
    private fun capture() = MaccmsCatalogNormalizer.normalize(SourceId("synthetic-source"),buildJsonObject {
        put("vod_id","17");put("vod_year","1994");put("vod_area","");put("opaque", "\uD800")
    }, -123)
    @Test fun file_encoding_preserves_raw_utf16_empty_claims_time_and_rules() {
        val c=capture();val encoded=CatalogCaptureCodec.encode(c)
        assertTrue(encoded.contains("\\ud800"))
        assertEquals(c,CatalogCaptureCodec.decode(encoded))
    }
    @Test fun unsupported_payload_version_is_not_reinterpreted() {
        val encoded=CatalogCaptureCodec.encode(capture()).replace("\"version\":1","\"version\":99")
        val e=assertThrows(StorageReadException::class.java) { CatalogCaptureCodec.decode(encoded) }
        assertEquals(StorageReadFailure.UNSUPPORTED_VERSION,e.reason)
    }
    @Test fun malformed_negative_known_year_is_an_explicit_read_failure() {
        val encoded=CatalogCaptureCodec.encode(capture()).replace("\"value\":1994","\"value\":-1")
        assertThrows(StorageReadException::class.java) { CatalogCaptureCodec.decode(encoded) }
    }
    @Test fun each_normalized_claim_exposes_its_original_field_name_after_roundtrip() {
        val c=CatalogCaptureCodec.decode(CatalogCaptureCodec.encode(capture()))
        assertEquals("vod_year",c.fieldNames["year"])
        assertEquals("vod_area",c.fieldNames["countries"])
        assertEquals("vod_lang",c.fieldNames["audioLanguages"])
        assertEquals("work_original_language",c.fieldNames["originalLanguages"])
    }

}
