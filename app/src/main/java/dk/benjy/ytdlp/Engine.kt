package dk.benjy.ytdlp

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Starts the bundled Python + yt-dlp + ffmpeg and keeps yt-dlp up to date. */
object Engine {
    sealed interface State {
        data object Starting : State
        data class Busy(val message: String) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updateLock = Mutex()
    private val _state = MutableStateFlow<State>(State.Starting)
    val state: StateFlow<State> = _state.asStateFlow()
    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version.asStateFlow()

    private const val PREFS = "engine"
    private const val LAST_UPDATE = "lastUpdate"

    fun start(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                _state.value = State.Busy("Starting yt-dlp…")
                YoutubeDL.init(app)
                FFmpeg.init(app)
                _version.value = YoutubeDL.version(app)
                // The yt-dlp bundled in the app ages quickly; refresh it at most once a day.
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val dayAgo = System.currentTimeMillis() - 24L * 3600 * 1000
                if (SettingsStore.current.autoUpdate && prefs.getLong(LAST_UPDATE, 0) < dayAgo) {
                    _state.value = State.Busy("Updating yt-dlp…")
                    runCatching { update(app) }
                }
                _state.value = State.Ready
            } catch (e: Exception) {
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Updates yt-dlp to the latest stable release. Returns a short status message. */
    suspend fun update(context: Context): String = withContext(Dispatchers.IO) {
        updateLock.withLock {
            val app = context.applicationContext
            val status = YoutubeDL.updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(LAST_UPDATE, System.currentTimeMillis()).apply()
            _version.value = YoutubeDL.version(app)
            when (status) {
                YoutubeDL.UpdateStatus.DONE -> "Updated yt-dlp to ${_version.value}"
                else -> "yt-dlp is up to date (${_version.value})"
            }
        }
    }
}
