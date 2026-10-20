package dev.vimivo.desktop.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/**
 * True while the pointer has moved over [surface] within the last [idleMillis], or while [hold] (controls hovered,
 * playback paused). Player controls use it to hide over the video and come back on mouse movement.
 */
@Composable
internal fun rememberPointerAwake(surface: Component, hold: Boolean, idleMillis: Long = DEFAULT_IDLE_MILLIS): Boolean {
    var lastMove by remember { mutableLongStateOf(System.nanoTime()) }
    var awake by remember { mutableStateOf(true) }
    DisposableEffect(surface) {
        val listener = object : MouseAdapter() {
            override fun mouseMoved(event: MouseEvent) {
                lastMove = System.nanoTime()
                awake = true
            }
        }
        surface.addMouseMotionListener(listener)
        onDispose { surface.removeMouseMotionListener(listener) }
    }
    LaunchedEffect(lastMove, hold) {
        awake = true
        if (hold) return@LaunchedEffect
        delay(idleMillis)
        awake = false
    }
    return awake
}

private const val DEFAULT_IDLE_MILLIS = 2_000L
