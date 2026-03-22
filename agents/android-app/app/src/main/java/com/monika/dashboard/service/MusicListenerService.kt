package com.monika.dashboard.service

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.monika.dashboard.data.DebugLog

class MusicListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "MusicListener"

        var currentMusic: MusicInfo? = null
            private set

        var isRunning: Boolean = false
            private set
    }

    data class MusicInfo(
        val title: String,
        val artist: String?,
        val app: String?
    )

    private var sessionManager: MediaSessionManager? = null

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateMusicFromSessions(controllers)
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        DebugLog.log("通知监听", "服务已启动")
        Log.i(TAG, "Service created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            sessionManager = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager
            sessionManager?.addOnActiveSessionsChangedListener(
                sessionListener,
                android.content.ComponentName(this, MusicListenerService::class.java)
            )
            // Check current sessions immediately
            val controllers = sessionManager?.getActiveSessions(
                android.content.ComponentName(this, MusicListenerService::class.java)
            )
            updateMusicFromSessions(controllers)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing notification listener permission", e)
        }
        Log.i(TAG, "Listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // We primarily use MediaSession API, but this keeps the service alive
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No-op
    }

    private fun updateMusicFromSessions(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) {
            currentMusic = null
            return
        }

        // Find the first actively playing session
        for (controller in controllers) {
            val state = controller.playbackState
            if (state?.state == PlaybackState.STATE_PLAYING) {
                val metadata = controller.metadata ?: continue
                val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                if (title.isNullOrBlank()) continue

                val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)

                currentMusic = MusicInfo(
                    title = title.take(256),
                    artist = artist?.take(256),
                    app = controller.packageName
                )
                DebugLog.log("音乐", "${title} - ${artist ?: "未知"}")
                return
            }
        }

        // No active playback found
        currentMusic = null
    }

    override fun onListenerDisconnected() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        isRunning = false
        currentMusic = null
        sessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }
}
