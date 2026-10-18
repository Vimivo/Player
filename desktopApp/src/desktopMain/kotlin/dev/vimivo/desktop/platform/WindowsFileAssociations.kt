package dev.vimivo.desktop.platform

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

/** One registry value under HKEY_CURRENT_USER. An empty [name] is the key's default value. */
internal data class RegistryValue(val key: String, val name: String, val data: String)

/**
 * Per-user (HKCU, no administrator rights) "Open with" and Default Apps registration for Windows:
 * a ProgID per media family, `OpenWithProgids` for every extension, and Capabilities +
 * RegisteredApplications so Vimivo is listed in Settings › Apps › Default apps. Windows does not let
 * applications make themselves the default; [DEFAULT_APPS_URI] opens the page where the user can.
 */
internal object WindowsFileAssociations {
    const val DEFAULT_APPS_URI = "ms-settings:defaultapps"
    private const val CLASSES = "Software\\Classes"
    private const val APP_KEY = "Software\\Vimivo"
    private const val CAPABILITIES = "$APP_KEY\\Capabilities"
    private const val REGISTERED_APPLICATIONS = "Software\\RegisteredApplications"
    private const val APP_NAME = "Vimivo"

    fun progId(family: MediaFamily): String = "Vimivo." + family.name.lowercase().replaceFirstChar(Char::uppercase)

    /** Every value to write for [executable]; pure, so it is unit-tested on every OS. */
    fun plan(executable: String, types: List<MediaType>): List<RegistryValue> {
        val command = "\"$executable\" \"%1\""
        val families = types.map(MediaType::family).distinct()
        val progIds = families.flatMap { family ->
            val id = progId(family)
            listOf(
                RegistryValue("$CLASSES\\$id", "", "$APP_NAME ${family.name.lowercase()}"),
                RegistryValue("$CLASSES\\$id\\DefaultIcon", "", "\"$executable\",0"),
                RegistryValue("$CLASSES\\$id\\shell\\open\\command", "", command),
            )
        }
        val openWith = types.map { RegistryValue("$CLASSES\\.${it.extension}\\OpenWithProgids", progId(it.family), "") }
        val capabilities = listOf(
            RegistryValue(CAPABILITIES, "ApplicationName", APP_NAME),
            RegistryValue(CAPABILITIES, "ApplicationDescription", "Private music and video player"),
        ) + types.map { RegistryValue("$CAPABILITIES\\FileAssociations", ".${it.extension}", progId(it.family)) }
        val registered = RegistryValue(REGISTERED_APPLICATIONS, APP_NAME, CAPABILITIES)
        return progIds + openWith + capabilities + registered
    }

    /** Writes the plan; idempotent, so it runs on every start and repairs a moved installation. */
    fun register(executable: String, types: List<MediaType> = DesktopMediaTypes.all) {
        plan(executable, types).forEach { value ->
            Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, value.key)
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, value.key, value.name, value.data)
        }
    }

    /** Removes everything [register] wrote (the "Remove file associations" action and uninstall cleanup). */
    fun unregister(types: List<MediaType> = DesktopMediaTypes.all) {
        val user = WinReg.HKEY_CURRENT_USER
        types.forEach { type ->
            val key = "$CLASSES\\.${type.extension}\\OpenWithProgids"
            if (Advapi32Util.registryValueExists(user, key, progId(type.family))) {
                Advapi32Util.registryDeleteValue(user, key, progId(type.family))
            }
        }
        types.map(MediaType::family).distinct().map { "$CLASSES\\${progId(it)}" }
            .plus(APP_KEY)
            .filter { Advapi32Util.registryKeyExists(user, it) }
            .forEach(::deleteTree)
        if (Advapi32Util.registryValueExists(user, REGISTERED_APPLICATIONS, APP_NAME)) {
            Advapi32Util.registryDeleteValue(user, REGISTERED_APPLICATIONS, APP_NAME)
        }
    }

    /** The Win32 API only deletes empty keys, so children go first. */
    private fun deleteTree(path: String) {
        val user = WinReg.HKEY_CURRENT_USER
        Advapi32Util.registryGetKeys(user, path).forEach { child -> deleteTree("$path\\$child") }
        Advapi32Util.registryDeleteKey(user, path)
    }
}
