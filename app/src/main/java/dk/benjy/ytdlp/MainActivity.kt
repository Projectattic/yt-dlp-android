package dk.benjy.ytdlp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import dk.benjy.ytdlp.ui.AppRoot
import dk.benjy.ytdlp.ui.AppTheme

class MainActivity : ComponentActivity() {
    /** A link shared into the app that the Download screen should show. */
    val sharedLink = mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleShare(intent)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            AppTheme {
                AppRoot(sharedLink = sharedLink)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** "Share → yt-dlp Direct" from YouTube or a browser. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val links = CommandBuilder.links(text)
        if (links.isEmpty()) return
        if (SettingsStore.current.startOnShare) {
            DownloadManager.enqueue(links)
            sharedLink.value = ""
        } else {
            sharedLink.value = links.joinToString("\n")
        }
        setIntent(Intent(this, MainActivity::class.java)) // don't re-handle on rotation
    }
}
