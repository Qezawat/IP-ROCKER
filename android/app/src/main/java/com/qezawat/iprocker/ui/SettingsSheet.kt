package com.qezawat.iprocker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.qezawat.iprocker.ui.components.ChoiceChips
import com.qezawat.iprocker.ui.components.RockerCard
import com.qezawat.iprocker.ui.components.SectionLabel
import com.qezawat.iprocker.ui.theme.RockerAccent
import com.qezawat.iprocker.ui.theme.RockerBackground
import com.qezawat.iprocker.ui.theme.TextSecondary
import com.qezawat.iprocker.ui.theme.VerdictCaution

/**
 * Explains what a given timeout will do, because the useful range spans an
 * order of magnitude and the trade-off is not obvious from the number alone.
 */
private fun timeoutAdvice(ms: Int): String = when {
    ms < 400 ->
        "Extreme. Only edges that answer in a few hundred milliseconds pass. " +
            "Fastest possible sweep, but on a mobile link most healthy edges " +
            "will be discarded as failures."
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

/** Renders an address count compactly, so 20000 reads as 20k on a chip. */
private fun countLabel(n: Int): String =
    if (n >= 1000 && n % 1000 == 0) "${n / 1000}k" else "$n"

/**
 * Warns about the cost of a large sweep. The app allows it — a wide sweep is
 * how rare clean blocks get found — but the time and data are worth stating.
 */
private fun countAdvice(n: Int): String = when {
    n <= 500 -> "Quick look. Finishes in a minute or two."
    n <= 2_500 -> "Normal sweep. A few minutes, modest data use."
    n <= 5_000 -> "Wide sweep. Expect several minutes and noticeable data use."
    else ->
        "Very wide sweep. This can run for a long time and use a lot of mobile " +
            "data. Keep the download test small, or off, at this size."
}

/** Describes a download-sample size, including the off case. */
private fun downloadLabel(bytes: Long): String =
    if (bytes <= 0L) "Off" else "${bytes / 1024} KB"

/** Describes a speed floor, including the off case. */
private fun speedLabel(kbps: Double): String =
    if (kbps <= 0.0) "Off" else "${kbps.toInt()} KB/s"

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
                ChoiceChips(
                    options = ScanSettings.PRESET_COUNTS,
                    selected = { it == settings.count },
                    label = ::countLabel,
                    onSelect = { n -> onChange { it.copy(count = n) } },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = countAdvice(settings.count),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (settings.count > 5_000) VerdictCaution else TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Parallel probes: ${settings.concurrency}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Slider(
                    value = settings.concurrency.toFloat(),
                    onValueChange = { v -> onChange { it.copy(concurrency = v.toInt()) } },
                    // Up to 500 in steps of 10. A wide sweep is only tolerable at
                    // high parallelism, so the ceiling has to be well above the
                    // socket count a cautious default would pick.
                    valueRange = 10f..500f,
                    steps = 48,
                )
                Spacer(Modifier.height(4.dp))
                ChoiceChips(
                    options = ScanSettings.PRESET_CONCURRENCY,
                    selected = { it == settings.concurrency },
                    label = { "$it" },
                    onSelect = { c -> onChange { it.copy(concurrency = c) } },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Higher finishes sooner but a phone on mobile data can run out " +
                        "of sockets and start reporting false failures.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (settings.concurrency > 200) VerdictCaution else TextSecondary,
                )
            }

            Spacer(Modifier.height(12.dp))

            RockerCard {
                SectionLabel("Ports")
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap to select one or more. Every extra port multiplies the " +
                        "number of probes, so two ports over 5000 addresses is " +
                        "10000 probes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                val selectedPorts = settings.selectedPorts()
                ChoiceChips(
                    options = ScanSettings.COMMON_PORTS,
                    selected = { it in selectedPorts },
                    label = { "$it" },
                    onSelect = { p -> onChange { it.togglePort(p) } },
                )
                if (selectedPorts.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${selectedPorts.size} ports selected — " +
                            "${settings.count * selectedPorts.size} probes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VerdictCaution,
                    )
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
                if (settings.speedTest) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Download sample: ${downloadLabel(settings.downloadBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    ChoiceChips(
                        options = ScanSettings.PRESET_DOWNLOAD_BYTES,
                        selected = { it == settings.downloadBytes },
                        label = ::downloadLabel,
                        onSelect = { b -> onChange { it.copy(downloadBytes = b) } },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "A bigger sample measures throughput more accurately but is " +
                            "downloaded once per address, so it is the main driver of " +
                            "data use on a wide sweep.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Reject slower than: ${speedLabel(settings.minSpeedKBps)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    ChoiceChips(
                        options = ScanSettings.PRESET_MIN_SPEED,
                        selected = { it == settings.minSpeedKBps },
                        label = ::speedLabel,
                        onSelect = { s -> onChange { it.copy(minSpeedKBps = s) } },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Discards addresses that answer and hold a connection but " +
                            "cannot actually carry traffic. Set it above the speed you " +
                            "would tolerate, not the speed you hope for.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
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
                    // 100 ms increments from 200 ms upward. The low end matters:
                    // a sub-300 ms timeout keeps only edges that answer almost
                    // instantly, which is the fastest way to sweep a large range
                    // when latency is already known to be good. It also produces
                    // false failures on a slow link, so the advice text below
                    // changes with the value.
                    valueRange = 200f..15000f,
                    steps = 147,
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
