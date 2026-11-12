package dev.vimivo.desktop.network

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.vimivo.cast.CastMime
import dev.vimivo.desktop.platform.DesktopDirectories
import dev.vimivo.network.KnownHosts
import dev.vimivo.network.RemoteEntry
import dev.vimivo.network.RemotePaths
import dev.vimivo.network.ShareProtocol
import dev.vimivo.network.ShareSession
import dev.vimivo.network.ShareSessionHolder
import dev.vimivo.network.SftpFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class ConnectForm {
    var protocol by mutableStateOf(ShareProtocol.SMB)
    var host by mutableStateOf("")
    var share by mutableStateOf("")
    var port by mutableStateOf("")
    var url by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")

    fun isComplete(): Boolean = when (protocol) {
        ShareProtocol.SMB -> host.isNotBlank() && share.isNotBlank()
        ShareProtocol.SFTP -> host.isNotBlank() && username.isNotBlank()
        ShareProtocol.WEBDAV -> url.isNotBlank()
    }
}

private class Browser(val path: String = "", val entries: List<RemoteEntry> = emptyList(), val loading: Boolean = false)

/**
 * Connect to a network share and play its media. Works for Windows / NAS shares (SMB), SSH servers (SFTP) and WebDAV
 * without any system tools: the share is read by this app and handed to the player through a local, private link.
 */
@Composable
internal fun DesktopNetworkShareDialog(
    holder: ShareSessionHolder,
    onOpenUrl: (url: String, title: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val form = remember { ConnectForm() }
    val knownHosts = remember { KnownHosts(DesktopDirectories.resolve().data.resolve("known_hosts").toFile()) }
    var browser by remember { mutableStateOf<Browser?>(if (holder.current != null) Browser(loading = true) else null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingFingerprint by remember { mutableStateOf<String?>(null) }

    fun browse(path: String) {
        val session = holder.current ?: return
        browser = Browser(path, browser?.entries.orEmpty(), loading = true)
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { session.fs.list(path) } }
            result.fold(
                onSuccess = { browser = Browser(path, it) },
                onFailure = { error = it.message ?: "Could not open the folder"; browser = Browser(path) },
            )
        }
    }

    fun finishConnect(result: Result<ShareSession>) {
        busy = false
        result.fold(
            onSuccess = { holder.replace(it); error = null; browse("") },
            onFailure = { error = it.message ?: "Could not connect" },
        )
    }

    fun connect() {
        busy = true
        error = null
        val secret = form.password.toCharArray()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                when (form.protocol) {
                    ShareProtocol.SMB -> ShareSession.smb(form.host, form.share, form.username, secret)
                    ShareProtocol.WEBDAV -> ShareSession.webDav(form.url, form.username, secret)
                    ShareProtocol.SFTP -> {
                        val port = form.port.toIntOrNull() ?: ShareProtocol.SFTP.defaultPort
                        if (knownHosts.fingerprint(form.host.trim(), port) == null) {
                            SftpFileSystem.probeFingerprint(form.host.trim(), port).fold(
                                onSuccess = { pendingFingerprint = it; Result.failure(IllegalStateException("")) },
                                onFailure = { Result.failure(it) },
                            )
                        } else {
                            ShareSession.sftp(form.host, port, form.username, secret, knownHosts)
                        }
                    }
                }
            }
            if (pendingFingerprint != null) busy = false else finishConnect(result)
        }
    }

    fun play(entry: RemoteEntry) {
        val session = holder.current ?: return
        scope.launch {
            val url = withContext(Dispatchers.IO) { session.playUrl(entry.path) }
            url.fold(
                onSuccess = { onOpenUrl(it, entry.name); onDismiss() },
                onFailure = { error = it.message ?: "Could not start playback" },
            )
        }
    }

    if (browser != null && holder.current == null) browser = null
    LaunchedBrowse(browser, holder) { browse("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(holder.current?.label ?: "Network share", style = MaterialTheme.typography.titleLarge, maxLines = 1)
            }
        },
        text = {
            val trust = pendingFingerprint
            when {
                trust != null -> TrustPrompt(form.host, trust)
                browser != null -> BrowserPane(browser!!, error, onUp = { browse(RemotePaths.parent(browser!!.path)) }, onOpen = ::browse, onPlay = ::play)
                else -> ConnectPane(form, busy, error)
            }
        },
        confirmButton = {
            val trust = pendingFingerprint
            when {
                trust != null -> Button(onClick = {
                    knownHosts.trust(form.host.trim(), form.port.toIntOrNull() ?: ShareProtocol.SFTP.defaultPort, trust)
                    pendingFingerprint = null
                    connect()
                }) { Text("Trust this server and connect") }
                browser != null -> TextButton(onClick = { holder.replace(null); browser = null }) { Text("Disconnect") }
                else -> Button(onClick = ::connect, enabled = form.isComplete() && !busy) { Text(if (busy) "Connecting…" else "Connect") }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (pendingFingerprint != null) pendingFingerprint = null else onDismiss() }) {
                Text(if (pendingFingerprint != null) "Cancel" else "Close")
            }
        },
    )
}

