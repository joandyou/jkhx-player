package com.jkhx.player.core.remote.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.jkhx.player.core.database.entity.RemoteServerEntity
import com.jkhx.player.core.remote.client.RemoteClientFactory
import com.jkhx.player.core.remote.client.SftpClient
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.InputStream

class SftpDataSource(
    private val serverResolver: (serverId: String) -> RemoteServerEntity?
) : BaseDataSource(/* isNetwork = */ true) {

    private var currentDataSpec: DataSpec? = null
    private var inStream: InputStream? = null
    private var bytesRemaining: Long = 0L
    private var isOpened = false

    override fun open(dataSpec: DataSpec): Long {
        currentDataSpec = dataSpec
        transferInitializing(dataSpec)

        val uri = dataSpec.uri
        val serverId = uri.host ?: throw IOException("URI SFTP không hợp lệ: thiếu serverId ($uri)")
        val server = serverResolver(serverId)
            ?: throw IOException("Không tìm thấy cấu hình máy chủ SFTP có mã: $serverId")

        val client = RemoteClientFactory.getClient(server) as? SftpClient
            ?: throw IOException("Máy chủ không phải giao thức SFTP: $serverId")

        val relativePath = uri.path?.trimStart('/') ?: ""

        try {
            val totalLength = runBlocking { client.getFileSize(relativePath) }
            val stream = runBlocking { client.openInputStream(relativePath, dataSpec.position) }
            inStream = stream

            if (dataSpec.position >= totalLength) {
                bytesRemaining = 0L
            } else if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                bytesRemaining = dataSpec.length
            } else {
                bytesRemaining = totalLength - dataSpec.position
            }

            isOpened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (e: Exception) {
            close()
            throw IOException("Lỗi mở tệp tin từ SFTP: ${e.message}", e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val stream = inStream ?: throw IOException("Luồng dữ liệu SFTP chưa được mở")
        val bytesToRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            minOf(length.toLong(), bytesRemaining).toInt()
        } else {
            length
        }

        val bytesRead = try {
            stream.read(buffer, offset, bytesToRead)
        } catch (e: Exception) {
            throw IOException("Lỗi đọc dữ liệu từ SFTP: ${e.message}", e)
        }

        if (bytesRead <= 0) {
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead
        }

        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = currentDataSpec?.uri

    override fun close() {
        if (isOpened) {
            isOpened = false
            try {
                inStream?.close()
            } catch (_: Exception) {}
            inStream = null
            transferEnded()
        }
    }
}
