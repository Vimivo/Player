package dev.vimivo.platform.android.playback

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.vimivo.app.R

/** A resizable system widget that controls the one authoritative Media3 session. */
class VimivoPlaybackWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context, manager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in widgetActions) return
        connect(context) { controller ->
            when (intent.action) {
                ACTION_PLAY_PAUSE -> if (controller.isPlaying) controller.pause() else controller.play()
                ACTION_NEXT -> controller.seekToNextMediaItem()
                ACTION_PREVIOUS -> controller.seekToPreviousMediaItem()
            }
            refresh(context)
        }
    }

    private fun refresh(
        context: Context,
        manager: AppWidgetManager = AppWidgetManager.getInstance(context),
        ids: IntArray = manager.getAppWidgetIds(ComponentName(context, VimivoPlaybackWidget::class.java)),
    ) {
        connect(context) { controller ->
            val media = controller.currentMediaItem
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_playback).apply {
                    setTextViewText(
                        R.id.widget_title,
                        media?.mediaMetadata?.title ?: context.getString(R.string.widget_nothing_playing),
                    )
                    setTextViewText(
                        R.id.widget_artist,
                        media?.mediaMetadata?.artist ?: context.getString(R.string.app_name),
                    )
                    setTextViewText(
                        R.id.widget_play_pause,
                        context.getString(if (controller.isPlaying) R.string.pause else R.string.play),
                    )
                    setOnClickPendingIntent(R.id.widget_previous, actionIntent(context, ACTION_PREVIOUS))
                    setOnClickPendingIntent(R.id.widget_play_pause, actionIntent(context, ACTION_PLAY_PAUSE))
                    setOnClickPendingIntent(R.id.widget_next, actionIntent(context, ACTION_NEXT))
                    setOnClickPendingIntent(R.id.widget_root, launchIntent(context))
                }
                manager.updateAppWidget(id, views)
            }
        }
    }

    private fun connect(context: Context, block: (MediaController) -> Unit) {
        val token = SessionToken(context, ComponentName(context, VimivoPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val controller = runCatching(future::get).getOrNull() ?: return@addListener
                try {
                    block(controller)
                } finally {
                    controller.release()
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun actionIntent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, VimivoPlaybackWidget::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun launchIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val ACTION_PLAY_PAUSE = "dev.vimivo.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "dev.vimivo.widget.NEXT"
        const val ACTION_PREVIOUS = "dev.vimivo.widget.PREVIOUS"
        val widgetActions = setOf(ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREVIOUS)
    }
}
