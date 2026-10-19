package dk.benjy.ytdlp

import java.io.File

/** Turns settings into yt-dlp arguments. Arguments are passed straight to the
 *  process (no shell), so nothing needs quoting. */
object CommandBuilder {

    fun infoArgs(cacheDir: File): List<String> = listOf(
        "--dump-single-json",
        "--flat-playlist",
        "--no-playlist", // a watch?v=…&list=… link means just that video
        "--no-warnings",
        "--cache-dir", cacheDir.absolutePath,
    )

    fun downloadArgs(
        s: AppSettings,
        mode: Mode,
        quality: VideoQuality,
        workDir: File,
        resultFile: File,
        cacheDir: File,
    ): List<String> {
        val a = mutableListOf<String>()

        a += listOf("-P", workDir.absolutePath, "-o", "%(title).150B.%(ext)s")
        a += listOf("--newline", "--no-playlist", "--no-mtime")
        a += listOf("--cache-dir", cacheDir.absolutePath)
        // Final path after all post-processing (doesn't imply --simulate, unlike --print).
        a += listOf("--print-to-file", "after_move:filepath", resultFile.absolutePath)

        when (mode) {
            Mode.VIDEO -> {
                val sort = mutableListOf<String>()
                if (s.preferH264) sort += "vcodec:h264"
                quality.height?.let { sort += "res:$it" }
                if (s.container == Container.MP4) sort += "ext:mp4:m4a"
                if (sort.isNotEmpty()) a += listOf("-S", sort.joinToString(","))
                a += listOf("--merge-output-format", s.container.ext)
                if (s.subtitles) {
                    a += "--write-subs"
                    if (s.autoSubtitles) a += "--write-auto-subs"
                    a += listOf("--sub-langs", s.subtitleLangs.ifBlank { "en.*" }, "--embed-subs")
                }
            }
            Mode.AUDIO -> {
                a += listOf("-f", "ba/b", "-x", "--audio-format", s.audioFormat.arg)
                if (s.audioFormat.lossy) a += listOf("--audio-quality", s.audioQuality.arg)
            }
        }

        // Thumbnail: kept for the Library, and embedded as cover art.
        a += listOf("--write-thumbnail", "--convert-thumbnails", "jpg", "-o", "thumbnail:cover.%(ext)s")
        if (s.embedThumbnail) a += "--embed-thumbnail"
        if (mode == Mode.AUDIO && s.squareAudioArt) {
            // Centre-crop the 16:9 thumbnail to a square, like album art.
            a += listOf(
                "--ppa",
                "ThumbnailsConvertor+FFmpeg_o:-c:v mjpeg -qmin 1 -qscale:v 1 " +
                    "-vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"",
            )
        }

        if (s.embedMetadata) {
            a += "--embed-metadata"
            a += listOf("--parse-metadata", "%(title)s:%(meta_title)s")
            if (s.channelAsArtist) {
                a += listOf("--parse-metadata", "%(channel,uploader)s:%(meta_artist)s")
                a += listOf("--parse-metadata", "%(channel,uploader)s:%(meta_album_artist)s")
            }
            if (s.channelAsAlbum) a += listOf("--parse-metadata", "%(channel,uploader)s:%(meta_album)s")
        }
        a += if (s.embedChapters) "--embed-chapters" else "--no-embed-chapters"

        if (s.sponsorBlock && s.sponsorCategories.isNotEmpty()) {
            val ordered = SPONSOR_CATEGORIES.map { it.id }.filter { it in s.sponsorCategories }
            a += listOf("--sponsorblock-remove", ordered.joinToString(","))
        }

        s.rateLimit.trim().takeIf { it.isNotEmpty() }?.let { a += listOf("-r", it) }
        a += tokenize(s.extraArgs)
        return a
    }

    /** Splits typed extra arguments, honouring "double" and 'single' quotes. */
    fun tokenize(text: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inToken = false
        var quote: Char? = null
        for (c in text) {
            val q = quote
            when {
                q != null -> if (c == q) quote = null else cur.append(c)
                c == '"' || c == '\'' -> { quote = c; inToken = true }
                c.isWhitespace() -> if (inToken) { out += cur.toString(); cur.clear(); inToken = false }
                else -> { cur.append(c); inToken = true }
            }
        }
        if (inToken) out += cur.toString()
        return out
    }

    fun links(text: String): List<String> =
        text.split(Regex("[\\s,]+")).map { it.trim() }.filter { it.startsWith("http", ignoreCase = true) }
}
