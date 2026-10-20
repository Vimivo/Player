package dev.vimivo.core.legal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThirdPartyNoticesTest {
    private val desktop = listOf(NoticePlatform.MACOS, NoticePlatform.WINDOWS, NoticePlatform.LINUX)

    @Test
    fun everyDesktopAppListsTheLgplMediaPackage() {
        desktop.forEach { platform ->
            val names = ThirdPartyNotices.forPlatform(platform).filter { it.mediaPackage }.map { it.name }
            assertTrue("mpv" in names && "ffmpeg" in names && "libplacebo" in names, "$platform: $names")
        }
    }

    @Test
    fun windowsAloneShipsTheDirect3dShaderCompilers() {
        assertTrue(ThirdPartyNotices.forPlatform(NoticePlatform.WINDOWS).any { it.name == "shaderc" })
        assertFalse(ThirdPartyNotices.forPlatform(NoticePlatform.MACOS).any { it.name == "shaderc" })
    }

    @Test
    fun androidShipsTheMediaPackageAndIosDoesNot() {
        val android = ThirdPartyNotices.forPlatform(NoticePlatform.ANDROID).filter { it.mediaPackage }.map { it.name }
        assertTrue("mpv" in android && "ffmpeg" in android, "$android")
        assertFalse("libbluray" in android, "$android")
        assertTrue(ThirdPartyNotices.forPlatform(NoticePlatform.IOS).none { it.mediaPackage })
        assertTrue(ThirdPartyNotices.components.none { "LibVLC" in it.name })
    }

    @Test
    fun noShippedComponentIsUsedUnderTheGplAlone() {
        // GPL may appear only as one choice of a dual licence, with a linking exception, or for the bundled JDK
        // (Classpath exception); never as the sole licence of shipped code (ADR-004).
        ThirdPartyNotices.components.forEach { component ->
            val license = component.license
            val gplOnly = "GPL" in license.replace("LGPL", "") &&
                " OR " !in license && "exception" !in license.lowercase()
            assertFalse(gplOnly, "${component.name}: $license")
        }
    }

    @Test
    fun lgplNoticeNamesTheLgplLibrariesAndTheSource() {
        val notice = assertNotNull(ThirdPartyNotices.lgplNotice(NoticePlatform.LINUX))
        assertTrue("mpv" in notice && "ffmpeg" in notice && ThirdPartyNotices.SOURCE_REPOSITORY in notice)
        assertTrue("mpv" in assertNotNull(ThirdPartyNotices.lgplNotice(NoticePlatform.ANDROID)))
        assertNull(ThirdPartyNotices.lgplNotice(NoticePlatform.IOS))
    }

    @Test
    fun everyComponentHasAVersionLicenceAndHttpsSource() {
        ThirdPartyNotices.components.forEach { component ->
            assertTrue(component.version.isNotBlank() && component.license.isNotBlank(), component.name)
            assertTrue(component.sourceUrl.startsWith("https://"), component.name)
            assertTrue(component.platforms.isNotEmpty(), component.name)
        }
        assertEquals(
            ThirdPartyNotices.components.size,
            ThirdPartyNotices.components.map { it.name to it.platforms }.toSet().size,
        )
    }
}
