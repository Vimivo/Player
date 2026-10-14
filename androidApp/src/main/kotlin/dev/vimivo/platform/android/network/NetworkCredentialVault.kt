package dev.vimivo.platform.android.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts remote-source passwords with a non-exportable Android Keystore key. */
class NetworkCredentialVault(context: Context) {
    private val preferences = context.getSharedPreferences("vimivo_network_credentials", Context.MODE_PRIVATE)

    fun save(locationId: String, password: CharArray) {
        require(locationId.isNotBlank())
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val encrypted = cipher.doFinal(password.concatToString().toByteArray(Charsets.UTF_8))
        val bytes = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size)
            .put(cipher.iv.size.toByte()).put(cipher.iv).put(encrypted).array()
        preferences.edit().putString(locationId, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
        password.fill('\u0000')
    }

    fun password(locationId: String): CharArray? = runCatching {
        val bytes = Base64.decode(preferences.getString(locationId, null), Base64.NO_WRAP)
        val ivSize = bytes.first().toInt()
        require(ivSize in 12..16)
        val iv = bytes.copyOfRange(1, 1 + ivSize)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }
        cipher.doFinal(bytes.copyOfRange(1 + ivSize, bytes.size)).toString(Charsets.UTF_8).toCharArray()
    }.getOrNull()

    fun remove(locationId: String) { preferences.edit().remove(locationId).apply() }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "vimivo.remote.credentials.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
    }
}
