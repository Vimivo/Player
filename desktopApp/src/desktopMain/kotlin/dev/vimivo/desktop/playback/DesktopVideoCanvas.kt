package dev.vimivo.desktop.playback

import java.awt.Canvas
import java.awt.Graphics

/**
 * The native surface libmpv draws into. libmpv owns every pixel, so AWT never paints it: no background clears on
 * expose, resize or show. The black behind the video comes from the hosting SwingPanel.
 */
internal class DesktopVideoCanvas : Canvas() {
    override fun update(graphics: Graphics) = Unit

    override fun paint(graphics: Graphics) = Unit
}
