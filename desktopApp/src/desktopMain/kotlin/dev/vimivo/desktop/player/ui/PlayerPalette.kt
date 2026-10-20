package dev.vimivo.desktop.player.ui

import androidx.compose.ui.graphics.Color

/** The player's own palette: a dark, VLC-like control surface in Vimivo's colours (spec §2.1, §3). */
internal object PlayerPalette {
    val bar = Color(0xEB141416) // #141416 at 92 %
    val hairline = Color.White.copy(alpha = 0.06f)
    val content = Color.White.copy(alpha = 0.85f)
    val contentMuted = Color.White.copy(alpha = 0.50f)
    val hover = Color.White.copy(alpha = 0.08f)
    val trackPlayed = Color.White
    val trackBuffered = Color.White.copy(alpha = 0.35f)
    val trackRemaining = Color.White.copy(alpha = 0.18f)
    val boost = Color(0xFFFFB300) // amber: volume above 100 %
    val abRange = Color(0xFF64D2FF).copy(alpha = 0.45f)
    val glass = Color(0xB81C1C1E) // #1C1C1E at 72 %
    val glassBorder = Color.White.copy(alpha = 0.08f)
}
