package dk.benjy.ytdlp.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.benjy.ytdlp.AudioFormat
import dk.benjy.ytdlp.AudioQuality
import dk.benjy.ytdlp.CommandBuilder
import dk.benjy.ytdlp.DownloadJob
import dk.benjy.ytdlp.DownloadManager
import dk.benjy.ytdlp.Engine
import dk.benjy.ytdlp.JobState
import dk.benjy.ytdlp.Mode
import dk.benjy.ytdlp.Players
import dk.benjy.ytdlp.SettingsStore
import dk.benjy.ytdlp.VideoQuality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(sharedLink: MutableState<String?>, onShowLibrary: () -> Unit) {
    val context = LocalContext.current
    val settings by SettingsStore.settings.collectAsStateWithLifecycle()
    val engine by Engine.state.collectAsStateWithLifecycle()
    val jobs by DownloadManager.jobs.collectAsStateWithLifecycle()
    var linkText by remember { mutableStateOf("") }
    var lastClip by remember { mutableIntStateOf(0) }
    val links = CommandBuilder.links(linkText)

    // A link shared into the app.
    LaunchedEffect(sharedLink.value) {
        sharedLink.value?.let { if (it.isNotEmpty()) linkText = it }
        sharedLink.value = null
    }

    // Auto-fill a copied YouTube link when the app comes to the front.
    // (Android only lets apps read the clipboard while they have focus.)
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) {
        if (!focused || !settings.autoPaste) return@LaunchedEffect
        val clip = clipboardText(context) ?: return@LaunchedEffect
        val hash = clip.hashCode()
        if (hash == lastClip) return@LaunchedEffect
        lastClip = hash
        val lower = clip.lowercase()
        if ((lower.contains("youtube.com/") || lower.contains("youtu.be/")) &&
            jobs.none { it.link == clip } && linkText.isBlank()
        ) {
            linkText = clip
        }
    }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Text("Download", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
        }

        item { EngineBanner(engine) }

        item {
            OutlinedTextField(
                value = linkText,
                onValueChange = { linkText = it },
                label = { Text("YouTube link(s)") },
                placeholder = { Text("Paste or share a link") },
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    if (linkText.isEmpty()) {
                        IconButton(onClick = { clipboardText(context)?.let { linkText = it } }) {
                            Icon(Icons.Filled.ContentPaste, "Paste")
                        }
                    } else {
                        IconButton(onClick = { linkText = "" }) { Icon(Icons.Filled.Close, "Clear") }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }

        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Mode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = settings.mode == m,
                        onClick = { SettingsStore.update { it.copy(mode = m) } },
                        shape = SegmentedButtonDefaults.itemShape(i, Mode.entries.size),
                    ) { Text(m.label) }
                }
            }
        }

        item {
            if (settings.mode == Mode.VIDEO) {
                ChoiceRow("Quality", VideoQuality.entries, settings.videoQuality, { it.label }) { q ->
                    SettingsStore.update { it.copy(videoQuality = q) }
                }
            } else {
                ChoiceRow("Format", AudioFormat.entries, settings.audioFormat, { it.label }) { f ->
                    SettingsStore.update { it.copy(audioFormat = f) }
                }
                if (settings.audioFormat.lossy) {
                    ChoiceRow("Quality", AudioQuality.entries, settings.audioQuality, { it.label }) { q ->
                        SettingsStore.update { it.copy(audioQuality = q) }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    DownloadManager.enqueue(links)
                    linkText = ""
                },
                enabled = links.isNotEmpty() && engine !is Engine.State.Failed,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            ) {
                Icon(Icons.Filled.Download, null)
                Spacer(Modifier.width(8.dp))
                Text(if (links.size > 1) "Download ${links.size} links" else "Download",
                    style = MaterialTheme.typography.titleMedium)
            }
            val folder = settings.folderName.ifBlank { "yt-dlp" }
            SubtleText(
                "Saves to ${if (settings.mode == Mode.AUDIO) "Music" else "Movies"}/$folder, where VLC finds it.",
                Modifier.padding(horizontal = 16.dp),
            )
        }

        val active = jobs.filter { it.isActive }
        val done = jobs.filter { !it.isActive }.reversed()

        if (active.isNotEmpty()) {
            item { SectionHeader("Downloading") }
            items(active, key = { it.id }) { JobCard(it, onShowLibrary) }
        }
        if (done.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Recent")
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { DownloadManager.clearFinished() }, modifier = Modifier.padding(top = 16.dp, end = 8.dp)) {
                        Text("Clear")
                    }
                }
            }
            items(done, key = { it.id }) { JobCard(it, onShowLibrary) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EngineBanner(state: Engine.State) {
    when (state) {
        is Engine.State.Ready -> {}
        is Engine.State.Starting, is Engine.State.Busy -> Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(12.dp))
                Text(if (state is Engine.State.Busy) state.message else "Starting yt-dlp…")
            }
        }
        is Engine.State.Failed -> Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Text("yt-dlp couldn't start: ${state.message}", Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun JobCard(job: DownloadJob, onShowLibrary: () -> Unit) {
    val context = LocalContext.current
    var showLog by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Thumbnail(job.mode, Modifier.width(112.dp).height(63.dp), url = job.thumbnailUrl)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(job.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    job.channel?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Text(
                        job.phase, style = MaterialTheme.typography.bodySmall,
                        color = when (job.state) {
                            JobState.FINISHED -> MaterialTheme.colorScheme.primary
                            JobState.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                when (job.state) {
                    JobState.QUEUED, JobState.RUNNING ->
                        IconButton(onClick = { DownloadManager.cancel(job.id) }) { Icon(Icons.Filled.Close, "Cancel") }
                    JobState.FINISHED -> job.resultUri?.let { uri ->
                        IconButton(onClick = { Players.play(context, uri, job.resultMime) }) {
                            Icon(Icons.Filled.PlayCircle, "Play", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    else -> {}
                }
            }

            if (job.state == JobState.RUNNING) {
                val f = job.fraction
                if (f != null) LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                job.detail?.let { SubtleText(it) }
            }

            if (job.state == JobState.FAILED) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(job.error ?: "Failed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { DownloadManager.retry(job.id) }) { Text("Retry") }
                    if (job.log.isNotEmpty()) {
                        TextButton(onClick = { showLog = !showLog }) { Text(if (showLog) "Hide details" else "Details") }
                    }
                }
                if (showLog) {
                    Text(job.log.takeLast(15).joinToString("\n"), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (job.state == JobState.FINISHED && job.resultUri != null) {
                TextButton(onClick = onShowLibrary) { Text("Show in Library") }
            }
        }
    }
}

private fun clipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}
