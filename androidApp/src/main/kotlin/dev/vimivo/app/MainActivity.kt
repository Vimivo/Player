package dev.vimivo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.vimivo.platform.android.playback.ui.PlaybackDevelopmentScreen
import dev.vimivo.playback.PlaybackEngineManager
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val playbackEngineManager: PlaybackEngineManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PlaybackDevelopmentScreen(playbackEngineManager) }
    }
}
