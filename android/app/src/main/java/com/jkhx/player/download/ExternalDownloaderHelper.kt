package com.jkhx.player.download

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.jkhx.player.R

object ExternalDownloaderHelper {

    fun openInExternalDownloader(
        context: Context,
        streamUrl: String,
        title: String,
        posterUrl: String? = null,
        headers: Map<String, String> = emptyMap()
    ) {
        val cleanUrl = streamUrl.substringBefore("#").substringBefore("|").ifBlank { streamUrl }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(cleanUrl), "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("title", title)
                if (!posterUrl.isNullOrBlank()) {
                    putExtra("poster", posterUrl)
                }

                val headerBundle = Bundle()
                headers.forEach { (k, v) ->
                    headerBundle.putString(k, v)
                }
                if (!headerBundle.isEmpty) {
                    putExtra("android.media.intent.extra.HTTP_HEADERS", headerBundle)
                    putExtra("android.provider.Browser.EXTRA_HEADERS", headerBundle)
                    putExtra("headers", headerBundle)
                }
                putExtra("secure_uri", true)
            }

            val chooser = Intent.createChooser(intent, context.getString(R.string.stream_download_external_chooser))
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.stream_download_external_error), Toast.LENGTH_SHORT).show()
        }
    }

    fun copyStreamUrlToClipboard(context: Context, rawUrl: String) {
        val cleanUrl = rawUrl.substringBefore("#").substringBefore("|").ifBlank { rawUrl }
        if (cleanUrl.isNotBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Stream Link", cleanUrl))
            Toast.makeText(context, context.getString(R.string.stream_copied_toast), Toast.LENGTH_SHORT).show()
        }
    }
}
