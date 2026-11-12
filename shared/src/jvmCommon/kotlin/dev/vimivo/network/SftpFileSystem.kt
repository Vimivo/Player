package dev.vimivo.network

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

/** Servers whose SSH key the user has accepted, one `host:port SHA256:...` line each. */
class KnownHosts(private val file: File) {
    fun fingerprint(host: String, port: Int): String? = lines().firstOrNull { it.first == "$host:$port" }?.second

    fun trust(host: String, port: Int, fingerprint: String) {
        val kept = lines().filterNot { it.first == "$host:$port" }
        file.parentFile?.mkdirs()
        file.writeText((kept + ("$host:$port" to fingerprint)).joinToString("\n") { "${it.first} ${it.second}" } + "\n")
    }

    private fun lines(): List<Pair<String, String>> = if (!file.isFile) emptyList() else file.readLines()
        .mapNotNull { line -> line.split(' ').takeIf { it.size == 2 }?.let { it[0] to it[1] } }
}

/** An SFTP server. The server's key must already be trusted ([KnownHosts]); [probeFingerprint] fetches it for the user to confirm. */
class SftpFileSystem private constructor(
    private val ssh: SSHClient,
    private val sftp: SFTPClient,
) : RemoteFileSystem {

    override fun list(path: String): List<RemoteEntry> = try {
        val entries = sftp.ls(path.ifEmpty { "." }).filter { it.name != "." && it.name != ".." }.map {
            RemoteEntry(it.name, RemotePaths.child(path, it.name), it.isDirectory, if (it.isDirectory) null else it.attributes.size)
        }
        RemotePaths.sorted(entries)
    } catch (e: IOException) {
        throw ShareException("Could not list this folder: ${e.message}", e)
    }

    override fun length(path: String): Long = sftp.size(path)

    override fun open(path: String, offset: Long): InputStream {
        val remote = sftp.open(path)
        val stream = remote.RemoteFileInputStream(offset)
        return object : InputStream() {
            override fun read(): Int = stream.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = stream.read(b, off, len)
            override fun close() {
                runCatching { stream.close() }
                runCatching { remote.close() }
            }
        }
    }

    override fun close() {
        runCatching { sftp.close() }
        runCatching { ssh.disconnect() }
    }

    companion object {
        /** `SHA256:...` of a server key, the form `ssh-keygen -l` prints. */
        fun fingerprintOf(key: PublicKey): String {
            val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob))
        }

        /** Connects only to learn the server's key fingerprint, then hangs up. Nothing is trusted yet. */
        fun probeFingerprint(host: String, port: Int = 22): Result<String> {
            var seen: String? = null
            val ssh = SSHClient()
            ssh.addHostKeyVerifier(object : HostKeyVerifier {
                override fun verify(hostname: String, p: Int, key: PublicKey): Boolean {
                    seen = fingerprintOf(key)
                    return false
                }
                override fun findExistingAlgorithms(hostname: String, p: Int): List<String> = emptyList()
            })
            return try {
                ssh.connect(host, port)
                Result.failure(ShareException("The server did not present a key"))
            } catch (e: IOException) {
                seen?.let { Result.success(it) } ?: Result.failure(ShareException("Could not reach $host: ${e.message}", e))
            } finally {
                runCatching { ssh.disconnect() }
            }
        }

        fun connect(host: String, port: Int, user: String, password: CharArray, knownHosts: KnownHosts): Result<SftpFileSystem> {
            val trusted = knownHosts.fingerprint(host, port)
                ?: return Result.failure(ShareException("This server has not been trusted yet"))
            val ssh = SSHClient()
            ssh.addHostKeyVerifier(object : HostKeyVerifier {
                override fun verify(hostname: String, p: Int, key: PublicKey): Boolean = fingerprintOf(key) == trusted
                override fun findExistingAlgorithms(hostname: String, p: Int): List<String> = emptyList()
            })
            return try {
                ssh.connect(host, port)
                ssh.authPassword(user, password)
                Result.success(SftpFileSystem(ssh, ssh.newSFTPClient()))
            } catch (e: net.schmizz.sshj.userauth.UserAuthException) {
                runCatching { ssh.disconnect() }
                Result.failure(ShareException("Wrong user name or password", e))
            } catch (e: net.schmizz.sshj.transport.TransportException) {
                runCatching { ssh.disconnect() }
                Result.failure(ShareException("The server's key does not match the one you trusted. If it was reinstalled, remove the old trust and connect again.", e))
            } catch (e: IOException) {
                runCatching { ssh.disconnect() }
                Result.failure(ShareException("Could not reach $host: ${e.message}", e))
            } finally {
                password.fill('\u0000')
            }
        }
    }
}
