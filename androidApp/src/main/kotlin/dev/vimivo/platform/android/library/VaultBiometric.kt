package dev.vimivo.platform.android.library

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/**
 * Fingerprint or face unlock for the private vault, using the platform prompt (Android 10+). The PIN always stays
 * available; a failed or cancelled prompt just leaves the PIN field in front of the user.
 */
object VaultBiometric {
    fun isAvailable(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val manager = activity.getSystemService(BiometricManager::class.java) ?: return false
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(activity: Activity, onSuccess: () -> Unit) {
        if (!isAvailable(activity)) return
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle("Unlock private vault")
            .setNegativeButton("Use PIN", activity.mainExecutor) { _, _ -> }
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            },
        )
    }
}
