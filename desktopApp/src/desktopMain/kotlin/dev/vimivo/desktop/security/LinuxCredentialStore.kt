@file:Suppress("FunctionNaming", "LongParameterList", "MagicNumber", "TooGenericExceptionCaught")

package dev.vimivo.desktop.security

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference

/**
 * Linux Secret Service credential storage using libsecret C APIs.
 *
 * Persists credentials to GNOME Keyring / KDE KWallet secret-service providers.
 * If libsecret is unavailable or running headless, falls back to MemoryCredentialStore
 * so that no credentials are ever written to plaintext storage.
 */
internal class LinuxCredentialStore(
    private val schemaName: String = "dev.vimivo.desktop.Password",
    private val fallback: SecureCredentialStore = MemoryCredentialStore(),
) : SecureCredentialStore {

    internal interface LibSecret : Library {
        @Structure.FieldOrder("name", "flags", "attributes", "reserved")
        class SecretSchema : Structure {
            @JvmField var name: String? = null

            @JvmField var flags: Int = 0

            @JvmField var attributes: Array<SecretSchemaAttribute> = Array(32) { SecretSchemaAttribute() }

            @JvmField var reserved: Pointer? = null

            constructor() : super()
        }

        @Structure.FieldOrder("name", "type")
        class SecretSchemaAttribute : Structure {
            @JvmField var name: String? = null

            @JvmField var type: Int = 0

            constructor() : super()
        }

        fun secret_password_store_sync(
            schema: SecretSchema,
            collection: String?,
            label: String,
            password: String,
            cancellable: Pointer?,
            error: PointerByReference?,
            vararg attributes: Any?,
        ): Boolean

        fun secret_password_lookup_sync(
            schema: SecretSchema,
            cancellable: Pointer?,
            error: PointerByReference?,
            vararg attributes: Any?,
        ): Pointer?

        fun secret_password_clear_sync(
            schema: SecretSchema,
            cancellable: Pointer?,
            error: PointerByReference?,
            vararg attributes: Any?,
        ): Boolean

        fun secret_password_free(password: Pointer)
    }

    private val libsecret: LibSecret? by lazy {
        val names = listOf("secret-1", "libsecret-1.so.0", "libsecret-1.so")
        names.asSequence()
            .mapNotNull { name -> runCatching { Native.load(name, LibSecret::class.java) }.getOrNull() }
            .firstOrNull()
    }

    private val schema: LibSecret.SecretSchema by lazy {
        LibSecret.SecretSchema().apply {
            name = schemaName
            flags = 0
            attributes[0].name = "account"
            attributes[0].type = 0
        }
    }

    override fun saveSecret(key: String, secret: CharArray): Result<Unit> {
        val api = libsecret ?: return fallback.saveSecret(key, secret)
        return runCatching {
            require(key.isNotBlank()) { "Key must not be blank" }
            val password = secret.concatToString()
            val success = api.secret_password_store_sync(
                schema,
                null,
                "Vimivo: $key",
                password,
                null,
                null,
                "account",
                key,
                null,
            )
            check(success) { "secret_password_store_sync failed" }
        }
    }

    override fun getSecret(key: String): Result<CharArray?> {
        val api = libsecret ?: return fallback.getSecret(key)
        return runCatching {
            require(key.isNotBlank()) { "Key must not be blank" }
            val ptr = api.secret_password_lookup_sync(
                schema,
                null,
                null,
                "account",
                key,
                null,
            ) ?: return@runCatching null
            try {
                ptr.getString(0).toCharArray()
            } finally {
                api.secret_password_free(ptr)
            }
        }
    }

    override fun deleteSecret(key: String): Result<Unit> {
        val api = libsecret ?: return fallback.deleteSecret(key)
        return runCatching {
            require(key.isNotBlank()) { "Key must not be blank" }
            api.secret_password_clear_sync(
                schema,
                null,
                null,
                "account",
                key,
                null,
            )
            Unit
        }
    }

    override fun close() {
        fallback.close()
    }
}
