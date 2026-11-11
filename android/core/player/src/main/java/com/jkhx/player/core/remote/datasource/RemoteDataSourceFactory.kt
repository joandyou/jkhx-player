package com.jkhx.player.core.remote.datasource

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.jkhx.player.core.database.AppDatabase
import com.jkhx.player.core.database.entity.RemoteServerEntity
import kotlinx.coroutines.runBlocking

class RemoteDataSourceFactory(
    private val context: Context,
    private val upstreamFactory: DataSource.Factory,
    private val transferListener: TransferListener? = null
) : DataSource.Factory {

    private fun resolveServer(serverId: String): RemoteServerEntity? {
        return runBlocking {
            try {
                AppDatabase.getInstance(context).remoteServerDao().getById(serverId)
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun createDataSource(): DataSource {
        val defaultDataSource = upstreamFactory.createDataSource()
        val smbDataSource = SmbDataSource(::resolveServer).apply {
            if (transferListener != null) addTransferListener(transferListener)
        }
        val sftpDataSource = SftpDataSource(::resolveServer).apply {
            if (transferListener != null) addTransferListener(transferListener)
        }

        return object : DataSource {
            private var activeSource: DataSource? = null

            override fun addTransferListener(transferListener: TransferListener) {
                defaultDataSource.addTransferListener(transferListener)
                smbDataSource.addTransferListener(transferListener)
                sftpDataSource.addTransferListener(transferListener)
            }

            override fun open(dataSpec: DataSpec): Long {
                val scheme = dataSpec.uri.scheme?.lowercase() ?: ""
                val source = when (scheme) {
                    "smb" -> smbDataSource
                    "sftp" -> sftpDataSource
                    else -> defaultDataSource
                }
                activeSource = source
                return source.open(dataSpec)
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val source = activeSource ?: defaultDataSource
                return source.read(buffer, offset, length)
            }

            override fun getUri(): Uri? = activeSource?.uri ?: defaultDataSource.uri

            override fun close() {
                try {
                    activeSource?.close()
                } finally {
                    activeSource = null
                }
            }
        }
    }
}
