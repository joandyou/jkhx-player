package com.jkhx.player.core.player.subtitle.translator

import com.jkhx.player.core.player.subtitle.model.SubtitleCue
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class GeminiSubtitleTranslator(
    private val okHttpClient: OkHttpClient,
    private val apiKeyProvider: () -> String = { "" },
    private val modelProvider: () -> String = { "gemini-3.5-flash-lite" }
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val translationClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    suspend fun testConnection(apiKey: String, model: String = "gemini-3.5-flash-lite"): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) return@withContext false to "API Key không được để trống"

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanKey"
        val payload = """
            {
              "contents": [{
                "parts": [{"text": "Chào bạn, hãy trả lời đúng 1 từ: OK"}]
              }],
              "generationConfig": {
                "temperature": 0.1,
                "maxOutputTokens": 20
              }
            }
        """.trimIndent()

        val req = Request.Builder()
            .url(url)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val start = System.currentTimeMillis()
        try {
            translationClient.newCall(req).execute().use { resp ->
                val duration = System.currentTimeMillis() - start
                val body = resp.body?.string() ?: ""
                if (resp.isSuccessful) {
                    true to "Kết nối Google Gemini AI thành công (${duration}ms)"
                } else {
                    false to "Google Gemini API phản hồi HTTP ${resp.code} (${duration}ms): $body"
                }
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            false to "Lỗi kết nối Gemini (${duration}ms): ${e.message ?: "Timeout"}"
        }
    }

    suspend fun translateCues(
        cues: List<SubtitleCue>,
        targetLanguageName: String = "Tiếng Việt",
        onProgress: (Float) -> Unit = {}
    ): List<SubtitleCue> = withContext(Dispatchers.IO) {
        if (cues.isEmpty()) return@withContext emptyList()
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Chưa cấu hình Google Gemini API Key trong Cài đặt")
        }
        val modelName = modelProvider().trim().ifBlank { "gemini-1.5-flash" }

        // Chia nhỏ phụ đề thành các nhóm 100 câu
        val chunkSize = 100
        val cueChunks = cues.chunked(chunkSize)
        val totalChunks = cueChunks.size
        val completedCount = AtomicInteger(0)

        val semaphore = Semaphore(3) // Tối đa 3 request đồng thời để tránh rate limit
        onProgress(0.05f)

        val deferredTranslations = cueChunks.mapIndexed { chunkIdx, chunk ->
            async {
                semaphore.withPermit {
                    val translatedMap = translateChunkWithRetry(
                        chunk = chunk,
                        apiKey = apiKey,
                        modelName = modelName,
                        targetLanguageName = targetLanguageName
                    )
                    val done = completedCount.incrementAndGet()
                    val progress = (done.toFloat() / totalChunks.toFloat()).coerceIn(0.05f, 0.98f)
                    onProgress(progress)
                    chunkIdx to translatedMap
                }
            }
        }

        val results = deferredTranslations.awaitAll().sortedBy { it.first }
        val fullTranslatedMap = mutableMapOf<Int, String>()
        for ((_, map) in results) {
            fullTranslatedMap.putAll(map)
        }

        onProgress(1.0f)

        // Ghép nội dung đã dịch vào đúng 100% timecode ban đầu
        cues.map { originalCue ->
            val translatedText = fullTranslatedMap[originalCue.index] ?: originalCue.text
            originalCue.copy(text = translatedText)
        }
    }

    private suspend fun translateChunkWithRetry(
        chunk: List<SubtitleCue>,
        apiKey: String,
        modelName: String,
        targetLanguageName: String
    ): Map<Int, String> {
        var lastError: Exception? = null
        for (attempt in 1..2) {
            try {
                return callGeminiForChunk(chunk, apiKey, modelName, targetLanguageName)
            } catch (e: Exception) {
                lastError = e
                delay(1000L * attempt)
            }
        }
        // Nếu thất bại sau 2 lần, giữ nguyên câu gốc của chunk đó để không ngắt quãng toàn bộ phim
        return chunk.associate { it.index to it.text }
    }

    private fun callGeminiForChunk(
        chunk: List<SubtitleCue>,
        apiKey: String,
        modelName: String,
        targetLanguageName: String
    ): Map<Int, String> {
        val inputList = buildJsonArray {
            for (cue in chunk) {
                add(
                    buildJsonObject {
                        put("id", cue.index)
                        put("text", cue.text)
                    }
                )
            }
        }.toString()

        val prompt = """
            Bạn là chuyên gia dịch thuật phụ đề phim điện ảnh chuyên nghiệp.
            Hãy dịch toàn bộ các câu thoại sau đây sang $targetLanguageName tự nhiên, mượt mà, phù hợp với văn phong phim ảnh và bối cảnh đối thoại.
            
            QUY TẮC BẮT BUỘC:
            1. Trả về ĐÚNG định dạng JSON Array chứa các JSON Object có 2 trường: "id" (số nguyên tương ứng) và "text" (câu đã dịch sang $targetLanguageName).
            2. Giữ nguyên 100% số lượng phần tử và đúng thứ tự id.
            3. KHÔNG thêm bất kỳ bình luận, giải thích, markdown hay text nào bên ngoài khối JSON [ ... ].
            
            Dữ liệu câu thoại cần dịch:
            $inputList
        """.trimIndent()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
        val payload = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", prompt)
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.2)
                put("topP", 0.95)
                put("responseMimeType", "application/json")
            }
        }.toString()

        val req = Request.Builder()
            .url(url)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        translationClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Gemini API lỗi HTTP ${resp.code}: ${resp.body?.string() ?: ""}")
            }
            val body = resp.body?.string() ?: throw IllegalStateException("Gemini phản hồi rỗng")
            val root = json.parseToJsonElement(body).jsonObject
            val candidates = root["candidates"]?.jsonArray
            val firstCandidate = candidates?.firstOrNull()?.jsonObject
            val contentObj = firstCandidate?.get("content")?.jsonObject
            val parts = contentObj?.get("parts")?.jsonArray
            val rawText = parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                ?: throw IllegalStateException("Không tìm thấy text trong phản hồi Gemini")

            val cleanJson = rawText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val resultArray = json.parseToJsonElement(cleanJson).jsonArray

            val map = mutableMapOf<Int, String>()
            for (i in 0 until resultArray.size) {
                try {
                    val obj = resultArray[i].jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
                    val text = obj["text"]?.jsonPrimitive?.content ?: continue
                    map[id] = text
                } catch (_: Exception) {}
            }
            return map
        }
    }
}
