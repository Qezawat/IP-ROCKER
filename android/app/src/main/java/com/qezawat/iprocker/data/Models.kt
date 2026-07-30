package com.qezawat.iprocker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The JSON contract with the Go core. Field names mirror the struct tags in
 * internal/score and internal/reputation; changing one side requires changing
 * the other.
 */
val IPRockerJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

@Serializable
data class ReputationInfo(
    val ip: String = "",
    @SerialName("is_datacenter") val isDatacenter: Boolean = false,
    @SerialName("is_proxy") val isProxy: Boolean = false,
    @SerialName("is_vpn") val isVpn: Boolean = false,
    @SerialName("is_tor") val isTor: Boolean = false,
    @SerialName("is_abuser") val isAbuser: Boolean = false,
    @SerialName("is_mobile") val isMobile: Boolean = false,
    @SerialName("is_satellite") val isSatellite: Boolean = false,
    @SerialName("is_bogon") val isBogon: Boolean = false,
    @SerialName("is_crawler") val isCrawler: Boolean = false,
    @SerialName("company_name") val companyName: String = "",
    @SerialName("company_abuse") val companyAbuse: Double = 0.0,
    val asn: Int = 0,
    @SerialName("asn_name") val asnName: String = "",
    @SerialName("asn_abuse") val asnAbuse: Double = 0.0,
    val route: String = "",
    val country: String = "",
    val city: String = "",
    val region: String = "",
    @SerialName("risk_percent") val riskPercent: Double = 0.0,
    val verdict: String = "unknown",
    val reasons: List<String> = emptyList(),
    val error: String = "",
) {
    /** True when the provider actually answered for this address. */
    val isVerified: Boolean get() = error.isEmpty()

    val verdictLevel: Verdict
        get() = when {
            !isVerified -> Verdict.UNKNOWN
            else -> when (verdict.lowercase()) {
                "clean" -> Verdict.CLEAN
                "caution" -> Verdict.CAUTION
                "dirty" -> Verdict.DIRTY
                else -> Verdict.UNKNOWN
            }
        }

    val location: String
        get() = listOf(country, region, city)
            .filter { it.isNotBlank() }
            .joinToString(" / ")
}

enum class Verdict { CLEAN, CAUTION, DIRTY, UNKNOWN }

@Serializable
data class Candidate(
    val ip: String = "",
    val port: Int = 443,
    @SerialName("avg_latency_ms") val avgLatencyMs: Double = 0.0,
    @SerialName("min_latency_ms") val minLatencyMs: Double = 0.0,
    @SerialName("jitter_ms") val jitterMs: Double = 0.0,
    @SerialName("loss_percent") val lossPercent: Double = 0.0,
    @SerialName("download_kbps") val downloadKbps: Double = 0.0,
    @SerialName("upload_kbps") val uploadKbps: Double = 0.0,
    val colo: String = "",
    @SerialName("held_open") val heldOpen: Boolean = false,
    @SerialName("websocket_ok") val webSocketOk: Boolean = false,
    @SerialName("tls_ok") val tlsOk: Boolean = false,
    val reputation: ReputationInfo? = null,
    val score: Double = 0.0,
    val healthy: Boolean = false,
    val verdict: String = "unknown",
    val notes: List<String> = emptyList(),
) {
    val endpoint: String get() = "$ip:$port"

    val verdictLevel: Verdict get() = reputation?.verdictLevel ?: Verdict.UNKNOWN

    /**
     * The single most useful line to show under an address: either why it was
     * rejected, or what makes it good.
     */
    val headline: String
        get() = when {
            !healthy && notes.isNotEmpty() -> notes.first()
            reputation?.isVerified == true ->
                "risk ${"%.0f".format(reputation.riskPercent)}% · ${reputation.location.ifBlank { "location unknown" }}"
            else -> "reputation not verified"
        }
}

@Serializable
data class ScanReport(
    val tested: Long = 0,
    val hits: Long = 0,
    @SerialName("duration_ms") val durationMs: Long = 0,
    @SerialName("reputation_error") val reputationError: String = "",
    @SerialName("clean_count") val cleanCount: Int = 0,
    val candidates: List<Candidate> = emptyList(),
) {
    val clean: List<Candidate> get() = candidates.filter { it.healthy }
}

@Serializable
data class ConfigLinkInfo(
    val protocol: String = "",
    val sni: String = "",
    val host: String = "",
    val path: String = "",
    val port: Int = 0,
    val transport: String = "",
    val security: String = "",
    val address: String = "",
)
