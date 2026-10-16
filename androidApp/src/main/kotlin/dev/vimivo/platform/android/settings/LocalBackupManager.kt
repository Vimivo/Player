@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "ImportOrdering", "LongMethod", "LongParameterList",
    "MaximumLineLength", "MaxLineLength", "TooManyFunctions", "Wrapping",
)

package dev.vimivo.platform.android.settings

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dev.vimivo.platform.android.database.BookmarkEntity
import dev.vimivo.platform.android.database.LibraryDao
import dev.vimivo.platform.android.database.OrganizationDao
import dev.vimivo.platform.android.database.PlaybackHistoryEntity
import dev.vimivo.platform.android.database.PlaylistDao
import dev.vimivo.platform.android.database.PlaylistEntity
import dev.vimivo.platform.android.database.PlaylistItemEntity
import dev.vimivo.platform.android.database.VimivoDatabase
import dev.vimivo.platform.android.network.NetworkHistory
import dev.vimivo.platform.android.network.NetworkHistoryStore
import dev.vimivo.settings.AppSettings
import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.EqualizerBand
import dev.vimivo.settings.HomeSection
import dev.vimivo.settings.SettingsRepository
import dev.vimivo.settings.SubtitleAppearance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class RestoreReport(
    val playlists: Int,
    val favorites: Int,
    val bookmarks: Int,
    val history: Int,
    val missingReferences: Int,
)

