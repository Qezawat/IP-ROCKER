package com.qezawat.iprocker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Everything the user can tune about a scan.
 *
 * Defaults are chosen for a phone on a metered mobile connection: a few hundred
 * addresses, a download sample large enough to be meaningful but small enough
 * not to burn data, and the stability and reputation checks on, because those
 * are the two things that separate a usable address from one that merely pings.
 */
data class ScanSettings(
    val count: Int = 400,
    val concurrency: Int = 48,
    val port: Int = 443,
    val mode: String = "http",
    val tries: Int = 3,
    val timeoutMs: Int = 6000,
    val holdMs: Int = 3000,
    val downloadBytes: Long = 256L * 1024,
    val uploadBytes: Long = 128L * 1024,

    val stabilityCheck: Boolean = true,
    val speedTest: Boolean = true,
    val uploadTest: Boolean = false,
    val reputationCheck: Boolean = true,
    val strict: Boolean = false,
    val ipv6: Boolean = false,

    val configLink: String = "",
    val sni: String = "",
    val host: String = "",
    val wsPath: String = "",
    val requireWebSocket: Boolean = false,

    val customRanges: String = "",
    val onlyCustomRanges: Boolean = false,
) {
    /** Ports that Cloudflare serves TLS on, offered as quick choices. */
    companion object {
        val COMMON_PORTS = listOf(443, 2053, 2083, 2087, 2096, 8443)
        val PRESET_COUNTS = listOf(150, 400, 1000, 2500)
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "iprocker_settings")

/**
 * Persists scan settings so a repeat scan needs no retyping. The config link is
 * stored because re-pasting it on every run is the main friction point, and it
 * never leaves the device.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<ScanSettings> = context.dataStore.data.map { p ->
        val d = ScanSettings()
        ScanSettings(
            count = p[Keys.COUNT] ?: d.count,
            concurrency = p[Keys.CONCURRENCY] ?: d.concurrency,
            port = p[Keys.PORT] ?: d.port,
            mode = p[Keys.MODE] ?: d.mode,
            tries = p[Keys.TRIES] ?: d.tries,
            timeoutMs = p[Keys.TIMEOUT] ?: d.timeoutMs,
            holdMs = p[Keys.HOLD] ?: d.holdMs,
            downloadBytes = p[Keys.DOWNLOAD] ?: d.downloadBytes,
            uploadBytes = p[Keys.UPLOAD] ?: d.uploadBytes,
            stabilityCheck = p[Keys.STABILITY] ?: d.stabilityCheck,
            speedTest = p[Keys.SPEED] ?: d.speedTest,
            uploadTest = p[Keys.UPLOAD_TEST] ?: d.uploadTest,
            reputationCheck = p[Keys.REPUTATION] ?: d.reputationCheck,
            strict = p[Keys.STRICT] ?: d.strict,
            ipv6 = p[Keys.IPV6] ?: d.ipv6,
            configLink = p[Keys.CONFIG_LINK] ?: d.configLink,
            sni = p[Keys.SNI] ?: d.sni,
            host = p[Keys.HOST] ?: d.host,
            wsPath = p[Keys.WS_PATH] ?: d.wsPath,
            requireWebSocket = p[Keys.REQUIRE_WS] ?: d.requireWebSocket,
            customRanges = p[Keys.CUSTOM_RANGES] ?: d.customRanges,
            onlyCustomRanges = p[Keys.ONLY_CUSTOM] ?: d.onlyCustomRanges,
        )
    }

    suspend fun save(s: ScanSettings) {
        context.dataStore.edit { p ->
            p[Keys.COUNT] = s.count
            p[Keys.CONCURRENCY] = s.concurrency
            p[Keys.PORT] = s.port
            p[Keys.MODE] = s.mode
            p[Keys.TRIES] = s.tries
            p[Keys.TIMEOUT] = s.timeoutMs
            p[Keys.HOLD] = s.holdMs
            p[Keys.DOWNLOAD] = s.downloadBytes
            p[Keys.UPLOAD] = s.uploadBytes
            p[Keys.STABILITY] = s.stabilityCheck
            p[Keys.SPEED] = s.speedTest
            p[Keys.UPLOAD_TEST] = s.uploadTest
            p[Keys.REPUTATION] = s.reputationCheck
            p[Keys.STRICT] = s.strict
            p[Keys.IPV6] = s.ipv6
            p[Keys.CONFIG_LINK] = s.configLink
            p[Keys.SNI] = s.sni
            p[Keys.HOST] = s.host
            p[Keys.WS_PATH] = s.wsPath
            p[Keys.REQUIRE_WS] = s.requireWebSocket
            p[Keys.CUSTOM_RANGES] = s.customRanges
            p[Keys.ONLY_CUSTOM] = s.onlyCustomRanges
        }
    }

    private object Keys {
        val COUNT = intPreferencesKey("count")
        val CONCURRENCY = intPreferencesKey("concurrency")
        val PORT = intPreferencesKey("port")
        val MODE = stringPreferencesKey("mode")
        val TRIES = intPreferencesKey("tries")
        val TIMEOUT = intPreferencesKey("timeout_ms")
        val HOLD = intPreferencesKey("hold_ms")
        val DOWNLOAD = longPreferencesKey("download_bytes")
        val UPLOAD = longPreferencesKey("upload_bytes")
        val STABILITY = booleanPreferencesKey("stability")
        val SPEED = booleanPreferencesKey("speed")
        val UPLOAD_TEST = booleanPreferencesKey("upload_test")
        val REPUTATION = booleanPreferencesKey("reputation")
        val STRICT = booleanPreferencesKey("strict")
        val IPV6 = booleanPreferencesKey("ipv6")
        val CONFIG_LINK = stringPreferencesKey("config_link")
        val SNI = stringPreferencesKey("sni")
        val HOST = stringPreferencesKey("host")
        val WS_PATH = stringPreferencesKey("ws_path")
        val REQUIRE_WS = booleanPreferencesKey("require_ws")
        val CUSTOM_RANGES = stringPreferencesKey("custom_ranges")
        val ONLY_CUSTOM = booleanPreferencesKey("only_custom")
    }
}
