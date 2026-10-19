package dk.benjy.ytdlp

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class JobState { QUEUED, RUNNING, FINISHED, FAILED, CANCELLED }

data class DownloadJob(
    val id: String = UUID.randomUUID().toString(),
    val link: String,
    val mode: Mode,
    val quality: VideoQuality,
    val state: JobState = JobState.QUEUED,
    val phase: String = "Waiting…",
    val title: String? = null,
    val channel: String? = null,
    val thumbnailUrl: String? = null,
    /** 0..1 across all parts, null while unknown. */
    val fraction: Float? = null,
    /** e.g. "12.3 MiB · 2.10 MiB/s · 0:12 left" */
    val detail: String? = null,
    val error: String? = null,
    val log: List<String> = emptyList(),
    val resultUri: Uri? = null,
    val resultMime: String? = null,
) {
    val isActive get() = state == JobState.QUEUED || state == JobState.RUNNING
    val displayTitle get() = title ?: link
}

/** The download queue. Jobs run one at a time on a background thread while
 *  [DownloadService] keeps the app alive and shows a notification. */
object DownloadManager {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()
    @Volatile private var processing = false
    private val cancelled = mutableSetOf<String>()

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun enqueue(links: List<String>, mode: Mode? = null) {
        if (links.isEmpty()) return
        val s = SettingsStore.current
        val newJobs = links.map { DownloadJob(link = it, mode = mode ?: s.mode, quality = s.videoQuality) }
        _jobs.update { it + newJobs }
        ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        startProcessing()
    }

