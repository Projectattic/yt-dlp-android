package dk.benjy.ytdlp

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        Library.init(this)
        DownloadManager.init(this)
        Engine.start(this)
    }
}

/** Opening and sharing finished files. */
object Players {
    private const val VLC = "org.videolan.vlc"

    fun isVlcInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(VLC, 0); true }.getOrDefault(false)

    /** Plays in VLC when installed and enabled, otherwise asks which app to use. */
    fun play(context: Context, uri: Uri, mime: String?) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (SettingsStore.current.openInVlc && isVlcInstalled(context)) {
            try {
                context.startActivity(Intent(intent).setPackage(VLC))
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        context.startActivity(Intent.createChooser(intent, "Play with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun share(context: Context, uri: Uri, mime: String?) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType(mime ?: "*/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openWeb(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
