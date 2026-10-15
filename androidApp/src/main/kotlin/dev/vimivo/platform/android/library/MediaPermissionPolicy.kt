package dev.vimivo.platform.android.library

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dev.vimivo.core.model.MediaPermissionAccess
import dev.vimivo.core.model.MediaPermissionState

class MediaPermissionPolicy(private val context: Context) {
    fun current(): MediaPermissionState = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> MediaPermissionState(
            audio = access(Manifest.permission.READ_MEDIA_AUDIO),
            video = when {
                granted(Manifest.permission.READ_MEDIA_VIDEO) -> MediaPermissionAccess.FULL
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaPermissionAccess.PARTIAL
                else -> MediaPermissionAccess.DENIED
            },
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> MediaPermissionState(
            audio = access(Manifest.permission.READ_MEDIA_AUDIO),
            video = access(Manifest.permission.READ_MEDIA_VIDEO),
        )
        else -> {
            val access = access(Manifest.permission.READ_EXTERNAL_STORAGE)
            MediaPermissionState(audio = access, video = access)
        }
    }

    fun audioRequest(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun videoRequest(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasNotificationPermission(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> granted(Manifest.permission.POST_NOTIFICATIONS)
        else -> true
    }

    fun notificationRequest(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        else -> emptyArray()
    }

    private fun access(permission: String) = if (granted(permission)) {
        MediaPermissionAccess.FULL
    } else {
        MediaPermissionAccess.DENIED
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
