package dk.benjy.ytdlp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.benjy.ytdlp.AudioFormat
import dk.benjy.ytdlp.AudioQuality
import dk.benjy.ytdlp.Container
import dk.benjy.ytdlp.DownloadManager
import dk.benjy.ytdlp.Engine
import dk.benjy.ytdlp.Players
import dk.benjy.ytdlp.SPONSOR_CATEGORIES
import dk.benjy.ytdlp.SettingsStore
import dk.benjy.ytdlp.VideoQuality
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val s by SettingsStore.settings.collectAsStateWithLifecycle()
    val version by Engine.version.collectAsStateWithLifecycle()
    val jobs by DownloadManager.jobs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var updating by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    fun set(transform: (dk.benjy.ytdlp.AppSettings) -> dk.benjy.ytdlp.AppSettings) = SettingsStore.update(transform)

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
        }

        // yt-dlp
        item {
            SectionHeader("yt-dlp")
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Version ${version ?: "—"}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                if (updating) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else OutlinedButton(
                    enabled = jobs.none { it.isActive },
                    onClick = {
                        updating = true
                        scope.launch {
                            val msg = runCatching { Engine.update(context) }
                                .getOrElse { "Update failed: ${it.message}" }
                            updating = false
                            snackbar.showSnackbar(msg)
                        }
                    },
                ) { Text("Update") }
            }
            SwitchRow("Update automatically (daily)", s.autoUpdate) { v -> set { it.copy(autoUpdate = v) } }
            SettingFooter("If downloads start failing, update first: YouTube changes often and yt-dlp keeps up.")
        }

        // Video
        item {
            SectionHeader("Video")
            ChoiceRow("Quality", VideoQuality.entries, s.videoQuality, { it.label }) { v -> set { it.copy(videoQuality = v) } }
            ChoiceRow("Container", Container.entries, s.container, { it.label }) { v -> set { it.copy(container = v) } }
            SwitchRow("Prefer H.264 (most compatible)", s.preferH264) { v -> set { it.copy(preferH264 = v) } }
            SettingFooter("VLC plays everything. H.264 only matters for old devices or TVs; YouTube offers it up to 1080p.")
            SwitchRow("Download subtitles", s.subtitles) { v -> set { it.copy(subtitles = v) } }
            if (s.subtitles) {
                SwitchRow("Include auto-generated", s.autoSubtitles) { v -> set { it.copy(autoSubtitles = v) } }
                OutlinedTextField(
                    value = s.subtitleLangs, onValueChange = { v -> set { it.copy(subtitleLangs = v) } },
                    label = { Text("Languages (e.g. en.*, da)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        // Audio
        item {
            SectionHeader("Audio")
            ChoiceRow("Format", AudioFormat.entries, s.audioFormat, { it.label }) { v -> set { it.copy(audioFormat = v) } }
            if (s.audioFormat.lossy) {
                ChoiceRow("Quality", AudioQuality.entries, s.audioQuality, { it.label }) { v -> set { it.copy(audioQuality = v) } }
            }
            SwitchRow("Square cover art", s.squareAudioArt) { v -> set { it.copy(squareAudioArt = v) } }
        }

        // Tags
        item {
            SectionHeader("Tags & artwork")
            SwitchRow("Embed thumbnail as cover art", s.embedThumbnail) { v -> set { it.copy(embedThumbnail = v) } }
            SwitchRow("Embed metadata", s.embedMetadata) { v -> set { it.copy(embedMetadata = v) } }
            SwitchRow("Channel as artist", s.channelAsArtist, enabled = s.embedMetadata) { v -> set { it.copy(channelAsArtist = v) } }
            SwitchRow("Channel as album", s.channelAsAlbum, enabled = s.embedMetadata) { v -> set { it.copy(channelAsAlbum = v) } }
            SwitchRow("Embed chapters", s.embedChapters) { v -> set { it.copy(embedChapters = v) } }
            SettingFooter("Files are named after the video. The title tag is the video name; artist and album artist are the channel.")
        }

        // SponsorBlock
        item {
            SectionHeader("SponsorBlock")
            SwitchRow("Cut sponsor segments", s.sponsorBlock) { v -> set { it.copy(sponsorBlock = v) } }
            if (s.sponsorBlock) {
                SPONSOR_CATEGORIES.forEach { cat ->
                    SwitchRow(cat.label, cat.id in s.sponsorCategories) { on ->
                        set { it.copy(sponsorCategories = if (on) it.sponsorCategories + cat.id else it.sponsorCategories - cat.id) }
                    }
                }
            }
        }

        // Saving & behaviour
        item {
            SectionHeader("Saving & behaviour")
            OutlinedTextField(
                value = s.folderName, onValueChange = { v -> set { it.copy(folderName = v) } },
                label = { Text("Folder name") }, singleLine = true,
                supportingText = { Text("Audio goes to Music/${s.folderName.ifBlank { "yt-dlp" }}, video to Movies/${s.folderName.ifBlank { "yt-dlp" }}") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            SwitchRow(
                if (Players.isVlcInstalled(context)) "Play in VLC" else "Play in VLC (not installed)",
                s.openInVlc,
            ) { v -> set { it.copy(openInVlc = v) } }
            SwitchRow("Start downloading when a link is shared", s.startOnShare) { v -> set { it.copy(startOnShare = v) } }
            SwitchRow("Auto-fill copied YouTube links", s.autoPaste) { v -> set { it.copy(autoPaste = v) } }
            SwitchRow("Allow playlists and channels", s.allowPlaylists) { v -> set { it.copy(allowPlaylists = v) } }
            SettingFooter("In the YouTube app: Share › yt-dlp Direct. Playlist links add every video to the queue.")
        }

        // Advanced
        item {
            SectionHeader("Advanced")
            OutlinedTextField(
                value = s.rateLimit, onValueChange = { v -> set { it.copy(rateLimit = v) } },
                label = { Text("Speed limit (e.g. 5M)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = s.extraArgs, onValueChange = { v -> set { it.copy(extraArgs = v) } },
                label = { Text("Extra yt-dlp arguments") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                if (confirmReset) {
                    TextButton(onClick = { SettingsStore.reset(); confirmReset = false }) {
                        Text("Tap again to reset everything", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { confirmReset = true }) { Text("Reset settings") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