class LocalBackupManager(
    private val context: Context,
    private val database: VimivoDatabase,
    private val library: LibraryDao,
    private val playlists: PlaylistDao,
    private val organization: OrganizationDao,
    private val settingsRepository: SettingsRepository,
    private val networkHistory: NetworkHistoryStore,
) {
    suspend fun export(uri: Uri): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val media = library.allMedia().associateBy { it.id }
            val root = JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("createdAtEpochMillis", System.currentTimeMillis())
                .put("settings", settingsRepository.settings.first().toJson())
                .put(
                    "favorites",
                    JSONArray(media.values.filter { it.isFavorite }.map { it.uri }),
                )
                .put("favoriteStreams", JSONArray(networkHistory.load().favorites.toList()))
                .put(
                    "playlists",
                    JSONArray().apply {
                        playlists.all().forEach { relation ->
                            put(
                                JSONObject()
                                    .put("id", relation.playlist.id)
                                    .put("name", relation.playlist.name)
                                    .put("updatedAtEpochMillis", relation.playlist.updatedAtEpochMillis)
                                    .put(
                                        "items",
                                        JSONArray(
                                            relation.items.sortedBy { it.position }
                                                .mapNotNull { media[it.mediaId]?.uri },
                                        ),
                                    ),
                            )
                        }
                    },
                )
                .put(
                    "bookmarks",
                    JSONArray().apply {
                        organization.allBookmarks().forEach { bookmark ->
                            media[bookmark.mediaId]?.let { item ->
                                put(
                                    JSONObject()
                                        .put("id", bookmark.id)
                                        .put("mediaUri", item.uri)
                                        .put("name", bookmark.name)
                                        .put("positionMillis", bookmark.positionMillis)
                                        .put("createdAtEpochMillis", bookmark.createdAtEpochMillis),
                                )
                            }
                        }
                    },
                )
                .put(
                    "history",
                    JSONArray().apply {
                        organization.allHistory().forEach { history ->
                            media[history.mediaId]?.let { item ->
                                put(
                                    JSONObject()
                                        .put("mediaUri", item.uri)
                                        .put("positionMillis", history.positionMillis)
                                        .put("lastPlayedAtEpochMillis", history.lastPlayedAtEpochMillis)
                                        .put("playCount", history.playCount)
                                        .put("completed", history.completed),
                                )
                            }
                        }
                    },
                )
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(root.toString(2)) }
                ?: error("The backup destination cannot be opened")
        }
    }

    suspend fun restore(uri: Uri): Result<RestoreReport> = runCatching {
        withContext(Dispatchers.IO) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("The selected backup cannot be opened")
            val root = JSONObject(text)
            require(root.getString("format") == FORMAT) { "This is not a Vimivo backup" }
            require(root.getInt("version") in 1..VERSION) { "This backup version is not supported" }
            val mediaByUri = library.allMedia().associateBy { it.uri }
            var restoredPlaylists = 0
            var restoredFavorites = 0
            var restoredBookmarks = 0
            var restoredHistory = 0
            var missing = 0
            database.withTransaction {
                root.optJSONArray("favorites").orEmpty().forEachString { mediaUri ->
                    mediaByUri[mediaUri]?.let {
                        library.setFavorite(it.id, true)
                        restoredFavorites++
                    } ?: missing++
                }
                root.optJSONArray("playlists").orEmpty().forEachObject { playlist ->
                    val ids = playlist.optJSONArray("items").orEmpty().strings().mapNotNull { mediaUri ->
                        mediaByUri[mediaUri]?.id ?: run {
                            missing++
                            null
                        }
                    }.distinct()
                    val id = playlist.getString("id")
                    playlists.upsertPlaylist(
                        PlaylistEntity(
                            id,
                            playlist.getString("name"),
                            playlist.optLong("updatedAtEpochMillis", System.currentTimeMillis()),
                        ),
                    )
                    playlists.deleteItems(id)
                    playlists.upsertItems(ids.mapIndexed { index, mediaId -> PlaylistItemEntity(id, mediaId, index) })
                    restoredPlaylists++
                }
                root.optJSONArray("bookmarks").orEmpty().forEachObject { bookmark ->
                    mediaByUri[bookmark.getString("mediaUri")]?.let { media ->
                        organization.upsertBookmark(
                            BookmarkEntity(
                                bookmark.getString("id"),
                                media.id,
                                bookmark.getString("name"),
                                bookmark.getLong("positionMillis").coerceAtLeast(0),
                                bookmark.getLong("createdAtEpochMillis"),
                            ),
                        )
                        restoredBookmarks++
                    } ?: missing++
                }
                root.optJSONArray("history").orEmpty().forEachObject { history ->
                    mediaByUri[history.getString("mediaUri")]?.let { media ->
                        val position = history.getLong("positionMillis").coerceAtLeast(0)
                        val playedAt = history.getLong("lastPlayedAtEpochMillis")
                        organizationHistory(media.id, position, playedAt, history)
                        restoredHistory++
                    } ?: missing++
                }
            }
            root.optJSONObject("settings")?.let { restored ->
                val settings = restored.toSettings()
                settingsRepository.update { settings }
            }
            val streams = root.optJSONArray("favoriteStreams").orEmpty().strings().toSet()
            if (streams.isNotEmpty()) {
                val existing = networkHistory.load()
                networkHistory.save(NetworkHistory(existing.recent, existing.favorites + streams))
            }
            RestoreReport(restoredPlaylists, restoredFavorites, restoredBookmarks, restoredHistory, missing)
        }
    }

    private suspend fun organizationHistory(
        mediaId: String,
        position: Long,
        playedAt: Long,
        value: JSONObject,
    ) {
        library.upsertHistory(
            PlaybackHistoryEntity(
                mediaId,
                position,
                playedAt,
                value.optInt("playCount", 0).coerceAtLeast(0),
                value.optBoolean("completed", false),
            ),
        )
        library.updatePlayback(mediaId, if (value.optBoolean("completed", false)) 0 else position, playedAt)
    }

    private companion object {
        const val FORMAT = "dev.vimivo.backup"
        const val VERSION = 1
    }
}

