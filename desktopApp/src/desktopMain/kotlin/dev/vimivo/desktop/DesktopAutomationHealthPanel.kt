@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "FunctionNaming", "Indentation", "LongMethod",
    "LongParameterList", "MagicNumber", "MaximumLineLength", "MaxLineLength", "NoConsecutiveBlankLines",
    "ParameterListWrapping", "TooManyFunctions", "Wrapping",
)

package dev.vimivo.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vimivo.core.model.AutomationActionType
import dev.vimivo.core.model.AutomationTrigger
import dev.vimivo.desktop.library.DesktopLibraryController
import dev.vimivo.desktop.library.DesktopLibraryUiState
import dev.vimivo.desktop.library.DryRunResult

@Composable
internal fun DesktopAutomationHealthPanel(
    state: DesktopLibraryUiState,
    controller: DesktopLibraryController,
) {
    var newRuleName by remember { mutableStateOf("") }
    var selectedTrigger by remember { mutableStateOf(AutomationTrigger.MANUAL) }
    var selectedAction by remember { mutableStateOf(AutomationActionType.REFRESH_SMART_COLLECTIONS) }
    var dryRunResult by remember { mutableStateOf<DryRunResult?>(null) }

    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Library Health & Automation", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Local-first maintenance, deterministic dry-runs, and operational integrity.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.healthReport?.let { health ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Library Health Dashboard", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Health Score: ${health.healthScorePercent}%",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (health.healthScorePercent >= 80) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Indexed: ${health.totalMediaCount}")
                            Text("Available: ${health.availableMediaCount}")
                            Text("Missing Files: ${health.missingMediaCount}")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Missing Metadata: ${health.missingMetadataCount}")
                            Text("Missing Artwork: ${health.missingArtworkCount}")
                            Text("Duplicate Clusters: ${health.exactDuplicateGroupCount}")
                        }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add Workflow Automation Rule", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = newRuleName,
                        onValueChange = { newRuleName = it },
                        label = { Text("Rule Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Trigger: ${selectedTrigger.name}")
                        OutlinedButton(onClick = {
                            val values = AutomationTrigger.entries
                            val nextIdx = (values.indexOf(selectedTrigger) + 1) % values.size
                            selectedTrigger = values[nextIdx]
                        }) { Text("Cycle Trigger") }

                        Text("Action: ${selectedAction.name}")
                        OutlinedButton(onClick = {
                            val values = AutomationActionType.entries
                            val nextIdx = (values.indexOf(selectedAction) + 1) % values.size
                            selectedAction = values[nextIdx]
                        }) { Text("Cycle Action") }
                    }

                    Button(
                        onClick = {
                            if (newRuleName.isNotBlank()) {
                                controller.createWorkflowRule(newRuleName, selectedTrigger, selectedAction)
                                newRuleName = ""
                            }
                        },
                        enabled = newRuleName.isNotBlank(),
                    ) {
                        Text("Save Rule")
                    }
                }
            }
        }

        dryRunResult?.let { result ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Dry-Run Preview: ${result.ruleName}", style = MaterialTheme.typography.titleMedium)
                        Text("Action: ${result.plannedAction.name}", color = MaterialTheme.colorScheme.primary)
                        Text("Estimated Items: ${result.estimatedAffectedItems}")
                        Text("Details: ${result.summary}", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { dryRunResult = null }) { Text("Dismiss Preview") }
                    }
                }
            }
        }

        item {
            Text("Active Workflow Rules", style = MaterialTheme.typography.titleMedium)
        }

        items(state.workflowRules) { rule ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(rule.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Trigger: ${rule.trigger.name} • Action: ${rule.action.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { dryRunResult = controller.dryRunRule(rule) }) {
                            Text("Dry-Run")
                        }
                        OutlinedButton(onClick = { controller.deleteWorkflowRule(rule.id) }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("Operation Journal (Recent Jobs)", style = MaterialTheme.typography.titleMedium)
        }

        items(state.journalEntries) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("${entry.operationType} — ${entry.stage.name}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Affected: ${entry.affectedCount} items • ${entry.details}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "${(System.currentTimeMillis() - entry.updatedAtEpochMillis) / 1000}s ago",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
