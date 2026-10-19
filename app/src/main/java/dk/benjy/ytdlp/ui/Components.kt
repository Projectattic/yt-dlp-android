package dk.benjy.ytdlp.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dk.benjy.ytdlp.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Thumbnail from a saved cover, a remote URL, or a frame of the media file. */
@Composable
fun Thumbnail(
    kind: Mode,
    modifier: Modifier = Modifier,
    file: File? = null,
    url: String? = null,
    mediaUri: Uri? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.clip(shape)) {
        Placeholder(kind)
        when {
            file != null -> AsyncImage(model = file, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            url != null -> AsyncImage(model = url, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            mediaUri != null -> {
                val context = LocalContext.current
                val bitmap by produceState<Bitmap?>(null, mediaUri) {
                    value = withContext(Dispatchers.IO) {
                        runCatching { context.contentResolver.loadThumbnail(mediaUri, Size(480, 270), null) }.getOrNull()
                    }
                }
                bitmap?.let {
                    Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun Placeholder(kind: Mode) {
    val colors = if (kind == Mode.AUDIO) listOf(Color(0xFFE0457B), Color(0xFF7B3FD1))
    else listOf(Color(0xFF2F7DE1), Color(0xFF3B3FB5))
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (kind == Mode.AUDIO) Icons.Filled.MusicNote else Icons.Filled.Movie, null,
            tint = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
fun KindBadge(kind: Mode, modifier: Modifier = Modifier) {
    Box(
        modifier.size(20.dp).clip(CircleShape)
            .background(if (kind == Mode.AUDIO) Color(0xFFE0457B) else Color(0xFF2F7DE1)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (kind == Mode.AUDIO) Icons.Filled.MusicNote else Icons.Filled.Movie, kind.label,
            tint = Color.White, modifier = Modifier.size(12.dp))
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
fun SettingFooter(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** A label with the current value; tapping opens a menu of choices. */
@Composable
fun <T> ChoiceRow(label: String, options: List<T>, selected: T, text: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(text(selected), color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, null, tint = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.align(Alignment.TopEnd)) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(text(option)) }, onClick = { onSelect(option); open = false })
            }
        }
    }
}

@Composable
fun SubtleText(text: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
