package dk.benjy.ytdlp

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class Mode(val label: String) { VIDEO("Video"), AUDIO("Audio") }

enum class VideoQuality(val label: String, val height: Int?) {
    BEST("Best available", null),
    P2160("2160p (4K)", 2160),
    P1440("1440p", 1440),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480),
    P360("360p", 360),
}

enum class Container(val label: String, val ext: String) { MP4("MP4", "mp4"), MKV("MKV", "mkv") }

enum class AudioFormat(val label: String, val arg: String, val lossy: Boolean) {
    M4A("M4A (AAC)", "m4a", true),
    MP3("MP3", "mp3", true),
    OPUS("Opus", "opus", true),
    FLAC("FLAC", "flac", false),
    BEST("Original (no conversion)", "best", false),
}

enum class AudioQuality(val label: String, val arg: String) {
    BEST("Best (VBR)", "0"),
    K320("320 kbps", "320K"),
    K256("256 kbps", "256K"),
    K192("192 kbps", "192K"),
    K128("128 kbps", "128K"),
}

data class SponsorCategory(val id: String, val label: String)

val SPONSOR_CATEGORIES = listOf(
    SponsorCategory("sponsor", "Sponsors"),
    SponsorCategory("selfpromo", "Self-promotion"),
    SponsorCategory("interaction", "Like/subscribe reminders"),
    SponsorCategory("intro", "Intros"),
    SponsorCategory("outro", "Outros & end cards"),
    SponsorCategory("preview", "Previews & recaps"),
    SponsorCategory("music_offtopic", "Non-music sections"),
    SponsorCategory("filler", "Filler tangents"),
)

data class AppSettings(
    val mode: Mode = Mode.VIDEO,
    val videoQuality: VideoQuality = VideoQuality.P1080,
    val container: Container = Container.MP4,
    val preferH264: Boolean = false,
    val audioFormat: AudioFormat = AudioFormat.M4A,
    val audioQuality: AudioQuality = AudioQuality.BEST,
    val embedThumbnail: Boolean = true,
    val squareAudioArt: Boolean = true,
    val embedMetadata: Boolean = true,
    val channelAsArtist: Boolean = true,
    val channelAsAlbum: Boolean = false,
    val embedChapters: Boolean = true,
    val subtitles: Boolean = false,
    val autoSubtitles: Boolean = false,
    val subtitleLangs: String = "en.*",
    val sponsorBlock: Boolean = false,
    val sponsorCategories: Set<String> = setOf("sponsor", "selfpromo", "interaction"),
    val allowPlaylists: Boolean = true,
    val startOnShare: Boolean = true,
    val autoPaste: Boolean = true,
    val openInVlc: Boolean = true,
    val folderName: String = "yt-dlp",
    val rateLimit: String = "",
    val extraArgs: String = "",
    val autoUpdate: Boolean = true,
)

/** App settings, persisted as JSON in SharedPreferences and exposed as a StateFlow. */
object SettingsStore {
    private const val KEY = "settings.v1"
    private lateinit var prefs: SharedPreferences
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    val current: AppSettings get() = _settings.value

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { runCatching { _settings.value = fromJson(JSONObject(it)) } }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit().putString(KEY, toJson(next).toString()).apply()
    }

    fun reset() = update { AppSettings() }

    private fun toJson(s: AppSettings) = JSONObject().apply {
        put("mode", s.mode.name)
        put("videoQuality", s.videoQuality.name)
        put("container", s.container.name)
        put("preferH264", s.preferH264)
        put("audioFormat", s.audioFormat.name)
        put("audioQuality", s.audioQuality.name)
        put("embedThumbnail", s.embedThumbnail)
        put("squareAudioArt", s.squareAudioArt)
        put("embedMetadata", s.embedMetadata)
        put("channelAsArtist", s.channelAsArtist)
        put("channelAsAlbum", s.channelAsAlbum)
        put("embedChapters", s.embedChapters)
        put("subtitles", s.subtitles)
        put("autoSubtitles", s.autoSubtitles)
        put("subtitleLangs", s.subtitleLangs)
        put("sponsorBlock", s.sponsorBlock)
        put("sponsorCategories", JSONArray(s.sponsorCategories.toList()))
        put("allowPlaylists", s.allowPlaylists)
        put("startOnShare", s.startOnShare)
        put("autoPaste", s.autoPaste)
        put("openInVlc", s.openInVlc)
        put("folderName", s.folderName)
        put("rateLimit", s.rateLimit)
        put("extraArgs", s.extraArgs)
        put("autoUpdate", s.autoUpdate)
    }

    /** Lenient: anything missing or unknown keeps its default. */
    private fun fromJson(j: JSONObject): AppSettings {
        val d = AppSettings()
        fun b(key: String, def: Boolean) = if (j.has(key)) j.optBoolean(key, def) else def
        fun s(key: String, def: String) = if (j.has(key)) j.optString(key, def) else def
        fun <E : Enum<E>> e(key: String, def: E, values: Array<E>): E =
            values.firstOrNull { it.name == j.optString(key) } ?: def
        val cats = j.optJSONArray("sponsorCategories")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } ?: d.sponsorCategories
        return AppSettings(
            mode = e("mode", d.mode, Mode.entries.toTypedArray()),
            videoQuality = e("videoQuality", d.videoQuality, VideoQuality.entries.toTypedArray()),
            container = e("container", d.container, Container.entries.toTypedArray()),
            preferH264 = b("preferH264", d.preferH264),
            audioFormat = e("audioFormat", d.audioFormat, AudioFormat.entries.toTypedArray()),
            audioQuality = e("audioQuality", d.audioQuality, AudioQuality.entries.toTypedArray()),
            embedThumbnail = b("embedThumbnail", d.embedThumbnail),
            squareAudioArt = b("squareAudioArt", d.squareAudioArt),
            embedMetadata = b("embedMetadata", d.embedMetadata),
            channelAsArtist = b("channelAsArtist", d.channelAsArtist),
            channelAsAlbum = b("channelAsAlbum", d.channelAsAlbum),
            embedChapters = b("embedChapters", d.embedChapters),
            subtitles = b("subtitles", d.subtitles),
            autoSubtitles = b("autoSubtitles", d.autoSubtitles),
            subtitleLangs = s("subtitleLangs", d.subtitleLangs),
            sponsorBlock = b("sponsorBlock", d.sponsorBlock),
            sponsorCategories = cats,
            allowPlaylists = b("allowPlaylists", d.allowPlaylists),
            startOnShare = b("startOnShare", d.startOnShare),
            autoPaste = b("autoPaste", d.autoPaste),
            openInVlc = b("openInVlc", d.openInVlc),
            folderName = s("folderName", d.folderName),
            rateLimit = s("rateLimit", d.rateLimit),
            extraArgs = s("extraArgs", d.extraArgs),
            autoUpdate = b("autoUpdate", d.autoUpdate),
        )
    }
}
