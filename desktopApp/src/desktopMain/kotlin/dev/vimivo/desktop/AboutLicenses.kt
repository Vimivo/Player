package dev.vimivo.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.vimivo.core.legal.NoticePlatform
import dev.vimivo.core.legal.ThirdPartyComponent
import dev.vimivo.core.legal.ThirdPartyNotices
import dev.vimivo.desktop.platform.DesktopPlatform
import dev.vimivo.desktop.playback.MpvNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File

/**
 * About ▸ open-source licences: the LGPL notice, the generated third-party list for this OS, and links to the exact
 * sources and build scripts (ADR-004). Links open only on an explicit click.
 */
@Composable
internal fun AboutLicensesSection(platform: NoticePlatform = currentNoticePlatform()) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var listVisible by remember { mutableStateOf(false) }
    val licensesDir = remember { bundledMediaLicensesDir() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Open-source licences", style = MaterialTheme.typography.titleSmall)
        ThirdPartyNotices.lgplNotice(platform)?.let { notice ->
            Text(
                notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { listVisible = true }) { Text("Third-party licences") }
            OutlinedButton(onClick = { uriHandler.openUri(ThirdPartyNotices.SOURCE_REPOSITORY) }) {
                Text("Source code")
            }
            OutlinedButton(onClick = { uriHandler.openUri(ThirdPartyNotices.BUILD_SCRIPTS_URL) }) {
                Text("Media build scripts")
            }
            if (licensesDir != null) {
                OutlinedButton(
                    onClick = { scope.launch { withContext(Dispatchers.IO) { openFolder(licensesDir) } } },
                    modifier = Modifier.semantics {
                        contentDescription = "Open the folder with the full licence texts"
                    },
                ) { Text("Licence texts") }
            }
        }
    }

    if (listVisible) {
        AlertDialog(
            onDismissRequest = { listVisible = false },
            title = { Text("Third-party licences") },
            text = { ComponentList(ThirdPartyNotices.forPlatform(platform), onOpen = uriHandler::openUri) },
            confirmButton = { TextButton(onClick = { listVisible = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun ComponentList(components: List<ThirdPartyComponent>, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(components, key = { it.name }) { component ->
            Column(Modifier.fillMaxWidth()) {
                Text("${component.name} ${component.version}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    component.license,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = { onOpen(component.sourceUrl) }) {
                    Text("Source", style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider()
            }
        }
    }
}

internal fun currentNoticePlatform(): NoticePlatform = when (DesktopPlatform.detect()) {
    DesktopPlatform.MACOS -> NoticePlatform.MACOS
    DesktopPlatform.WINDOWS -> NoticePlatform.WINDOWS
    else -> NoticePlatform.LINUX
}

/** The licence texts the bundlers copy next to the media libraries; null in development runs. */
internal fun bundledMediaLicensesDir(): File? {
    val candidates = listOfNotNull(
        MpvNative.findBundledFrameworksDir()?.resolveSibling("Resources"),
        MpvNative.findBundledWindowsDllDir(),
        MpvNative.findBundledLinuxLibDir(),
    )
    return candidates.map { it.resolve("media-licenses") }.firstOrNull(File::isDirectory)
}

private fun openFolder(directory: File) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
        runCatching { Desktop.getDesktop().open(directory) }
            .onFailure { error -> System.err.println("Vimivo: could not open $directory: ${error.message}") }
    }
}
