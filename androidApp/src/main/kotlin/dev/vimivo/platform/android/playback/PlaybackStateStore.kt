package dev.vimivo.platform.android.playback

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.playbackDataStore by preferencesDataStore("vimivo_playback")

data class RestoredPlaybackState(
    val items: List<MediaItem>,
    val currentIndex: Int,
    val positionMillis: Long,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
)

class PlaybackStateStore(private val context: Context) {
    suspend fun save(
        items: List<MediaItem>,
        currentIndex: Int,
        positionMillis: Long,
        repeatMode: Int,
        shuffleEnabled: Boolean,
    ) {
        val root = JSONObject()
            .put("currentIndex", currentIndex)
            .put("positionMillis", positionMillis.coerceAtLeast(0))
            .put("repeatMode", repeatMode)
            .put("shuffleEnabled", shuffleEnabled)
            .put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
        context.playbackDataStore.edit { it[KEY] = root.toString() }
    }

    suspend fun restore(): RestoredPlaybackState? = runCatching {
        val raw = context.playbackDataStore.data.first()[KEY] ?: return null
        val root = JSONObject(raw)
        val array = root.getJSONArray("items")
        val items = buildList { repeat(array.length()) { add(array.getJSONObject(it).toMediaItem()) } }
        if (items.isEmpty()) return null
        RestoredPlaybackState(
            items = items,
            currentIndex = root.optInt("currentIndex").coerceIn(items.indices),
            positionMillis = root.optLong("positionMillis").coerceAtLeast(0),
            repeatMode = root.optInt("repeatMode", Player.REPEAT_MODE_OFF),
            shuffleEnabled = root.optBoolean("shuffleEnabled"),
        )
    }.getOrNull()

    private companion object {
        val KEY = stringPreferencesKey("session_state_v1")
    }
}

private fun MediaItem.toJson() = JSONObject()
    .put("id", mediaId)
    .put("uri", localConfiguration?.uri?.toString())
    .put("title", mediaMetadata.title?.toString())
    .put("artist", mediaMetadata.artist?.toString())
    .put("album", mediaMetadata.albumTitle?.toString())
    .put("artwork", mediaMetadata.artworkUri?.toString())
    .put("mimeType", localConfiguration?.mimeType)

private fun JSONObject.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(getString("id"))
    .setUri(getString("uri"))
    .setMimeType(optString("mimeType").takeIf(String::isNotBlank))
    .setMediaMetadata(
        androidx.media3.common.MediaMetadata.Builder()
            .setTitle(optString("title").takeIf(String::isNotBlank))
            .setArtist(optString("artist").takeIf(String::isNotBlank))
            .setAlbumTitle(optString("album").takeIf(String::isNotBlank))
            .setArtworkUri(optString("artwork").takeIf(String::isNotBlank)?.let(android.net.Uri::parse))
            .build(),
    )
    .build()
