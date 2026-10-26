package com.jkhx.player.core.player.tts.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiTtsEngine(
    private val okHttpClient: OkHttpClient,
    override val engineId: String = "gemini-3.8-flash-tts",
    private val apiKeyProvider: () -> String,
    private val apiEndpointProvider: () -> String = { "" },
    private val audioFormatProvider: () -> String = { "opus" }
) : TtsEngine {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun synthesize(text: String, voiceId: String, speechRate: Float): ByteArray? = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        val cleanText = text.replace(Regex("<[^>]*>"), "").trim()
        if (cleanText.isBlank()) return@withContext null

        val normVoiceId = when (voiceId.lowercase().trim()) {
            "alloy", "bao_nam" -> "bao_nam"
            "onyx", "minh_quang" -> "minh_quang"
            "echo", "tuan_khang" -> "tuan_khang"
            "fable", "phuong_vy" -> "phuong_vy"
            "nova", "female", "mai_chi" -> "mai_chi"
            "shimmer", "anh_duong" -> "anh_duong"
            else -> "bao_nam"
        }

        val isFemale = normVoiceId == "phuong_vy" || normVoiceId == "mai_chi" || normVoiceId == "anh_duong"
        // Ánh xạ giọng tiếng Việt cho Edge-TTS OpenAI Gateway: Nam -> NamMinh, Nữ -> HoaiMy
        val edgeVoice = if (isFemale) "vi-VN-HoaiMyNeural" else "vi-VN-NamMinhNeural"

        var endpoint = apiEndpointProvider().trim()

        // 1. Nếu cấu hình proxy / endpoint dạng OpenAI-compatible (openai-edge-tts, OneAPI, NewAPI, v1/audio/speech)
        if (endpoint.isNotBlank()) {
            val fullUrl = if (endpoint.endsWith("/v1/audio/speech")) {
                endpoint
            } else if (endpoint.endsWith("/v1")) {
                "$endpoint/audio/speech"
            } else {
                endpoint.trimEnd('/') + "/v1/audio/speech"
            }

            // Gửi tts-1 làm model chính, tự động fallback sang tts-1-hd và gpt-4o-mini-tts nếu lỗi
            val modelsToTry = listOf("tts-1", "tts-1-hd", "gpt-4o-mini-tts")
            val prefFormat = audioFormatProvider().trim().lowercase().ifBlank { "opus" }
            val formatsToTry = if (prefFormat == "opus") listOf("opus", "mp3") else listOf("mp3")

            for (modelCandidate in modelsToTry) {
                for (fmt in formatsToTry) {
                    val payload = buildJsonObject {
                        put("model", modelCandidate)
                        put("input", cleanText)
                        put("voice", edgeVoice)
                        put("speed", speechRate.coerceIn(0.5f, 2.0f))
                        put("response_format", fmt)
                    }.toString()

                    val reqBuilder = Request.Builder()
                        .url(fullUrl)
                        .addHeader("Content-Type", "application/json")
                        .post(payload.toRequestBody("application/json".toMediaType()))

                    if (apiKey.isNotBlank()) {
                        reqBuilder.addHeader("Authorization", "Bearer $apiKey")
                    }

                    try {
                        client.newCall(reqBuilder.build()).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val bytes = resp.body?.bytes()
                                if (bytes != null && bytes.isNotEmpty()) {
                                    return@withContext bytes
                                }
                            } else if (resp.code == 400 && fmt == "opus") {
                                // Server không hỗ trợ opus -> tự động thử tiếp mp3
                                return@use
                            }
                        }
                    } catch (_: Exception) {
                        // Thử tiếp
                    }
                }
            }
        }

        // 2. Fallback sang Google Generative Language REST API nếu không cấu hình endpoint proxy riêng
        if (apiKey.isBlank()) return@withContext null
        val googleModel = "gemini-2.0-flash"
        val googleUrl = "https://generativelanguage.googleapis.com/v1beta/models/$googleModel:generateContent?key=$apiKey"

        val googleVoiceName = when (normVoiceId) {
            "tuan_khang" -> "Fenrir"
            "minh_quang" -> "Charon"
            "phuong_vy" -> "Aoede"
            "mai_chi" -> "Kore"
            "anh_duong" -> "Leda"
            else -> "Puck" // bao_nam
        }

        val genderPrompt = when (normVoiceId) {
            "tuan_khang" -> "Bạn là một DIỄN VIÊN NAM GIỌNG MIỀN NAM BỘ. Hãy dùng chất giọng đàn ông miền Nam hào sảng, phóng khoáng, dứt khoát, tự nhiên và nam tính."
            "phuong_vy" -> "Bạn là một DIỄN VIÊN NỮ GIỌNG MIỀN NAM BỘ. Hãy dùng chất giọng con gái miền Nam ngọt ngào, dịu dàng, mềm mại, truyền cảm và nữ tính."
            "minh_quang" -> "Bạn là một DIỄN VIÊN NAM TRƯỞNG THÀNH GIỌNG MIỀN BẮC. Hãy dùng chất giọng đàn ông trầm ấm, dày dặn, uy quyền, điềm tĩnh và sâu lắng."
            "mai_chi" -> "Bạn là một DIỄN VIÊN NỮ GIỌNG MIỀN BẮC. Hãy dùng chất giọng phụ nữ ngọt ngào, ấm áp, dịu dàng, giàu tình cảm và truyền cảm."
            "anh_duong" -> "Bạn là một DIỄN VIÊN NỮ TRẺ GIỌNG MIỀN BẮC. Hãy dùng chất giọng con gái trong trẻo, tươi sáng, thanh thoát, cao vút và đầy sức sống."
            else -> "Bạn là một DIỄN VIÊN NAM GIỌNG MIỀN BẮC TỰ NHIÊN. Hãy dùng chất giọng đàn ông tự nhiên, chuẩn mực, rõ ràng, đĩnh đạc và chân thực." // bao_nam
        }

        val googlePayload = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject {
                            put(
                                "text",
                                "$genderPrompt Tuyệt đối tuân thủ đúng giới tính nhân vật (đàn ông hoặc phụ nữ). Hãy đọc câu thoại sau bằng tiếng Việt với ngữ điệu chân thực, ngắt nghỉ tự nhiên:\n\"$cleanText\""
                            )
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.6)
                putJsonArray("responseModalities") {
                    add("AUDIO")
                }
                putJsonObject("speechConfig") {
                    putJsonObject("voiceConfig") {
                        putJsonObject("prebuiltVoiceConfig") {
                            put("voiceName", googleVoiceName)
                        }
                    }
                }
            }
        }.toString()

        val googleReq = Request.Builder()
            .url(googleUrl)
            .addHeader("Content-Type", "application/json")
            .post(googlePayload.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(googleReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val bodyStr = resp.body?.string() ?: return@withContext null
                val root = json.parseToJsonElement(bodyStr).jsonObject
                val candidates = root["candidates"]?.jsonArray
                val parts = candidates?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray
                val audioPart = parts?.firstOrNull { it.jsonObject.containsKey("inlineData") }
                val base64Data = audioPart?.jsonObject?.get("inlineData")?.jsonObject?.get("data")?.jsonPrimitive?.content
                if (!base64Data.isNullOrBlank()) {
                    return@withContext android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                }
            }
        } catch (_: Exception) {}

        null
    }

    override fun release() {}
}
