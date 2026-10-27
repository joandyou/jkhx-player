package com.jkhx.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jkhx.player.R
import com.jkhx.player.core.database.entity.FavoriteEntity
import com.jkhx.player.core.database.entity.HistoryEntity
import com.jkhx.player.core.model.MovieItem
import com.jkhx.player.ui.theme.AppTheme
import com.jkhx.player.ui.theme.ShapeTokens
import com.jkhx.player.ui.theme.tvFocusableItem

private val REGEX_COMPLETE_EPISODE = Regex(
    """(?:hoàn\s*tất|hoan\s*tat|trọn\s*bộ|tron\s*bo|full|tập|tap)?\s*\(?(\d+)\s*/\s*(\d+)\)?""",
    RegexOption.IGNORE_CASE
)
private val REGEX_EPISODE = Regex(
    """(?:tập|tap|ep|episode)\s*(\d+)""",
    RegexOption.IGNORE_CASE
)
private val REGEX_TOTAL_IN_NAME = Regex(
    """\(?(\d+)\s*(?:tập|eps?|episodes?)\)?""",
    RegexOption.IGNORE_CASE
)

/**
 * Thẻ phim dùng chung cho cả HomeScreen và MovieListScreen
 * Đồng bộ chuẩn UI: poster 2:3 bo góc 12dp, compound badge FHD/Hoàn Tất hoặc FHD + Full,
 * tiêu đề 2 dòng đậm và dòng phụ Year • Vietsub.
 */
