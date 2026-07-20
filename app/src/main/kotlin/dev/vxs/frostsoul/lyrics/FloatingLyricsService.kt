package dev.vxs.frostsoul.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.vxs.frostsoul.MainActivity
import dev.vxs.frostsoul.R
import dev.vxs.frostsoul.lyrics.LyricsManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Foreground service that hosts the floating lyrics overlay.
 * Runs as a foreground service with mediaPlayback type to stay alive during playback.
 */
@AndroidEntryPoint
class FloatingLyricsService : LifecycleService() {

    @Inject
    lateinit var lyricsManager: LyricsManager

    @Inject
    lateinit var overlayWindowManager: OverlayWindowManager

    @Inject
    lateinit var overlaySettings: OverlaySettings

    private val binder = LocalBinder()
    private var composeView: ComposeView? = null

    inner class LocalBinder : Binder() {
        fun getService(): FloatingLyricsService = this@FloatingLyricsService
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        setupOverlay()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        overlayWindowManager.hide()
        super.onDestroy()
    }

    private fun setupOverlay() {
        composeView = ComposeView(this).apply {
            setContent {
                val currentEntry by lyricsManager.currentEntry.collectAsState()
                val nextEntry by lyricsManager.playbackState.collectAsState()
                val isPlaying by lyricsManager.isPlaying.collectAsState()

                // Collect settings
                val settingsSnapshot = remember {
                    OverlaySettingsData(
                        transparency = 0.75f,
                        fontSize = 18,
                        twoLineMode = true,
                        autoHideWhenPaused = true,
                        isPlaying = isPlaying,
                        showTranslation = false,
                        showRomanized = false
                    )
                }

                FloatingLyricsOverlay(
                    currentEntry = currentEntry,
                    nextEntry = nextEntry.nextEntry,
                    settings = settingsSnapshot.copy(isPlaying = isPlaying)
                )
            }
        }

        // Show overlay
        overlayWindowManager.show(
            content = composeView!!,
            x = 0,
            y = 200,
            locked = false,
            touchThrough = false
        )

        // Observe settings changes
        combine(
            overlaySettings.locked,
            overlaySettings.touchThroughWhenLocked
        ) { locked, touchThrough ->
            overlayWindowManager.updateLockedState(locked, touchThrough)
        }.launchIn(lifecycleScope)
    }

    private fun createNotification(): Notification {
        val channelId = "floating_lyrics_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Floating Lyrics",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating lyrics overlay active"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("ArchiveTune Floating Lyrics")
            .setContentText("Lyrics overlay is active")
            .setSmallIcon(R.drawable.lyrics)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1002

        fun start(context: Context) {
            val intent = Intent(context, FloatingLyricsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingLyricsService::class.java))
        }
    }
}
