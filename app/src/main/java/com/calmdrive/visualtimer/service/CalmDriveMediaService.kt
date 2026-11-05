package com.calmdrive.visualtimer.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import com.calmdrive.visualtimer.CalmDriveApp
import com.calmdrive.visualtimer.MainActivity
import com.calmdrive.visualtimer.R
import com.calmdrive.visualtimer.data.PreferencesRepository
import com.calmdrive.visualtimer.engine.RenderState
import com.calmdrive.visualtimer.engine.SilentKeepAlivePlayer
import com.calmdrive.visualtimer.engine.TimerArtworkRenderer
import com.calmdrive.visualtimer.engine.TimerEngine
import com.calmdrive.visualtimer.model.NumberMode
import com.calmdrive.visualtimer.model.VisualStyle

class CalmDriveMediaService : MediaBrowserServiceCompat() {

    companion object {
        const val ACTION_SUBTRACT_1M = "com.calmdrive.visualtimer.ACTION_SUBTRACT_1M"
        const val ACTION_ADD_1M = "com.calmdrive.visualtimer.ACTION_ADD_1M"
        const val ACTION_ADD_5M = "com.calmdrive.visualtimer.ACTION_ADD_5M"
        const val ACTION_RESET = "com.calmdrive.visualtimer.ACTION_RESET"
        const val ACTION_SKIP_THEN = "com.calmdrive.visualtimer.ACTION_SKIP_THEN"

        private const val NOTIFICATION_ID = 1001
        private const val ROOT_ID = "calmdrive_root"
        private const val TAB_ROUTINES = "tab_routines"
        private const val TAB_QUICK = "tab_quick"
        private const val TAB_STYLES = "tab_styles"

        var instance: CalmDriveMediaService? = null
            private set
    }

    private lateinit var mediaSession: MediaSessionCompat
    lateinit var timerEngine: TimerEngine
        private set
    private lateinit var prefs: PreferencesRepository
    private val silentKeepAlive = SilentKeepAlivePlayer()

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = PreferencesRepository(this)
        timerEngine = TimerEngine(this)

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this, 0, sessionActivityIntent, PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSessionCompat(this, "CalmDriveMediaSession").apply {
            setSessionActivity(sessionActivityPendingIntent)
            setCallback(sessionCallback)
            isActive = true
        }
        sessionToken = mediaSession.sessionToken

        timerEngine.onStateChanged = { renderState ->
            updateMediaSessionAndNotification(renderState)
        }

