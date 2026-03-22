# Live Dashboard Android App — Code Guide

> Auto-generated reference for Lyra. Updated: 2026-03-22

## Build & Deploy

- **Min SDK**: check `app/build.gradle.kts` → `minSdk`
- **Build**: `./gradlew assembleDebug` (from `agents/android-app/`)
- **APK output**: `app/build/outputs/apk/debug/app-debug.apk`
- **Install**: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Architecture Overview

```
MainActivity (Compose UI, 3 tabs)
  ├─ SetupScreen     → server config + monitoring toggle
  ├─ HealthScreen    → Health Connect permissions & sync config
  └─ StatusScreen    → debug log viewer

Services (background):
  ├─ AppMonitorService   → AccessibilityService, tracks foreground app
  └─ MusicListenerService → NotificationListenerService, tracks now-playing

Workers:
  └─ HealthSyncWorker    → WorkManager periodic health data sync

Data:
  ├─ SettingsStore   → DataStore (prefs) + EncryptedSharedPreferences (token)
  ├─ ReportClient    → OkHttp3 HTTP client for API calls
  └─ DebugLog        → in-memory circular log (100 entries)
```

## File Map

| File | Role | When to touch |
|------|------|---------------|
| `MainActivity.kt` | Entry point, Scaffold + TopAppBar (connection status), tab navigation | UI layout changes, connection indicator |
| `ui/screens/SetupScreen.kt` | Server URL/token/interval config, save button, monitoring toggle | Config UI, monitoring start/stop |
| `ui/screens/HealthScreen.kt` | Health Connect permissions, type toggles, sync interval | Health data config |
| `ui/screens/StatusScreen.kt` | Shows DebugLog entries in a scrollable list | Debug log display |
| `ui/theme/Theme.kt` | Material 3 theme, colors (`Primary`, `Border`, etc.) | Color/style changes |
| `data/SettingsStore.kt` | All persistent settings (DataStore + encrypted token) | Adding new settings |
| `data/DebugLog.kt` | Thread-safe circular log buffer (ConcurrentLinkedDeque, max 100) | Logging changes |
| `network/ReportClient.kt` | HTTP client: `reportApp()`, `reportHealthData()`, `testConnection()` | API changes, new endpoints |
| `service/AppMonitorService.kt` | AccessibilityService: foreground app detection + heartbeat reporting | Reporting logic, battery, music integration |
| `service/MusicListenerService.kt` | NotificationListenerService: MediaSession now-playing extraction | Music detection |
| `health/HealthConnectManager.kt` | Reads 17 health data types from Google Health Connect API | Adding health types |
| `health/HealthSyncWorker.kt` | WorkManager periodic worker for health sync (15-60 min) | Sync schedule, retry logic |
| `health/HealthDataTypes.kt` | Health type metadata (labels, units, icons) | Health type display |
| `DashboardApp.kt` | Application class, WorkManager config | App-level init |
| `PermissionRationaleActivity.kt` | Health Connect permission rationale page | Permission flow |
| `AndroidManifest.xml` | Permissions, service declarations, queries | New services/permissions |

## Key Flows

### App Monitoring Flow
1. User enables AccessibilityService in system settings
2. User clicks "开始监听" in SetupScreen → sets `monitoringEnabled = true`
3. `AppMonitorService.onAccessibilityEvent()` detects foreground app change
4. 500ms debounce → `reportAppChange()` → `sendReport()`
5. `sendReport()` checks `monitoringEnabled` flag first
6. `ReportClient.reportApp()` POSTs to `/api/report` with app_id, battery, music
7. Heartbeat loop also calls `sendReport()` at configured interval
8. Failed reports queued in `offlineQueue` (max 50), flushed on next success

### Connection Status Flow
1. `MainActivity.DashboardTopBar()` runs `LaunchedEffect` loop
2. Every 5 seconds: creates temp `ReportClient`, calls `testConnection()` (GET `/api/health`)
3. Updates `connected` state → shows "已连接" (green) or "未连接" (gray) in TopAppBar

### Health Sync Flow
1. User grants Health Connect permissions in HealthScreen
2. User selects health types + sync interval
3. `HealthSyncWorker` runs periodically via WorkManager
4. Reads from Health Connect → POSTs to `/api/health-data`

## Common Issues & Fixes

| Symptom | Cause | Fix |
|---------|-------|-----|
| App crash on "测试连接" | (Removed in v2) old test button created ReportClient on main thread | Already fixed — button replaced with auto-test |
| "未连接" but server is up | URL missing `https://` or token empty | Check SetupScreen config, ensure saved |
| No activity reports | AccessibilityService not enabled, or `monitoringEnabled = false` | System Settings → Accessibility → enable service, then click "开始监听" |
| No music info | NotificationListenerService not granted | System Settings → Notification access → enable |
| Health sync not working | Health Connect not installed or permissions denied | Install Health Connect app, grant permissions in HealthScreen |
| Battery drain | Report interval too low (e.g. 10s) | Increase interval to 30-60s |
| Offline queue full | Network down for extended period, 50 pending reports dropped | Queue auto-flushes on reconnect; increase MAX_OFFLINE_QUEUE if needed |
| Token save fails | EncryptedSharedPreferences unavailable (rare, old devices) | Warning shown in SetupScreen; no workaround |
| 后台被杀 | OEM 电池优化 | SetupScreen "后台保活状态" → 忽略电池优化 + 厂商特殊设置 |
| 切后台停止上报 | 无障碍服务未开启 | SetupScreen → 检查 ✓/✗ 状态 → "去设置" |

## API Endpoints Used

| Method | Path | Purpose | Called by |
|--------|------|---------|-----------|
| POST | `/api/report` | Report foreground app + battery + music | AppMonitorService |
| POST | `/api/health-data` | Upload health records | HealthSyncWorker |
| GET | `/api/health` | Connection test (health check) | MainActivity auto-test |

## Settings Keys (DataStore)

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `server_url` | String | `""` | Server base URL (HTTPS required) |
| `report_interval` | Int | `60` | Heartbeat interval in seconds (10-300) |
| `health_sync_interval` | Int | `15` | Health sync interval in minutes (15-60) |
| `enabled_health_types` | Set\<String\> | `emptySet()` | Which health types to sync |
| `monitoring_enabled` | Boolean | `false` | Whether to actively report |
| `token` (encrypted) | String | `null` | Auth token (AES256-GCM) |
