package dev.vimivo.core.util

/**
 * Privacy-first log and string sanitizer.
 * Ensures user paths, personal home directory names, query tokens, and credentials
 * are never leaked into diagnostic messages or error output.
 */
object SafeLogger {
    private val USER_PATH_REGEX = Regex("""(?i)(?:/Users/|/home/|[A-Z]:\\Users\\)([^/\\]+)[/\\]?""")
    private val EMBEDDED_CREDENTIALS_REGEX = Regex("""(?i)([a-z0-9+.-]+://)[^:@/\s]+:[^@/\s]+@""")
    private val SENSITIVE_QUERY_PARAM_REGEX = Regex(
        """(?i)([?&](?:token|auth|key|secret|password|passwd|sig|signature|""" +
            """access_token|api_key|client_secret|session_token|jwt|ticket)=)([^&#\s]+)""",
    )
    private val SENSITIVE_KEY_VALUE_REGEX = Regex(
        """(?i)\b((?:token|auth|key|secret|password|passwd|sig|signature|""" +
            """access_token|api_key|client_secret|session_token|jwt|ticket)=)([^\s&#]+)""",
    )
    private val AUTH_HEADER_REGEX = Regex("""(?i)(Bearer|Basic)\s+[A-Za-z0-9._~+/-]+=*""")

    /**
     * Redacts the username component of absolute filesystem paths across macOS, Linux, and Windows.
     * E.g., "/Users/johndoe/Music/track.mp3" -> "~/<user>/Music/track.mp3"
     */
    fun redactPath(path: String): String {
        return path.replace(USER_PATH_REGEX) { matchResult ->
            val prefix = matchResult.value
            when {
                prefix.startsWith("/Users/", ignoreCase = true) -> "~/user/"
                prefix.startsWith("/home/", ignoreCase = true) -> "~/user/"
                prefix.matches(Regex("""(?i)[A-Z]:\\Users\\[^\\]+\\?""")) -> """C:\Users\user\"""
                else -> "~/user/"
            }
        }
    }

    /**
     * Redacts sensitive query parameters (token, auth, signature, password) and embedded credentials from URLs.
     */
    fun redactUrl(url: String): String {
        return url.replace(EMBEDDED_CREDENTIALS_REGEX, "$1[REDACTED]@")
            .replace(SENSITIVE_QUERY_PARAM_REGEX, "$1[REDACTED]")
            .replace(AUTH_HEADER_REGEX, "$1 [REDACTED]")
    }

    /**
     * Sanitizes general error or diagnostic messages.
     */
    fun sanitize(message: String): String {
        return redactUrl(redactPath(message))
            .replace(SENSITIVE_KEY_VALUE_REGEX, "$1[REDACTED]")
    }
}
