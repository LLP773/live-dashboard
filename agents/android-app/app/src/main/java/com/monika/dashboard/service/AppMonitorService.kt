package com.monika.dashboard.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.monika.dashboard.data.DebugLog
import com.monika.dashboard.data.SettingsStore
import com.monika.dashboard.network.ReportClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentLinkedQueue

class AppMonitorService : AccessibilityService() {

    companion object {
        private const val TAG = "AppMonitor"
        private const val DEBOUNCE_MS = 500L
        private const val MAX_OFFLINE_QUEUE = 50

        var currentForegroundApp: String = ""
            private set

        var isRunning: Boolean = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var settings: SettingsStore? = null
    private var client: ReportClient? = null

    private var lastReportedApp = ""
    private var lastReportTime = 0L
    private var debounceJob: Job? = null
    private var heartbeatJob: Job? = null

    // Offline queue for reports when network is unavailable
    private val offlineQueue = ConcurrentLinkedQueue<PendingReport>()

    private data class PendingReport(
        val appId: String,
        val title: String,
        val batteryPercent: Int?,
        val batteryCharging: Boolean?,
        val timestamp: Long = System.currentTimeMillis()
    )

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(applicationContext)
        isRunning = true
        DebugLog.log("无障碍", "服务已启动")
        Log.i(TAG, "Service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        startHeartbeat()
        Log.i(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        // Ignore system UI and keyboard packages
        if (packageName.startsWith("com.android.systemui") ||
            packageName.contains("inputmethod")) return

        currentForegroundApp = packageName

        // Debounce: cancel previous pending report, schedule new one
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            reportAppChange(packageName)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        isRunning = false
        currentForegroundApp = ""
        DebugLog.log("无障碍", "服务已停止")
        heartbeatJob?.cancel()
        debounceJob?.cancel()
        scope.cancel()
        client?.shutdown()
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }

    private fun startHeartbeat() {
        heartbeatJob = scope.launch {
            val store = settings ?: return@launch
            while (isActive) {
                val intervalSec = store.reportInterval.first()
                delay(intervalSec * 1000L)

                if (currentForegroundApp.isNotEmpty()) {
                    sendReport(currentForegroundApp, currentForegroundApp)
                }
            }
        }
    }

    private suspend fun reportAppChange(packageName: String) {
        if (packageName == lastReportedApp) return

        DebugLog.log("上报", "切换到 $packageName")
        sendReport(packageName, packageName)
        lastReportedApp = packageName
        lastReportTime = System.currentTimeMillis()
    }

    private suspend fun sendReport(appId: String, title: String) {
        // Check if monitoring is enabled
        val store = settings ?: return
        val enabled = store.monitoringEnabled.first()
        if (!enabled) return

        val reportClient = getOrCreateClient() ?: return

        val battery = getBatteryInfo()
        val music = MusicListenerService.currentMusic

        val result = runCatching {
            reportClient.reportApp(
                appId = appId,
                windowTitle = title,
                batteryPercent = battery?.first,
                batteryCharging = battery?.second,
                musicTitle = music?.title,
                musicArtist = music?.artist,
                musicApp = music?.app
            )
        }.getOrElse { Result.failure(it) }

        if (result.isFailure) {
            // Queue for retry if offline
            if (offlineQueue.size < MAX_OFFLINE_QUEUE) {
                offlineQueue.add(PendingReport(appId, title, battery?.first, battery?.second))
            }
            DebugLog.log("上报", "失败: ${result.exceptionOrNull()?.message}")
            Log.w(TAG, "Report failed: ${result.exceptionOrNull()?.message}")
        } else {
            // Flush offline queue on success
            flushOfflineQueue(reportClient)
        }
    }

    private suspend fun flushOfflineQueue(reportClient: ReportClient) {
        while (offlineQueue.isNotEmpty()) {
            val pending = offlineQueue.peek() ?: break
            val result = runCatching {
                reportClient.reportApp(
                    appId = pending.appId,
                    windowTitle = pending.title,
                    batteryPercent = pending.batteryPercent,
                    batteryCharging = pending.batteryCharging
                )
            }.getOrElse { Result.failure(it) }

            if (result.isSuccess) {
                offlineQueue.poll()
            } else {
                break // Stop flushing on first failure
            }
        }
    }

    private suspend fun getOrCreateClient(): ReportClient? {
        client?.let { return it }
        val store = settings ?: return null
        val url = store.serverUrl.first()
        val token = store.getToken()
        if (url.isEmpty() || token.isNullOrEmpty()) return null

        return try {
            ReportClient(url, token).also { client = it }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create client: ${e.message}")
            null
        }
    }

    private fun getBatteryInfo(): Pair<Int, Boolean>? {
        return try {
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryIntent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                if (level >= 0 && scale > 0) {
                    val percent = (level * 100) / scale
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                    Pair(percent, charging)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
