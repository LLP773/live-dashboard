package com.monika.dashboard.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.monika.dashboard.data.SettingsStore
import com.monika.dashboard.service.AppMonitorService
import com.monika.dashboard.service.MusicListenerService
import com.monika.dashboard.ui.theme.Primary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun SetupScreen(settings: SettingsStore) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val serverUrl by settings.serverUrl.collectAsState(initial = "")
    val reportInterval by settings.reportInterval.collectAsState(initial = 60)
    val monitoringEnabled by settings.monitoringEnabled.collectAsState(initial = false)

    var urlInput by remember(serverUrl) { mutableStateOf(serverUrl) }
    var tokenInput by remember { mutableStateOf("") }
    var intervalInput by remember(reportInterval) { mutableStateOf(reportInterval.toString()) }

    // Load token asynchronously to avoid blocking main thread
    LaunchedEffect(Unit) {
        try {
            val token = withContext(Dispatchers.IO) { settings.getToken() }
            tokenInput = token ?: ""
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            tokenInput = ""
        }
    }
    var showToken by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "服务器配置",
            style = MaterialTheme.typography.headlineMedium
        )

        // Server URL
        OutlinedTextField(
            value = urlInput,
            onValueChange = {
                urlInput = it
                urlError = null
            },
            label = { Text("服务器地址") },
            placeholder = { Text("https://your-dashboard.example.com") },
            isError = urlError != null,
            supportingText = urlError?.let { err -> { Text(err) } }
                ?: { Text("必须使用 HTTPS（仅 localhost 允许 HTTP）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )

        // Token
        OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            label = { Text("Token 密钥") },
            singleLine = true,
            visualTransformation = if (showToken) VisualTransformation.None
                else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "隐藏" else "显示")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )

        // Report Interval
        OutlinedTextField(
            value = intervalInput,
            onValueChange = { intervalInput = it.filter { c -> c.isDigit() } },
            label = { Text("心跳间隔（秒）") },
            supportingText = { Text("10-300 秒") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )

        // Save Button
        Button(
            onClick = {
                scope.launch {
                    val url = urlInput.trim()
                    if (!SettingsStore.validateUrl(url)) {
                        urlError = "地址无效：必须使用 HTTPS 或 http://localhost"
                        return@launch
                    }
                    if (!settings.isSecureStorageAvailable) {
                        statusMsg = "无法保存：安全存储不可用"
                        return@launch
                    }
                    settings.setServerUrl(url)
                    settings.setToken(tokenInput)
                    val seconds = intervalInput.toIntOrNull()?.coerceIn(10, 300) ?: 60
                    settings.setReportInterval(seconds)
                    statusMsg = "设置已保存"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Text("保存设置")
        }

        // Start/Stop monitoring toggle
        Button(
            onClick = {
                scope.launch {
                    if (!monitoringEnabled) {
                        // Check if accessibility service is running
                        if (!AppMonitorService.isRunning) {
                            statusMsg = "请先开启无障碍服务"
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            } catch (_: Exception) {
                                statusMsg = "无法打开无障碍设置，请手动前往"
                            }
                            return@launch
                        }
                    }
                    val newState = !monitoringEnabled
                    settings.setMonitoringEnabled(newState)
                    statusMsg = if (newState) "监听已开启" else "监听已关闭"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (monitoringEnabled)
                    MaterialTheme.colorScheme.error
                else Primary
            )
        ) {
            Text(if (monitoringEnabled) "关闭监听" else "开始监听")
        }

        // Status message
        statusMsg?.let { msg ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = msg,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // --- Background keep-alive section ---
        Text(
            text = "后台保活状态",
            style = MaterialTheme.typography.headlineMedium
        )

        // Service status as reactive Compose state, refreshed on lifecycle resume
        val lifecycleOwner = LocalLifecycleOwner.current
        var accessibilityOk by remember { mutableStateOf(AppMonitorService.isRunning) }
        var notificationOk by remember { mutableStateOf(MusicListenerService.isRunning) }
        val pm = remember { context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager }
        var batteryOptimized by remember {
            mutableStateOf(pm?.isIgnoringBatteryOptimizations(context.packageName) == true)
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    accessibilityOk = AppMonitorService.isRunning
                    notificationOk = MusicListenerService.isRunning
                    batteryOptimized = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        ServiceStatusRow("无障碍服务", accessibilityOk) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) {}
        }

        ServiceStatusRow("通知监听权限", notificationOk) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) {}
        }

        ServiceStatusRow("电池优化已忽略", batteryOptimized) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                } catch (_: Exception) {}
            }
        }

        // OEM-specific guidance
        val manufacturer = remember { Build.MANUFACTURER.lowercase(Locale.ROOT) }
        val oemTip = when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
                "小米/Redmi：设置 → 应用设置 → 应用管理 → Live Dashboard → 省电策略 → 无限制，并开启「自启动」"
            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                "华为/荣耀：设置 → 电池 → 启动管理 → Live Dashboard → 手动管理 → 三个开关全部打开"
            manufacturer.contains("samsung") ->
                "三星：设置 → 电池 → 后台使用限制 → 从「深度睡眠」列表中移除 Live Dashboard"
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") ->
                "OPPO/Realme/一加：设置 → 电池 → 更多电池设置 → 关闭「智能功耗管理」，并在应用管理中允许后台运行和自启动"
            manufacturer.contains("vivo") ->
                "vivo：设置 → 电池 → 后台功耗管理 → Live Dashboard → 允许后台高耗电"
            else -> null
        }
        if (oemTip != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "厂商特殊设置",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = oemTip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        // Secure storage warning
        if (!settings.isSecureStorageAvailable) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = "安全存储不可用，Token 无法安全保存。",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun ServiceStatusRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (ok) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = if (ok) "✓" else "✗",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (ok) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ok) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer
                )
            }
            if (!ok) {
                TextButton(onClick = onFix) {
                    Text("去设置")
                }
            }
        }
    }
}
