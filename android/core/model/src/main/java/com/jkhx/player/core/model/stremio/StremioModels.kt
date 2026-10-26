package com.jkhx.player.core.model.stremio

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * Bộ giải mã linh hoạt cho StremioResourceDefinition:
 * Hỗ trợ cả dạng String ("catalog") lẫn Object ({"name": "catalog", "types": [...]})
 */
object StremioResourceSerializer : KSerializer<StremioResourceDefinition> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): StremioResourceDefinition {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("This serializer can only be used with Json")
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> StremioResourceDefinition(name = element.content)
            is JsonObject -> {
                val name = element["name"]?.jsonPrimitive?.content ?: ""
                val types = element["types"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                val idPrefixes = element["idPrefixes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                StremioResourceDefinition(name = name, types = types, idPrefixes = idPrefixes)
            }
            else -> StremioResourceDefinition(name = "")
        }
    }

    override fun serialize(encoder: Encoder, value: StremioResourceDefinition) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalStateException("This serializer can only be used with Json")
        if (value.types.isEmpty() && value.idPrefixes.isEmpty()) {
            jsonEncoder.encodeJsonElement(JsonPrimitive(value.name))
        } else {
            val jsonObject = buildJsonObject {
                put("name", value.name)
                if (value.types.isNotEmpty()) {
                    put("types", buildJsonArray {
                        value.types.forEach { add(JsonPrimitive(it)) }
                    })
                }
                if (value.idPrefixes.isNotEmpty()) {
                    put("idPrefixes", buildJsonArray {
                        value.idPrefixes.forEach { add(JsonPrimitive(it)) }
                    })
                }
            }
            jsonEncoder.encodeJsonElement(jsonObject)
        }
    }
}

/**
 * Bộ giải mã linh hoạt cho StremioExtraItem:
 * Hỗ trợ cả dạng String ("genre") lẫn Object ({"name": "genre", "isRequired": false, "options": [...]})
 */
object StremioExtraSerializer : KSerializer<StremioExtraItem> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): StremioExtraItem {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("This serializer can only be used with Json")
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> StremioExtraItem(name = element.content)
            is JsonObject -> {
                val name = element["name"]?.jsonPrimitive?.content ?: ""
                val isRequired = element["isRequired"]?.jsonPrimitive?.booleanOrNull ?: false
                val options = element["options"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                StremioExtraItem(name = name, isRequired = isRequired, options = options)
            }
            else -> StremioExtraItem(name = "")
        }
    }

    override fun serialize(encoder: Encoder, value: StremioExtraItem) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalStateException("This serializer can only be used with Json")
        if (value.options.isEmpty() && !value.isRequired) {
            jsonEncoder.encodeJsonElement(JsonPrimitive(value.name))
        } else {
            val jsonObject = buildJsonObject {
                put("name", value.name)
                if (value.isRequired) {
                    put("isRequired", true)
                }
                if (value.options.isNotEmpty()) {
                    put("options", buildJsonArray {
                        value.options.forEach { add(JsonPrimitive(it)) }
                    })
                }
            }
            jsonEncoder.encodeJsonElement(jsonObject)
        }
    }
}

@Serializable(with = StremioResourceSerializer::class)
data class StremioResourceDefinition(
    val name: String,
    val types: List<String> = emptyList(),
    val idPrefixes: List<String> = emptyList()
)

@Serializable(with = StremioExtraSerializer::class)
data class StremioExtraItem(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String> = emptyList()
)

@Serializable
data class StremioCatalogDefinition(
    val type: String,
    val id: String,
    val name: String? = null,
    val genres: List<String> = emptyList(),
    val extra: List<StremioExtraItem> = emptyList()
) {
    val displayName: String get() = name ?: "$type - $id"
}

@Serializable
data class StremioBehaviorHints(
    val configurable: Boolean = false,
    val configurationRequired: Boolean = false,
    val configurationUrl: String? = null
)

