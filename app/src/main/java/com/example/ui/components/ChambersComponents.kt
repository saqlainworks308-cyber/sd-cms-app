package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CasePriority
import com.example.data.model.CaseStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatEpochDateTime(epochMs: Long): String {
    if (epochMs <= 0L) return "Not Scheduled"
    return SimpleDateFormat("EEE, dd MMM • hh:mm a", Locale.getDefault()).format(Date(epochMs))
}

fun formatEpochDateOnly(epochMs: Long): String {
    if (epochMs <= 0L) return "Not Set"
    return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(epochMs))
}

fun formatEpochTimeOnly(epochMs: Long): String {
    if (epochMs <= 0L) return "TBA"
    return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(epochMs))
}

fun openExternalUrl(context: Context, rawUrl: String) {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return
    val normalized = if (trimmed.startsWith("http://", true) ||
        trimmed.startsWith("https://", true) ||
        trimmed.startsWith("keep://", true)
    ) {
        trimmed
    } else {
        "https://$trimmed"
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
    }.onFailure {
        Toast.makeText(context, "Unable to open link", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun PriorityBadge(priorityWire: String) {
    val priority = CasePriority.fromWire(priorityWire)
    val (bg, fg) = when (priority) {
        CasePriority.CRITICAL -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        CasePriority.HIGH -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
        CasePriority.STANDARD -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = priority.displayLabel.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun StatusBadge(statusWire: String) {
    val status = CaseStatus.fromWire(statusWire)
    val (bg, fg) = when (status) {
        CaseStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        CaseStatus.PENDING_FILING -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        CaseStatus.ADJOURNED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
        CaseStatus.DISPOSED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = status.displayLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun PlatformActionPill(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = tint.copy(alpha = 0.6f),
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
fun EmptyChambersCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
