package com.jkhx.player.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

object ShapeTokens {
    val Badge = RoundedCornerShape(4.dp)
    val SmallPill = RoundedCornerShape(6.dp)
    val Chip = RoundedCornerShape(8.dp)
    val Button = RoundedCornerShape(10.dp)
    val Card = RoundedCornerShape(12.dp)
    val LargeChip = RoundedCornerShape(16.dp)
    val BannerCard = RoundedCornerShape(20.dp)
    val Circle = CircleShape
}