@Composable
fun MovieCard(
    movie: MovieItem,
    modifier: Modifier = Modifier,
    sourceBadge: String? = null,
    onClick: () -> Unit
) {
    val isSeries = remember(movie.type, movie.episodeCurrent) {
        movie.type.equals("series", ignoreCase = true) ||
            movie.type.equals("tv", ignoreCase = true) ||
            movie.type.equals("tvshows", ignoreCase = true) ||
            movie.type.equals("hoathinh", ignoreCase = true) ||
            (!movie.episodeCurrent.isNullOrBlank() &&
             !movie.episodeCurrent.equals("Full", ignoreCase = true) &&
             !movie.episodeCurrent.equals("FULL", ignoreCase = true))
    }
    val qualityText = remember(movie.quality) {
        (movie.quality?.ifBlank { null } ?: "FHD").uppercase()
    }

    val completedLabel = stringResource(R.string.home_tag_completed)
    val completedFormat = stringResource(R.string.home_tag_completed_format)
    val episodeFormat = stringResource(R.string.home_tag_episode_format)
    val fullLabel = stringResource(R.string.home_tag_full)

    val episodeBadge = remember(movie.episodeCurrent, movie.name, isSeries, completedLabel, completedFormat, episodeFormat, fullLabel) {
        formatEpisodeBadge(
            raw = movie.episodeCurrent,
            movieName = movie.name,
            isSeries = isSeries,
            completedLabel = completedLabel,
            completedFormat = completedFormat,
            episodeFormat = episodeFormat,
            fullLabel = fullLabel
        )
    }

    val imageUrl = remember(movie.posterUrl, movie.thumbUrl) {
        movie.posterUrl?.ifBlank { null } ?: movie.thumbUrl
    }
    val context = LocalContext.current
    val badgeRed = AppTheme.colors.badgeRed
    val badgeDark = AppTheme.colors.badgeDark
    val subTextColor = AppTheme.colors.subText
    val ratingColor = AppTheme.colors.ratingStar

    Column(
        modifier = modifier
            .clip(ShapeTokens.Card)
            .tvFocusableItem(cornerRadius = 12.dp, focusedScale = 1.04f)
            .clickable(onClick = onClick)
    ) {
        // Khung Poster
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(ShapeTokens.Card)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = remember(imageUrl) {
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        .size(240, 360)
                        .crossfade(false)
                        .allowHardware(true)
                        .memoryCacheKey(imageUrl)
                        .build()
                },
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (isSeries) {
                // Phim bộ: Nếu có episodeBadge -> Compound badge: [ FHD | Hoàn Tất (6/6) ] hoặc [ FHD | Tập 6 ]
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(ShapeTokens.Badge)
                        .background(badgeDark),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(badgeRed)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = qualityText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    if (!episodeBadge.isNullOrBlank()) {
                        Text(
                            text = episodeBadge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                }
            } else {
                // Phim lẻ: Tag chất lượng màu đỏ [FHD]
                Surface(
                    color = badgeRed,
                    shape = ShapeTokens.Badge,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = qualityText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Tag Full góc trên bên phải (nếu có)
                if (!episodeBadge.isNullOrBlank()) {
                    Surface(
                        color = badgeDark,
                        shape = ShapeTokens.Badge,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = episodeBadge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Điểm đánh giá (Rating badge) ở góc dưới bên phải poster
            val movieRating = movie.rating
            if (movieRating != null && movieRating > 0.0) {
                Surface(
                    color = Color(0xCC000000),
                    shape = ShapeTokens.Badge,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "★",
                            fontSize = 9.sp,
                            color = ratingColor
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f", movieRating),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Huy hiệu nguồn plugin ở góc dưới bên trái poster (nếu có)
            if (!sourceBadge.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                    shape = ShapeTokens.Badge,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp)
                ) {
                    Text(
                        text = sourceBadge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        // Tiêu đề phim: 2 dòng, in đậm, cắt dấu 3 chấm nếu quá dài
        Text(
            text = movie.name,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(3.dp))

        // Thông tin phụ: Năm • Phụ đề/Thuyết minh
        val defaultLangStr = stringResource(R.string.home_tag_vietsub)
        val subText = remember(movie.year, movie.language, defaultLangStr) {
            val yearStr = movie.year?.takeIf { it > 0 }?.toString()
            val langStr = movie.language?.ifBlank { null } ?: defaultLangStr
            if (yearStr != null) "$yearStr • $langStr" else langStr
        }

        Text(
            text = subText,
            fontSize = 11.5.sp,
            color = subTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Thẻ phim đang xem (Continue Watching) với thanh tiến trình ở chân poster
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueWatchingCard(
    history: HistoryEntity,
    modifier: Modifier = Modifier,
    sourceBadge: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val progress = remember(history.positionMs, history.durationMs) {
        if (history.durationMs > 0L) {
            (history.positionMs.toFloat() / history.durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

    val context = LocalContext.current
    val badgeRed = AppTheme.colors.badgeRed
    val badgeDark = AppTheme.colors.badgeDark
    val subTextColor = AppTheme.colors.subText
    val defaultWatchingLabel = stringResource(R.string.movie_card_watching_now)
    val singleMovieBadge = stringResource(R.string.badge_single_movie)
    val rawEp = history.lastEpisodeName?.trim()
    val epName = when {
        rawEp.isNullOrBlank() -> defaultWatchingLabel
        rawEp.equals("Gốc", ignoreCase = true) || rawEp.equals("Tập Gốc", ignoreCase = true) -> singleMovieBadge
        else -> rawEp
    }

    Column(
        modifier = modifier
            .clip(ShapeTokens.Card)
            .tvFocusableItem(cornerRadius = 12.dp, focusedScale = 1.04f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(ShapeTokens.Card)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = remember(history.thumbUrl) {
                    ImageRequest.Builder(context)
                        .data(history.thumbUrl)
                        .size(240, 360)
                        .crossfade(false)
                        .allowHardware(true)
                        .memoryCacheKey(history.thumbUrl)
                        .build()
                },
                contentDescription = history.movieName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Icon Play tròn mờ tinh tế ở tâm poster gợi ý chạm để phát ngay
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.42f),
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.Center)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.95f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Badge tập phim góc trên bên trái: Chuyển "Gốc" sang "Phim Lẻ" (Việt) / "Full" (Anh)
            Surface(
                color = badgeDark,
                shape = ShapeTokens.Badge,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Text(
                    text = epName,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    maxLines = 1
                )
            }

            // Nút xóa lịch sử góc trên bên phải
            if (onDelete != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(ShapeTokens.Badge)
                        .background(Color(0x99000000))
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.movie_card_delete_history),
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Huy hiệu nguồn plugin ở góc dưới bên trái poster (nếu có, đặt trên thanh tiến trình)
            if (!sourceBadge.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                    shape = ShapeTokens.Badge,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 5.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = sourceBadge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        maxLines = 1
                    )
                }
            }

            // Thanh tiến trình ở chân poster
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0x80000000))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progress.coerceAtLeast(0.04f))
                        .background(badgeRed)
                )
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = history.movieName,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(3.dp))

        val percentText = "${(progress * 100).toInt()}%"
        val subText = if (epName.isNotBlank() && epName != defaultWatchingLabel) {
            "$percentText • $epName"
        } else {
            percentText
        }

        Text(
            text = subText,
            fontSize = 11.5.sp,
            color = subTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Thẻ phim/truyện trong màn hình Yêu thích (Favorites)
 * Gồm poster 2:3, huy hiệu nguồn plugin ở góc dưới bên trái,
 * nút xóa yêu thích (dấu x tương tự lịch sử xem) ở góc trên bên phải, và tiêu đề bên dưới.
 */
@Composable
fun FavoriteMovieCard(
    favorite: FavoriteEntity,
    modifier: Modifier = Modifier,
    sourceBadge: String? = null,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val subTextColor = AppTheme.colors.subText

    Column(
        modifier = modifier
            .clip(ShapeTokens.Card)
            .tvFocusableItem(cornerRadius = 12.dp, focusedScale = 1.04f)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(ShapeTokens.Card)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = remember(favorite.thumbUrl) {
                    ImageRequest.Builder(context)
                        .data(favorite.thumbUrl)
                        .size(240, 360)
                        .crossfade(false)
                        .allowHardware(true)
                        .memoryCacheKey(favorite.thumbUrl)
                        .build()
                },
                contentDescription = favorite.movieName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Nút xóa yêu thích góc trên bên phải (dấu x tương tự của lịch sử xem)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(ShapeTokens.Badge)
                    .background(Color(0x99000000))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.favorites_card_delete),
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }

            // Huy hiệu nguồn plugin ở góc dưới bên trái poster (nếu có)
            if (!sourceBadge.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                    shape = ShapeTokens.Badge,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp)
                ) {
                    Text(
                        text = sourceBadge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = favorite.movieName,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(3.dp))

        if (!sourceBadge.isNullOrBlank()) {
            Text(
                text = sourceBadge,
                fontSize = 11.5.sp,
                color = subTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Chuẩn hoá nhãn tập hiển thị trên card phim:
 * - Nếu ra đủ/full tập -> "Hoàn Tất (X/X)" hoặc "Hoàn Tất"
 * - Nếu đang ra -> "Tập X" (ví dụ: Tập 6, Tập 16)
 * - Tuyệt đối không hiển thị "Tập mới"
 */
private fun formatEpisodeBadge(
    raw: String?,
    movieName: String?,
    isSeries: Boolean,
    completedLabel: String,
    completedFormat: String,
    episodeFormat: String,
    fullLabel: String
): String? {
    val trimmed = raw?.trim() ?: ""

    // 1. Dạng Hoàn tất (X/X) hoặc (X/X) hoặc Tập X/Y
    val matchComplete = REGEX_COMPLETE_EPISODE.find(trimmed)
    if (matchComplete != null) {
        val curInt = matchComplete.groupValues[1].toIntOrNull()
        val totalInt = matchComplete.groupValues[2].toIntOrNull()
        return if (curInt != null && totalInt != null && curInt >= totalInt) {
            String.format(completedFormat, totalInt.toString(), totalInt.toString())
        } else if (curInt != null) {
            String.format(episodeFormat, curInt.toString())
        } else {
            String.format(episodeFormat, matchComplete.groupValues[1])
        }
    }

    // 2. Dạng "Tập X", "Tap X", "Ep X"
    val matchEp = REGEX_EPISODE.find(trimmed)
    if (matchEp != null) {
        val epNum = matchEp.groupValues[1].toIntOrNull() ?: 1
        return String.format(episodeFormat, epNum.toString())
    }

    // 3. Chuỗi thuần số (ví dụ: "6", "16")
    val pureNum = trimmed.toIntOrNull()
    if (pureNum != null) {
        return String.format(episodeFormat, pureNum.toString())
    }

    // 4. Chuỗi chứa "hoàn tất" hoặc "full"
    if (trimmed.contains("hoàn tất", ignoreCase = true) || trimmed.contains("hoan tat", ignoreCase = true) ||
        trimmed.contains("trọn bộ", ignoreCase = true) || trimmed.contains("tron bo", ignoreCase = true) ||
        trimmed.contains("completed", ignoreCase = true)) {
        if (movieName != null) {
            val totalInName = REGEX_TOTAL_IN_NAME.find(movieName)
            if (totalInName != null) {
                val total = totalInName.groupValues[1]
                return String.format(completedFormat, total, total)
            }
        }
        return completedLabel
    }
    if (trimmed.equals("full", ignoreCase = true)) {
        return if (isSeries) completedLabel else fullLabel
    }

    // 5. Nếu chuỗi rỗng hoặc không có số tập, quét số tập từ tên phim
    if (movieName != null) {
        val nameMatchComplete = REGEX_COMPLETE_EPISODE.find(movieName)
        if (nameMatchComplete != null) {
            val curInt = nameMatchComplete.groupValues[1].toIntOrNull()
            val totalInt = nameMatchComplete.groupValues[2].toIntOrNull()
            return if (curInt != null && totalInt != null && curInt >= totalInt) {
                String.format(completedFormat, totalInt.toString(), totalInt.toString())
            } else if (curInt != null) {
                String.format(episodeFormat, curInt.toString())
            } else null
        }
        val nameMatchEp = REGEX_EPISODE.find(movieName)
        if (nameMatchEp != null) {
            val epNum = nameMatchEp.groupValues[1].toIntOrNull() ?: 1
            return String.format(episodeFormat, epNum.toString())
        }
    }

    // 6. Nếu là phim lẻ: mặc định "Full"
    if (!isSeries) {
        return fullLabel
    }

    // 7. Nếu là phim bộ nhưng chưa rõ tập: trả về null (KHÔNG bịa "Tập 1", chỉ hiển thị badge [ FHD ])
    return null
}
