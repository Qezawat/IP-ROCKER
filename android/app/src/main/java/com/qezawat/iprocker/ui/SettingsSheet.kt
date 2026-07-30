package com.qezawat.iprocker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qezawat.iprocker.data.ScanSettings
import com.qezawat.iprocker.ui.components.RockerCard
import com.qezawat.iprocker.ui.components.SectionLabel
import com.qezawat.iprocker.ui.theme.RockerAccent
import com.qezawat.iprocker.ui.theme.RockerBackground
import com.qezawat.iprocker.ui.theme.RockerSurfaceHigh
import com.qezawat.iprocker.ui.theme.TextSecondary
import com.qezawat.iprocker.ui.theme.VerdictCaution

/**
 * Explains what a given timeout will do, because the useful range spans an
 * order of magnitude and the trade-off is not obvious from the number alone.
 */
private fun timeoutAdvice(ms: Int): String = when {
    ms < 1000 ->
        "Very aggressive. Only edges that answer almost instantly survive, so " +
            "the scan is fast but healthy addresses on a slow mobile link will " +
            "be discarded as failures."
    ms < 2500 ->
        "Fast. Good for a quick sweep of many addresses when your connection " +
            "is stable."
    ms <= 8000 ->
        "Balanced. Suits most mobile networks."
    else ->
        "Patient. Catches usable but slow edges at the cost of a much longer " +
            "scan."
}

/**
 * The settings sheet.
 *
 * Each toggle states what it costs and what it buys, because the trade-off
 * between scan speed and result quality is the main decision the user makes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: ScanSettings,
    onChange: ((ScanSettings) -> ScanSettings) -> Unit,
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
                .padding(bottom = 36.dp),
        ) {
            Text(
                text = "SCAN SETTINGS",
                style = MaterialTheme.typography.titleMedium,
                color = RockerAccent,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            RockerCard {
                SectionLabel("Your config")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Paste a VLESS, Trojan or VMess link and the scan will use its " +
                        "own SNI, host, path and port instead of a generic Cloudflare " +
                        "hostname, so the results match what your config will actually do.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = settings.configLink,
                    onValueChange = { v -> onChange { it.copy(configLink = v) } },
                    label = { Text("Config link (optional)") },
                    placeholder = { Text("vless://…") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))

            RockerCard {
                SectionLabel("How many addresses")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScanSettings.PRESET_COUNTS.forEach { n ->
                        AssistChip(
                            onClick = { onChange { it.copy(count = n) } },
                            label = { Text("$n") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (settings.count == n) {
                                    RockerAccent.copy(alpha = 0.18f)
                                } else {
                                    RockerSurfaceHigh
                                },
                                labelColor = if (settings.count == n) RockerAccent else TextSecondary,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Parallel probes: ${settings.concurrency}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Slider(
                    value = settings.concurrency.toFloat(),
                    onValueChange = { v -> onChange { it.copy(concurrency = v.toInt()) } },
                    valueRange = 8f..160f,
                    steps = 18,
                )
                Text(
                    "Higher finishes sooner but a phone on mobile data can run out " +
                        "of sockets and start reporting false failures.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }

            Spacer(Modifier.height(12.dp))

            RockerCard {
                SectionLabel("Port")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScanSettings.COMMON_PORTS.forEach { p ->
                        AssistChip(
                            onClick = { onChange { it.copy(port = p) } },
                            label = { Text("$p") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (settings.port == p) {
                                    RockerAccent.copy(alpha = 0.18f)
                                } else {
                                    RockerSurfaceHigh
                                },
                                labelColor = if (settings.port == p) RockerAccent else TextSecondary,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            RockerCard {
                SectionLabel("Checks")
                Spacer(Modifier.height(6.dp))

                SettingToggle(
                    title = "Reputation check",
                    subtitle = "Rates every answering address for proxy, VPN and abuse " +
                        "flags. This is what finds genuinely clean addresses rather " +
                        "than merely fast ones.",
                    checked = settings.reputationCheck,
                    onCheckedChange = { v -> onChange { it.copy(reputationCheck = v) } },
                )
                SettingToggle(
                    title = "Stability hold",
                    subtitle = "Keeps a connection idle for a few seconds to catch " +
                        "filtering that allows the first request and then resets.",
                    checked = settings.stabilityCheck,
                    onCheckedChange = { v -> onChange { it.copy(stabilityCheck = v) } },
                )
                SettingToggle(
                    title = "Download test",
                    subtitle = "Transfers a real payload. Costs a little data but " +
                        "rejects addresses that answer yet cannot carry traffic.",
                    checked = settings.speedTest,
                    onCheckedChange = { v -> onChange { it.copy(speedTest = v) } },
                )
                SettingToggle(
                    title = "Upload test",
                    subtitle = "Also measures upstream capacity. Slower, but a proxy " +
                        "connection needs both directions.",
                    checked = settings.uploadTest,
                    onCheckedChange = { v -> onChange { it.copy(uploadTest = v) } },
                )
                SettingToggle(
                    title = "Strict mode",
                    subtitle = "Only accepts addresses that are clean, stable, fast and " +
                        "WebSocket-capable. Returns far fewer results.",
                    checked = settings.strict,
                    onCheckedChange = { v -> onChange { it.copy(strict = v) } },
                )
                SettingToggle(
                    title = "Scan IPv6",
                    subtitle = "Probes Cloudflare's IPv6 space instead of IPv4. Only " +
                        "useful if your network and config support IPv6.",
                    checked = settings.ipv6,
                    onCheckedChange = { v -> onChange { it.copy(ipv6 = v) } },
                )
            }

            Spacer(Modifier.height(12.dp))

            RockerCard {
                SectionLabel("Timing")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Attempts per address: ${settings.tries}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Slider(
                    value = settings.tries.toFloat(),
                    onValueChange = { v -> onChange { it.copy(tries = v.toInt()) } },
                    valueRange = 2f..6f,
                    steps = 3,
                )
                Text(
                    "How many times each address is probed. More attempts measure " +
                        "loss and jitter accurately but make the scan proportionally " +
                        "slower. Three or four is a good balance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Timeout: ${settings.timeoutMs} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Slider(
                    value = settings.timeoutMs.toFloat(),
                    onValueChange = { v -> onChange { it.copy(timeoutMs = v.toInt()) } },
                    // Steps land on 500 ms increments across the whole range, so
                    // sub-second timeouts are reachable. A low timeout discards
                    // slow edges early and speeds the scan up several times over;
                    // too low on a mobile network turns healthy edges into false
                    // failures, which is why the guidance below is shown.
                    valueRange = 500f..15000f,
                    steps = 28,
                )
                Text(
                    text = timeoutAdvice(settings.timeoutMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (settings.timeoutMs < 1000) VerdictCaution else TextSecondary,
                )
            }

            Spacer(Modifier.height(12.dp))

            RockerCard {
                SectionLabel("Custom ranges")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Comma-separated CIDRs to add to the scan, for example " +
                        "104.16.0.0/16, 172.67.0.0/16.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = settings.customRanges,
                    onValueChange = { v -> onChange { it.copy(customRanges = v) } },
                    label = { Text("Extra CIDRs") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                SettingToggle(
                    title = "Only these ranges",
                    subtitle = "Ignore the built-in Cloudflare list and scan only what " +
                        "you entered above.",
                    checked = settings.onlyCustomRanges,
                    onCheckedChange = { v -> onChange { it.copy(onlyCustomRanges = v) } },
                )
            }
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.padding(horizontal = 6.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
