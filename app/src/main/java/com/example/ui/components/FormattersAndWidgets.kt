package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BorderLavender
import com.example.ui.theme.PurpleLight
import com.example.ui.theme.PurplePrimary
import com.example.ui.theme.PurpleTint
import com.example.ui.theme.PurpleVibrant
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.WhitePure
import java.util.Locale

fun formatSpeedBps(bytesPerSec: Long): Pair<String, String> {
    if (bytesPerSec <= 0L) return "0.0" to "KB/s"
    val bitsPerSec = bytesPerSec * 8.0
    return when {
        bitsPerSec >= 1_000_000.0 -> {
            String.format(Locale.US, "%.2f", bitsPerSec / 1_000_000.0) to "Mbps"
        }
        bytesPerSec >= 1024L -> {
            String.format(Locale.US, "%.1f", bytesPerSec / 1024.0) to "KB/s"
        }
        else -> {
            "$bytesPerSec" to "B/s"
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

@Composable
fun BrandPillBadge(
    text: String,
    containerColor: Color = PurpleLight,
    contentColor: Color = PurplePrimary,
    leadingDotColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (leadingDotColor != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(leadingDotColor)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
fun MetricStatTile(
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    accentColor: Color = PurplePrimary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .border(1.dp, BorderLavender, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = WhitePure
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (unit.isNotBlank()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LiveThroughputSparkline(
    downloadSamples: List<Long>,
    uploadSamples: List<Long>,
    modifier: Modifier = Modifier
) {
    val maxVal = (downloadSamples + uploadSamples).maxOrNull()?.coerceAtLeast(1024L) ?: 1024L
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PurpleTint)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            if (downloadSamples.size < 2) {
                drawLine(
                    color = BorderLavender,
                    start = Offset(0f, h * 0.8f),
                    end = Offset(w, h * 0.8f),
                    strokeWidth = 2.dp.toPx()
                )
                return@Canvas
            }

            fun buildPath(samples: List<Long>): Path {
                val path = Path()
                val stepX = w / (samples.size - 1).coerceAtLeast(1)
                samples.forEachIndexed { idx, v ->
                    val x = idx * stepX
                    val norm = (v.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                    val y = h - (norm * (h * 0.85f)) - (h * 0.05f)
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                return path
            }

            val downPath = buildPath(downloadSamples)
            drawPath(
                path = downPath,
                brush = Brush.horizontalGradient(listOf(PurplePrimary, PurpleVibrant)),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            if (uploadSamples.size >= 2) {
                val upPath = buildPath(uploadSamples)
                drawPath(
                    path = upPath,
                    color = StatusConnected.copy(alpha = 0.85f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}
