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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.vimivo.cast.CastMime
import dev.vimivo.desktop.platform.DesktopDirectories
import dev.vimivo.desktop.platform.FriendlyError
import dev.vimivo.network.KnownHosts
import dev.vimivo.network.RemoteEntry
import dev.vimivo.network.RemotePaths
import dev.vimivo.network.SftpFileSystem
import dev.vimivo.network.ShareProtocol
import dev.vimivo.network.ShareSession
import dev.vimivo.network.ShareSessionHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the user typed on the connect screen. */
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

    val sftpPort: Int get() = port.toIntOrNull() ?: ShareProtocol.SFTP.defaultPort
}

/** The folder being shown. */
private class FolderView(val path: String = "", val entries: List<RemoteEntry> = emptyList(), val loading: Boolean = false)

/** All the dialog's behaviour, so the composables below only draw. */
private class ShareDialogState(
    private val scope: CoroutineScope,
    private val holder: ShareSessionHolder,
    private val knownHosts: KnownHosts,
    private val onOpenUrl: (url: String, title: String) -> Unit,
    private val onDismiss: () -> Unit,
) {
    val form = ConnectForm()
    var folder by mutableStateOf<FolderView?>(if (holder.current != null) FolderView(loading = true) else null)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var pendingFingerprint by mutableStateOf<String?>(null)

    val title: String get() = holder.current?.label ?: "Network share"

    fun openRootIfConnected() {
        if (folder?.loading == true && holder.current != null) browse("")
    }

    fun browse(path: String) {
        val session = holder.current ?: return
        folder = FolderView(path, folder?.entries.orEmpty(), loading = true)
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { session.fs.list(path) } }.fold(
                onSuccess = { folder = FolderView(path, it) },
                onFailure = { error = FriendlyError.describe(it, "Could not open the folder"); folder = FolderView(path) },
            )
        }
    }

    fun up() = browse(RemotePaths.parent(folder?.path.orEmpty()))

    fun connect() {
        busy = true
        error = null
        val secret = form.password.toCharArray()
        scope.launch {
            val result = withContext(Dispatchers.IO) { open(secret) }
            busy = false
            result?.fold(
                onSuccess = { holder.replace(it); error = null; browse("") },
                onFailure = { error = FriendlyError.describe(it, "Could not connect") },
            )
        }
    }

    /** Null means a trust prompt is now showing instead of a result. */
    private fun open(secret: CharArray): Result<ShareSession>? = when (form.protocol) {
        ShareProtocol.SMB -> ShareSession.smb(form.host, form.share, form.username, secret)
        ShareProtocol.WEBDAV -> ShareSession.webDav(form.url, form.username, secret)
        ShareProtocol.SFTP -> openSftp(secret)
    }

    private fun openSftp(secret: CharArray): Result<ShareSession>? {
        val host = form.host.trim()
        if (knownHosts.fingerprint(host, form.sftpPort) != null) {
            return ShareSession.sftp(host, form.sftpPort, form.username, secret, knownHosts)
        }
        val probe = SftpFileSystem.probeFingerprint(host, form.sftpPort)
        probe.getOrNull()?.let { pendingFingerprint = it; return null }
        return Result.failure(probe.exceptionOrNull() ?: IllegalStateException("Could not reach $host"))
    }

    fun trustAndConnect() {
        val fingerprint = pendingFingerprint ?: return
        knownHosts.trust(form.host.trim(), form.sftpPort, fingerprint)
        pendingFingerprint = null
        connect()
    }

    fun play(entry: RemoteEntry) {
        val session = holder.current ?: return
        scope.launch {
            withContext(Dispatchers.IO) { session.playUrl(entry.path) }.fold(
                onSuccess = { onOpenUrl(it, entry.name); onDismiss() },
                onFailure = { error = FriendlyError.describe(it, "Could not start playback") },
            )
        }
    }

    fun disconnect() {
        holder.replace(null)
        folder = null
    }

    fun cancelOrClose() {
        if (pendingFingerprint != null) pendingFingerprint = null else onDismiss()
    }
}

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
    val knownHosts = remember { KnownHosts(DesktopDirectories.resolve().data.resolve("known_hosts").toFile()) }
    val state = remember { ShareDialogState(scope, holder, knownHosts, onOpenUrl, onDismiss) }
    LaunchedEffect(Unit) { state.openRootIfConnected() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(state.title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            }
        },
        text = { DialogBody(state) },
        confirmButton = { PrimaryAction(state) },
        dismissButton = {
            TextButton(onClick = state::cancelOrClose) { Text(if (state.pendingFingerprint != null) "Cancel" else "Close") }
        },
    )
}

@Composable
private fun DialogBody(state: ShareDialogState) {
    val fingerprint = state.pendingFingerprint
    val folder = state.folder
    when {
        fingerprint != null -> TrustPrompt(state.form.host, fingerprint)
        folder != null -> BrowserPane(folder, state.error, state::up, state::browse, state::play)
        else -> ConnectPane(state.form, state.busy, state.error)
    }
}

@Composable
private fun PrimaryAction(state: ShareDialogState) {
    when {
        state.pendingFingerprint != null -> Button(onClick = state::trustAndConnect) { Text("Trust this server and connect") }
        state.folder != null -> TextButton(onClick = state::disconnect) { Text("Disconnect") }
        else -> Button(onClick = state::connect, enabled = state.form.isComplete() && !state.busy) {
            Text(if (state.busy) "Connecting…" else "Connect")
        }
    }
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
        AddressFields(form)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(if (form.protocol == ShareProtocol.SMB) "User (blank = guest)" else "User", form.username, Modifier.weight(1f)) { form.username = it }
            Field("Password", form.password, Modifier.weight(1f), password = true) { form.password = it }
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
private fun AddressFields(form: ConnectForm) {
    if (form.protocol == ShareProtocol.WEBDAV) {
        Field("Address, e.g. https://nas.local/dav", form.url) { form.url = it }
        return
    }
    Field("Server name or IP, e.g. nas.local", form.host) { form.host = it }
    if (form.protocol == ShareProtocol.SMB) Field("Share name, e.g. Movies", form.share) { form.share = it }
    else Field("Port (22)", form.port) { form.port = it.filter(Char::isDigit).take(5) }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), password: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
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
        Text(fingerprint, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
    }
}

@Composable
private fun BrowserPane(folder: FolderView, error: String?, onUp: () -> Unit, onOpen: (String) -> Unit, onPlay: (RemoteEntry) -> Unit) {
    Column(modifier = Modifier.width(460.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onUp, enabled = folder.path.isNotEmpty() && !folder.loading) {
                Icon(Icons.Outlined.ArrowUpward, contentDescription = "Up one folder")
            }
            Text("/" + folder.path, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
            if (folder.loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        error?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        LazyColumn(Modifier.heightIn(max = 340.dp).fillMaxWidth()) {
            items(folder.entries, key = { it.path }) { entry -> EntryRow(entry, onOpen, onPlay) }
        }
        if (!folder.loading && folder.entries.isEmpty()) {
            Text("This folder is empty.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun EntryRow(entry: RemoteEntry, onOpen: (String) -> Unit, onPlay: (RemoteEntry) -> Unit) {
    val playable = !entry.isDirectory && CastMime.isMedia(entry.name)
    val usable = entry.isDirectory || playable
    val color = if (usable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val icon = when {
        entry.isDirectory -> Icons.Outlined.Folder
        playable -> Icons.Outlined.PlayCircle
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = usable) { if (entry.isDirectory) onOpen(entry.path) else onPlay(entry) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(12.dp))
        Text(
            entry.name,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
            color = if (usable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
    }
}
