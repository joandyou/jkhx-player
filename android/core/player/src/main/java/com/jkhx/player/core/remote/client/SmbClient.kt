package com.jkhx.player.core.remote.client

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File as SmbFile
import com.jkhx.player.core.remote.model.RemoteFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

class SmbClient(
    val host: String,
    val port: Int = 445,
    val shareName: String,
    val username: String? = null,
    val password: String? = null,
    val domain: String? = null,
    val isAnonymous: Boolean = false
) : RemoteClient {

    val cleanHost: String
    val effectivePort: Int
    val effectiveShare: String
    init {
        var h = host.trim()
        if (h.startsWith("smb://", ignoreCase = true)) h = h.substring(6)
        h = h.trimStart('\\', '/')
        val slashIdx = h.indexOfAny(charArrayOf('/', '\\'))
        val extractedShare = if (slashIdx != -1) h.substring(slashIdx + 1).trim('/', '\\') else ""
        if (slashIdx != -1) h = h.substring(0, slashIdx)

        if (h.contains(':')) {
            val parts = h.split(':')
            cleanHost = parts[0]
            effectivePort = parts.getOrNull(1)?.toIntOrNull() ?: port
        } else {
            cleanHost = h
            effectivePort = if (port > 0) port else 445
        }
        effectiveShare = if (extractedShare.isNotBlank()) extractedShare else shareName.trim('/', '\\')
    }

    private val smbConfig = SmbConfig.builder()
        .withTimeout(15, TimeUnit.SECONDS)
        .withSoTimeout(15, TimeUnit.SECONDS)
        .build()

    private val smbClient = SMBClient(smbConfig)
    private var connection: Connection? = null
    private var session: Session? = null
    private var diskShare: DiskShare? = null

    private fun getOrConnectDiskShare(): DiskShare {
        val currentShare = diskShare
        if (currentShare != null && currentShare.isConnected) {
            return currentShare
        }

        val conn = smbClient.connect(cleanHost, effectivePort)
        connection = conn

        val auth = if (isAnonymous || username.isNullOrBlank()) {
            AuthenticationContext.guest()
        } else {
            AuthenticationContext(username, (password ?: "").toCharArray(), domain ?: "")
        }

        val sess = conn.authenticate(auth)
        session = sess

        val targetShare = effectiveShare.ifBlank { "share" }
        val share = sess.connectShare(targetShare) as DiskShare
        diskShare = share
        return share
    }

    override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = smbClient.connect(cleanHost, effectivePort)
            connection = conn
            val auth = if (isAnonymous || username.isNullOrBlank()) {
                AuthenticationContext.guest()
            } else {
                AuthenticationContext(username, (password ?: "").toCharArray(), domain ?: "")
            }
            val sess = conn.authenticate(auth)
            session = sess

            if (effectiveShare.isNotBlank()) {
                val share = sess.connectShare(effectiveShare) as DiskShare
                diskShare = share
                if (share.isConnected) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Không thể kết nối đến thư mục chia sẻ SMB: $effectiveShare"))
                }
            } else {
                if (conn.isConnected) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Không thể kết nối đến dịch vụ SMB"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi kết nối SMB ($cleanHost:$effectivePort): ${e.message}"))
        }
    }

    override suspend fun listFiles(path: String): Result<List<RemoteFileItem>> = withContext(Dispatchers.IO) {
        try {
            val share = getOrConnectDiskShare()
            val cleanPath = path.trim('/', '\\').replace('/', '\\')

            val fileList = share.list(cleanPath)
            val result = mutableListOf<RemoteFileItem>()

            for (item in fileList) {
                val name = item.fileName
                if (name == "." || name == "..") continue

                val isDir = (item.fileAttributes and 0x10L) != 0L
                val relativePath = if (cleanPath.isBlank()) name else "$cleanPath\\$name".replace('\\', '/')

                result.add(
                    RemoteFileItem(
                        name = name,
                        path = relativePath,
                        isDirectory = isDir,
                        size = if (isDir) 0L else item.endOfFile
                    )
                )
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openSmbFile(path: String): SmbFile {
        val share = getOrConnectDiskShare()
        val cleanPath = path.trim('/', '\\').replace('/', '\\')
        return share.openFile(
            cleanPath,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
    }

    override suspend fun openInputStream(path: String, offset: Long): InputStream = withContext(Dispatchers.IO) {
        val smbFile = openSmbFile(path)
        val inStream = smbFile.inputStream
        if (offset > 0L) {
            inStream.skip(offset)
        }
        inStream
    }

    override suspend fun getFileSize(path: String): Long = withContext(Dispatchers.IO) {
        val smbFile = openSmbFile(path)
        try {
            smbFile.fileInformation.standardInformation.endOfFile
        } finally {
            smbFile.closeSilently()
        }
    }

    override fun close() {
        try { diskShare?.close() } catch (_: Exception) {}
        try { session?.close() } catch (_: Exception) {}
        try { connection?.close() } catch (_: Exception) {}
        try { smbClient.close() } catch (_: Exception) {}
        diskShare = null
        session = null
        connection = null
    }
}
