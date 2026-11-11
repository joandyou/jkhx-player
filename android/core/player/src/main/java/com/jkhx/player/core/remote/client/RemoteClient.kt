package com.jkhx.player.core.remote.client

import com.jkhx.player.core.remote.model.RemoteFileItem
import java.io.InputStream

interface RemoteClient {
    suspend fun testConnection(): Result<Boolean>
    suspend fun listFiles(path: String = ""): Result<List<RemoteFileItem>>
    suspend fun openInputStream(path: String, offset: Long = 0L): InputStream
    suspend fun getFileSize(path: String): Long
    fun close()
}
