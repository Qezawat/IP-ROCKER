package com.qezawat.iprocker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qezawat.iprocker.data.Verdict
import com.qezawat.iprocker.ui.theme.RockerAccent
import com.qezawat.iprocker.ui.theme.RockerOutline
import com.qezawat.iprocker.ui.theme.RockerSurface
import com.qezawat.iprocker.ui.theme.RockerSurfaceHigh
import com.qezawat.iprocker.ui.theme.TextSecondary
import com.qezawat.iprocker.ui.theme.VerdictCaution
import com.qezawat.iprocker.ui.theme.VerdictClean
import com.qezawat.iprocker.ui.theme.VerdictDirty
import com.qezawat.iprocker.ui.theme.VerdictUnknown

fun Verdict.color(): Color = when (this) {
    Verdict.CLEAN -> VerdictClean
    Verdict.CAUTION -> VerdictCaution
    Verdict.DIRTY -> VerdictDirty
    Verdict.UNKNOWN -> VerdictUnknown
}

fun Verdict.label(): String = when (this) {
    Verdict.CLEAN -> "CLEAN"
    Verdict.CAUTION -> "CAUTION"
    Verdict.DIRTY -> "HIGH RISK"
    Verdict.UNKNOWN -> "UNVERIFIED"
}

/**
 * A card surface used for every grouped block of content.
 */
@Composable
fun RockerCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RockerSurface)
            .border(1.dp, RockerOutline, RoundedCornerShape(18.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = RockerAccent,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

/**
 * The traffic-light pill. The colour is always paired with a text label so the
 * information does not depend on colour perception alone.
 */
@Composable
fun VerdictBadge(
    verdict: Verdict,
    riskPercent: Double?,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(verdict.color(), label = "verdictColor")
    val text = buildString {
        if (riskPercent != null && verdict != Verdict.UNKNOWN) {
            append("%.0f%% ".format(riskPercent))
        }
        append(verdict.label())
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.16f))
            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics { contentDescription = "Reputation: $text" },
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(tint),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A small key/value row used throughout the details sheet. */
@Composable
fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = valueColor,
        )
    }
}

/** A yes/no check with an explicit word, never colour alone. */
@Composable
fun FlagChip(label: String, flagged: Boolean, modifier: Modifier = Modifier) {
    val tint = if (flagged) VerdictDirty else VerdictClean
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(RockerSurfaceHigh)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (flagged) "YES" else "NO",
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * A progress bar that animates indeterminately while a phase has no known
 * total, so the user can tell the app is working rather than stalled.
 */
@Composable
fun ScanProgressBar(
    fraction: Float,
    indeterminate: Boolean,
    modifier: Modifier = Modifier,
) {
    if (indeterminate) {
        val transition = rememberInfiniteTransition(label = "scanPulse")
        val shift by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200),
                repeatMode = RepeatMode.Restart,
            ),
            label = "shift",
        )
        Box(
            modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(RockerSurfaceHigh),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.35f)
                    .height(6.dp)
                    .alpha(0.9f)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, RockerAccent, Color.Transparent),
                        ),
                    )
                    .padding(start = (shift * 200).dp),
            )
        }
    } else {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50)),
            color = RockerAccent,
            trackColor = RockerSurfaceHigh,
        )
    }
}
