package com.jkhx.player.core.remote.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.smbj.share.File as SmbFile
import com.jkhx.player.core.database.entity.RemoteServerEntity
import com.jkhx.player.core.remote.client.RemoteClientFactory
import com.jkhx.player.core.remote.client.SmbClient
import java.io.IOException

class SmbDataSource(
    private val serverResolver: (serverId: String) -> RemoteServerEntity?
) : BaseDataSource(/* isNetwork = */ true) {

    private var currentDataSpec: DataSpec? = null
    private var smbFile: SmbFile? = null
    private var bytesRemaining: Long = 0L
    private var currentPosition: Long = 0L
    private var isOpened = false

    override fun open(dataSpec: DataSpec): Long {
        currentDataSpec = dataSpec
        transferInitializing(dataSpec)

        val uri = dataSpec.uri
        val serverId = uri.host ?: throw IOException("URI SMB không hợp lệ: thiếu serverId ($uri)")
        val server = serverResolver(serverId)
            ?: throw IOException("Không tìm thấy cấu hình máy chủ SMB có mã: $serverId")

        val client = RemoteClientFactory.getClient(server) as? SmbClient
            ?: throw IOException("Máy chủ không phải giao thức SMB: $serverId")

        val relativePath = uri.path?.trimStart('/') ?: ""
        currentPosition = dataSpec.position

        try {
            val file = client.openSmbFile(relativePath)
            smbFile = file

            val totalLength = file.fileInformation.standardInformation.endOfFile
            if (currentPosition >= totalLength) {
                bytesRemaining = 0L
            } else if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                bytesRemaining = dataSpec.length
            } else {
                bytesRemaining = totalLength - currentPosition
            }

            isOpened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (e: Exception) {
            close()
            throw IOException("Lỗi mở tệp tin từ SMB: ${e.message}", e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val file = smbFile ?: throw IOException("Tệp tin SMB chưa được mở")
        val bytesToRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            minOf(length.toLong(), bytesRemaining).toInt()
        } else {
            length
        }

        val bytesRead = try {
            file.read(buffer, currentPosition, offset, bytesToRead)
        } catch (e: Exception) {
            throw IOException("Lỗi đọc dữ liệu từ SMB: ${e.message}", e)
        }

        if (bytesRead <= 0) {
            return C.RESULT_END_OF_INPUT
        }

        currentPosition += bytesRead
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
                smbFile?.closeSilently()
            } catch (_: Exception) {}
            smbFile = null
            transferEnded()
        }
    }
}
