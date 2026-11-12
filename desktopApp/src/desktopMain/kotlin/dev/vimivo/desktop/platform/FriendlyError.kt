package dev.vimivo.desktop.platform

import dev.vimivo.network.ShareException
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException
import java.sql.SQLException
import javax.net.ssl.SSLException

/**
 * Turns an exception into a sentence a person can act on. Technical detail stays in the log; the screen says what
 * happened and what to try. Unknown problems keep their own message rather than a vague "something went wrong".
 */
internal object FriendlyError {
    fun describe(error: Throwable, fallback: String = "Something went wrong."): String {
        val root = rootCause(error)
        return networkMessage(root) ?: fileMessage(root) ?: when {
            root is ShareException -> root.message ?: fallback
            root is SQLException -> "The library database had a problem. Restarting Vimivo usually fixes it; your files are not affected."
            root is OutOfMemoryError -> "Vimivo ran out of memory. Close other programs or try a smaller file."
            else -> root.message?.takeIf { it.isNotBlank() } ?: fallback
        }
    }

    private fun networkMessage(error: Throwable): String? = when (error) {
        is UnknownHostException -> "Could not find that server. Check the name and your network connection."
        is ConnectException -> "The server did not answer. Check that it is on and that the address and port are right."
        is SocketTimeoutException -> "The server took too long to answer. Try again, or check your network."
        is SSLException -> "The secure connection failed. The server's certificate may be invalid or out of date."
        else -> null
    }

    private fun fileMessage(error: Throwable): String? = when {
        error is NoSuchFileException || error is FileNotFoundException ->
            "That file or folder is gone. It may have been moved, renamed, or its drive unplugged."
        error is AccessDeniedException ->
            "Vimivo is not allowed to read that. Check its permissions (on macOS: System Settings, Privacy & Security, Files and Folders)."
        error.message?.contains("No space left", ignoreCase = true) == true -> "The disk is full. Free some space and try again."
        else -> null
    }

    private fun rootCause(error: Throwable): Throwable {
        var current = error
        var depth = 0
        while (current.cause != null && current.cause !== current && depth < MAX_CAUSE_DEPTH) {
            current = current.cause!!
            depth++
        }
        return current
    }

    private const val MAX_CAUSE_DEPTH = 10
}
