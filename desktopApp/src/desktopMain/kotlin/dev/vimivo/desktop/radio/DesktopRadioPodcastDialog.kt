package dev.vimivo.desktop.radio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

internal data class RadioStation(
    val name: String,
    val genre: String,
    val streamUrl: String,
    val country: String = "Global",
    val bitrate: String = "128 kbps",
)

internal data class PodcastEpisode(
    val title: String,
    val date: String,
    val audioUrl: String,
    val duration: String? = null,
)

private val CURATED_STATIONS = listOf(
    RadioStation("SomaFM: Groove Salad", "Chillout / Ambient", "https://ice2.somafm.com/groovesalad-128-mp3", "USA", "128 kbps"),
    RadioStation("KEXP 90.3 FM", "Alternative / Indie", "https://kexp-mp3-128.streamguys1.com/kexp128.mp3", "USA", "128 kbps"),
    RadioStation("Radio Paradise", "Eclectic Rock", "https://stream.radioparadise.com/mp3-192", "USA", "192 kbps"),
    RadioStation("BBC World Service", "News / Talk", "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service", "UK", "96 kbps"),
    RadioStation("Classic FM", "Classical", "https://media-ssl.musicradio.com/ClassicFM", "UK", "128 kbps"),
    RadioStation("Jazz24", "Jazz", "https://live.wostreaming.net/manifest/ppm-jazz24mp3-ibc1.m3u8", "USA", "128 kbps"),
    RadioStation("Chillhop Radio", "Lo-Fi / Beats", "https://stream.zeno.fm/f3wvbbqmdg8uv", "Global", "128 kbps"),
    RadioStation("SomaFM: Drone Zone", "Ambient Space", "https://ice1.somafm.com/dronezone-128-mp3", "USA", "128 kbps"),
)

