package com.jkhx.player.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jkhx.player.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class VideoDownloadService : Service() {

    companion object {
        const val ACTION_START_DOWNLOAD = "com.jkhx.player.download.START"
        const val ACTION_CANCEL_DOWNLOAD = "com.jkhx.player.download.CANCEL"
        const val EXTRA_TASK_ID = "extra_task_id"
        private const val CHANNEL_ID = "jkhx_video_downloads"
        private const val NOTIFICATION_ID = 2026
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager
    private var isDownloadingActive = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID)

        if (action == ACTION_CANCEL_DOWNLOAD && !taskId.isNullOrBlank()) {
            VideoDownloadManager.cancelDownload(taskId)
            return START_NOT_STICKY
        }

        if (action == ACTION_START_DOWNLOAD) {
            val initialNotif = buildProgressNotification(
                title = getString(R.string.stream_download_title),
                contentText = getString(R.string.stream_download_preparing),
                progress = 0,
                speedText = "",
                taskId = taskId
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, initialNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, initialNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                }
            } else {
                startForeground(NOTIFICATION_ID, initialNotif)
            }

            if (!isDownloadingActive) {
                isDownloadingActive = true
                processQueue()
            }
        }

        return START_NOT_STICKY
    }

    private fun processQueue() {
        serviceScope.launch {
            while (true) {
                val nextTask = VideoDownloadManager.tasks.value.firstOrNull { it.status == DownloadState.QUEUED }
                if (nextTask == null) {
                    isDownloadingActive = false
                    break
                }

                if (VideoDownloadManager.isCancelled(nextTask.id)) {
                    VideoDownloadManager.updateTask(nextTask.id) { it.status = DownloadState.CANCELLED }
                    continue
                }

                executeTask(nextTask)
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun executeTask(task: DownloadTask) {
        VideoDownloadManager.updateTask(task.id) {
            it.status = DownloadState.DOWNLOADING
            it.speedText = "Đang kết nối..."
        }

        val destFile = task.destinationPath?.let { File(it) }
        if (destFile == null) {
            VideoDownloadManager.updateTask(task.id) {
                it.status = DownloadState.FAILED
                it.errorMessage = "Không thể tạo file lưu trữ"
            }
            return
        }

        val isHls = task.url.contains(".m3u8", ignoreCase = true)
        val success = if (isHls) {
            HlsDownloader.downloadHls(
                task = task,
                destFile = destFile,
                isCancelled = { VideoDownloadManager.isCancelled(task.id) },
                onProgress = { percent, downloaded, speed ->
                    VideoDownloadManager.updateTask(task.id) {
                        it.progressPercent = percent
                        it.downloadedBytes = downloaded
                        it.speedText = speed
                    }
                    val notif = buildProgressNotification(
                        title = task.title,
                        contentText = "$speed • $percent%",
                        progress = percent,
                        speedText = speed,
                        taskId = task.id
                    )
                    notificationManager.notify(NOTIFICATION_ID, notif)
                }
            )
        } else {
            DirectDownloader.downloadDirect(
                task = task,
                destFile = destFile,
                isCancelled = { VideoDownloadManager.isCancelled(task.id) },
                onProgress = { percent, downloaded, total, speed ->
                    VideoDownloadManager.updateTask(task.id) {
                        it.progressPercent = percent
                        it.downloadedBytes = downloaded
                        if (total > 0) it.totalBytes = total
                        it.speedText = speed
                    }
                    val notif = buildProgressNotification(
                        title = task.title,
                        contentText = "$speed • $percent%",
                        progress = percent,
                        speedText = speed,
                        taskId = task.id
                    )
                    notificationManager.notify(NOTIFICATION_ID, notif)
                }
            )
        }

        val finalCancelled = VideoDownloadManager.isCancelled(task.id)
        if (finalCancelled) {
            VideoDownloadManager.updateTask(task.id) {
                it.status = DownloadState.CANCELLED
                it.speedText = "Đã hủy"
            }
            try { destFile.delete() } catch (_: Exception) {}
        } else if (success) {
            VideoDownloadManager.updateTask(task.id) {
                it.status = DownloadState.COMPLETED
                it.progressPercent = 100
                it.speedText = "Hoàn tất"
            }
            try {
                android.media.MediaScannerConnection.scanFile(
                    applicationContext,
                    arrayOf(destFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
            } catch (_: Exception) {}
            showCompletedNotification(task)
        } else {
            VideoDownloadManager.updateTask(task.id) {
                it.status = DownloadState.FAILED
                it.speedText = "Lỗi tải xuống"
            }
        }
    }

    private fun buildProgressNotification(
        title: String,
        contentText: String,
        progress: Int,
        speedText: String,
        taskId: String?
    ): android.app.Notification {
        val cancelIntent = Intent(this, VideoDownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
            putExtra(EXTRA_TASK_ID, taskId ?: "")
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            taskId.hashCode(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.common_cancel), cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showCompletedNotification(task: DownloadTask) {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.stream_download_completed_title))
            .setContentText(task.title)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(task.id.hashCode(), notif)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.download_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.download_notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
