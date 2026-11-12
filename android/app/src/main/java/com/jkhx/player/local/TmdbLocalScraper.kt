package com.jkhx.player.local

import android.content.Context
import android.util.Log
import com.jkhx.player.JkhxApplication
import com.jkhx.player.core.database.AppDatabase
import com.jkhx.player.core.network.tmdb.TmdbConfig
import com.jkhx.player.core.network.tmdb.TmdbItemResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Trình cào và làm giàu dữ liệu TMDB cho Thư viện Offline / Nội bộ và Máy chủ từ xa.
 * Hỗ trợ bóc tách tên phim, năm phát hành, truy vấn API TMDB theo ngôn ngữ ứng dụng
 * và lưu trữ thông tin (poster w500, backdrop w1280, rating, năm, tóm tắt tiếng Việt) vào Room Database.
 */
object TmdbLocalScraper {
    private const val TAG = "TmdbLocalScraper"

    private val yearRegex = Regex("""\b(19\d{2}|20\d{2})\b""")

    private val sceneTags = listOf(
        "1080p", "720p", "2160p", "4k", "bluray", "bdrip", "brrip", "webrip", "web-dl", "web",
        "remux", "hdr", "hdr10", "hdr10plus", "dovi", "dv", "dolby", "atmos", "truehd", "dts-hd",
        "dts", "ac3", "ddp5", "ddp", "aac", "x264", "x265", "h264", "h265", "hevc", "avc",
        "10bit", "8bit", "yify", "yts", "rarbg", "galaxyrg", "sparks", "geckos", "flux",
        "vietsub", "thuyetminh", "longtieng", "thuyết minh", "lồng tiếng", "sub", "dub"
    )

    private val episodeCleanRegex = Regex("""(?i)(?:s\d{1,2}e\d{1,3}|ep?\s*\d{1,3}|tập\s*\d{1,3}|part\s*\d{1,3})""")

    /**
     * Tách tên phim sạch và năm phát hành từ tên file hoặc tên thư mục
     */
    fun extractTitleAndYear(rawName: String): Pair<String, String?> {
        var clean = rawName.substringBeforeLast('.') // Bỏ đuôi file nếu có
        // Bỏ các cặp ngoặc vuông [tags]
        clean = clean.replace(Regex("""\[.*?\]"""), " ")
        // Bỏ ký hiệu tập S01E01...
        clean = clean.replace(episodeCleanRegex, " ")

        // Thay các dấu chấm, gạch dưới, gạch ngang thành dấu cách
        clean = clean.replace('.', ' ').replace('_', ' ').replace('-', ' ')

        // Tìm năm phát hành
        val yearMatch = yearRegex.findAll(clean).lastOrNull()
        val year = yearMatch?.groupValues?.get(1)

        // Cắt bỏ phần sau năm nếu tìm thấy năm
        if (yearMatch != null) {
            clean = clean.substring(0, yearMatch.range.first)
        }

        // Loại bỏ các tag kỹ thuật
        for (tag in sceneTags) {
            clean = clean.replace(Regex("""(?i)\b$tag\b"""), " ")
        }

        // Dọn dẹp khoảng trắng thừa và ngoặc tròn rỗng
        clean = clean.replace(Regex("""\(\s*\)"""), " ")
        clean = clean.trim().replace(Regex("""\s+"""), " ")

        // Nếu chuỗi làm sạch quá ngắn (do tên gốc toàn tag), fallback về rawName bỏ đuôi
        if (clean.length < 2) {
            clean = rawName.substringBeforeLast('.').trim()
        }

        return Pair(clean, year)
    }

