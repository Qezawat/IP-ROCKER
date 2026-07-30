package com.qezawat.iprocker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qezawat.iprocker.data.ReputationInfo
import com.qezawat.iprocker.ui.theme.RockerBackground
import com.qezawat.iprocker.ui.theme.TextSecondary
import com.qezawat.iprocker.ui.theme.VerdictDirty

/**
 * The address details sheet.
 *
 * Every flag is shown with an explicit YES/NO word, and an unverified lookup
 * says so plainly rather than displaying zeros that would read as "clean".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpDetailsSheet(
    info: ReputationInfo?,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RockerBackground,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "IP DETAILS",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Data source: ipapi.is",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(16.dp))

            when {
                loading -> Row(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Text("Looking up reputation…", color = TextSecondary)
                }

                error != null -> RockerCard {
                    Text(
                        text = "Lookup failed",
                        style = MaterialTheme.typography.titleMedium,
                        color = VerdictDirty,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    // Show the provider's actual error, not a generic message,
                    // so a rate limit is distinguishable from being offline.
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }

                info != null -> DetailsBody(info)
            }
        }
    }
}

@Composable
private fun DetailsBody(info: ReputationInfo) {
    RockerCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = info.ip,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            VerdictBadge(
                verdict = info.verdictLevel,
                riskPercent = info.riskPercent.takeIf { info.isVerified },
            )
        }

        if (!info.isVerified) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Reputation could not be verified: ${info.error}",
                style = MaterialTheme.typography.bodySmall,
                color = VerdictDirty,
            )
        }

        Spacer(Modifier.height(12.dp))
        DetailRow("Location", info.location.ifBlank { "unknown" })
        DetailRow("Operator", info.companyName.ifBlank { "unknown" })
        DetailRow("ASN", if (info.asn > 0) "AS${info.asn} ${info.asnName}" else "unknown")
        DetailRow("Route", info.route.ifBlank { "unknown" })
        DetailRow("Owner abuse ratio", "%.4f".format(info.companyAbuse))
        DetailRow("ASN abuse ratio", "%.4f".format(info.asnAbuse))
    }

    Spacer(Modifier.height(12.dp))

    RockerCard {
        SectionLabel("Safety checks")
        Spacer(Modifier.height(10.dp))

        // Two per row keeps every label legible on a narrow phone.
        val flags = listOf(
            "Known abuse" to info.isAbuser,
            "Open proxy" to info.isProxy,
            "VPN endpoint" to info.isVpn,
            "Tor exit" to info.isTor,
            "Crawler" to info.isCrawler,
            "Bogon" to info.isBogon,
        )
        flags.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { (label, flagged) ->
                    FlagChip(label, flagged, Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(2.dp))
        // Being a datacenter address is stated but not treated as a fault:
        // every Cloudflare edge is one, so it carries no signal here.
        Text(
            text = if (info.isDatacenter) {
                "Datacenter address — expected for a Cloudflare edge, not a risk factor."
            } else {
                "Not a datacenter address."
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }

    if (info.reasons.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        RockerCard {
            SectionLabel("Why this score")
            Spacer(Modifier.height(8.dp))
            info.reasons.forEach { reason ->
                Text(
                    text = "• $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}
