package com.jkhx.player.core.player.subtitle.provider

import com.jkhx.player.core.player.subtitle.model.OnlineSubtitleItem
import com.jkhx.player.core.player.subtitle.model.SubtitleSearchQuery
import java.io.File

interface SubtitleProvider {
    val name: String
    suspend fun search(query: SubtitleSearchQuery): List<OnlineSubtitleItem>
    suspend fun download(item: OnlineSubtitleItem, destFile: File): File
}

