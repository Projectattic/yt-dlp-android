package dk.benjy.ytdlp

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** What we know about a download beyond the file itself. */
data class StoredInfo(
    val videoId: String?,
    val title: String,
    val channel: String?,
    val duration: Double?,
    val uploadDate: String?, // yyyyMMdd
    val webpageUrl: String?,
    val description: String?,
    val thumbnailFile: String?,
    val downloadedAt: Long,
)

data class MediaItem(
    val uri: Uri,
    val kind: Mode,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long,
    val durationMs: Long?,
    val info: StoredInfo?,
    val thumbnail: File?,
) {
    val title: String get() = info?.title ?: displayName.substringBeforeLast('.')
    val channel: String? get() = info?.channel
    val extension: String get() = displayName.substringAfterLast('.', "").uppercase()
    val durationText: String?
        get() {
            val seconds = info?.duration?.toLong() ?: durationMs?.div(1000) ?: return null
            if (seconds <= 0) return null
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
}

/**
 * Finished downloads live in shared storage, where VLC and every other player
 * find them: audio in Music/<folder>, video in Movies/<folder>.
 * The app only lists files it created itself.
 */
object Library {
    private lateinit var app: Context
    private lateinit var dbFile: File
    lateinit var thumbsDir: File
        private set
    private val db = mutableMapOf<String, StoredInfo>()

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    fun init(context: Context) {
        app = context.applicationContext
        dbFile = File(app.filesDir, "library.json")
        thumbsDir = File(app.filesDir, "thumbs").apply { mkdirs() }
        loadDb()
    }

    private fun key(kind: Mode, id: Long) = "${kind.name}:$id"

    // ---- Saving ----

    /** Copies a finished file into Music/ or Movies/ and records its details. */
    suspend fun save(file: File, kind: Mode, info: StoredInfo, thumbnail: File?): Uri =
        withContext(Dispatchers.IO) {
            val resolver = app.contentResolver
            val ext = file.extension.lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: if (kind == Mode.AUDIO) "audio/*" else "video/*"
            val folder = SettingsStore.current.folderName.trim().trim('/').ifEmpty { "yt-dlp" }
            val (collection, base) = when (kind) {
                Mode.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                    Environment.DIRECTORY_MUSIC
                Mode.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                    Environment.DIRECTORY_MOVIES
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$base/$folder")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Android refused to create the file in $base/$folder")
            try {
                resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }

            var stored = info
            if (thumbnail != null && thumbnail.exists()) {
                val name = (info.videoId ?: System.currentTimeMillis().toString()) + ".jpg"
                thumbnail.copyTo(File(thumbsDir, name), overwrite = true)
                stored = info.copy(thumbnailFile = name)
            }
            synchronized(db) { db[key(kind, ContentUris.parseId(uri))] = stored }
            saveDb()
            refreshNow()
            uri
        }

    // ---- Listing ----

    suspend fun refresh() = withContext(Dispatchers.IO) { refreshNow() }

    private fun refreshNow() {
        val folder = SettingsStore.current.folderName.trim().trim('/').ifEmpty { "yt-dlp" }
        val result = mutableListOf<MediaItem>()
        query(Mode.AUDIO, MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            "${Environment.DIRECTORY_MUSIC}/$folder/", result)
        query(Mode.VIDEO, MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            "${Environment.DIRECTORY_MOVIES}/$folder/", result)
        _items.value = result.sortedByDescending { it.info?.downloadedAt ?: (it.dateAdded * 1000) }
    }

    private fun query(kind: Mode, collection: Uri, relativePath: String, into: MutableList<MediaItem>) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DURATION,
        )
        runCatching {
            app.contentResolver.query(
                collection, projection,
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?", arrayOf(relativePath),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val info = synchronized(db) { db[key(kind, id)] }
                    into += MediaItem(
                        uri = ContentUris.withAppendedId(collection, id),
                        kind = kind,
                        displayName = c.getString(1) ?: "",
                        mimeType = c.getString(2) ?: if (kind == Mode.AUDIO) "audio/*" else "video/*",
                        size = c.getLong(3),
                        dateAdded = c.getLong(4),
                        durationMs = if (c.isNull(5)) null else c.getLong(5),
                        info = info,
                        thumbnail = info?.thumbnailFile?.let { File(thumbsDir, it) }?.takeIf { it.exists() },
                    )
                }
            }
        }
    }

    // ---- Deleting ----

    suspend fun delete(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        val ok = runCatching { app.contentResolver.delete(item.uri, null, null) > 0 }.getOrDefault(false)
        if (ok) {
            val removed = synchronized(db) { db.remove(key(item.kind, ContentUris.parseId(item.uri))) }
            val thumb = removed?.thumbnailFile
            val shared = synchronized(db) { db.values.any { it.thumbnailFile == thumb } }
            if (thumb != null && !shared) File(thumbsDir, thumb).delete()
            saveDb()
            refreshNow()
        }
        ok
    }

    // ---- Persistence ----

    private fun loadDb() {
        if (!dbFile.exists()) return
        runCatching {
            val root = JSONObject(dbFile.readText())
            for (k in root.keys()) {
                val o = root.getJSONObject(k)
                db[k] = StoredInfo(
                    videoId = o.optString("videoId").ifEmpty { null },
                    title = o.optString("title"),
                    channel = o.optString("channel").ifEmpty { null },
                    duration = if (o.has("duration")) o.optDouble("duration") else null,
                    uploadDate = o.optString("uploadDate").ifEmpty { null },
                    webpageUrl = o.optString("webpageUrl").ifEmpty { null },
                    description = o.optString("description").ifEmpty { null },
                    thumbnailFile = o.optString("thumbnailFile").ifEmpty { null },
                    downloadedAt = o.optLong("downloadedAt"),
                )
            }
        }
    }

    private fun saveDb() {
        val root = JSONObject()
        synchronized(db) {
            for ((k, v) in db) {
                root.put(k, JSONObject().apply {
                    put("videoId", v.videoId ?: "")
                    put("title", v.title)
                    put("channel", v.channel ?: "")
                    v.duration?.let { put("duration", it) }
                    put("uploadDate", v.uploadDate ?: "")
                    put("webpageUrl", v.webpageUrl ?: "")
                    put("description", v.description ?: "")
                    put("thumbnailFile", v.thumbnailFile ?: "")
                    put("downloadedAt", v.downloadedAt)
                })
            }
        }
        val tmp = File(dbFile.parentFile, "library.json.tmp")
        tmp.writeText(root.toString())
        tmp.renameTo(dbFile)
    }
}