        // Start initial state in ready/paused mode
        updateMediaSessionAndNotification(timerEngine.buildCurrentState())
    }

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            if (timerEngine.isPaused) {
                timerEngine.togglePlayPause()
            }
            silentKeepAlive.start()
        }

        override fun onPause() {
            if (!timerEngine.isPaused) {
                timerEngine.togglePlayPause()
            }
            silentKeepAlive.pause()
        }

        override fun onSkipToNext() {
            timerEngine.skipToThen()
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            when (action) {
                ACTION_SUBTRACT_1M -> timerEngine.adjustTime(-60)
                ACTION_ADD_1M -> timerEngine.adjustTime(60)
                ACTION_ADD_5M -> timerEngine.adjustTime(300)
                ACTION_RESET -> {
                    timerEngine.resetTimer()
                    silentKeepAlive.pause()
                }
                ACTION_SKIP_THEN -> timerEngine.skipToThen()
            }
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            if (mediaId == null) return
            when {
                mediaId.startsWith("routine_") -> {
                    val id = mediaId.removePrefix("routine_")
                    val found = prefs.getRoutines().find { it.id == id }
                    if (found != null) {
                        timerEngine.startRoutine(found, announce = true, autoStart = false)
                    }
                }
                mediaId.startsWith("quick_") -> {
                    val mins = mediaId.removePrefix("quick_").toIntOrNull() ?: 5
                    timerEngine.startQuickTimer(mins, autoStart = false)
                }
                mediaId.startsWith("style_") -> {
                    val styleId = mediaId.removePrefix("style_")
                    prefs.visualStyle = VisualStyle.fromId(styleId)
                    timerEngine.notifyState()
                }
            }
        }
    }

    private fun updateMediaSessionAndNotification(state: RenderState) {
        // 1. Generate 512x512 Bitmap Artwork for Coolwalk Media Card
        val artwork = TimerArtworkRenderer.render(state)

        val titleText = when {
            state.remainingSec <= 0 -> "🎉 Arrived • ${state.currentTitle} ${state.currentIcon}"
            state.numberMode == NumberMode.NONE -> "${state.currentTitle} ${state.currentIcon}"
            else -> "${state.formattedTime} left • ${state.currentTitle} ${state.currentIcon}"
        }

        val subtitleText = when {
            state.remainingSec == state.totalSec && state.isPaused -> "Ready • Tap Play to start"
            state.isPaused -> "⏸ Paused (Tap Play to resume)"
            state.isFirstStep && !state.thenTitle.isNullOrEmpty() ->
                "Then: ${state.thenIcon ?: "✨"} ${state.thenTitle} (${timerEngine.activeRoutine.thenMins}m)"
            !state.isFirstStep -> "Step 2 of 2 • Doing great!"
            else -> "Visual Timer"
        }

        // 2. Update MediaMetadataCompat
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, titleText)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitleText)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "CalmDrive")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.totalSec * 1000L)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
            .build()
        mediaSession.setMetadata(metadata)

        // 3. Configure PlaybackStateCompat with CustomActions for the car screen
        val playbackStateVal = if (state.isPaused) {
            PlaybackStateCompat.STATE_PAUSED
        } else {
            PlaybackStateCompat.STATE_PLAYING
        }

        val elapsedMillis = ((state.totalSec - state.remainingSec) * 1000L).coerceAtLeast(0L)
        val playbackSpeed = if (state.isPaused) 0f else 1f

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            )
            .setState(playbackStateVal, elapsedMillis, playbackSpeed)
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    ACTION_SUBTRACT_1M,
                    getString(R.string.action_minus_1m),
                    R.drawable.ic_minus_1m
                ).build()
            )
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    ACTION_ADD_1M,
                    getString(R.string.action_plus_1m),
                    R.drawable.ic_plus_1m
                ).build()
            )
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    ACTION_ADD_5M,
                    getString(R.string.action_plus_5m),
                    R.drawable.ic_plus_5m
                ).build()
            )
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    ACTION_RESET,
                    getString(R.string.action_reset),
                    R.drawable.ic_reset
                ).build()
            )

        if (state.isFirstStep && !state.thenTitle.isNullOrEmpty()) {
            stateBuilder.addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    ACTION_SKIP_THEN,
                    getString(R.string.action_skip_then),
                    R.drawable.ic_timer
                ).build()
            )
        }

        mediaSession.setPlaybackState(stateBuilder.build())

        // 4. Update Foreground Service Notification
        val notification = buildForegroundNotification(titleText, subtitleText)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildForegroundNotification(title: String, subtitle: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (timerEngine.isPaused) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play, "Play",
                androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this, PlaybackStateCompat.ACTION_PLAY
                )
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause, "Pause",
                androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this, PlaybackStateCompat.ACTION_PAUSE
                )
            )
        }

        return NotificationCompat.Builder(this, CalmDriveApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentIntent(contentPendingIntent)
            .addAction(playPauseAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            .setOngoing(!timerEngine.isPaused)
            .setOnlyAlertOnce(true)
            .build()
    }

    // MediaBrowserService Tree for car screen drawer (browse routines & quick timers)
    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot(ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        val items = mutableListOf<MediaBrowserCompat.MediaItem>()
        when (parentId) {
            ROOT_ID -> {
                items.add(createBrowseFolder(TAB_ROUTINES, "Saved Routines", "First → Then car schedules"))
                items.add(createBrowseFolder(TAB_QUICK, "Quick Timers", "Fixed 2m to 30m countdowns"))
                items.add(createBrowseFolder(TAB_STYLES, "Visual Style", "Disc, Journey Path, Blocks"))
            }
            TAB_ROUTINES -> {
                prefs.getRoutines().forEach { r ->
                    val desc = if (!r.thenTitle.isNullOrEmpty()) {
                        "Then: ${r.thenIcon ?: ""} ${r.thenTitle} (${r.thenMins}m)"
                    } else "Single timer"
                    items.add(createPlayableItem("routine_${r.id}", "${r.firstIcon} ${r.firstTitle} (${r.firstMins}m)", desc))
                }
            }
            TAB_QUICK -> {
                listOf(2, 5, 10, 15, 20, 30).forEach { mins ->
                    items.add(createPlayableItem("quick_$mins", "⏱️ $mins Minute Timer", "Quick countdown"))
                }
            }
            TAB_STYLES -> {
                VisualStyle.entries.forEach { style ->
                    val isCurrent = if (prefs.visualStyle == style) " (Active)" else ""
                    items.add(createPlayableItem("style_${style.id}", "${style.icon} ${style.title}$isCurrent", "Switch graphic"))
                }
            }
        }
        result.sendResult(items)
    }

    private fun createBrowseFolder(id: String, title: String, subtitle: String): MediaBrowserCompat.MediaItem {
        val desc = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()
        return MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    private fun createPlayableItem(id: String, title: String, subtitle: String): MediaBrowserCompat.MediaItem {
        val desc = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()
        return MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    override fun onDestroy() {
        super.onDestroy()
        silentKeepAlive.release()
        timerEngine.release()
        mediaSession.release()
        instance = null
    }
}
