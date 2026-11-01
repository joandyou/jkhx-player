package com.jkhx.player.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.jkhx.player.ActivePlayerSession
import com.jkhx.player.MainActivity
import com.jkhx.player.R
import com.jkhx.player.core.player.tts.novel.NovelTtsManager

class NovelAudioService : Service() {

    companion object {
        const val CHANNEL_ID = "novel_audio_tts_channel_v3"
        const val NOTIFICATION_ID = 2002

        const val ACTION_START = "com.jkhx.player.action.NOVEL_START"
        const val ACTION_TOGGLE_PLAY = "com.jkhx.player.action.NOVEL_TOGGLE_PLAY"
        const val ACTION_PREV_PARAGRAPH = "com.jkhx.player.action.NOVEL_PREV_PARAGRAPH"
        const val ACTION_NEXT_PARAGRAPH = "com.jkhx.player.action.NOVEL_NEXT_PARAGRAPH"
        const val ACTION_STOP = "com.jkhx.player.action.NOVEL_STOP"

        fun start(context: Context) {
            val intent = Intent(context, NovelAudioService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NovelAudioService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var novelTtsManager: NovelTtsManager? = null
    private var mediaSession: MediaSessionCompat? = null

    private var lastNotifiedParagraph = -1
    private var lastNotifiedPlaying = false
    private var lastNotifiedChapter = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
        novelTtsManager = NovelTtsManager.getInstanceOrNull()
        novelTtsManager?.onServiceStateChanged = { isPlaying, chapterTitle, paragraphIdx, total ->
            updateNotification(isPlaying, chapterTitle, paragraphIdx, total)
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "NovelAudioService").apply {
            isActive = true
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    novelTtsManager?.togglePlayPause()
                }

                override fun onPause() {
                    novelTtsManager?.togglePlayPause()
                }

                override fun onSkipToNext() {
                    novelTtsManager?.nextParagraph()
                }

                override fun onSkipToPrevious() {
                    novelTtsManager?.previousParagraph()
                }

                override fun onStop() {
                    stopServiceInternal()
                }
            })
        }
    }

    private fun stopServiceInternal() {
        novelTtsManager?.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = novelTtsManager ?: NovelTtsManager.getInstanceOrNull().also {
            novelTtsManager = it
            it?.onServiceStateChanged = { isPlaying, chapterTitle, paragraphIdx, total ->
                updateNotification(isPlaying, chapterTitle, paragraphIdx, total)
            }
        }

        when (intent?.action) {
            ACTION_START -> {
                val isPlaying = manager?.isPlaying?.value ?: true
                val chapter = manager?.chapterTitle?.value ?: getString(R.string.reader_tts_default_chapter)
                val curIdx = manager?.currentParagraphIndex?.value ?: 0
                val total = manager?.paragraphs?.value?.size ?: 0
                val notification = buildNotification(isPlaying, chapter, curIdx, total)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_TOGGLE_PLAY -> {
                manager?.togglePlayPause()
            }
            ACTION_PREV_PARAGRAPH -> {
                manager?.previousParagraph()
            }
            ACTION_NEXT_PARAGRAPH -> {
                manager?.nextParagraph()
            }
            ACTION_STOP -> {
                stopServiceInternal()
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.reader_tts_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.reader_tts_channel_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        isPlaying: Boolean,
        chapterTitle: String,
        paragraphIdx: Int,
        totalParagraphs: Int
    ): Notification {
        val storyName = ActivePlayerSession.movieName.ifBlank { getString(R.string.reader_tts_reading_novel) }
        val contentText = if (totalParagraphs > 0) {
            "$chapterTitle • ${getString(R.string.reader_tts_paragraph_label, paragraphIdx + 1, totalParagraphs)}"
        } else {
            chapterTitle
        }

        // Cập nhật trạng thái MediaSession
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, paragraphIdx.toLong(), 1f)
            .build()
        mediaSession?.setPlaybackState(playbackState)

        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val prevIntent = Intent(this, NovelAudioService::class.java).apply { action = ACTION_PREV_PARAGRAPH }
        val prevPending = PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0))

        val toggleIntent = Intent(this, NovelAudioService::class.java).apply { action = ACTION_TOGGLE_PLAY }
        val togglePending = PendingIntent.getService(this, 2, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0))

        val nextIntent = Intent(this, NovelAudioService::class.java).apply { action = ACTION_NEXT_PARAGRAPH }
        val nextPending = PendingIntent.getService(this, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0))

        val stopIntent = Intent(this, NovelAudioService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 4, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0))

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) getString(R.string.player_pause) else getString(R.string.player_play)

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
            .setShowCancelButton(true)
            .setCancelButtonIntent(stopPending)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(storyName)
            .setContentText(contentText)
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setStyle(mediaStyle)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, getString(R.string.reader_tts_prev_paragraph), prevPending)
            .addAction(playPauseIcon, playPauseTitle, togglePending)
            .addAction(android.R.drawable.ic_media_next, getString(R.string.reader_tts_next_paragraph), nextPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.common_close), stopPending)
            .build()
    }

    private fun updateNotification(
        isPlaying: Boolean,
        chapterTitle: String,
        paragraphIdx: Int,
        totalParagraphs: Int
    ) {
        if (isPlaying == lastNotifiedPlaying && paragraphIdx == lastNotifiedParagraph && chapterTitle == lastNotifiedChapter) {
            return
        }
        lastNotifiedPlaying = isPlaying
        lastNotifiedParagraph = paragraphIdx
        lastNotifiedChapter = chapterTitle

        val notification = buildNotification(isPlaying, chapterTitle, paragraphIdx, totalParagraphs)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        novelTtsManager?.onServiceStateChanged = null
        mediaSession?.release()
        mediaSession = null
    }
}
