package dev.vimivo.platform.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.vimivo.app.R
import dev.vimivo.core.legal.NoticePlatform
import dev.vimivo.core.legal.ThirdPartyNotices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Settings ▸ Open-source licences: the LGPL notice, the generated third-party list for Android, links to the exact
 * sources, and the bundled LGPL 2.1 text (ADR-004). Links open only on an explicit tap.
 */
@Composable
fun AndroidLicensesDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var lgplVisible by remember { mutableStateOf(false) }
    val components = remember { ThirdPartyNotices.forPlatform(NoticePlatform.ANDROID) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.third_party_notices)) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ThirdPartyNotices.lgplNotice(NoticePlatform.ANDROID)?.let { notice ->
                    item { Text(notice, style = MaterialTheme.typography.bodySmall) }
                    item {
                        TextButton(onClick = { lgplVisible = true }) { Text(stringResource(R.string.lgpl_text)) }
                    }
                }
                item {
                    TextButton(onClick = { uriHandler.openUri(ThirdPartyNotices.SOURCE_REPOSITORY) }) {
                        Text(stringResource(R.string.source_code))
                    }
                }
                items(components, key = { it.name }) { component ->
                    Column(Modifier.fillMaxWidth()) {
                        HorizontalDivider()
                        Text("${component.name} ${component.version}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            component.license,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = { uriHandler.openUri(component.sourceUrl) }) {
                            Text(stringResource(R.string.component_source), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_notices)) } },
    )

    if (lgplVisible) LgplTextDialog(onDismiss = { lgplVisible = false })
}

@Composable
private fun LgplTextDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        text = withContext(Dispatchers.IO) {
            try {
                context.assets.open(LGPL_ASSET).bufferedReader().use { it.readText() }
            } catch (error: IOException) {
                "LGPL 2.1: https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt (${error.message})"
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lgpl_text)) },
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_notices)) } },
    )
}

private const val LGPL_ASSET = "licenses/LGPL-2.1.txt"
