package dev.vimivo.desktop.playback

import dev.vimivo.desktop.platform.DesktopPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MpvLocaleTest {
    @Test
    fun numericCategoryMatchesEachCLibrary() {
        assertEquals(1, MpvLocale.lcNumeric(DesktopPlatform.LINUX))
        assertEquals(4, MpvLocale.lcNumeric(DesktopPlatform.MACOS))
        assertNull(MpvLocale.lcNumeric(DesktopPlatform.WINDOWS))
    }

    @Test
    fun resetsTheNumericLocaleToCOnThisHost() {
        val platform = DesktopPlatform.detect()
        if (MpvLocale.lcNumeric(platform) == null) return
        assertEquals("C", MpvLocale.ensureCNumeric(platform))
    }
}
