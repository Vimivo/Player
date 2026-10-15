@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength")

package dev.vimivo.desktop.library

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopDatabaseTest {
    @Test
    fun initializesAndReopensWithoutDataLoss() {
        val directory = Files.createTempDirectory("vimivo-db-test")
        val path = directory.resolve("vimivo.sqlite")
        DesktopDatabase.open(path).use { database ->
            database.transaction { connection ->
                connection.prepareStatement("INSERT INTO settings(key, value) VALUES (?, ?)").use { statement ->
                    statement.setString(1, "theme")
                    statement.setString(2, "dark")
                    statement.executeUpdate()
                }
            }
        }
        DesktopDatabase.open(path).use { database ->
            database.transaction { connection ->
                connection.createStatement().executeQuery("SELECT value FROM settings WHERE key = 'theme'").use { result ->
                    result.next()
                    assertEquals("dark", result.getString(1))
                }
                connection.createStatement().executeQuery("SELECT version FROM schema_version").use { result ->
                    result.next()
                    assertEquals(4, result.getInt(1))
                }
            }
        }
    }
}
