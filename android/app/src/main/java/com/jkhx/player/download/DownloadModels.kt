package com.jkhx.player.download

enum class DownloadState {
    IDLE,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadTask(
    val id: String,
    val url: String,
    val title: String,
    val movieName: String,
    val episodeName: String? = null,
    val posterUrl: String? = null,
    val quality: String? = null,
    val headers: Map<String, String> = emptyMap(),
    var status: DownloadState = DownloadState.QUEUED,
    var totalBytes: Long = 0L,
    var downloadedBytes: Long = 0L,
    var progressPercent: Int = 0,
    var speedText: String = "0 KB/s",
    var destinationPath: String? = null,
    var errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
