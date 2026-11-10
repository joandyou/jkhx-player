package com.jkhx.player.download

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.widget.Toast
import com.jkhx.player.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object VideoDownloadManager {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val cancelledTaskIds = mutableSetOf<String>()

    fun isCancelled(taskId: String): Boolean = synchronized(cancelledTaskIds) {
        cancelledTaskIds.contains(taskId)
    }

    fun enqueueDownload(
        context: Context,
        url: String,
        movieName: String,
        episodeName: String? = null,
        posterUrl: String? = null,
        quality: String? = null,
        headers: Map<String, String> = emptyMap()
    ): DownloadTask {
        val cleanUrl = url.substringBefore("#").substringBefore("|").ifBlank { url }
        val title = if (!episodeName.isNullOrBlank() && !episodeName.equals(movieName, true)) {
            "$movieName - $episodeName"
        } else {
            movieName
        }

        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9._ -]"), "_")
        val fileName = "$sanitizedTitle.mp4"
        val publicDownloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "JKHX Player")
        val downloadDir = try {
            if (!publicDownloadDir.exists()) publicDownloadDir.mkdirs()
            if (publicDownloadDir.canWrite()) publicDownloadDir
            else context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "downloads")
        } catch (_: Exception) {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "downloads")
        }
        downloadDir.mkdirs()
        val destFile = File(downloadDir, fileName)

        val task = DownloadTask(
            id = cleanUrl.hashCode().toString(),
            url = cleanUrl,
            title = title,
            movieName = movieName,
            episodeName = episodeName,
            posterUrl = posterUrl,
            quality = quality,
            headers = headers,
            status = DownloadState.QUEUED,
            destinationPath = destFile.absolutePath
        )

        synchronized(cancelledTaskIds) {
            cancelledTaskIds.remove(task.id)
        }

        val currentList = _tasks.value.toMutableList()
        currentList.removeAll { it.id == task.id }
        currentList.add(0, task)
        _tasks.value = currentList

        // Khởi chạy Foreground Service để tải ngầm
        val serviceIntent = Intent(context, VideoDownloadService::class.java).apply {
            action = VideoDownloadService.ACTION_START_DOWNLOAD
            putExtra(VideoDownloadService.EXTRA_TASK_ID, task.id)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        Toast.makeText(context, context.getString(R.string.stream_download_started_toast), Toast.LENGTH_SHORT).show()
        return task
    }

    fun cancelDownload(taskId: String) {
        synchronized(cancelledTaskIds) {
            cancelledTaskIds.add(taskId)
        }
        updateTask(taskId) {
            it.status = DownloadState.CANCELLED
            it.speedText = "Đã hủy"
        }
    }

    fun getTask(taskId: String): DownloadTask? {
        return _tasks.value.find { it.id == taskId }
    }

    fun updateTask(taskId: String, block: (DownloadTask) -> Unit) {
        val currentList = _tasks.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == taskId }
        if (index >= 0) {
            val task = currentList[index].copy()
            block(task)
            currentList[index] = task
            _tasks.value = currentList
        }
    }
}