private fun AppSettings.toJson() = JSONObject()
    .put("themeMode", themeMode.name)
    .put("preferredEngine", preferredEngine.name)
    .put("resumePlayback", resumePlayback)
    .put("dynamicColor", dynamicColor)
    .put("accentColor", accentColor.name)
    .put("artworkBasedAccents", artworkBasedAccents)
    .put("reduceAnimations", reduceAnimations)
    .put("reduceTransparency", reduceTransparency)
    .put("highContrast", highContrast)
    .put("largerTouchTargets", largerTouchTargets)
    .put("shuffleBehavior", shuffleBehavior.name)
    .put("repeatBehavior", repeatBehavior.name)
    .put("musicResumeBehavior", musicResumeBehavior.name)
    .put("defaultLibrarySort", defaultLibrarySort.name)
    .put("libraryViewMode", libraryViewMode.name)
    .put("restoreQueue", restoreQueue)
    .put("defaultVideoSpeed", defaultVideoSpeed)
    .put("doubleTapSeekSeconds", doubleTapSeekSeconds)
    .put("videoGestureSensitivity", videoGestureSensitivity)
    .put("videoRotationBehavior", videoRotationBehavior.name)
    .put("videoResumeBehavior", videoResumeBehavior.name)
    .put("videoAspectPreference", videoAspectPreference.name)
    .put("decoderPreference", decoderPreference.name)
    .put("preferredSubtitleLanguage", preferredSubtitleLanguage)
    .put("preferredAudioLanguage", preferredAudioLanguage)
    .put("subtitlesEnabled", subtitlesEnabled)
    .put("subtitleDelayMillis", subtitleDelayMillis)
    .put("audioDelayMillis", audioDelayMillis)
    .put("audioEqEnabled", audioEnhancements.enabled)
    .put("audioEqPreset", audioEnhancements.preset.name)
    .put("audioEqBands", JSONArray().apply {
        audioEnhancements.bands.forEach { put(JSONObject().put("frequencyHz", it.centerFrequencyHz).put("gainDb", it.gainDb)) }
    })
    .put("audioBassStrength", audioEnhancements.bassStrength)
    .put("audioTrebleGainDb", audioEnhancements.trebleGainDb)
    .put("audioPreampDb", audioEnhancements.preampDb)
    .put("audioVisualizerEnabled", audioVisualizerEnabled)
    .put("subtitleTextSize", subtitleAppearance.textSizeSp)
    .put("subtitlePosition", subtitleAppearance.positionFraction)
    .put("subtitleTextColor", subtitleAppearance.textColorArgb)
    .put("subtitleBackgroundColor", subtitleAppearance.backgroundColorArgb)
    .put("subtitleEdgeStyle", subtitleAppearance.edgeStyle.name)
    .put("subtitleEdgeColor", subtitleAppearance.edgeColorArgb)
    .put("hiddenMediaBehavior", hiddenMediaBehavior.name)
    .put("autoReconnectStreams", autoReconnectStreams)
    .put("homeSections", JSONArray(homeSections.map(HomeSection::name)))

