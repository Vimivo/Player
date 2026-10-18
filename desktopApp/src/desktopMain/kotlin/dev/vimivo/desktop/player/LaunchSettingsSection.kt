package dev.vimivo.desktop.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.vimivo.desktop.platform.DefaultPlayerResult
import dev.vimivo.desktop.platform.DefaultPlayerSetup
import dev.vimivo.desktop.platform.DesktopPlatform
import dev.vimivo.desktop.resources.Res
import dev.vimivo.desktop.resources.launch_folder_siblings
import dev.vimivo.desktop.resources.launch_make_default
import dev.vimivo.desktop.resources.launch_make_default_applied
import dev.vimivo.desktop.resources.launch_make_default_cancel
import dev.vimivo.desktop.resources.launch_make_default_confirm
import dev.vimivo.desktop.resources.launch_make_default_confirm_body
import dev.vimivo.desktop.resources.launch_make_default_confirm_title
import dev.vimivo.desktop.resources.launch_make_default_failed
import dev.vimivo.desktop.resources.launch_make_default_opened
import dev.vimivo.desktop.resources.launch_open_mode_queue
import dev.vimivo.desktop.resources.launch_open_mode_replace
import dev.vimivo.desktop.resources.launch_settings_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Settings for how files from the OS open: single-instance mode, folder "Up Next", and the default player. */
@Composable
internal fun LaunchSettingsSection(state: PlayerState, onIntent: (PlayerIntent) -> Unit) {
    var confirmLinux by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<DefaultPlayerResult?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.launch_settings_title), style = MaterialTheme.typography.titleMedium)
            OpenModeRow(OpenMode.REPLACE, Res.string.launch_open_mode_replace, state.openMode, onIntent)
            OpenModeRow(OpenMode.ADD_TO_QUEUE, Res.string.launch_open_mode_queue, state.openMode, onIntent)
            Row(
                Modifier.fillMaxWidth().toggleable(
                    value = state.folderSiblings,
                    role = Role.Checkbox,
                    onValueChange = { onIntent(PlayerIntent.SetFolderSiblings(it)) },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.folderSiblings, onCheckedChange = null)
                Text(stringResource(Res.string.launch_folder_siblings), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = {
                if (DesktopPlatform.detect() == DesktopPlatform.LINUX) {
                    confirmLinux = true
                } else {
                    result = DefaultPlayerSetup.makeDefault(confirmed = false)
                }
            }) { Text(stringResource(Res.string.launch_make_default)) }
            result?.let { Text(it.message(), style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (confirmLinux) {
        AlertDialog(
            onDismissRequest = { confirmLinux = false },
            title = { Text(stringResource(Res.string.launch_make_default_confirm_title)) },
            text = { Text(stringResource(Res.string.launch_make_default_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLinux = false
                    result = DefaultPlayerSetup.makeDefault(confirmed = true)
                }) { Text(stringResource(Res.string.launch_make_default_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLinux = false }) {
                    Text(stringResource(Res.string.launch_make_default_cancel))
                }
            },
        )
    }
}

@Composable
private fun OpenModeRow(mode: OpenMode, label: StringResource, selected: OpenMode, onIntent: (PlayerIntent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(
            selected = mode == selected,
            role = Role.RadioButton,
            onClick = { onIntent(PlayerIntent.SetOpenMode(mode)) },
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = mode == selected, onClick = null)
        Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun DefaultPlayerResult.message(): String = when (this) {
    DefaultPlayerResult.OpenedSystemSettings -> stringResource(Res.string.launch_make_default_opened)
    is DefaultPlayerResult.Applied -> stringResource(Res.string.launch_make_default_applied, mimeTypes)
    is DefaultPlayerResult.Instructions -> steps
    is DefaultPlayerResult.Failed -> stringResource(Res.string.launch_make_default_failed, reason)
}
