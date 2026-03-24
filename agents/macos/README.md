# Live Dashboard — macOS Agent

监控前台窗口并向 Live Dashboard 后端上报应用使用状态。

## 安装

**需要**: macOS 10.14+, Python 3.10+

1. 解压下载的 `macos-agent.zip`
2. 创建虚拟环境并安装依赖（推荐）：
   ```bash
   python3 -m venv .venv
   source .venv/bin/activate
   pip install -r requirements.txt
   ```
3. 复制 `config.example.json` 为 `config.json`，填入你的信息：
   ```json
   {
     "server_url": "https://your-domain.com",
     "token": "你的设备密钥",
     "interval_seconds": 5,
     "heartbeat_seconds": 60
   }
   ```
4. 运行：
   ```bash
   .venv/bin/python agent.py
   ```

> 首次运行时，macOS 会弹出权限请求，需在「系统设置 → 隐私与安全性 → 辅助功能」中授权终端或 Python。

## 开机自启（launchd，可选）

```bash
cat > ~/Library/LaunchAgents/com.live-dashboard.agent.plist << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.live-dashboard.agent</string>
    <key>ProgramArguments</key>
    <array>
        <string>/替换为实际路径/macos-agent/.venv/bin/python</string>
        <string>/替换为实际路径/macos-agent/agent.py</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>WorkingDirectory</key>
    <string>/替换为实际路径/macos-agent</string>
</dict>
</plist>
EOF

launchctl load ~/Library/LaunchAgents/com.live-dashboard.agent.plist
```

## 配置说明

| 字段 | 说明 | 默认值 |
|------|------|--------|
| `server_url` | 后端地址 | 必填 |
| `token` | 设备密钥（部署服务端时生成的） | 必填 |
| `interval_seconds` | 上报间隔（秒） | `5` |
| `heartbeat_seconds` | AFK 时心跳间隔（秒） | `60` |

## 功能

- **前台应用检测**: 通过 AppleScript 获取前台应用名和窗口标题
- **音乐检测**: 查询 Spotify、Apple Music、QQ音乐、网易云音乐的播放状态
- **电量上报**: 通过 psutil 获取电池信息
