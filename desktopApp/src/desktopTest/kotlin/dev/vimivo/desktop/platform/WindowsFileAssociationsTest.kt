package dev.vimivo.desktop.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowsFileAssociationsTest {
    private val exe = "C:\\Users\\a\\AppData\\Local\\Vimivo\\Vimivo.exe"
    private val plan = WindowsFileAssociations.plan(exe, DesktopMediaTypes.all)

    @Test
    fun everyExtensionIsOfferedThroughItsFamilyProgId() {
        DesktopMediaTypes.all.forEach { type ->
            val key = "Software\\Classes\\.${type.extension}\\OpenWithProgids"
            assertTrue(
                plan.any { it.key == key && it.name == WindowsFileAssociations.progId(type.family) },
                type.extension
            )
            assertTrue(
                plan.any { it.key.endsWith("FileAssociations") && it.name == ".${type.extension}" },
                type.extension
            )
        }
    }

    @Test
    fun progIdsOpenTheExecutableWithTheFileAndAppearInDefaultApps() {
        val command = plan.single { it.key == "Software\\Classes\\Vimivo.Video\\shell\\open\\command" }
        assertEquals("\"$exe\" \"%1\"", command.data)
        assertEquals(
            setOf("Vimivo.Video", "Vimivo.Audio", "Vimivo.Playlist", "Vimivo.Subtitle"),
            MediaFamily.entries.map(WindowsFileAssociations::progId).toSet()
        )
        val registered = plan.single { it.key == "Software\\RegisteredApplications" }
        assertEquals("Software\\Vimivo\\Capabilities", registered.data)
    }

    @Test
    fun everythingStaysUnderTheCurrentUserHive() {
        assertTrue(plan.all { it.key.startsWith("Software\\") }, "only HKCU\\Software paths, never HKLM")
    }
}