/** Opens the root folder when the dialog is reopened on a share that is still connected. */
@Composable
private fun LaunchedBrowse(browser: Browser?, holder: ShareSessionHolder, start: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { if (browser?.loading == true && holder.current != null) start() }
}

@Composable
private fun ConnectPane(form: ConnectForm, busy: Boolean, error: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(420.dp)) {
        Text(
            "Browse and play media from a Windows or NAS share, an SSH server, or WebDAV. Nothing is installed and your password stays in memory only.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ShareProtocol.entries.forEach { p ->
                FilterChip(selected = form.protocol == p, onClick = { form.protocol = p }, label = { Text(p.name.replace("WEBDAV", "WebDAV")) })
            }
        }
        when (form.protocol) {
            ShareProtocol.WEBDAV -> Field("Address", form.url, "https://nas.local/dav") { form.url = it }
            else -> {
                Field("Server name or IP", form.host, "192.168.1.100 or nas.local") { form.host = it }
                if (form.protocol == ShareProtocol.SMB) Field("Share name", form.share, "Movies") { form.share = it }
                else Field("Port", form.port, "22") { form.port = it.filter(Char::isDigit).take(5) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(if (form.protocol == ShareProtocol.SMB) "User (blank = guest)" else "User", form.username, "", Modifier.weight(1f)) { form.username = it }
            Field("Password", form.password, "", Modifier.weight(1f), password = true) { form.password = it }
        }
        if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Connecting…", style = MaterialTheme.typography.bodySmall)
        }
        error?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun Field(label: String, value: String, hint: String, modifier: Modifier = Modifier.fillMaxWidth(), password: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (hint.isEmpty()) null else ({ Text(hint) }),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun TrustPrompt(host: String, fingerprint: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(420.dp)) {
        Text("First time connecting to ${host.trim()}", style = MaterialTheme.typography.titleSmall)
        Text(
            "Check that this key matches your server before trusting it. If it does not, someone may be pretending to be your server.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(fingerprint, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
    }
}

@Composable
private fun BrowserPane(browser: Browser, error: String?, onUp: () -> Unit, onOpen: (String) -> Unit, onPlay: (RemoteEntry) -> Unit) {
    Column(modifier = Modifier.width(460.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onUp, enabled = browser.path.isNotEmpty() && !browser.loading) {
                Icon(Icons.Outlined.ArrowUpward, contentDescription = "Up one folder")
            }
            Text("/" + browser.path, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
            if (browser.loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        error?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        LazyColumn(Modifier.heightIn(max = 340.dp).fillMaxWidth()) {
            items(browser.entries, key = { it.path }) { entry ->
                val playable = !entry.isDirectory && CastMime.isMedia(entry.name)
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = entry.isDirectory || playable) { if (entry.isDirectory) onOpen(entry.path) else onPlay(entry) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        when {
                            entry.isDirectory -> Icons.Outlined.Folder
                            playable -> Icons.Outlined.PlayCircle
                            else -> Icons.AutoMirrored.Outlined.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = if (entry.isDirectory || playable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        entry.name,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (entry.isDirectory || playable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
        if (!browser.loading && browser.entries.isEmpty()) Text("This folder is empty.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
    }
}
