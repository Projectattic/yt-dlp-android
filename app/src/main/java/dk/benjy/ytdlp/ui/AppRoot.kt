package dk.benjy.ytdlp.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.benjy.ytdlp.DownloadManager

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF5FD4E8), secondary = Color(0xFFFFC85A))
        else -> lightColorScheme(primary = Color(0xFF0E6B7E), secondary = Color(0xFFB07A00))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private enum class Tab(val label: String) { DOWNLOAD("Download"), LIBRARY("Library"), SETTINGS("Settings") }

@Composable
fun AppRoot(sharedLink: MutableState<String?>) {
    var tab by rememberSaveable { mutableStateOf(Tab.DOWNLOAD) }
    val snackbar = remember { SnackbarHostState() }
    val jobs by DownloadManager.jobs.collectAsStateWithLifecycle()
    val active = jobs.count { it.isActive }

    // A shared link always brings you to the Download screen.
    LaunchedEffect(sharedLink.value) {
        if (sharedLink.value != null) tab = Tab.DOWNLOAD
    }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            label = { Text(t.label) },
                            icon = {
                                when (t) {
                                    Tab.DOWNLOAD -> BadgedBox(badge = { if (active > 0) Badge { Text("$active") } }) {
                                        Icon(Icons.Filled.Download, null)
                                    }
                                    Tab.LIBRARY -> Icon(Icons.Filled.VideoLibrary, null)
                                    Tab.SETTINGS -> Icon(Icons.Filled.Settings, null)
                                }
                            },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    Tab.DOWNLOAD -> DownloadScreen(sharedLink, onShowLibrary = { tab = Tab.LIBRARY })
                    Tab.LIBRARY -> LibraryScreen()
                    Tab.SETTINGS -> SettingsScreen()
                }
            }
        }
    }
}
