package com.jkhx.player.ui.screens.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jkhx.player.ui.theme.AppTheme
import com.jkhx.player.ui.theme.ShapeTokens

/**
 * Hiệu ứng cọ vẽ animate-pulse dịu nhẹ, chu kỳ nhanh tạo cảm giác tải tức thì
 */
@Composable
fun rememberSkeletonPulseBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "skeleton_pulse")
    val alphaAnim by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
    return Brush.linearGradient(
        colors = listOf(
            baseColor.copy(alpha = alphaAnim * 0.75f),
            baseColor.copy(alpha = alphaAnim),
            highlightColor.copy(alpha = alphaAnim * 0.80f),
            baseColor.copy(alpha = alphaAnim * 0.75f)
        )
    )
}

/**
 * Khối placeholder cơ bản bo góc với brush mờ ảo
 */
@Composable
fun SkeletonBlock(
    brush: Brush,
    width: Dp,
    height: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush)
    )
}

/**
 * Placeholder cho phần HeroBanner Adaptive Cinema Showcase (Trái: Hàng Chips + Tiêu đề | Phải: Card Poster 2:3 cao sát Top Menu | Đáy: Thumbnail Strip)
 */
@Composable
fun HeroBannerSkeleton(
    brush: Brush,
    isTablet: Boolean,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val bannerHeight = if (isTablet) 420.dp else (configuration.screenHeightDp.dp * 0.54f).coerceIn(405.dp, 460.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(bannerHeight)
            .background(brush)
    ) {
        // Lớp chuyển tiếp Gradient mờ dần từ thân ảnh xuống đáy màn hình
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bannerHeight * 0.72f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.35f to MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                        0.75f to MaterialTheme.colorScheme.background.copy(alpha = 0.90f),
                        1.0f to MaterialTheme.colorScheme.background
                    )
                )
        )

        // Bố cục Adaptive Cinema Showcase (Trái: Thông tin phim | Phải: Card Poster lớn | Đáy: Dải Mini-Poster full width)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    top = if (isTablet) 88.dp else 90.dp,
                    bottom = 8.dp
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = if (isTablet) 24.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cột Trái: Tiêu đề trên cùng -> Tên gốc -> Hàng Chips ở dưới
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = if (isTablet) 24.dp else 14.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    SkeletonBlock(
                        brush = brush,
                        width = if (isTablet) 300.dp else 190.dp,
                        height = if (isTablet) 28.dp else 22.dp,
                        cornerRadius = 6.dp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SkeletonBlock(
                        brush = brush,
                        width = if (isTablet) 200.dp else 130.dp,
                        height = 14.dp,
                        cornerRadius = 4.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SkeletonBlock(brush = brush, width = 68.dp, height = 22.dp, cornerRadius = 8.dp)
                        SkeletonBlock(brush = brush, width = 44.dp, height = 22.dp, cornerRadius = 8.dp)
                        SkeletonBlock(brush = brush, width = 58.dp, height = 22.dp, cornerRadius = 8.dp)
                        SkeletonBlock(brush = brush, width = 36.dp, height = 22.dp, cornerRadius = 8.dp)
                    }
                }

                // Cột Phải: Card Poster 2:3 lớn căn giữa
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.95f)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(if (isTablet) 16.dp else 14.dp))
                        .background(brush)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dải 8 Mini-Poster Thumbnail Strip ở đáy căn giữa full chiều rộng
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isTablet) 24.dp else 16.dp)
                ) {
                    repeat(if (isTablet) 8 else 6) { idx ->
                        SkeletonBlock(
                            brush = brush,
                            width = if (idx == 0) 44.dp else 38.dp,
                            height = if (idx == 0) 62.dp else 54.dp,
                            cornerRadius = 8.dp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Placeholder cho hàng "Bạn đang quan tâm gì?" (Quick Categories)
 */
@Composable
fun QuickCategoriesSkeleton(
    brush: Brush,
    isTablet: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        SkeletonBlock(
            brush = brush,
            width = 160.dp,
            height = 16.dp,
            cornerRadius = 4.dp,
            modifier = Modifier.padding(horizontal = if (isTablet) 28.dp else 16.dp, vertical = 6.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isTablet) 28.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val chipWidths = listOf(84.dp, 92.dp, 76.dp, 88.dp, 96.dp)
            chipWidths.forEach { width ->
                SkeletonBlock(
                    brush = brush,
                    width = width,
                    height = 32.dp,
                    cornerRadius = 16.dp
                )
            }
        }
    }
}

/**
 * Placeholder cho thẻ phim đơn lẻ (Poster tỷ lệ 2:3 + Tiêu đề)
 */
@Composable
fun MovieCardSkeleton(
    brush: Brush,
    isTablet: Boolean
) {
    val cardWidth = if (isTablet) AppTheme.dimens.cardWidthTablet else AppTheme.dimens.cardWidthMobile
    Column(
        modifier = Modifier.width(cardWidth)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(ShapeTokens.Card)
                .background(brush)
        )
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBlock(
            brush = brush,
            width = cardWidth * 0.85f,
            height = 14.dp,
            cornerRadius = 4.dp
        )
        Spacer(modifier = Modifier.height(4.dp))
        SkeletonBlock(
            brush = brush,
            width = cardWidth * 0.50f,
            height = 11.dp,
            cornerRadius = 3.dp
        )
    }
}

/**
 * Placeholder cho hàng phim ngang (Movie Section Row)
 */
@Composable
fun MovieRowSkeleton(
    brush: Brush,
    titleWidth: Dp,
    isTablet: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.dimens.space16, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SkeletonBlock(
                brush = brush,
                width = titleWidth,
                height = 18.dp,
                cornerRadius = 4.dp
            )
            SkeletonBlock(
                brush = brush,
                width = 20.dp,
                height = 16.dp,
                cornerRadius = 4.dp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.dimens.space16),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space12)
        ) {
            repeat(if (isTablet) 6 else 4) {
                MovieCardSkeleton(brush = brush, isTablet = isTablet)
            }
        }
    }
}

/**
 * Toàn bộ màn hình Skeleton Loading cho HomeScreen khi mở app hoặc đổi Provider
 */
@Composable
fun HomeSkeletonLoading(
    modifier: Modifier = Modifier,
    isTablet: Boolean = false
) {
    val brush = rememberSkeletonPulseBrush()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item(key = "skeleton_hero") {
            HeroBannerSkeleton(brush = brush, isTablet = isTablet)
        }
        item(key = "skeleton_categories") {
            QuickCategoriesSkeleton(brush = brush, isTablet = isTablet)
        }
        item(key = "skeleton_row_1") {
            MovieRowSkeleton(brush = brush, titleWidth = 160.dp, isTablet = isTablet)
        }
        item(key = "skeleton_row_2") {
            MovieRowSkeleton(brush = brush, titleWidth = 140.dp, isTablet = isTablet)
        }
        item(key = "skeleton_row_3") {
            MovieRowSkeleton(brush = brush, titleWidth = 180.dp, isTablet = isTablet)
        }
    }
}
