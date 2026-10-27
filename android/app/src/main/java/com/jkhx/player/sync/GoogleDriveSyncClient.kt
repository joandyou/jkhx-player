package com.jkhx.player.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import com.jkhx.player.core.network.SslHelper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GoogleDriveFileInfo(
    val id: String,
    val name: String,
    val modifiedTime: String? = null
)

class DriveAuthException(message: String) : Exception(message)

class GoogleDriveSyncClient(
    private val okHttpClient: OkHttpClient = SslHelper.configureSsl(
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
    ).build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val syncFileName = "jkhx_sync_data.json"

    suspend fun findSyncFile(token: String): GoogleDriveFileInfo? = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='$syncFileName' and trashed=false&fields=files(id,name,modifiedTime)"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 401) {
                throw DriveAuthException("Access token expired or unauthorized")
            }
            if (!response.isSuccessful) {
                println("[GoogleDriveSyncClient] findSyncFile failed: ${response.code} ${response.message}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val root = JSONObject(body)
            val filesArray = root.optJSONArray("files") ?: return@withContext null
            if (filesArray.length() == 0) return@withContext null

            val first = filesArray.getJSONObject(0)
            GoogleDriveFileInfo(
                id = first.getString("id"),
                name = first.getString("name"),
                modifiedTime = first.optString("modifiedTime")
            )
        }
    }

    suspend fun downloadSyncData(token: String, fileId: String): JkhxSyncPayload? = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 401) {
                throw DriveAuthException("Access token expired or unauthorized")
            }
            if (!response.isSuccessful) {
                println("[GoogleDriveSyncClient] downloadSyncData failed: ${response.code} ${response.message}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            try {
                json.decodeFromString<JkhxSyncPayload>(body)
            } catch (e: Exception) {
                println("[GoogleDriveSyncClient] Error decoding sync payload: ${e.message}")
                null
            }
        }
    }

    suspend fun uploadNewSyncData(token: String, payload: JkhxSyncPayload): String? = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        val metadataJson = """{"name":"$syncFileName","parents":["appDataFolder"]}"""
        val payloadJson = json.encodeToString(JkhxSyncPayload.serializer(), payload)

        val multipartBody = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(
                metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType())
            )
            .addPart(
                payloadJson.toRequestBody("application/json; charset=UTF-8".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(multipartBody)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 401) {
                throw DriveAuthException("Access token expired or unauthorized")
            }
            if (!response.isSuccessful) {
                println("[GoogleDriveSyncClient] uploadNewSyncData failed: ${response.code} ${response.message}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val root = JSONObject(body)
            root.optString("id")
        }
    }

    suspend fun updateSyncData(token: String, fileId: String, payload: JkhxSyncPayload): Boolean = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
        val payloadJson = json.encodeToString(JkhxSyncPayload.serializer(), payload)
        val body = payloadJson.toRequestBody("application/json; charset=UTF-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .patch(body)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 401) {
                throw DriveAuthException("Access token expired or unauthorized")
            }
            if (!response.isSuccessful) {
                println("[GoogleDriveSyncClient] updateSyncData failed: ${response.code} ${response.message}")
                return@withContext false
            }
            true
        }
    }
}

