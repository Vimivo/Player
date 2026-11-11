package dev.vimivo.desktop.net

import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Shared hardening for the small LAN-facing servers (web remote, Wi-Fi transfer).
 *
 * Every server uses a fresh random session token that is only shown to the user in the
 * app window, binds to a single private IPv4 address rather than every interface, and
 * compares tokens in constant time.
 */
internal object LanAccess {
    private const val TOKEN_BYTES = 18
    private val random = SecureRandom()

    fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun tokenMatches(expected: String, actual: String?): Boolean {
        if (actual.isNullOrEmpty()) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            actual.toByteArray(Charsets.UTF_8),
        )
    }

    /** Parses a raw query string into decoded key/value pairs; later duplicates are ignored. */
    fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (pair in rawQuery.split('&')) {
            if (pair.isEmpty()) continue
            val idx = pair.indexOf('=')
            val key = decode(if (idx < 0) pair else pair.substring(0, idx))
            val value = if (idx < 0) "" else decode(pair.substring(idx + 1))
            result.putIfAbsent(key, value)
        }
        return result
    }

    /** The first private (RFC 1918) IPv4 address of an up, non-virtual interface, or null. */
    fun privateIpv4(): Inet4Address? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
    }.getOrNull()

    /** Escapes [value] as the contents of a JSON string literal (without the quotes). */
    fun jsonEscape(value: String): String = buildString(value.length + 8) {
        for (c in value) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
    }

    private fun decode(value: String): String =
        runCatching { java.net.URLDecoder.decode(value, Charsets.UTF_8) }.getOrDefault(value)
}
