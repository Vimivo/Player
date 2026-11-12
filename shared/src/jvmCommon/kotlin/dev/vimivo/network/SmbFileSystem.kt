package dev.vimivo.network

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet

/** A Windows / Samba share over SMB2/3 (pure Java client; no GPL code). Empty [user] connects as a guest. */
class SmbFileSystem private constructor(
    private val client: SMBClient,
    private val connection: Connection,
    private val session: Session,
    private val share: DiskShare,
) : RemoteFileSystem {

    override fun list(path: String): List<RemoteEntry> = try {
        val entries = share.list(path.toSmb()).filter { it.fileName != "." && it.fileName != ".." }.map { info ->
            val directory = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
            RemoteEntry(info.fileName, RemotePaths.child(path, info.fileName), directory, if (directory) null else info.endOfFile)
        }
        RemotePaths.sorted(entries)
    } catch (e: com.hierynomus.mssmb2.SMBApiException) {
        throw ShareException("The share refused to list this folder (${e.statusCode})", e)
    }

    override fun length(path: String): Long = try {
        share.getFileInformation(path.toSmb()).standardInformation.endOfFile
    } catch (e: com.hierynomus.mssmb2.SMBApiException) {
        throw IOException("Cannot read ${RemotePaths.name(path)} (${e.statusCode})", e)
    }

    override fun open(path: String, offset: Long): InputStream {
        val file = share.openFile(
            path.toSmb(),
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        )
        return object : InputStream() {
            private var position = offset
            private val single = ByteArray(1)

            override fun read(): Int = if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xFF

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val count = file.read(b, position, off, len)
                if (count > 0) position += count
                return count
            }

            override fun close() = file.close()
        }
    }

    override fun close() {
        runCatching { share.close() }
        runCatching { session.close() }
        runCatching { connection.close() }
        runCatching { client.close() }
    }

    private fun String.toSmb() = replace('/', '\\')

    companion object {
        fun connect(host: String, shareName: String, user: String, password: CharArray, domain: String = "", port: Int = 445): Result<SmbFileSystem> {
            val client = SMBClient()
            return try {
                val connection = client.connect(host, port)
                val auth = if (user.isBlank()) AuthenticationContext.guest() else AuthenticationContext(user, password, domain)
                val session = connection.authenticate(auth)
                val share = session.connectShare(shareName) as? DiskShare
                    ?: throw ShareException("\"$shareName\" is not a file share")
                Result.success(SmbFileSystem(client, connection, session, share))
            } catch (e: ShareException) {
                runCatching { client.close() }
                Result.failure(e)
            } catch (e: com.hierynomus.mssmb2.SMBApiException) {
                runCatching { client.close() }
                Result.failure(ShareException(smbMessage(e), e))
            } catch (e: IOException) {
                runCatching { client.close() }
                Result.failure(ShareException("Could not reach $host: ${e.message}", e))
            } catch (e: com.hierynomus.protocol.transport.TransportException) {
                runCatching { client.close() }
                Result.failure(ShareException("Could not reach $host: ${e.message}", e))
            } finally {
                password.fill('\u0000')
            }
        }

        private fun smbMessage(e: com.hierynomus.mssmb2.SMBApiException): String = when (e.statusCode) {
            com.hierynomus.mserref.NtStatus.STATUS_LOGON_FAILURE.value -> "Wrong user name or password"
            com.hierynomus.mserref.NtStatus.STATUS_BAD_NETWORK_NAME.value -> "That share name does not exist on the server"
            com.hierynomus.mserref.NtStatus.STATUS_ACCESS_DENIED.value -> "The server denied access"
            else -> "The server refused the connection (${e.statusCode})"
        }
    }
}
