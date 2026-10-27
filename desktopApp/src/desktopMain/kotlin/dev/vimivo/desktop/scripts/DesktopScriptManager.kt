package dev.vimivo.desktop.scripts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vimivo.desktop.platform.DesktopPlatform
import java.awt.Desktop
import java.io.File

internal object DesktopScriptManager {

    fun getScriptsDirectory(): File {
        val baseDir = dev.vimivo.desktop.platform.DesktopDirectories.resolve().data.resolve("scripts").toFile()
        if (!baseDir.exists()) {
            baseDir.mkdirs()
            val readme = File(baseDir, "README.txt")
            if (!readme.exists()) {
                readme.writeText(
                    """
                    Vimivo User Scripts Directory
                    =============================
                    You can place custom mpv / Lua or JavaScript user scripts in this directory.
                    Any '.lua' or '.js' file placed here can be loaded into the player engine.

                    Example scripts:
                    - custom-osc.lua
                    - auto-crop.lua
                    - sponsor-skip.lua
                    """.trimIndent()
                )
            }
        }
        return baseDir
    }

    fun openScriptsDirectory() {
        runCatching {
            val dir = getScriptsDirectory()
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir)
            } else {
                ProcessBuilder("open", dir.absolutePath).start()
            }
        }
    }

    fun listScripts(): List<File> {
        val dir = getScriptsDirectory()
        return dir.listFiles()
            ?.filter { it.isFile && (it.extension.equals("lua", ignoreCase = true) || it.extension.equals("js", ignoreCase = true)) }
            .orEmpty()
    }
}

@Composable
internal fun DesktopScriptsDialog(
    onDismiss: () -> Unit,
) {
    val dir = remember { DesktopScriptManager.getScriptsDirectory() }
    val scripts = remember { DesktopScriptManager.listScripts() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("User Scripts Folder", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Extend player features with custom Lua or JavaScript user scripts.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Scripts folder location:", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            dir.absolutePath,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { DesktopScriptManager.openScriptsDirectory() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open in Finder / File Manager")
                        }
                    }
                }

                Text("Installed Scripts (${scripts.size}):", style = MaterialTheme.typography.labelLarge)

                if (scripts.isEmpty()) {
                    Text(
                        "No scripts installed yet. Drop '.lua' or '.js' files into the scripts folder above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(scripts) { script ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(script.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}
