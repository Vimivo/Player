@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "DEPRECATION", "FunctionNaming", "LongMethod",
    "LongParameterList", "MaximumLineLength", "MaxLineLength", "MultiLineIfElse", "NoUnusedImports",
    "TooGenericExceptionCaught", "Wrapping",
)

package dev.vimivo.desktop.feedback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.vimivo.core.diagnostics.DiagnosticReport
import dev.vimivo.core.feedback.FeedbackCategory
import dev.vimivo.core.feedback.FeedbackDraft
import dev.vimivo.core.feedback.FeedbackReportFormatter
import dev.vimivo.core.feedback.FeedbackType
import dev.vimivo.core.feedback.FeedbackValidationResult
import dev.vimivo.core.feedback.FeedbackValidator
import dev.vimivo.core.feedback.IssueFrequency
import dev.vimivo.core.feedback.MediaPlaybackContext
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun DesktopFeedbackDialog(
    onDismissRequest: () -> Unit,
    repository: DesktopFeedbackDraftRepository = remember { DesktopFeedbackDraftRepository() },
    diagnosticReport: DiagnosticReport? = null,
    initialDraft: FeedbackDraft? = null,
    initialPlaybackContext: MediaPlaybackContext? = null,
    onDraftSaved: ((FeedbackDraft) -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()

    var activeTab by remember {
        mutableStateOf(
            when (initialDraft?.type) {
                FeedbackType.FEATURE_REQUEST -> 1
                FeedbackType.PLAYBACK_ISSUE -> 2
                FeedbackType.ACCESSIBILITY_ISSUE -> 0
                else -> if (initialPlaybackContext != null) 2 else 0
            },
        )
    }

    var draftId by remember {
        mutableStateOf(initialDraft?.id ?: "draft-${System.currentTimeMillis()}")
    }
    var title by remember { mutableStateOf(initialDraft?.title.orEmpty()) }
    var description by remember { mutableStateOf(initialDraft?.description.orEmpty()) }
    var stepsToReproduce by remember { mutableStateOf(initialDraft?.stepsToReproduce.orEmpty()) }
    var actualBehavior by remember { mutableStateOf(initialDraft?.actualBehavior.orEmpty()) }
    var proposedSolution by remember { mutableStateOf(initialDraft?.proposedSolution.orEmpty()) }
    var workaround by remember { mutableStateOf(initialDraft?.workaround.orEmpty()) }
    var selectedCategory by remember {
        mutableStateOf(initialDraft?.category ?: if (initialPlaybackContext != null) FeedbackCategory.PLAYBACK else FeedbackCategory.USER_INTERFACE)
    }
    var selectedFrequency by remember {
        mutableStateOf(initialDraft?.frequency ?: IssueFrequency.ALWAYS)
    }
    var includeDiagnostics by remember {
        mutableStateOf(initialDraft?.includeDiagnostics ?: true)
    }
    var mediaContext by remember {
        mutableStateOf(initialDraft?.mediaContext ?: initialPlaybackContext)
    }

    var savedDrafts by remember { mutableStateOf<List<FeedbackDraft>>(emptyList()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorMessage by remember { mutableStateOf(false) }
    var isPreviewMode by remember { mutableStateOf(false) }

    fun refreshDrafts() {
        coroutineScope.launch {
            savedDrafts = repository.listDrafts()
        }
    }

    LaunchedEffect(Unit) {
        refreshDrafts()
    }

    val currentFeedbackType = when (activeTab) {
        0 -> FeedbackType.BUG_REPORT
        1 -> FeedbackType.FEATURE_REQUEST
        2 -> FeedbackType.PLAYBACK_ISSUE
        else -> FeedbackType.BUG_REPORT
    }

    fun buildCurrentDraft(): FeedbackDraft {
        val now = System.currentTimeMillis()
        return FeedbackDraft(
            id = draftId,
            type = currentFeedbackType,
            category = selectedCategory,
            title = title,
            description = description,
            stepsToReproduce = stepsToReproduce,
            actualBehavior = actualBehavior,
            proposedSolution = proposedSolution,
            workaround = workaround,
            frequency = selectedFrequency,
            mediaContext = mediaContext,
            includeDiagnostics = includeDiagnostics,
            appVersion = "1.0.0-rc.1",
            platform = "macOS / Windows / Linux (Desktop JVM)",
            osVersion = "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
            architecture = System.getProperty("os.arch") ?: "unknown",
            createdAtEpochMs = initialDraft?.createdAtEpochMs ?: now,
            updatedAtEpochMs = now,
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.width(680.dp),
        title = {
            Column {
                Text("Community Feedback & Issue Reporter", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Submit privacy-safe bug reports, playback issues, or feature requests.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ScrollableTabRow(
                    selectedTabIndex = activeTab,
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0; isPreviewMode = false },
                        text = { Text("Bug Report") },
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1; isPreviewMode = false },
                        text = { Text("Feature Request") },
                    )
                    Tab(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2; isPreviewMode = false },
                        text = { Text("Playback Issue") },
                    )
                    Tab(
                        selected = activeTab == 3,
                        onClick = { activeTab = 3; isPreviewMode = false; refreshDrafts() },
                        text = { Text("Saved Drafts (${savedDrafts.size})") },
                    )
                }

                statusMessage?.let { msg ->
                    Surface(
                        color = if (isErrorMessage) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isErrorMessage) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }

                if (activeTab == 3) {
                    // Saved Drafts List
                    if (savedDrafts.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "No saved feedback drafts found. Compose a report and click 'Save Draft'.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        ) {
                            items(savedDrafts) { draft ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                draft.title.ifBlank { "(Untitled Draft)" },
                                                style = MaterialTheme.typography.titleSmall,
                                            )
                                            Text(
                                                "${draft.type.displayName} • ${draft.category.displayName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Button(
                                                onClick = {
                                                    draftId = draft.id
                                                    title = draft.title
                                                    description = draft.description
                                                    stepsToReproduce = draft.stepsToReproduce
                                                    actualBehavior = draft.actualBehavior
                                                    proposedSolution = draft.proposedSolution
                                                    workaround = draft.workaround
                                                    selectedCategory = draft.category
                                                    selectedFrequency = draft.frequency
                                                    includeDiagnostics = draft.includeDiagnostics
                                                    mediaContext = draft.mediaContext
                                                    activeTab = when (draft.type) {
                                                        FeedbackType.BUG_REPORT -> 0
                                                        FeedbackType.FEATURE_REQUEST -> 1
                                                        FeedbackType.PLAYBACK_ISSUE -> 2
                                                        FeedbackType.ACCESSIBILITY_ISSUE -> 0
                                                    }
                                                    statusMessage = "Loaded draft: ${draft.title}"
                                                    isErrorMessage = false
                                                },
                                                modifier = Modifier.semantics {
                                                    role = Role.Button
                                                    contentDescription = "Load draft: ${draft.title.ifBlank { "Untitled" }}"
                                                },
                                            ) {
                                                Text("Load")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        repository.deleteDraft(draft.id)
                                                        refreshDrafts()
                                                    }
                                                },
                                                modifier = Modifier.semantics {
                                                    role = Role.Button
                                                    contentDescription = "Delete draft: ${draft.title.ifBlank { "Untitled" }}"
                                                },
                                            ) {
                                                Text("Delete")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (isPreviewMode) {
                    // Preview formatted Markdown
                    val draft = buildCurrentDraft()
                    val markdown = FeedbackReportFormatter.formatMarkdown(draft, diagnosticReport)
                    Column(Modifier.weight(1f)) {
                        Text("Report Preview (Sanitized Markdown):", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        ) {
                            LazyColumn(Modifier.padding(12.dp)) {
                                item {
                                    Text(
                                        markdown,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Compose form
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        item {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Issue Title / Summary *") },
                                placeholder = {
                                    Text(
                                        if (activeTab == 1) "e.g., Add custom accent color picker"
                                        else "e.g., Playback audio stutters when seeking large FLAC file"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Feedback title input" },
                                singleLine = true,
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = {
                                    Text(
                                        if (activeTab == 1) "User Problem & Desired Improvement *"
                                        else "What Happened / Problem Description *"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(100.dp).semantics { contentDescription = "Feedback description input" },
                            )
                        }

                        if (activeTab == 0 || activeTab == 2) {
                            item {
                                OutlinedTextField(
                                    value = stepsToReproduce,
                                    onValueChange = { stepsToReproduce = it },
                                    label = { Text("Steps to Reproduce (or What Actually Happened) *") },
                                    placeholder = { Text("1. Open file...\n2. Press pause...\n3. Observe...") },
                                    modifier = Modifier.fillMaxWidth().height(90.dp).semantics { contentDescription = "Steps to reproduce input" },
                                )
                            }
                        }

                        if (activeTab == 1) {
                            item {
                                OutlinedTextField(
                                    value = proposedSolution,
                                    onValueChange = { proposedSolution = it },
                                    label = { Text("Proposed Solution / Ideal Behavior") },
                                    placeholder = { Text("How would you like this feature to work?") },
                                    modifier = Modifier.fillMaxWidth().height(80.dp),
                                )
                            }
                            item {
                                OutlinedTextField(
                                    value = workaround,
                                    onValueChange = { workaround = it },
                                    label = { Text("Current Workaround (if any)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                )
                            }
                        }

                        if (activeTab == 2 && mediaContext != null) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Active Media Context (Auto-Detected):", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            "Container: ${mediaContext?.container ?: "Unknown"} | Video: ${mediaContext?.videoCodec ?: "None"} | Audio: ${mediaContext?.audioCodec ?: "None"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        mediaContext?.errorMessage?.let { err ->
                                            Text("Last Engine Error: $err", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { includeDiagnostics = !includeDiagnostics },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = includeDiagnostics,
                                    onCheckedChange = { includeDiagnostics = it },
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Include privacy-safe diagnostic excerpt", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "Includes playback engine version and recent error codes. Zero private media files, directories, or credentials.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (activeTab != 3) {
                    OutlinedButton(
                        onClick = {
                            val draft = buildCurrentDraft()
                            coroutineScope.launch {
                                repository.saveDraft(draft)
                                onDraftSaved?.invoke(draft)
                                statusMessage = "Draft saved to local storage."
                                isErrorMessage = false
                                refreshDrafts()
                            }
                        },
                    ) {
                        Text("Save Draft")
                    }

                    OutlinedButton(
                        onClick = { isPreviewMode = !isPreviewMode },
                    ) {
                        Text(if (isPreviewMode) "Edit Form" else "Preview")
                    }

                    Button(
                        onClick = {
                            val draft = buildCurrentDraft()
                            val validation = FeedbackValidator.validate(draft)
                            if (!validation.isValid) {
                                statusMessage = (validation as FeedbackValidationResult.Invalid).errorMessage
                                isErrorMessage = true
                            } else {
                                val markdown = FeedbackReportFormatter.formatMarkdown(draft, diagnosticReport)
                                val selection = StringSelection(markdown)
                                Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
                                statusMessage = "Report copied to clipboard! Ready to paste into GitHub."
                                isErrorMessage = false
                            }
                        },
                    ) {
                        Text("Copy Report")
                    }

                    Button(
                        onClick = {
                            val draft = buildCurrentDraft()
                            val validation = FeedbackValidator.validate(draft)
                            if (!validation.isValid) {
                                statusMessage = (validation as FeedbackValidationResult.Invalid).errorMessage
                                isErrorMessage = true
                            } else {
                                val url = FeedbackReportFormatter.generateGitHubIssueUrl(draft, diagnosticReport)
                                val result = repository.openGitHubInBrowser(url)
                                if (result.isSuccess) {
                                    statusMessage = "Opening pre-filled issue form in your browser..."
                                    isErrorMessage = false
                                } else {
                                    // Fallback: Copy to clipboard if browser launch fails
                                    val markdown = FeedbackReportFormatter.formatMarkdown(draft, diagnosticReport)
                                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(markdown), null)
                                    statusMessage = "Browser unavailable. Report copied to clipboard instead!"
                                    isErrorMessage = false
                                }
                            }
                        },
                    ) {
                        Text("Open on GitHub")
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismissRequest) {
                Text("Close")
            }
        },
    )
}