    fun cancel(id: String) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        when (job.state) {
            JobState.QUEUED -> edit(id) { it.copy(state = JobState.CANCELLED, phase = "Cancelled") }
            JobState.RUNNING -> {
                synchronized(cancelled) { cancelled += id }
                edit(id) { it.copy(phase = "Cancelling…") }
                YoutubeDL.destroyProcessById(id)
            }
            else -> {}
        }
    }

    fun retry(id: String) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        _jobs.update { list -> list.filterNot { it.id == id } + DownloadJob(link = job.link, mode = job.mode, quality = job.quality) }
        ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        startProcessing()
    }

    fun remove(id: String) = _jobs.update { list -> list.filterNot { it.id == id && !it.isActive } }
    fun clearFinished() = _jobs.update { list -> list.filter { it.isActive } }

    private fun edit(id: String, transform: (DownloadJob) -> DownloadJob) =
        _jobs.update { list -> list.map { if (it.id == id) transform(it) else it } }

    @Synchronized
    private fun startProcessing() {
        if (processing) return
        processing = true
        scope.launch {
            try {
                // Wait for Python/yt-dlp to be ready (first launch unpacks it).
                while (Engine.state.value !is Engine.State.Ready) {
                    if (Engine.state.value is Engine.State.Failed) {
                        _jobs.value.filter { it.state == JobState.QUEUED }.forEach { job ->
                            edit(job.id) { it.copy(state = JobState.FAILED, phase = "Failed", error = "yt-dlp couldn't start.") }
                        }
                        return@launch
                    }
                    kotlinx.coroutines.delay(300)
                }
                while (true) {
                    val next = _jobs.value.firstOrNull { it.state == JobState.QUEUED } ?: break
                    run(next)
                }
            } finally {
                processing = false
                // Something may have been queued just as we finished.
                if (_jobs.value.any { it.state == JobState.QUEUED }) startProcessing()
            }
        }
    }

    // ---- One download ----

    private suspend fun run(job: DownloadJob) {
        val id = job.id
        edit(id) { it.copy(state = JobState.RUNNING, phase = "Getting video info…", fraction = null) }
        val workDir = File(app.cacheDir, "job-$id").apply { mkdirs() }
        val cacheDir = File(app.cacheDir, "yt-dlp-cache").apply { mkdirs() }
        val log = ArrayDeque<String>()
        try {
            // 1. Info: title, channel, thumbnail, and whether it's a playlist.
            val infoReq = YoutubeDLRequest(job.link).addCommands(CommandBuilder.infoArgs(cacheDir))
            val infoOut = YoutubeDL.execute(infoReq, id, null).out
            val info = JSONObject(infoOut.trim().lines().last { it.startsWith("{") })
            if (isCancelled(id)) return markCancelled(id)

            val type = info.optString("_type")
            if (type == "playlist" || type == "multi_video") {
                return expandPlaylist(job, info)
            }

            val title = info.optString("title").ifEmpty { null }
            val channel = info.optString("channel").ifEmpty { info.optString("uploader") }.ifEmpty { null }
            edit(id) {
                it.copy(title = title, channel = channel, thumbnailUrl = info.optString("thumbnail").ifEmpty { null },
                    phase = "Starting download…")
            }

            // 2. Download with live progress.
            val resultFile = File(workDir, "result.txt")
            val args = CommandBuilder.downloadArgs(SettingsStore.current, job.mode, job.quality, workDir, resultFile, cacheDir)
            val req = YoutubeDLRequest(job.link).addCommands(args)
            val progress = ProgressParser(expectedParts = if (job.mode == Mode.VIDEO) 2 else 1)
            YoutubeDL.execute(req, id) { _, _, line ->
                val clean = line.trim()
                if (clean.isNotEmpty()) {
                    synchronized(log) { log.addLast(clean); while (log.size > 40) log.removeFirst() }
                }
                progress.feed(clean)?.let { p ->
                    edit(id) { it.copy(phase = p.phase, fraction = p.fraction ?: it.fraction, detail = p.detail) }
                }
            }
            if (isCancelled(id)) return markCancelled(id)

            // 3. Find the finished file and move it to Music/ or Movies/.
            edit(id) { it.copy(phase = "Saving…", fraction = 1f, detail = null) }
            val output = resultFile.takeIf { it.exists() }?.readLines()?.lastOrNull { it.isNotBlank() }
                ?.let { File(it.trim()) }?.takeIf { it.exists() }
                ?: workDir.listFiles()?.filter { it.isFile && it.extension.lowercase() in MEDIA_EXTS }
                    ?.maxByOrNull { it.length() }
                ?: throw IllegalStateException("yt-dlp finished, but no file was produced.")
            val cover = File(workDir, "cover.jpg").takeIf { it.exists() }
            val stored = StoredInfo(
                videoId = info.optString("id").ifEmpty { null },
                title = title ?: output.nameWithoutExtension,
                channel = channel,
                duration = info.optDouble("duration").takeIf { !it.isNaN() },
                uploadDate = info.optString("upload_date").ifEmpty { null },
                webpageUrl = info.optString("webpage_url").ifEmpty { job.link },
                description = info.optString("description").ifEmpty { null },
                thumbnailFile = null,
                downloadedAt = System.currentTimeMillis(),
            )
            val uri = Library.save(output, job.mode, stored, cover)
            val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(output.extension.lowercase())
            edit(id) {
                it.copy(state = JobState.FINISHED, phase = "Downloaded", fraction = 1f, detail = null,
                    resultUri = uri, resultMime = mime, log = synchronized(log) { log.toList() })
            }
        } catch (e: YoutubeDL.CanceledException) {
            markCancelled(id)
        } catch (e: Exception) {
            if (isCancelled(id)) return markCancelled(id)
            val message = cleanError(e.message ?: e.javaClass.simpleName)
            val lines = synchronized(log) { log.toList() } + (e.message?.lines()?.filter { it.isNotBlank() } ?: emptyList())
            edit(id) {
                it.copy(state = JobState.FAILED, phase = "Failed", error = message, fraction = null, detail = null,
                    log = lines.takeLast(40))
            }
        } finally {
            workDir.deleteRecursively()
            synchronized(cancelled) { cancelled -= id }
        }
    }

    private fun expandPlaylist(job: DownloadJob, info: JSONObject) {
        val entries = info.optJSONArray("entries")
        val links = mutableListOf<String>()
        if (entries != null) {
            for (i in 0 until entries.length()) {
                val e = entries.optJSONObject(i) ?: continue
                val url = e.optString("url").ifEmpty { e.optString("webpage_url") }
                    .ifEmpty { e.optString("id").takeIf { it.isNotEmpty() }?.let { "https://www.youtube.com/watch?v=$it" } ?: "" }
                if (url.isNotEmpty()) links += url
            }
        }
        val name = info.optString("title").ifEmpty { "Playlist" }
        if (!SettingsStore.current.allowPlaylists) {
            edit(job.id) {
                it.copy(state = JobState.FAILED, title = name, phase = "Failed",
                    error = "This is a playlist (${links.size} videos). Turn on Settings › Allow playlists to download it.")
            }
            return
        }
        _jobs.update { list ->
            list.map {
                if (it.id == job.id) it.copy(state = JobState.FINISHED, title = name,
                    phase = "Added ${links.size} videos to the queue", fraction = null)
                else it
            } + links.map { DownloadJob(link = it, mode = job.mode, quality = job.quality) }
        }
    }

    private fun isCancelled(id: String) = synchronized(cancelled) { id in cancelled }

    private fun markCancelled(id: String) =
        edit(id) { it.copy(state = JobState.CANCELLED, phase = "Cancelled", fraction = null, detail = null) }

    private fun cleanError(raw: String): String {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val err = lines.lastOrNull { it.startsWith("ERROR:") } ?: lines.lastOrNull() ?: raw
        return err.removePrefix("ERROR:").trim()
    }

    private val MEDIA_EXTS = setOf("mp4", "mkv", "webm", "m4a", "mp3", "opus", "ogg", "flac", "wav", "aac")
}

