package com.jkhx.player.core.remote.client

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jkhx.player.core.remote.model.RemoteFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Vector

class SftpClient(
    val host: String,
    val port: Int = 22,
    val basePath: String = "/",
    val username: String,
    val password: String? = null
) : RemoteClient {

    val cleanHost: String
    val effectivePort: Int
    val effectiveUser: String
    init {
        var h = host.trim()
        if (h.startsWith("ssh ", ignoreCase = true)) h = h.substring(4).trim()
        if (h.startsWith("sftp://", ignoreCase = true)) h = h.substring(7).trim()
        val userInHost = if (h.contains('@')) h.substringBefore('@') else null
        if (userInHost != null) h = h.substringAfter('@')
        h = h.substringBefore('/')
        if (h.contains(':')) {
            val parts = h.split(':')
            cleanHost = parts[0]
            effectivePort = parts.getOrNull(1)?.toIntOrNull() ?: port
        } else {
            cleanHost = h
            effectivePort = if (port > 0) port else 22
        }
        effectiveUser = if (!userInHost.isNullOrBlank()) userInHost else username.ifBlank { "root" }
    }

    private val jsch = JSch()
    private var session: Session? = null
    private var channelSftp: ChannelSftp? = null

    private fun getOrConnectSftp(): ChannelSftp {
        val currentChannel = channelSftp
        if (currentChannel != null && currentChannel.isConnected) {
            return currentChannel
        }

        val sess = jsch.getSession(effectiveUser, cleanHost, effectivePort)
        if (!password.isNullOrBlank()) {
            sess.setPassword(password)
        }
        val config = java.util.Properties()
        config["StrictHostKeyChecking"] = "no"
        config["PreferredAuthentications"] = "password,keyboard-interactive,publickey"
        sess.setConfig(config)
        sess.timeout = 15000
        sess.connect(15000)
        session = sess

        val chan = sess.openChannel("sftp") as ChannelSftp
        chan.connect(15000)
        channelSftp = chan
        return chan
    }

    private fun resolveFullPath(subPath: String): String {
        val cleanBase = if (basePath.startsWith("/")) basePath else "/$basePath"
        val cleanSub = subPath.trimStart('/')
        return if (cleanSub.isBlank()) cleanBase else {
            val withSlash = if (cleanBase.endsWith("/")) cleanBase else "$cleanBase/"
            "$withSlash$cleanSub"
        }
    }

    override suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val sftp = getOrConnectSftp()
            if (sftp.isConnected) {
                Result.success(true)
            } else {
                Result.failure(Exception("Không thể mở kết nối SFTP tới máy chủ $cleanHost:$effectivePort"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi xác thực hoặc kết nối SFTP ($cleanHost:$effectivePort): ${e.message}"))
        }
    }

    override suspend fun listFiles(path: String): Result<List<RemoteFileItem>> = withContext(Dispatchers.IO) {
        try {
            val sftp = getOrConnectSftp()
            val targetPath = resolveFullPath(path)

            @Suppress("UNCHECKED_CAST")
            val entries = sftp.ls(targetPath) as Vector<ChannelSftp.LsEntry>
            val result = mutableListOf<RemoteFileItem>()

            for (entry in entries) {
                val name = entry.filename
                if (name == "." || name == "..") continue

                val isDir = entry.attrs.isDir
                val relativePath = if (path.isBlank()) name else "${path.trimEnd('/')}/$name".trimStart('/')

                result.add(
                    RemoteFileItem(
                        name = name,
                        path = relativePath,
                        isDirectory = isDir,
                        size = if (isDir) 0L else entry.attrs.size
                    )
                )
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun openInputStream(path: String, offset: Long): InputStream = withContext(Dispatchers.IO) {
        val sftp = getOrConnectSftp()
        val fullPath = resolveFullPath(path)
        sftp.get(fullPath, null, offset)
    }

    override suspend fun getFileSize(path: String): Long = withContext(Dispatchers.IO) {
        val sftp = getOrConnectSftp()
        val fullPath = resolveFullPath(path)
        val attrs = sftp.stat(fullPath)
        attrs.size
    }

    override fun close() {
        try { channelSftp?.disconnect() } catch (_: Exception) {}
        try { session?.disconnect() } catch (_: Exception) {}
        channelSftp = null
        session = null
    }
}
