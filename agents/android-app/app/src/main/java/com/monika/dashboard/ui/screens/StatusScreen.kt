package com.monika.dashboard.ui.screens

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.monika.dashboard.data.DebugLog
import com.monika.dashboard.health.HealthConnectManager
import com.monika.dashboard.service.AppMonitorService
import com.monika.dashboard.service.MusicListenerService
import com.monika.dashboard.ui.theme.Border
import com.monika.dashboard.ui.theme.Secondary
import com.monika.dashboard.ui.theme.TextMuted

@Composable
fun StatusScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Check actual system permission status, refresh on every resume
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var notificationEnabled by remember { mutableStateOf(false) }
    var healthAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            accessibilityEnabled = isAccessibilityServiceEnabled(context)
            notificationEnabled = isNotificationListenerEnabled(context)
            healthAvailable = HealthConnectManager.isAvailable(context)
        }
    }

    // Poll current app/music info every second (these change frequently)
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            tick++
        }
    }
    val currentApp = remember(tick) { AppMonitorService.currentForegroundApp }
    val currentMusic = remember(tick) { MusicListenerService.currentMusic }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "服务状态",
            style = MaterialTheme.typography.headlineMedium
        )

        // Accessibility Service
        StatusCard(
            title = "无障碍服务",
            subtitle = "前台应用检测",
            isActive = accessibilityEnabled,
            onAction = {
                try {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                } catch (_: Exception) {
                    Toast.makeText(context, "无法打开设置", Toast.LENGTH_SHORT).show()
                }
            },
            actionLabel = if (accessibilityEnabled) "设置" else "启用"
        )

        // Notification Listener
        StatusCard(
            title = "通知监听",
            subtitle = "音乐检测",
            isActive = notificationEnabled,
            onAction = {
                try {
                    context.startActivity(
                        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                } catch (_: Exception) {
                    Toast.makeText(context, "无法打开设置", Toast.LENGTH_SHORT).show()
                }
            },
            actionLabel = if (notificationEnabled) "设置" else "启用"
        )

        // Health Connect
        StatusCard(
            title = "Health Connect",
            subtitle = "健康数据同步",
            isActive = healthAvailable,
            onAction = null,
            actionLabel = null
        )

        Divider(color = Border, thickness = 1.dp)

        // Current state
        Text(
            text = "当前状态",
            style = MaterialTheme.typography.titleMedium
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Border, RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                InfoRow("前台应用", currentApp.ifEmpty { "未检测到" })
                val music = currentMusic
                if (music != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    InfoRow("正在播放", "${music.title}${music.artist?.let { " - $it" } ?: ""}")
                    music.app?.let { app ->
                        InfoRow("音乐应用", app)
                    }
                }
            }
        }

        Divider(color = Border, thickness = 1.dp)

        // Debug log
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "调试日志",
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = { DebugLog.clear() }) {
                Text("清空", style = MaterialTheme.typography.bodySmall)
            }
        }

        val logLines = remember(tick) { DebugLog.lines }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 300.dp)
                .border(1.dp, Border, RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (logLines.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无日志",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            } else {
                val logScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .verticalScroll(logScrollState)
                ) {
                    logLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Check if our AccessibilityService is enabled in system settings */
private fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val expected = ComponentName(context, AppMonitorService::class.java).flattenToString()
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return TextUtils.SimpleStringSplitter(':').apply { setString(enabledServices) }
        .any { it.equals(expected, ignoreCase = true) }
}

/** Check if our NotificationListenerService is enabled in system settings */
private fun isNotificationListenerEnabled(context: android.content.Context): Boolean {
    val expected = ComponentName(context, MusicListenerService::class.java).flattenToString()
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return enabledListeners.split(":").any { it.equals(expected, ignoreCase = true) }
}

@Composable
private fun StatusCard(
    title: String,
    subtitle: String,
    isActive: Boolean,
    onAction: (() -> Unit)?,
    actionLabel: String?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isActive) Secondary.copy(alpha = 0.5f) else Border,
                RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot
            Surface(
                modifier = Modifier.size(10.dp),
                shape = RoundedCornerShape(5.dp),
                color = if (isActive) Secondary else TextMuted.copy(alpha = 0.4f)
            ) {}

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
            }

            if (onAction != null && actionLabel != null) {
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(0.6f)
        )
    }
}
