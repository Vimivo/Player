@file:Suppress("FunctionNaming", "MagicNumber", "TooGenericExceptionCaught")

package dev.vimivo.desktop.security

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.W32APIOptions
import java.nio.charset.StandardCharsets

/**
 * Windows Credential Manager implementation using Win32 Advapi32 APIs.
 *
 * Saves, retrieves, and deletes generic credentials securely via CredWriteW,
 * CredReadW, CredDeleteW, and CredFree without plaintext disk persistence.
 */
internal class WindowsCredentialStore(
    private val prefix: String = "Vimivo:",
    private val fallback: SecureCredentialStore = MemoryCredentialStore(),
) : SecureCredentialStore {

    internal interface Advapi32 : Library {
        @Structure.FieldOrder(
            "flags", "type", "targetName", "comment", "lastWritten",
            "credentialBlobSize", "credentialBlob", "persist",
            "attributeCount", "attributes", "targetAlias", "userName",
        )
        class CredentialW : Structure {
            @JvmField var flags: Int = 0

            @JvmField var type: Int = CRED_TYPE_GENERIC

            @JvmField var targetName: WString? = null

            @JvmField var comment: WString? = null

            @JvmField var lastWritten: Long = 0L

            @JvmField var credentialBlobSize: Int = 0

            @JvmField var credentialBlob: Pointer? = null

            @JvmField var persist: Int = CRED_PERSIST_LOCAL_MACHINE

            @JvmField var attributeCount: Int = 0

            @JvmField var attributes: Pointer? = null

            @JvmField var targetAlias: WString? = null

            @JvmField var userName: WString? = null

            constructor() : super()
            constructor(p: Pointer) : super(p) { read() }
        }

        fun CredWriteW(credential: CredentialW, flags: Int): Boolean
        fun CredReadW(targetName: WString, type: Int, flags: Int, credentialPtr: PointerByReference): Boolean
        fun CredDeleteW(targetName: WString, type: Int, flags: Int): Boolean
        fun CredFree(buffer: Pointer)

        companion object {
            const val CRED_TYPE_GENERIC = 1
            const val CRED_PERSIST_LOCAL_MACHINE = 2
        }
    }

    private val advapi32: Advapi32? by lazy {
        runCatching {
            Native.load("Advapi32", Advapi32::class.java, W32APIOptions.UNICODE_OPTIONS)
        }.getOrNull()
    }

    override fun saveSecret(key: String, secret: CharArray): Result<Unit> {
        val api = advapi32 ?: return fallback.saveSecret(key, secret)
        return runCatching {
            require(key.isNotBlank()) { "Key must not be blank" }
            val targetName = WString(prefix + key)
            val secretBytes = StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(secret))
            val byteCount = secretBytes.remaining()
            val mem = Memory(byteCount.toLong())
            try {
                val arr = ByteArray(byteCount)
                secretBytes.get(arr)
                mem.write(0, arr, 0, byteCount)
                arr.fill(0)

                val cred = Advapi32.CredentialW().apply {
                    this.targetName = targetName
                    this.type = Advapi32.CRED_TYPE_GENERIC
                    this.persist = Advapi32.CRED_PERSIST_LOCAL_MACHINE
                    this.credentialBlob = mem
                    this.credentialBlobSize = byteCount
                }
                val success = api.CredWriteW(cred, 0)
                check(success) { "CredWriteW failed with error ${Native.getLastError()}" }
            } finally {
                mem.clear()
            }
        }
    }

    override fun getSecret(key: String): Result<CharArray?> {
        val api = advapi32 ?: return fallback.getSecret(key)
        return runCatching {
            require(key.isNotBlank()) { "Key must not be blank" }
            val targetName = WString(prefix + key)
            val ptrRef = PointerByReference()
            val success = api.CredReadW(targetName, Advapi32.CRED_TYPE_GENERIC, 0, ptrRef)
            if (!success) {
                val error = Native.getLastError()
                if (error == ERROR_NOT_FOUND) return@runCatching null
                error("CredReadW failed with error $error")
            }
            val credPtr = ptrRef.value ?: return@runCatching null
            try {
                val cred = Advapi32.CredentialW(credPtr)
                val blobSize = cred.credentialBlobSize
                val blobPtr = cred.credentialBlob
                if (blobSize <= 0 || blobPtr == null) return@runCatching CharArray(0)
                val bytes = blobPtr.getByteArray(0, blobSize)
                val decoded = StandardCharsets.UTF_8.decode(java.nio.ByteBuffer.wrap(bytes))
                bytes.fill(0)
                val chars = CharArray(decoded.remaining())
                decoded.get(chars)
                chars
            } finally {
                api.CredFree(credPtr)
            }
        }
    }

    override fun deleteSecret(key: String): Result<Unit> {
        val api = advapi32 ?: return fallback.deleteSecret(key)
        return runCatching {
            require(key.isNotBlank()) { "Key must not be blank" }
            val targetName = WString(prefix + key)
            val success = api.CredDeleteW(targetName, Advapi32.CRED_TYPE_GENERIC, 0)
            if (!success) {
                val error = Native.getLastError()
                if (error != ERROR_NOT_FOUND) {
                    error("CredDeleteW failed with error $error")
                }
            }
        }
    }

    override fun close() {
        fallback.close()
    }

    private companion object {
        const val ERROR_NOT_FOUND = 1168
    }
}