@Serializable
data class StremioManifest(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val description: String? = null,
    val resources: List<StremioResourceDefinition> = emptyList(),
    val types: List<String> = emptyList(),
    val catalogs: List<StremioCatalogDefinition> = emptyList(),
    val idPrefixes: List<String> = emptyList(),
    val logo: String? = null,
    val icon: String? = null,
    val background: String? = null,
    val behaviorHints: StremioBehaviorHints? = null
) {
    fun hasResource(resName: String): Boolean =
        resources.any { it.name.equals(resName, ignoreCase = true) }

    val effectiveIconUrl: String?
        get() = icon ?: logo

    fun getConfigureUrl(addonUrl: String): String? {
        if (!behaviorHints?.configurationUrl.isNullOrBlank()) {
            return behaviorHints?.configurationUrl
        }
        val isConfigurable = behaviorHints?.configurable == true || behaviorHints?.configurationRequired == true
        if (isConfigurable) {
            return if (addonUrl.contains("/manifest.json")) {
                addonUrl.replace("/manifest.json", "/configure")
            } else {
                addonUrl.trimEnd('/') + "/configure"
            }
        }
        return null
    }
}

@Serializable
data class StremioMetaPreview(
    val id: String,
    val type: String,
    val name: String? = null,
    val title: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val year: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList()
) {
    val displayName: String get() = name ?: title ?: id
    val displayPoster: String? get() = poster ?: logo
    val displayYear: Int? get() = year?.toIntOrNull() ?: releaseInfo?.take(4)?.toIntOrNull()
}

@Serializable
data class StremioCatalogResponse(
    val metas: List<StremioMetaPreview> = emptyList()
)

@Serializable
data class StremioVideo(
    val id: String,
    val title: String? = null,
    val name: String? = null,
    val released: String? = null,
    val thumbnail: String? = null,
    val episode: Int? = null,
    val season: Int? = null,
    val overview: String? = null
) {
    val displayTitle: String get() = title ?: name ?: "Tập ${episode ?: 1}"
}

@Serializable
data class StremioTrailer(
    val source: String? = null,
    val type: String? = null
)

@Serializable
data class StremioMetaDetail(
    val id: String,
    val type: String,
    val name: String? = null,
    val title: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val year: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val runtime: String? = null,
    val trailers: List<StremioTrailer> = emptyList(),
    val videos: List<StremioVideo> = emptyList()
) {
    val displayName: String get() = name ?: title ?: id
    val displayPoster: String? get() = poster ?: logo
    val displayYear: Int? get() = year?.toIntOrNull() ?: releaseInfo?.take(4)?.toIntOrNull()
}

@Serializable
data class StremioMetaResponse(
    val meta: StremioMetaDetail? = null
)

@Serializable
data class StremioProxyHeaders(
    val request: Map<String, String> = emptyMap(),
    val response: Map<String, String> = emptyMap()
)

@Serializable
data class StremioStreamBehaviorHints(
    val notWebReady: Boolean = false,
    val bingeGroup: String? = null,
    val proxyHeaders: StremioProxyHeaders? = null,
    val headers: Map<String, String> = emptyMap()
) {
    val allRequestHeaders: Map<String, String>
        get() {
            val map = mutableMapOf<String, String>()
            proxyHeaders?.request?.let { map.putAll(it) }
            map.putAll(headers)
            return map
        }
}

@Serializable
data class StremioStreamSubtitle(
    val id: String? = null,
    val url: String,
    val lang: String = "vi",
    val title: String? = null,
    val label: String? = null,
    @SerialName("SubFileName") val subFileName: String? = null
) {
    val displayTitle: String?
        get() = title ?: label ?: subFileName ?: id
}

@Serializable
data class StremioSubtitleResponse(
    val subtitles: List<StremioStreamSubtitle> = emptyList()
)

@Serializable
data class StremioStream(
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val quality: String? = null,
    val url: String? = null,
    val externalUrl: String? = null,
    val type: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val behaviorHints: StremioStreamBehaviorHints? = null,
    val subtitles: List<StremioStreamSubtitle> = emptyList()
) {
    val displayTitle: String
        get() = listOfNotNull(name, title ?: description)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
            .ifBlank { "Stream" }

    val effectiveUrl: String?
        get() = url ?: externalUrl
}

@Serializable
data class StremioStreamResponse(
    val streams: List<StremioStream> = emptyList()
)