/** Reads yt-dlp's --newline output and turns it into phase / progress text. */
class ProgressParser(private var expectedParts: Int) {
    data class Update(val phase: String, val fraction: Float?, val detail: String?)

    private var part = 0
    private val progress = Regex(
        """\[download\]\s+([\d.]+)%(?:\s+of\s+~?\s*([\d.]+\s*\w+))?(?:\s+at\s+([\d.]+\s*\w+/s))?(?:\s+ETA\s+([\d:]+))?"""
    )

    fun feed(line: String): Update? {
        when {
            line.startsWith("[download] Destination:") -> {
                part += 1
                if (part > expectedParts) expectedParts = part
                return Update(partLabel(), base(), null)
            }
            line.startsWith("[download]") -> {
                val m = progress.find(line) ?: return null
                val pct = m.groupValues[1].toFloatOrNull() ?: return null
                val p = (pct / 100f).coerceIn(0f, 1f)
                val parts = expectedParts.coerceAtLeast(1)
                val overall = ((part - 1).coerceAtLeast(0) + p) / parts
                val detail = listOfNotNull(
                    m.groupValues[2].takeIf { it.isNotEmpty() }?.let { "%.0f%% of %s".format(pct, it) }
                        ?: "%.0f%%".format(pct),
                    m.groupValues[3].takeIf { it.isNotEmpty() },
                    m.groupValues[4].takeIf { it.isNotEmpty() }?.let { "$it left" },
                ).joinToString(" · ")
                return Update(partLabel(), overall.coerceIn(0f, 1f), detail)
            }
            line.startsWith("[Merger]") -> return Update("Merging video and audio…", 1f, null)
            line.startsWith("[ExtractAudio]") -> return Update("Converting audio…", 1f, null)
            line.startsWith("[VideoConvertor]") || line.startsWith("[VideoRemuxer]") ->
                return Update("Converting video…", 1f, null)
            line.startsWith("[SponsorBlock]") || line.startsWith("[ModifyChapters]") ->
                return Update("Removing sponsor segments…", 1f, null)
            line.startsWith("[EmbedSubtitle]") -> return Update("Embedding subtitles…", 1f, null)
            line.startsWith("[ThumbnailsConvertor]") -> return Update("Preparing cover art…", 1f, null)
            line.startsWith("[EmbedThumbnail]") -> return Update("Embedding cover art…", 1f, null)
            line.startsWith("[Metadata]") -> return Update("Writing title and artist…", 1f, null)
        }
        return null
    }

    private fun base(): Float = ((part - 1).coerceAtLeast(0)).toFloat() / expectedParts.coerceAtLeast(1)

    private fun partLabel(): String =
        if (expectedParts > 1) "Downloading · part ${part.coerceAtLeast(1)} of $expectedParts" else "Downloading"
}
