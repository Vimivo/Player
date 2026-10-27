package dev.vimivo.desktop.player.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DesktopJumpToTimeDialog(
    currentPositionMillis: Long,
    durationMillis: Long?,
    onJump: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf(playerClock(currentPositionMillis)) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun executeJump(millis: Long) {
        val target = if (durationMillis != null && durationMillis > 0) {
            millis.coerceIn(0L, durationMillis)
        } else {
            millis.coerceAtLeast(0L)
        }
        onJump(target)
        onDismiss()
    }

    fun submit() {
        val parsed = parseTimeInput(input.trim())
        if (parsed != null) {
            executeJump(parsed)
        } else {
            errorText = "Invalid format. Use mm:ss, hh:mm:ss, or seconds."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(440.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Jump to Time", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val currentFormatted = playerClock(currentPositionMillis)
                val totalFormatted = durationMillis?.let(::playerClock) ?: "Unknown"
                Text(
                    "Current: $currentFormatted / $totalFormatted",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        errorText = null
                    },
                    label = { Text("Time (e.g. 15:30 or 1:12:00)") },
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Quick Jumps", style = MaterialTheme.typography.labelMedium)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                ) {
                    val duration = durationMillis ?: 0L
                    FilterChip(
                        selected = false,
                        onClick = { executeJump(0L) },
                        label = { Text("00:00") },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { executeJump(currentPositionMillis - 60_000L) },
                        label = { Text("-1m") },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { executeJump(currentPositionMillis - 10_000L) },
                        label = { Text("-10s") },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { executeJump(currentPositionMillis + 10_000L) },
                        label = { Text("+10s") },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { executeJump(currentPositionMillis + 60_000L) },
                        label = { Text("+1m") },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { executeJump(currentPositionMillis + 300_000L) },
                        label = { Text("+5m") },
                    )
                    if (duration > 0L) {
                        FilterChip(
                            selected = false,
                            onClick = { executeJump(duration / 2) },
                            label = { Text("50%") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = ::submit) {
                Text("Jump")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

internal fun parseTimeInput(raw: String): Long? {
    val clean = raw.trim()
    val secondsOnly = clean.toLongOrNull()
    if (secondsOnly != null) {
        return secondsOnly * 1000L
    }
    val parts = clean.split(':').map { it.trim().toDoubleOrNull() }
    if (parts.isEmpty() || parts.any { it == null }) {
        return null
    }
    val nonNull = parts.filterNotNull()
    return when (nonNull.size) {
        1 -> (nonNull[0] * 1000).toLong()
        2 -> ((nonNull[0] * 60 + nonNull[1]) * 1000).toLong()
        3 -> ((nonNull[0] * 3600 + nonNull[1] * 60 + nonNull[2]) * 1000).toLong()
        else -> null
    }
}
