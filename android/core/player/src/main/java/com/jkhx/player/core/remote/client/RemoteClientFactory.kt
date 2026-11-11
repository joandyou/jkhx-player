package com.jkhx.player.core.remote.client

import com.jkhx.player.core.database.entity.RemoteServerEntity
import java.util.concurrent.ConcurrentHashMap

object RemoteClientFactory {
    private val clientCache = ConcurrentHashMap<String, RemoteClient>()

    /**
     * Tạo một client mới độc lập, không lưu cache. Thích hợp dùng cho test kết nối.
     */
    fun createClient(server: RemoteServerEntity): RemoteClient {
        return when (server.protocol.lowercase()) {
            "smb" -> SmbClient(
                host = server.host,
                port = if (server.port > 0) server.port else 445,
                shareName = server.basePath.ifBlank { "share" },
                username = server.username,
                password = server.password,
                isAnonymous = server.isAnonymous
            )
            "sftp" -> SftpClient(
                host = server.host,
                port = if (server.port > 0) server.port else 22,
                basePath = server.basePath.ifBlank { "/" },
                username = server.username ?: "root",
                password = server.password
            )
            else -> WebDavClient(
                host = server.host,
                port = if (server.port > 0) server.port else (if (server.useSsl) 443 else 80),
                useSsl = server.useSsl,
                basePath = server.basePath.ifBlank { "/" },
                username = server.username,
                password = server.password
            )
        }
    }

    /**
     * Lấy client đã lưu cache hoặc tạo mới nếu chưa có. Cache key bao gồm cả protocol, host và port để tránh xung đột.
     */
    fun getClient(server: RemoteServerEntity): RemoteClient {
        val cacheKey = "${server.id}_${server.protocol}_${server.host}_${server.port}_${server.useSsl}"
        return clientCache.computeIfAbsent(cacheKey) {
            createClient(server)
        }
    }

    fun removeClient(serverId: String) {
        val keysToRemove = clientCache.keys.filter { it == serverId || it.startsWith("${serverId}_") }
        for (k in keysToRemove) {
            clientCache.remove(k)?.close()
        }
    }

    fun clearAll() {
        clientCache.values.forEach { it.close() }
        clientCache.clear()
    }
}
