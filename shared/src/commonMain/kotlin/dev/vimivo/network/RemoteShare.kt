package dev.vimivo.network

/** One file or folder on a remote share. [path] is relative to the share root, slash separated, no leading slash. */
data class RemoteEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long? = null,
)

/** How a share is reached. The password is only ever held in memory for the session. */
enum class ShareProtocol(val label: String, val defaultPort: Int) {
    SMB("SMB (Windows share, NAS)", 445),
    SFTP("SFTP (SSH)", 22),
    WEBDAV("WebDAV", 443),
}

object RemotePaths {
    /** "a/b" + "c" = "a/b/c"; empty parent gives just the child. */
    fun child(parent: String, name: String): String = if (parent.isEmpty()) name else "${parent.trimEnd('/')}/$name"

    /** "a/b/c" -> "a/b"; "a" -> ""; "" -> "". */
    fun parent(path: String): String = path.trimEnd('/').substringBeforeLast('/', "")

    fun name(path: String): String = path.trimEnd('/').substringAfterLast('/')

    /** Folders first, then names without regard to case: the order people expect in a file browser. */
    fun sorted(entries: List<RemoteEntry>): List<RemoteEntry> =
        entries.sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.thenBy { it.name.lowercase() })

    /** Plain http is fine on a home network, never across the internet. */
    fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home.arpa")) return true
        val parts = h.split('.')
        if (parts.size != 4) return false
        val n = parts.map { it.toIntOrNull() ?: return false }
        if (n.any { it !in 0..255 }) return false
        return n[0] == 10 || n[0] == 127 || (n[0] == 192 && n[1] == 168) || (n[0] == 172 && n[1] in 16..31) ||
            (n[0] == 169 && n[1] == 254)
    }
}

/** Reads a WebDAV `207 Multi-Status` reply to a PROPFIND. Namespace prefixes vary between servers, so they are ignored. */
object WebDavMultistatus {
    private val responseBlock = Regex("<(?:\\w+:)?response[\\s>].*?</(?:\\w+:)?response>", RegexOption.DOT_MATCHES_ALL)

    /**
     * Returns the children of the folder that was asked for ([requestPath], URL path of the request). The folder's own
     * entry is left out. Each entry's [RemoteEntry.path] is its URL path relative to [rootPath].
     */
    fun parse(xml: String, requestPath: String, rootPath: String): List<RemoteEntry> {
        val self = requestPath.trimEnd('/')
        val root = rootPath.trimEnd('/')
        val entries = responseBlock.findAll(xml).mapNotNull { match ->
            val block = match.value
            val href = tag(block, "href") ?: return@mapNotNull null
            val decoded = decode(hrefPath(href)).trimEnd('/')
            if (decoded == self || !decoded.startsWith("$root/")) return@mapNotNull null
            val relative = decoded.removePrefix("$root/")
            val isDirectory = Regex("<(?:\\w+:)?collection\\s*/?>").containsMatchIn(block)
            RemoteEntry(
                name = relative.substringAfterLast('/'),
                path = relative,
                isDirectory = isDirectory,
                sizeBytes = tag(block, "getcontentlength")?.trim()?.toLongOrNull(),
            )
        }.toList()
        return RemotePaths.sorted(entries)
    }

    private fun tag(block: String, name: String): String? =
        Regex("<(?:\\w+:)?$name>(.*?)</(?:\\w+:)?$name>", RegexOption.DOT_MATCHES_ALL).find(block)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** The path part of an `href`, which servers send as either a full URL or an absolute path. */
    private fun hrefPath(href: String): String {
        val schemeEnd = href.indexOf("://")
        if (schemeEnd < 0) return href
        val pathStart = href.indexOf('/', schemeEnd + 3)
        return if (pathStart < 0) "/" else href.substring(pathStart)
    }

    /** Percent-decoding as UTF-8; a malformed escape is kept as typed. */
    private fun decode(path: String): String {
        val out = StringBuilder()
        val pending = ArrayList<Byte>()
        fun flush() {
            if (pending.isNotEmpty()) {
                out.append(pending.toByteArray().decodeToString())
                pending.clear()
            }
        }
        var i = 0
        while (i < path.length) {
            val escaped = if (path[i] == '%' && i + 2 < path.length + 0) path.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (escaped != null) {
                pending.add(escaped.toByte())
                i += 3
            } else {
                flush()
                out.append(path[i])
                i++
            }
        }
        flush()
        return out.toString()
    }
}
