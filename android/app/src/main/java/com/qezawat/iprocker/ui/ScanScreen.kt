package com.qezawat.iprocker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qezawat.iprocker.ui.components.IpDetailsSheet
import com.qezawat.iprocker.ui.components.ResultCard
import com.qezawat.iprocker.ui.components.RockerCard
import com.qezawat.iprocker.ui.components.ScanProgressBar
import com.qezawat.iprocker.ui.components.SectionLabel
import com.qezawat.iprocker.ui.theme.RockerAccent
import com.qezawat.iprocker.ui.theme.RockerBackground
import com.qezawat.iprocker.ui.theme.RockerSurfaceHigh
import com.qezawat.iprocker.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(viewModel: ScanViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }

    var showSettings by remember { mutableStateOf(false) }

    // Messages are surfaced once and cleared, so a stale error never lingers.
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(state.configApplied) {
        state.configApplied?.let {
            snackbarHost.showSnackbar(it)
            viewModel.consumeConfigApplied()
        }
    }

    Scaffold(
        containerColor = RockerBackground,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "IP ROCKER",
                            style = MaterialTheme.typography.titleLarge,
                            color = RockerAccent,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "clean Cloudflare edges, not just fast ones",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.semantics { contentDescription = "Scan settings" },
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RockerBackground),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ControlCard(
                    state = state,
                    onStart = viewModel::startScan,
                    onStop = viewModel::stopScan,
                    onCopyPhase1 = {
                        val text = viewModel.exportText("phase1")
                        if (text.isEmpty()) return@ControlCard
                        copyToClipboard(context, text)
                    },
                    onCopyWorking = {
                        val text = viewModel.exportWorkingText()
                        if (text.isEmpty()) return@ControlCard
                        copyToClipboard(context, text)
                    },
                    onSaveWorking = {
                        val text = viewModel.exportWorkingText()
                        if (text.isEmpty()) return@ControlCard
                        val uri = saveTextFile(context, "working_ips.txt", text)
                        if (uri != null) {
                            viewModel.consumeMessage()
                            viewModel.updateMessage("Saved working_ips.txt")
                        }
                    },
                )
            }

            if (state.report != null || state.liveHits.isNotEmpty()) {
                item {
                    FilterRow(
                        current = state.filter,
                        usableCount = state.report?.clean?.size ?: state.liveHits.size,
                        totalCount = state.report?.candidates?.size ?: state.liveHits.size,
                        onSelect = viewModel::setFilter,
                    )
                }
            }

            val results = state.visibleResults
            itemsIndexed(
                items = results,
                key = { _, candidate -> candidate.endpoint },
            ) { index, candidate ->
                ResultCard(
                    candidate = candidate,
                    rank = index + 1,
                    onDetails = viewModel::showDetails,
                    onCopy = { copyToClipboard(context, it.endpoint) },
                )
            }

            if (results.isEmpty() && !state.scanning) {
                item { EmptyState(state.report == null) }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = state.settings,
            onChange = viewModel::updateSettings,
            onDismiss = { showSettings = false },
        )
    }

    if (state.details != null || state.detailsLoading || state.detailsError != null) {
        IpDetailsSheet(
            info = state.details,
            loading = state.detailsLoading,
            error = state.detailsError,
            onDismiss = viewModel::closeDetails,
        )
    }
}

@Composable
private fun ControlCard(
    state: UiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCopyPhase1: () -> Unit,
    onCopyWorking: () -> Unit,
    onSaveWorking: () -> Unit,
) {
    RockerCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel(if (state.scanning) state.phase else "ready")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        state.scanning -> "${state.tested} probed · ${state.hits} answered"
                        state.report != null ->
                            "${state.report.tested} probed · ${state.report.cleanCount} usable"
                        else -> "${state.settings.count} addresses queued"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Phase 1 copies every address that answered; Phase 2 copies only the
            // working ones and also offers to save them as working_ips.txt.
            if (state.report != null && state.report.candidates.isNotEmpty()) {
                IconButton(
                    onClick = onCopyPhase1,
                    modifier = Modifier.semantics { contentDescription = "Copy all answered endpoints" },
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextSecondary)
                }
            }
            if (state.report?.cleanCount?.let { it > 0 } == true) {
                IconButton(
                    onClick = onCopyWorking,
                    modifier = Modifier.semantics { contentDescription = "Copy working endpoints" },
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RockerAccent)
                }
                IconButton(
                    onClick = onSaveWorking,
                    modifier = Modifier.semantics { contentDescription = "Save working_ips.txt" },
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = RockerAccent)
                }
            }
        }

        AnimatedVisibility(visible = state.scanning) {
            Column {
                Spacer(Modifier.height(12.dp))
                ScanProgressBar(
                    fraction = state.progressFraction,
                    // The reputation phase has no per-address total, so an
                    // indeterminate bar is honest rather than a fake percentage.
                    indeterminate = state.total <= 0 || state.phase.startsWith("checking"),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${state.inFlight} in flight",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = if (state.scanning) onStop else onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.scanning) {
                    MaterialTheme.colorScheme.error
                } else {
                    RockerAccent
                },
            ),
        ) {
            Icon(
                imageVector = if (state.scanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (state.scanning) "STOP" else "START SCAN",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FilterRow(
    current: ResultFilter,
    usableCount: Int,
    totalCount: Int,
    onSelect: (ResultFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val options = listOf(
            ResultFilter.USABLE to "Usable ($usableCount)",
            ResultFilter.ALL to "All ($totalCount)",
            ResultFilter.FLAGGED to "Rejected (${totalCount - usableCount})",
        )
        options.forEach { (filter, label) ->
            FilterChip(
                selected = current == filter,
                onClick = { onSelect(filter) },
                label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = RockerSurfaceHigh,
                    selectedContainerColor = RockerAccent.copy(alpha = 0.18f),
                    labelColor = TextSecondary,
                    selectedLabelColor = RockerAccent,
                ),
            )
        }
    }
}

@Composable
private fun EmptyState(neverRun: Boolean) {
    RockerCard {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (neverRun) "No scan yet" else "Nothing to show",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (neverRun) {
                        "Paste your config link in settings for results that match " +
                            "your own front, then start the scan."
                    } else {
                        "Try a larger address count, or switch off Strict mode in settings."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("IP ROCKER", text))
}

/**
 * Writes text to a user-picked file via the storage access framework and returns
 * the resulting URI. Returns null if the user cancels the picker. Using SAF
 * (rather than a fixed path) avoids the scoped-storage restrictions on newer
 * Android and lets the user save working_ips.txt wherever they like.
 */
private fun saveTextFile(context: Context, fileName: String, text: String): android.net.Uri? {
    val resolver = context.contentResolver
    val values = android.content.ContentValues().apply {
        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
    }
    val collection = android.provider.MediaStore.Downloads.getContentUri("external")
    val uri = resolver.insert(collection, values) ?: return null
    resolver.openOutputStream(uri)?.use { out ->
        out.write(text.toByteArray(Charsets.UTF_8))
    }
    return uri
}
