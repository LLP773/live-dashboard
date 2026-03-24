# Live Dashboard — Windows Agent

监控前台窗口并向 Live Dashboard 后端上报应用使用状态。

## 安装

**需要**: Python 3.10+

1. 解压下载的 `windows-agent.zip`
2. 安装依赖：
   ```bash
   pip install -r requirements.txt
   ```
3. 复制 `config.example.json` 为 `config.json`，填入你的信息：
   ```json
   {
     "server_url": "https://your-domain.com",
     "token": "你的设备密钥",
     "interval_seconds": 5,
     "heartbeat_seconds": 60,
     "idle_threshold_seconds": 300
   }
   ```
4. 运行：
   ```bash
   python agent.py
   ```

## 打包为 .exe（可选）

运行 `build.bat`，会用 PyInstaller 打包为单文件 `dist/live-dashboard-agent.exe`。

将 `config.json` 放在 `.exe` 同目录下即可运行。

## 开机自启（可选）

将 `.exe` 和 `config.json` 放在固定目录后，以管理员身份运行 `install-task.bat`，会创建 Windows 任务计划在登录时自动启动。

## 配置说明

| 字段 | 说明 | 默认值 |
|------|------|--------|
| `server_url` | 后端地址 | 必填 |
| `token` | 设备密钥（部署服务端时生成的） | 必填 |
| `interval_seconds` | 上报间隔（秒） | `5` |
| `heartbeat_seconds` | AFK 时心跳间隔（秒） | `60` |
| `idle_threshold_seconds` | 无操作多久后进入 AFK 模式（秒） | `300` |

## 功能

- **前台应用检测**: 实时检测当前前台窗口的应用和标题
- **音乐检测**: 自动识别 Spotify、QQ音乐、网易云、foobar2000、酷狗、酷我、AIMP 等播放器，解析歌曲信息
- **电量上报**: 笔记本自动上报电池信息，台式机不显示（正常）
- **AFK 检测**: 无键鼠输入超过阈值后切换为心跳模式，用户返回后自动恢复
