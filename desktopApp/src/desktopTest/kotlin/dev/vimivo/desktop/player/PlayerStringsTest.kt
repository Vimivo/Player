package dev.vimivo.desktop.player

import dev.vimivo.desktop.resources.Res
import dev.vimivo.desktop.resources.hud_muted
import dev.vimivo.desktop.resources.hud_volume
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerStringsTest {
    private val original = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(original)

    @Test
    fun banglaAndEnglishDefineTheSameStrings() {
        val english = keys("values")
        val bangla = keys("values-bn")
        assertEquals(english, bangla, "missing in bn: ${english - bangla}, extra in bn: ${bangla - english}")
    }

    @Test
    fun resolvesBanglaWhenTheSystemLanguageIsBangla() = runBlocking {
        Locale.setDefault(Locale.forLanguageTag("bn-BD"))
        assertEquals("শব্দ বন্ধ", getString(Res.string.hud_muted))
        assertEquals("ভলিউম 135%", getString(Res.string.hud_volume, "135%"))

        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Muted", getString(Res.string.hud_muted))
        assertEquals("Volume 135%", getString(Res.string.hud_volume, "135%"))
    }

    @Test
    fun speedsDropTrailingZeros() {
        assertEquals("1", formatSpeed(1f))
        assertEquals("1.25", formatSpeed(1.25f))
    }

    private fun keys(folder: String): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/desktopMain/composeResources/$folder/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }.toSet()
    }
}
