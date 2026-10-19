package dk.benjy.ytdlp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Keeps downloads running when the app is in the background, with a progress notification. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress of running downloads"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(DONE_CHANNEL, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, ONGOING_ID, build("Starting…", null, null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ytdlp:download")
                .apply { acquire(6 * 60 * 60 * 1000L) }
        }
        if (watcher == null) {
            watcher = scope.launch {
                var finishedBefore = DownloadManager.jobs.value.count { it.state == JobState.FINISHED }
                DownloadManager.jobs.collectLatest { jobs ->
                    val active = jobs.filter { it.isActive }
                    val finished = jobs.count { it.state == JobState.FINISHED }
                    if (finished > finishedBefore) {
                        jobs.lastOrNull { it.state == JobState.FINISHED && it.resultUri != null }?.let { notifyDone(it) }
                        finishedBefore = finished
                    }
                    if (active.isEmpty()) {
                        stopSelf()
                        return@collectLatest
                    }
                    val running = active.firstOrNull { it.state == JobState.RUNNING }
                    val queued = active.count { it.state == JobState.QUEUED }
                    val title = running?.displayTitle ?: "Waiting to start…"
                    val text = listOfNotNull(running?.phase, running?.detail,
                        if (queued > 0) "$queued more queued" else null).joinToString(" · ")
                    getSystemService(NotificationManager::class.java)
                        .notify(ONGOING_ID, build(title, text, running?.fraction))
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun build(title: String, text: String?, fraction: Float?): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .setProgress(100, ((fraction ?: 0f) * 100).toInt(), fraction == null)
            .build()

    private fun notifyDone(job: DownloadJob) {
        val n = NotificationCompat.Builder(this, DONE_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Downloaded")
            .setContentText(job.displayTitle)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java).notify(job.id.hashCode(), n)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val DONE_CHANNEL = "downloads_done"
        private const val ONGOING_ID = 1
    }
}
