@file:Suppress(
    "ArgumentListWrapping",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "Indentation",
    "ImportOrdering",
    "LongMethod",
    "LongParameterList",
    "MaximumLineLength",
    "MaxLineLength",
    "MultiLineIfElse",
    "ParameterListWrapping",
    "PropertyWrapping",
    "StringTemplate",
    "TooManyFunctions",
    "Wrapping",
)

package dev.vimivo.platform.android.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vimivo.core.feedback.FeedbackCategory
import dev.vimivo.core.feedback.FeedbackDraft
import dev.vimivo.core.feedback.FeedbackReportFormatter
import dev.vimivo.core.feedback.FeedbackType
import dev.vimivo.core.feedback.FeedbackValidationResult
import dev.vimivo.core.feedback.FeedbackValidator
import dev.vimivo.core.feedback.IssueFrequency
import dev.vimivo.core.feedback.MediaPlaybackContext

@Composable
fun AndroidFeedbackDialog(
    onDismiss: () -> Unit,
    mediaContext: MediaPlaybackContext? = null,
) {
    val context = LocalContext.current
    var selectedType by remember {
        mutableStateOf(if (mediaContext != null) FeedbackType.PLAYBACK_ISSUE else FeedbackType.BUG_REPORT)
    }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(IssueFrequency.OFTEN) }
    var includeDiagnostics by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0.0-android"
    }

    val osVersion = remember { "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" }

    fun buildCurrentDraft(): FeedbackDraft {
        return FeedbackDraft(
            id = "android-fb-${System.currentTimeMillis()}",
            type = selectedType,
            category = when (selectedType) {
                FeedbackType.PLAYBACK_ISSUE -> FeedbackCategory.PLAYBACK
                FeedbackType.ACCESSIBILITY_ISSUE -> FeedbackCategory.ACCESSIBILITY
                FeedbackType.FEATURE_REQUEST -> FeedbackCategory.USER_INTERFACE
                else -> FeedbackCategory.CRASH_OR_FREEZE
            },
            title = title,
            description = description,
            stepsToReproduce = steps,
            frequency = frequency,
            includeDiagnostics = includeDiagnostics,
            mediaContext = mediaContext,
            platform = "Android",
            osVersion = osVersion,
            appVersion = appVersion,
        )
    }

    val validation = remember(title, description, steps, selectedType) {
        FeedbackValidator.validate(buildCurrentDraft())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Help & Community Feedback",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close feedback dialog")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Feedback reports are sanitized locally. " +
                        "No credentials or media files are ever transmitted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                ScrollableTabRow(
                    selectedTabIndex = selectedType.ordinal,
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = 0.dp,
                ) {
                    FeedbackType.entries.forEach { type ->
                        Tab(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            text = { Text(type.displayName) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title / Summary *") },
                    placeholder = {
                        Text(
                            when (selectedType) {
                                FeedbackType.BUG_REPORT -> "e.g., Stutter when backgrounding video"
                                FeedbackType.FEATURE_REQUEST -> "e.g., Support opus embedded album art"
                                FeedbackType.PLAYBACK_ISSUE -> "e.g., Audio out of sync after seek"
                                FeedbackType.ACCESSIBILITY_ISSUE -> "e.g., TalkBack missing queue position label"
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = {
                        Text(
                            if (selectedType == FeedbackType.FEATURE_REQUEST) {
                                "User Problem & Proposed Benefit *"
                            } else {
                                "Detailed Description *"
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )

                if (selectedType != FeedbackType.FEATURE_REQUEST) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = steps,
                        onValueChange = { steps = it },
                        label = { Text("Steps to Reproduce") },
                        placeholder = { Text("1. Play sample\n2. Tap pause\n3. Observe symptom") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = includeDiagnostics,
                        onCheckedChange = { includeDiagnostics = it },
                    )
                    Text(
                        text = "Include sanitized diagnostic excerpt (logs & codec info)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }

                if (validation is FeedbackValidationResult.Invalid) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = validation.errors.joinToString("\n"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                statusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = " $msg",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                OutlinedButton(
                    onClick = {
                        val draft = buildCurrentDraft()
                        val markdown = FeedbackReportFormatter.formatMarkdown(draft, null)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Vimivo Feedback", markdown))
                        statusMessage = "Sanitized report copied to clipboard!"
                    },
                    enabled = validation.isValid,
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text(" Copy", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = {
                        val draft = buildCurrentDraft()
                        val markdown = FeedbackReportFormatter.formatMarkdown(draft, null)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "[Vimivo Feedback] ${draft.title}")
                            putExtra(Intent.EXTRA_TEXT, markdown)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Sanitized Report"))
                    },
                    enabled = validation.isValid,
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Text(" Share", style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = {
                        val draft = buildCurrentDraft()
                        val url = FeedbackReportFormatter.generateGitHubIssueUrl(draft, null)
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }.onFailure {
                            statusMessage = "Could not open browser. Please use Copy button."
                        }
                    },
                    enabled = validation.isValid,
                ) {
                    Icon(Icons.Outlined.OpenInBrowser, contentDescription = null)
                    Text(" GitHub", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
