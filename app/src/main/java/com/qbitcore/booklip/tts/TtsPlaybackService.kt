package com.qbitcore.booklip.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.qbitcore.booklip.BooklipApplication
import com.qbitcore.booklip.MainActivity
import com.qbitcore.booklip.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service that exists while text-to-speech is playing or paused:
 * it keeps the process alive with the screen off and publishes a media
 * session, so the notification shade, lock screen and headset buttons can
 * play / pause / stop — what `UIBackgroundModes = audio` plus
 * MPRemoteCommandCenter do in the iOS app.
 */
class TtsPlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: TtsController
    private lateinit var session: MediaSessionCompat

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        player = (application as BooklipApplication).tts
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Text to speech", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        session = MediaSessionCompat(this, "BooklipTts").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = player.resume()
                override fun onPause() = player.pause()
                override fun onStop() = player.stop()
            })
            isActive = true
        }
        // Must enter the foreground promptly after startForegroundService().
        publish(player.state.value)
        scope.launch {
            // The state also changes with every spoken sentence; the notification
            // only cares about play / pause and what is playing.
            player.state
                .map { it.copy(spokenRange = null, sleepMinutes = null) }
                .distinctUntilChanged()
                .collect { state -> if (state.isActive) publish(state) else shutDown() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> if (player.state.value.isPlaying) player.pause() else player.resume()
            ACTION_STOP -> player.stop()
        }
        if (!player.state.value.isActive) shutDown()
        return START_NOT_STICKY
    }

    // Swiping the app away ends the reading session.
    override fun onTaskRemoved(rootIntent: Intent?) {
        player.stop()
        shutDown()
    }

    private fun shutDown() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publish(state: TtsState) {
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, state.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, state.author.takeIf { it != "Unknown" }.orEmpty())
                .build()
        )
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP
                )
                .setState(
                    if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    if (state.isPlaying) 1f else 0f,
                )
                .build()
        )
        val notification = buildNotification(state)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type) }
    }

    private fun buildNotification(state: TtsState): Notification {
        val immutable = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        fun action(action: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, TtsPlaybackService::class.java).setAction(action), immutable,
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), immutable,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tts)
            .setContentTitle(state.title)
            .setContentText(state.author.takeIf { it.isNotBlank() && it != "Unknown" })
            .setContentIntent(open)
            .setOngoing(state.isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (state.isPlaying) R.drawable.ic_tts_pause else R.drawable.ic_tts_play,
                if (state.isPlaying) "Pause" else "Play",
                action(ACTION_TOGGLE, 1),
            )
            .addAction(R.drawable.ic_tts_stop, "Stop", action(ACTION_STOP, 2))
            .setDeleteIntent(action(ACTION_STOP, 2))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "tts_playback"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_TOGGLE = "com.qbitcore.booklip.tts.TOGGLE"
        private const val ACTION_STOP = "com.qbitcore.booklip.tts.STOP"

        fun start(context: Context) {
            // Playback always starts from the visible reader, so the app is in
            // the foreground here; if the system refuses anyway, speech still
            // plays — just without the background guarantee.
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, TtsPlaybackService::class.java))
            }
        }
    }
}
