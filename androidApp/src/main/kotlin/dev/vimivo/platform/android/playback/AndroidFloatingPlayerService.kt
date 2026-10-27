@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package dev.vimivo.platform.android.playback

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import dev.vimivo.app.R
import org.koin.android.ext.android.inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Floating popup player service that renders playback in a movable window above other applications
 * matching MX Player's signature popup window player.
 */
class AndroidFloatingPlayerService : Service() {
    private val connection: PlaybackController by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var player: Player? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        setupFloatingWindow()
    }

    private fun setupFloatingWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            POPUP_WIDTH_DP,
            POPUP_HEIGHT_DP,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        val playerView = PlayerView(this).apply {
            useController = false
        }
        root.addView(
            playerView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        // Overlay controls
        val controls = FrameLayout(this)
        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(android.graphics.Color.argb(128, 0, 0, 0))
            setOnClickListener { stopSelf() }
        }
        controls.addView(
            closeBtn,
            FrameLayout.LayoutParams(80, 80).apply {
                gravity = Gravity.TOP or Gravity.END
            },
        )

        val playBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(android.graphics.Color.argb(128, 0, 0, 0))
            setOnClickListener {
                player?.let { p ->
                    if (p.isPlaying) {
                        p.pause()
                        setImageResource(android.R.drawable.ic_media_play)
                    } else {
                        p.play()
                        setImageResource(android.R.drawable.ic_media_pause)
                    }
                }
            }
        }
        controls.addView(
            playBtn,
            FrameLayout.LayoutParams(80, 80).apply {
                gravity = Gravity.CENTER
            },
        )
        root.addView(controls)

        // Drag handling
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(root, params)
                    true
                }
                else -> false
            }
        }

        floatingView = root
        windowManager?.addView(root, params)

        // Observe player connection
        scope.launch {
            connection.controller.collect { connectedPlayer ->
                player = connectedPlayer
                playerView.player = connectedPlayer
                if (connectedPlayer?.isPlaying == true) {
                    playBtn.setImageResource(android.R.drawable.ic_media_pause)
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        floatingView?.let { windowManager?.removeView(it) }
        floatingView = null
        super.onDestroy()
    }

    companion object {
        private const val POPUP_WIDTH_DP = 640
        private const val POPUP_HEIGHT_DP = 360

        fun start(context: Context) {
            val intent = Intent(context, AndroidFloatingPlayerService::class.java)
            context.startService(intent)
        }
    }
}