private fun JSONObject.toSettings(): AppSettings {
    val defaults = AppSettings()
    return defaults.copy(
        themeMode = enum("themeMode", defaults.themeMode),
        preferredEngine = enum("preferredEngine", defaults.preferredEngine),
        resumePlayback = optBoolean("resumePlayback", defaults.resumePlayback),
        dynamicColor = optBoolean("dynamicColor", defaults.dynamicColor),
        accentColor = enum("accentColor", defaults.accentColor),
        artworkBasedAccents = optBoolean("artworkBasedAccents", defaults.artworkBasedAccents),
        reduceAnimations = optBoolean("reduceAnimations", defaults.reduceAnimations),
        reduceTransparency = optBoolean("reduceTransparency", defaults.reduceTransparency),
        highContrast = optBoolean("highContrast", defaults.highContrast),
        largerTouchTargets = optBoolean("largerTouchTargets", defaults.largerTouchTargets),
        shuffleBehavior = enum("shuffleBehavior", defaults.shuffleBehavior),
        repeatBehavior = enum("repeatBehavior", defaults.repeatBehavior),
        musicResumeBehavior = enum("musicResumeBehavior", defaults.musicResumeBehavior),
        defaultLibrarySort = enum("defaultLibrarySort", defaults.defaultLibrarySort),
        libraryViewMode = enum("libraryViewMode", defaults.libraryViewMode),
        restoreQueue = optBoolean("restoreQueue", defaults.restoreQueue),
        defaultVideoSpeed = optDouble("defaultVideoSpeed", defaults.defaultVideoSpeed.toDouble()).toFloat(),
        doubleTapSeekSeconds = optInt("doubleTapSeekSeconds", defaults.doubleTapSeekSeconds),
        videoGestureSensitivity = optDouble(
            "videoGestureSensitivity",
            defaults.videoGestureSensitivity.toDouble(),
        ).toFloat(),
        videoRotationBehavior = enum("videoRotationBehavior", defaults.videoRotationBehavior),
        videoResumeBehavior = enum("videoResumeBehavior", defaults.videoResumeBehavior),
        videoAspectPreference = enum("videoAspectPreference", defaults.videoAspectPreference),
        decoderPreference = enum("decoderPreference", defaults.decoderPreference),
        preferredSubtitleLanguage = optString("preferredSubtitleLanguage", defaults.preferredSubtitleLanguage),
        preferredAudioLanguage = optString("preferredAudioLanguage", defaults.preferredAudioLanguage),
        subtitlesEnabled = optBoolean("subtitlesEnabled", defaults.subtitlesEnabled),
        subtitleDelayMillis = optLong("subtitleDelayMillis", defaults.subtitleDelayMillis),
        audioDelayMillis = optLong("audioDelayMillis", defaults.audioDelayMillis),
        audioEnhancements = AudioEnhancementSettings(
            enabled = optBoolean("audioEqEnabled", defaults.audioEnhancements.enabled),
            preset = enum("audioEqPreset", defaults.audioEnhancements.preset),
            bands = optJSONArray("audioEqBands").orEmpty().objects().mapNotNull { band ->
                val frequency = band.optInt("frequencyHz").takeIf { it > 0 } ?: return@mapNotNull null
                EqualizerBand(frequency, band.optDouble("gainDb", 0.0).toFloat())
            },
            bassStrength = optDouble("audioBassStrength", 0.0).toFloat(),
            trebleGainDb = optDouble("audioTrebleGainDb", 0.0).toFloat(),
            preampDb = optDouble("audioPreampDb", 0.0).toFloat(),
        ),
        audioVisualizerEnabled = optBoolean("audioVisualizerEnabled", defaults.audioVisualizerEnabled),
        subtitleAppearance = SubtitleAppearance(
            textSizeSp = optDouble("subtitleTextSize", defaults.subtitleAppearance.textSizeSp.toDouble()).toFloat(),
            positionFraction = optDouble(
                "subtitlePosition",
                defaults.subtitleAppearance.positionFraction.toDouble(),
            ).toFloat(),
            textColorArgb = optLong("subtitleTextColor", defaults.subtitleAppearance.textColorArgb),
            backgroundColorArgb = optLong("subtitleBackgroundColor", defaults.subtitleAppearance.backgroundColorArgb),
            edgeStyle = enum("subtitleEdgeStyle", defaults.subtitleAppearance.edgeStyle),
            edgeColorArgb = optLong("subtitleEdgeColor", defaults.subtitleAppearance.edgeColorArgb),
        ),
        hiddenMediaBehavior = enum("hiddenMediaBehavior", defaults.hiddenMediaBehavior),
        autoReconnectStreams = optBoolean("autoReconnectStreams", defaults.autoReconnectStreams),
        homeSections = optJSONArray("homeSections").orEmpty().strings()
            .mapNotNull { name -> HomeSection.entries.firstOrNull { it.name == name } }
            .takeIf { it.isNotEmpty() } ?: defaults.homeSections,
    )
}

private inline fun <reified T : Enum<T>> JSONObject.enum(
    key: String,
    fallback: T,
): T = optString(key).takeIf { it.isNotEmpty() }
    ?.let { value -> enumValues<T>().firstOrNull { it.name == value } }
    ?: fallback

private fun JSONArray?.orEmpty() = this ?: JSONArray()
private fun JSONArray.strings() = List(length()) { getString(it) }
private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
private inline fun JSONArray.forEachString(block: (String) -> Unit) = repeat(length()) { block(getString(it)) }
private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) = repeat(length()) { block(getJSONObject(it)) }
