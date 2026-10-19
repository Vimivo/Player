package dev.vimivo.desktop.playback

import java.awt.Canvas
import java.awt.Graphics

/**
 * The native surface libmpv draws into. AWT must never paint it: on X11 a background clear on repaint (expose,
 * resize, show) can land over libmpv's child window and leave the picture black until the next repaint.
 */
internal class DesktopVideoCanvas : Canvas() {
    override fun update(graphics: Graphics) = Unit

    override fun paint(graphics: Graphics) = Unit
}
