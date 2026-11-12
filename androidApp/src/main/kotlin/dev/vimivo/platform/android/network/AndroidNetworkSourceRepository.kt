@file:Suppress(
    "ArgumentListWrapping",
    "MaxLineLength",
    "MaximumLineLength",
    "MultiLineIfElse",
    "NestedBlockDepth",
    "TooGenericExceptionCaught",
    "UseCheckOrError",
    "Wrapping",
)

package dev.vimivo.platform.android.network

import android.content.Context
import android.util.Xml
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MediaSource
import dev.vimivo.network.NetworkBrowseResult
import dev.vimivo.network.NetworkLocation
import dev.vimivo.network.NetworkProtocol
import dev.vimivo.network.NetworkSourceRepository
import dev.vimivo.network.RemoteDirectoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

private val Context.remoteLocationsDataStore by preferencesDataStore("vimivo_remote_locations")

/** Android implementation deliberately persists endpoints only; credentials remain session-only. */
class AndroidNetworkSourceRepository(
    private val context: Context,
    private val credentials: NetworkCredentialVault,
) : NetworkSourceRepository {
    override suspend fun locations(): List<NetworkLocation> = context.remoteLocationsDataStore.data.first()[LOCATIONS]
        ?.decodeLocations().orEmpty()

    override suspend fun save(location: NetworkLocation): Result<Unit> = runCatching {
        validateLocation(location)
        context.remoteLocationsDataStore.edit { preferences ->
            val updated = preferences[LOCATIONS].decodeLocations().filterNot { it.id == location.id } + location
            preferences[LOCATIONS] = updated.encodeLocations()
        }
    }

    override suspend fun remove(id: String): Result<Unit> = runCatching {
        context.remoteLocationsDataStore.edit { preferences ->
            preferences[LOCATIONS] = preferences[LOCATIONS].decodeLocations().filterNot { it.id == id }.encodeLocations()
        }
        credentials.remove(id)
    }

    override suspend fun browse(location: NetworkLocation, path: String): NetworkBrowseResult = when (location.protocol) {
        NetworkProtocol.WEBDAV -> browseWebDav(location, path)
        NetworkProtocol.RTSP -> NetworkBrowseResult.Failure("RTSP is a playback URL, not a browsable file service", recoverable = false)
        NetworkProtocol.SMB -> browseSmb(location, path)
        NetworkProtocol.UPNP, NetworkProtocol.DLNA -> NetworkBrowseResult.Failure("Select a discovered media server before browsing its ContentDirectory")
        NetworkProtocol.FTP -> NetworkBrowseResult.Failure("FTP is intentionally not enabled; use SFTP, WebDAV, or SMB2/3")
        NetworkProtocol.SFTP -> browseSftp(location, path)
    }

    /** Open SMB connections, one per location and share, kept so folders open without logging in again. */
    private val smbSessions = java.util.concurrent.ConcurrentHashMap<String, dev.vimivo.network.ShareSession>()

    /** [path] is `smb://host/share[/folder]`. Each media file's link goes through the private local player proxy. */
    private suspend fun browseSmb(location: NetworkLocation, path: String): NetworkBrowseResult = withContext(Dispatchers.IO) {
        runCatching {
            val uri = URI(path)
            val host = requireNotNull(uri.host) { "The SMB address needs a server name" }
            val segments = uri.path.trim('/').split('/').filter { it.isNotEmpty() }
            val share = segments.firstOrNull() ?: throw IllegalArgumentException("Add the share name: smb://$host/ShareName")
            val folder = segments.drop(1).joinToString("/")
            val session = smbSessions[location.id + "|" + share] ?: connectSmb(location, host, share)
            session.fs.list(folder).map { entry ->
                val entryUri = "smb://$host/$share/${entry.path}"
                val media = if (entry.isDirectory || !dev.vimivo.cast.CastMime.isMedia(entry.name)) {
                    null
                } else {
                    MediaItem(MediaId(entryUri), session.playUrl(entry.path).getOrThrow(), entry.name, MediaKind.STREAM, source = MediaSource.NETWORK)
                }
                RemoteDirectoryEntry(entry.name, entryUri, entry.isDirectory, entry.sizeBytes, media = media)
            }
        }.fold(
            onSuccess = { NetworkBrowseResult.Success(it) },
            onFailure = { NetworkBrowseResult.Failure(it.message ?: "Could not browse this SMB share") },
        )
    }

    private val sftpSessions = java.util.concurrent.ConcurrentHashMap<String, dev.vimivo.network.ShareSession>()
    private val knownHosts by lazy { dev.vimivo.network.KnownHosts(java.io.File(context.filesDir, "known_hosts")) }

    override suspend fun trustHost(host: String, port: Int, fingerprint: String) {
        withContext(Dispatchers.IO) { knownHosts.trust(host, port, fingerprint) }
    }

    /**
     * [path] is `sftp://host[:port]/folder`. A server seen for the first time is not contacted with a password: the
     * person is shown its key fingerprint ([NetworkBrowseResult.NeedsTrust]) and confirms it first.
     */
    private suspend fun browseSftp(location: NetworkLocation, path: String): NetworkBrowseResult = withContext(Dispatchers.IO) {
        val uri = runCatching { URI(path) }.getOrNull()
        val host = uri?.host ?: return@withContext NetworkBrowseResult.Failure("The SFTP address needs a server name: sftp://host/folder")
        val port = if (uri.port > 0) uri.port else dev.vimivo.network.ShareProtocol.SFTP.defaultPort
        val key = "${location.id}|$host:$port"
        if (sftpSessions[key] == null && knownHosts.fingerprint(host, port) == null) {
            return@withContext dev.vimivo.network.SftpFileSystem.probeFingerprint(host, port).fold(
                onSuccess = { NetworkBrowseResult.NeedsTrust(host, port, it) },
                onFailure = { NetworkBrowseResult.Failure(it.message ?: "Could not reach $host") },
            )
        }
        runCatching {
            val session = sftpSessions[key] ?: connectSftp(location, host, port, key)
            session.fs.list(uri.path.trim('/')).map { entry ->
                val entryUri = "sftp://$host:$port/${entry.path}"
                val media = if (entry.isDirectory || !dev.vimivo.cast.CastMime.isMedia(entry.name)) {
                    null
                } else {
                    MediaItem(MediaId(entryUri), session.playUrl(entry.path).getOrThrow(), entry.name, MediaKind.STREAM, source = MediaSource.NETWORK)
                }
                RemoteDirectoryEntry(entry.name, entryUri, entry.isDirectory, entry.sizeBytes, media = media)
            }
        }.fold(
            onSuccess = { NetworkBrowseResult.Success(it) },
            onFailure = { NetworkBrowseResult.Failure(it.message ?: "Could not browse this SFTP server") },
        )
    }

    private fun connectSftp(location: NetworkLocation, host: String, port: Int, key: String): dev.vimivo.network.ShareSession {
        val user = location.username?.takeIf { it.isNotBlank() } ?: throw SecurityException("Add a user name to this location")
        val password = credentials.password(location.id) ?: throw SecurityException("Credentials have expired or are unavailable; update this location")
        val session = dev.vimivo.network.ShareSession.sftp(host, port, user, password, knownHosts).getOrThrow()
        sftpSessions[key] = session
        return session
    }

    private fun connectSmb(location: NetworkLocation, host: String, share: String): dev.vimivo.network.ShareSession {
        val password = if (location.username.isNullOrBlank()) CharArray(0) else {
            credentials.password(location.id) ?: throw SecurityException("Credentials have expired or are unavailable; update this location")
        }
        val session = dev.vimivo.network.ShareSession.smb(host, share, location.username.orEmpty(), password).getOrThrow()
        smbSessions[location.id + "|" + share] = session
        return session
    }

    private suspend fun browseWebDav(location: NetworkLocation, path: String): NetworkBrowseResult = withContext(Dispatchers.IO) {
        runCatching {
            require(path.startsWith("https://")) { "WebDAV locations must use HTTPS" }
            val connection = (URL(path).openConnection() as HttpURLConnection).apply {
                requestMethod = "PROPFIND"
                doOutput = true
                setRequestProperty("Depth", "1")
                setRequestProperty("Content-Type", "text/xml; charset=utf-8")
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                if (!location.username.isNullOrBlank()) {
                    val password = credentials.password(location.id)
                        ?: throw SecurityException("Credentials have expired or are unavailable; update this location")
                    try {
                        val token = "${location.username}:${password.concatToString()}".toByteArray(Charsets.UTF_8)
                        setRequestProperty("Authorization", "Basic ${android.util.Base64.encodeToString(token, android.util.Base64.NO_WRAP)}")
                    } finally {
                        password.fill('\u0000')
                    }
                }
            }
            connection.outputStream.use { it.write(PROPFIND_BODY) }
            val status = connection.responseCode
            when (status) {
                HTTP_MULTI_STATUS -> connection.inputStream.use { parseWebDav(path, it.readBytes()) }
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> throw SecurityException("The WebDAV server denied access")
                HttpURLConnection.HTTP_NOT_FOUND -> throw IllegalStateException("The WebDAV folder no longer exists")
                else -> throw IllegalStateException("WebDAV server returned HTTP $status")
            }
        }.fold(
            onSuccess = { NetworkBrowseResult.Success(it) },
            onFailure = { NetworkBrowseResult.Failure(it.message ?: "Could not browse this WebDAV location") },
        )
    }

    private fun parseWebDav(requestedUrl: String, xml: ByteArray): List<RemoteDirectoryEntry> {
        val parser = Xml.newPullParser().apply { setInput(xml.inputStream(), "UTF-8") }
        val entries = mutableListOf<RemoteDirectoryEntry>()
        var href: String? = null
        var collection = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) when (parser.name.substringAfter(':')) {
                "href" -> href = parser.nextText()
                "collection" -> collection = true
                "response" -> { href = null; collection = false }
            }
            if (parser.eventType == XmlPullParser.END_TAG && parser.name.substringAfter(':') == "response") {
                href?.let { raw ->
                    val uri = URI(requestedUrl).resolve(raw).toString()
                    if (uri.trimEnd('/') != requestedUrl.trimEnd('/')) {
                        val name = URI(uri).path.trimEnd('/').substringAfterLast('/').ifBlank { uri }
                        entries += RemoteDirectoryEntry(name, uri, collection, media = if (collection) null else uri.toRemoteMedia())
                    }
                }
            }
            parser.next()
        }
        return entries.sortedWith(compareByDescending<RemoteDirectoryEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private fun validateLocation(location: NetworkLocation) {
        require(location.id.isNotBlank() && location.name.isNotBlank()) { "Name and identifier are required" }
        val scheme = URI(location.endpoint).scheme?.lowercase()
        when (location.protocol) {
            NetworkProtocol.WEBDAV -> require(scheme == "https") { "WebDAV must use HTTPS" }
            NetworkProtocol.RTSP -> require(scheme in setOf("rtsp", "rtsps")) { "Use an RTSP or RTSPS URL" }
            NetworkProtocol.SMB -> require(scheme == "smb") { "Use an SMB URL" }
            else -> Unit
        }
        require(URI(location.endpoint).rawUserInfo.isNullOrBlank()) { "Do not place credentials in the location URL" }
    }

    private companion object {
        val LOCATIONS = stringPreferencesKey("remote_locations")
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 20_000
        const val HTTP_MULTI_STATUS = 207
        val PROPFIND_BODY = """<?xml version=\"1.0\"?><propfind xmlns=\"DAV:\"><prop><resourcetype/></prop></propfind>""".toByteArray()
    }
}

private fun String.toRemoteMedia() = MediaItem(MediaId(this), this, URI(this).path.substringAfterLast('/'), MediaKind.STREAM, source = MediaSource.NETWORK)
private fun String?.decodeLocations(): List<NetworkLocation> = runCatching {
    JSONArray(this ?: "[]").let { array -> List(array.length()) { index -> array.getJSONObject(index).toLocation() } }
}.getOrDefault(emptyList())
private fun List<NetworkLocation>.encodeLocations() = JSONArray(map { it.toJson() }).toString()
private fun NetworkLocation.toJson() = JSONObject().apply { put("id", id); put("name", name); put("protocol", protocol.name); put("endpoint", endpoint); put("username", username); put("favorite", favorite) }
private fun JSONObject.toLocation() = NetworkLocation(getString("id"), getString("name"), NetworkProtocol.valueOf(getString("protocol")), getString("endpoint"), optString("username").takeIf { it.isNotBlank() }, optBoolean("favorite"))
