package dk.benjy.ytdlp.ui

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.benjy.ytdlp.DownloadManager
import dk.benjy.ytdlp.JobState
import dk.benjy.ytdlp.Library
import dk.benjy.ytdlp.MediaItem
import dk.benjy.ytdlp.Mode
import dk.benjy.ytdlp.Players
import kotlinx.coroutines.launch

private enum class Filter(val label: String) { ALL("All"), VIDEO("Video"), AUDIO("Audio") }

@Composable
fun LibraryScreen() {
    val items by Library.items.collectAsStateWithLifecycle()
    val jobs by DownloadManager.jobs.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(Filter.ALL) }
    var search by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<MediaItem?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current

    // Refresh when shown, when the app regains focus, and when a download finishes.
    val focused = LocalWindowInfo.current.isWindowFocused
    val finishedCount = jobs.count { it.state == JobState.FINISHED }
    LaunchedEffect(focused, finishedCount) { Library.refresh() }

    val visible = items.filter {
        when (filter) {
            Filter.ALL -> true
            Filter.VIDEO -> it.kind == Mode.VIDEO
            Filter.AUDIO -> it.kind == Mode.AUDIO
        }
    }.filter {
        val q = search.trim()
        q.isEmpty() || it.title.contains(q, ignoreCase = true) || (it.channel?.contains(q, ignoreCase = true) == true)
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
        }
        item {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text("Search title or channel") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Filter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
        }
        if (visible.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (items.isEmpty()) "No downloads yet.\nFinished downloads appear here."
                        else "Nothing matches.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(visible, key = { it.uri.toString() }) { item ->
            MediaRow(item, onDelete = { pendingDelete = item })
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete “${item.title}”?") },
            text = { Text("The file is removed from your phone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        if (!Library.delete(item)) snackbar.showSnackbar("Couldn't delete the file.")
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaRow(item: MediaItem, onDelete: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = { Players.play(context, item.uri, item.mimeType) }, onLongClick = { menu = true })
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Thumbnail(item.kind, Modifier.width(128.dp).height(72.dp), file = item.thumbnail,
                mediaUri = if (item.thumbnail == null) item.uri else null)
            KindBadge(item.kind, Modifier.align(Alignment.TopStart).padding(4.dp))
            item.durationText?.let {
                Text(
                    it, color = Color.White, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.channel?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(
                "${item.extension} · ${Formatter.formatShortFileSize(context, item.size)}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Play") }, leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                    onClick = { menu = false; Players.play(context, item.uri, item.mimeType) })
                DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Filled.Share, null) },
                    onClick = { menu = false; Players.share(context, item.uri, item.mimeType) })
                item.info?.webpageUrl?.let { url ->
                    DropdownMenuItem(text = { Text("Open on YouTube") }, leadingIcon = { Icon(Icons.Filled.OpenInNew, null) },
                        onClick = { menu = false; Players.openWeb(context, url) })
                }
                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                    onClick = { menu = false; onDelete() })
            }
        }
    }
}