@Composable
internal fun DesktopRadioPodcastDialog(
    onPlayMedia: (MediaItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var stations by remember { mutableStateOf(CURATED_STATIONS) }
    var isSearchingRadio by remember { mutableStateOf(false) }

    // Podcast state
    var rssUrl by remember { mutableStateOf("") }
    var podcastEpisodes by remember { mutableStateOf<List<PodcastEpisode>>(emptyList()) }
    var isLoadingPodcast by remember { mutableStateOf(false) }
    var podcastError by remember { mutableStateOf<String?>(null) }

    fun searchRadio() {
        if (searchQuery.isBlank()) {
            stations = CURATED_STATIONS
            return
        }
        isSearchingRadio = true
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching {
                    val encoded = URLEncoder.encode(searchQuery.trim(), "UTF-8")
                    val url = URI("https://de1.api.radio-browser.info/json/stations/byname/$encoded?limit=20").toURL()
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    if (conn.responseCode == 200) {
                        val text = conn.inputStream.bufferedReader().use { it.readText() }
                        parseRadioBrowserJson(text)
                    } else {
                        CURATED_STATIONS.filter { it.name.contains(searchQuery, ignoreCase = true) || it.genre.contains(searchQuery, ignoreCase = true) }
                    }
                }.getOrElse {
                    CURATED_STATIONS.filter { it.name.contains(searchQuery, ignoreCase = true) || it.genre.contains(searchQuery, ignoreCase = true) }
                }
            }
            stations = list.ifEmpty { CURATED_STATIONS }
            isSearchingRadio = false
        }
    }

    fun loadPodcastFeed() {
        if (rssUrl.isBlank()) return
        isLoadingPodcast = true
        podcastError = null
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    val url = URI(rssUrl.trim()).toURL()
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    val xml = conn.inputStream.bufferedReader().use { it.readText() }
                    parsePodcastRss(xml)
                }.getOrNull()
            }
            isLoadingPodcast = false
            if (parsed.isNullOrEmpty()) {
                podcastError = "Could not parse podcast feed. Ensure the URL is a valid RSS podcast feed."
            } else {
                podcastEpisodes = parsed
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Radio, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Internet Radio & Podcasts", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Internet Radio") },
                        icon = { Icon(Icons.Outlined.Radio, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Podcasts (RSS)") },
                        icon = { Icon(Icons.Outlined.Podcasts, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }

                if (selectedTab == 0) {
                    // Radio tab
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search stations or genres") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Button(onClick = ::searchRadio, enabled = !isSearchingRadio) {
                            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Find")
                        }
                    }

                    if (isSearchingRadio) {
                        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(stations) { station ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        onPlayMedia(
                                            MediaItem(
                                                id = MediaId(station.streamUrl),
                                                uri = station.streamUrl,
                                                title = station.name,
                                                artist = station.genre,
                                                kind = MediaKind.STREAM,
                                            )
                                        )
                                        onDismiss()
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(station.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                "${station.genre} • ${station.country} • ${station.bitrate}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Icon(Icons.Outlined.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Podcast tab
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = rssUrl,
                            onValueChange = { rssUrl = it },
                            label = { Text("Podcast RSS Feed URL") },
                            placeholder = { Text("https://example.com/feed.xml") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Button(onClick = ::loadPodcastFeed, enabled = rssUrl.isNotBlank() && !isLoadingPodcast) {
                            Icon(Icons.Outlined.RssFeed, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Load")
                        }
                    }

                    if (isLoadingPodcast) {
                        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    } else if (podcastEpisodes.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(podcastEpisodes) { episode ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        onPlayMedia(
                                            MediaItem(
                                                id = MediaId(episode.audioUrl),
                                                uri = episode.audioUrl,
                                                title = episode.title,
                                                artist = episode.date,
                                                kind = MediaKind.STREAM,
                                            )
                                        )
                                        onDismiss()
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                episode.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                "${episode.date}${episode.duration?.let { " • $it" } ?: ""}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Icon(Icons.Outlined.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    } else if (podcastError != null) {
                        Text(
                            podcastError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            "Enter any podcast RSS feed URL above to browse and stream episodes directly in Vimivo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

private fun parseRadioBrowserJson(json: String): List<RadioStation> {
    val results = mutableListOf<RadioStation>()
    val items = json.split("{\"changeuuid\":").drop(1)
    for (item in items) {
        val name = item.substringAfter("\"name\":\"", "").substringBefore("\"")
        val url = item.substringAfter("\"url_resolved\":\"", "").substringBefore("\"").ifBlank {
            item.substringAfter("\"url\":\"", "").substringBefore("\"")
        }
        val tags = item.substringAfter("\"tags\":\"", "").substringBefore("\"")
        val country = item.substringAfter("\"country\":\"", "Global").substringBefore("\"")
        val bitrate = item.substringAfter("\"bitrate\":", "128").substringBefore(",")

        if (name.isNotBlank() && url.startsWith("http")) {
            results.add(
                RadioStation(
                    name = name,
                    genre = tags.take(30).ifBlank { "Radio" },
                    streamUrl = url,
                    country = country.ifBlank { "Global" },
                    bitrate = "$bitrate kbps",
                )
            )
        }
    }
    return results
}

private fun parsePodcastRss(xml: String): List<PodcastEpisode> {
    val episodes = mutableListOf<PodcastEpisode>()
    val items = xml.split("<item>").drop(1)
    for (item in items) {
        val title = item.substringAfter("<title>", "").substringBefore("</title>")
            .replace("<![CDATA[", "").replace("]]>", "").trim()
        val pubDate = item.substringAfter("<pubDate>", "").substringBefore("</pubDate>").trim()
        val enc = item.substringAfter("<enclosure", "").substringBefore("/>")
        val audioUrl = enc.substringAfter("url=\"", "").substringBefore("\"").ifBlank {
            enc.substringAfter("url='", "").substringBefore("'")
        }
        val duration = item.substringAfter("<itunes:duration>", "").substringBefore("</itunes:duration>").trim()

        if (title.isNotBlank() && audioUrl.startsWith("http")) {
            episodes.add(
                PodcastEpisode(
                    title = title,
                    date = pubDate.take(16),
                    audioUrl = audioUrl,
                    duration = duration.ifBlank { null },
                )
            )
        }
    }
    return episodes
}
