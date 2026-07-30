package com.qezawat.iprocker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These tests pin the JSON contract with the Go core. If a struct tag in
 * internal/score or internal/reputation changes without the Kotlin model
 * following, the app would silently show zeros; these catch that.
 */
class ModelsTest {

    @Test
    fun `candidate decodes a real go payload`() {
        val json = """
        {
          "ip": "104.25.228.176",
          "port": 443,
          "avg_latency_ms": 742.1,
          "min_latency_ms": 700.0,
          "jitter_ms": 21.4,
          "loss_percent": 0.0,
          "download_kbps": 360.2,
          "upload_kbps": 0.0,
          "colo": "FRA",
          "held_open": true,
          "websocket_ok": false,
          "tls_ok": true,
          "score": 71.3,
          "healthy": true,
          "verdict": "clean",
          "reputation": {
            "ip": "104.25.228.176",
            "is_datacenter": true,
            "is_proxy": false,
            "is_abuser": false,
            "company_name": "Cloudflare, Inc.",
            "company_abuse": 0.0076,
            "asn": 13335,
            "asn_name": "Cloudflare, Inc.",
            "asn_abuse": 0.0153,
            "route": "104.25.0.0/16",
            "country": "United States",
            "city": "San Francisco",
            "risk_percent": 5.7,
            "verdict": "clean"
          }
        }
        """.trimIndent()

        val c = IPRockerJson.decodeFromString<Candidate>(json)

        assertEquals("104.25.228.176:443", c.endpoint)
        assertEquals(71.3, c.score, 0.01)
        assertTrue(c.healthy)
        assertTrue(c.heldOpen)
        assertEquals("FRA", c.colo)
        assertEquals(Verdict.CLEAN, c.verdictLevel)
        assertEquals(13335, c.reputation?.asn)
        assertTrue(c.reputation!!.isVerified)
    }

    /**
     * An address the provider could not rate must resolve to UNKNOWN, never to
     * CLEAN, because treating an outage as a pass would hand over risky
     * addresses.
     */
    @Test
    fun `unverified reputation is unknown not clean`() {
        val json = """{"ip":"1.2.3.4","verdict":"clean","error":"provider unreachable"}"""
        val info = IPRockerJson.decodeFromString<ReputationInfo>(json)

        assertFalse(info.isVerified)
        assertEquals(Verdict.UNKNOWN, info.verdictLevel)
    }

    @Test
    fun `rejected candidate headline shows the reason`() {
        val json = """
        {
          "ip": "5.5.5.5",
          "port": 443,
          "healthy": false,
          "score": 0.0,
          "notes": ["connection reset during idle hold", "packet loss above threshold"]
        }
        """.trimIndent()

        val c = IPRockerJson.decodeFromString<Candidate>(json)
        assertFalse(c.healthy)
        assertEquals("connection reset during idle hold", c.headline)
    }

    @Test
    fun `report separates usable from rejected`() {
        val json = """
        {
          "tested": 216,
          "hits": 23,
          "duration_ms": 36930,
          "clean_count": 2,
          "reputation_error": "",
          "candidates": [
            {"ip":"1.1.1.1","port":443,"healthy":true,"score":70.0},
            {"ip":"2.2.2.2","port":443,"healthy":true,"score":60.0},
            {"ip":"3.3.3.3","port":443,"healthy":false,"score":0.0}
          ]
        }
        """.trimIndent()

        val report = IPRockerJson.decodeFromString<ScanReport>(json)
        assertEquals(216L, report.tested)
        assertEquals(3, report.candidates.size)
        assertEquals(2, report.clean.size)
    }

    /** Unknown fields must not break decoding when the Go side gains a field. */
    @Test
    fun `unknown fields are ignored`() {
        val json = """{"ip":"9.9.9.9","port":443,"score":1.0,"brand_new_field":"x"}"""
        val c = IPRockerJson.decodeFromString<Candidate>(json)
        assertEquals("9.9.9.9", c.ip)
    }

    @Test
    fun `location joins only the parts that exist`() {
        val info = ReputationInfo(country = "Germany", city = "Dreieich")
        assertEquals("Germany / Dreieich", info.location)

        val empty = ReputationInfo()
        assertEquals("", empty.location)
    }
}