    /**
     * Tự động cào và cập nhật metadata TMDB cho 1 mục thư viện (chạy ngầm bất đồng bộ)
     */
    suspend fun scrapeAndEnrich(
        context: Context,
        entityId: String,
        rawTitle: String,
        type: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val (searchTitle, parsedYear) = extractTitleAndYear(rawTitle)
            if (searchTitle.isBlank()) return@withContext false

            val tmdbRepo = JkhxApplication.instance.tmdbRepository
            val language = TmdbConfig.getAppLanguage()

            // 1. Tìm kiếm TMDB
            var results = tmdbRepo.searchTmdb(query = searchTitle, type = type, language = language)
            if (results.isEmpty() && searchTitle != rawTitle) {
                // Thử lại với tên gốc nếu clean title không tìm thấy
                results = tmdbRepo.searchTmdb(query = rawTitle.substringBeforeLast('.'), type = type, language = language)
            }
            if (results.isEmpty()) {
                Log.d(TAG, "Không tìm thấy kết quả TMDB cho: $searchTitle")
                return@withContext false
            }

            // 2. Tìm kết quả phù hợp nhất (ưu tiên khớp năm)
            val bestMatch = if (!parsedYear.isNullOrBlank()) {
                results.firstOrNull { r ->
                    val rYear = (r.releaseDate ?: r.firstAirDate)?.take(4)
                    rYear == parsedYear
                } ?: results.first()
            } else {
                results.first()
            }

            // 3. Áp dụng kết quả TMDB vào Database
            applyTmdbMatchInternal(context, entityId, bestMatch, type, language, parsedYear)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi tự động cào TMDB cho $rawTitle: ${e.message}", e)
            false
        }
    }

    /**
     * Áp dụng kết quả TMDB được chọn thủ công từ Fix Match Dialog
     */
    suspend fun applyTmdbMatch(
        context: Context,
        entityId: String,
        result: TmdbItemResult,
        type: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val language = TmdbConfig.getAppLanguage()
            applyTmdbMatchInternal(context, entityId, result, type, language, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi áp dụng Fix Match cho entity $entityId: ${e.message}", e)
            false
        }
    }

    private suspend fun applyTmdbMatchInternal(
        context: Context,
        entityId: String,
        result: TmdbItemResult,
        type: String?,
        language: String,
        fallbackYear: String?
    ) {
        val tmdbRepo = JkhxApplication.instance.tmdbRepository
        val mediaType = result.mediaType ?: type ?: "movie"

        // Lấy chi tiết bổ sung (overview, genres, backdrop...)
        val detail = tmdbRepo.getTmdbDetail(result.id.toString(), type = mediaType, language = language)

        val title = detail?.title?.ifBlank { null }
            ?: detail?.name?.ifBlank { null }
            ?: result.title?.ifBlank { null }
            ?: result.name?.ifBlank { null }
            ?: "Không có tiêu đề"

        val originalTitle = detail?.originalTitle?.ifBlank { null }
            ?: detail?.originalName?.ifBlank { null }
            ?: result.originalTitle?.ifBlank { null }
            ?: result.originalName

        val posterPath = detail?.posterPath?.ifBlank { null } ?: result.posterPath?.ifBlank { null }
        val posterUrl = TmdbConfig.imageUrl(posterPath, "w500")

        val backdropPath = detail?.backdropPath?.ifBlank { null } ?: result.backdropPath?.ifBlank { null }
        val backdropUrl = TmdbConfig.imageUrl(backdropPath, "w1280")

        val overview = detail?.overview?.ifBlank { null } ?: result.overview?.ifBlank { null }

        val rating = detail?.voteAverage ?: result.voteAverage

        val year = (detail?.releaseDate ?: detail?.firstAirDate ?: result.releaseDate ?: result.firstAirDate)?.take(4)
            ?: fallbackYear

        val genres = detail?.genres?.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name }

        AppDatabase.getInstance(context).localLibraryDao().updateMetadata(
            id = entityId,
            title = title,
            coverUrl = posterUrl,
            description = overview,
            tmdbId = result.id,
            rating = rating,
            year = year,
            backdropUrl = backdropUrl,
            originalTitle = originalTitle,
            genres = genres
        )
        Log.d(TAG, "Đã cập nhật TMDB metadata thành công cho ID $entityId: $title ($year)")
    }
}
