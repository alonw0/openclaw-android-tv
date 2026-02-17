## OpenClaw Node (Android TV) (internal)

TV-optimized Android node app: connects to the **Gateway WebSocket** (`_openclaw-gw._tcp`) and exposes **Canvas + Chat + Voice Wake** on your television.

Notes:
- The node keeps the connection alive via a **foreground service** (persistent notification with connection status).
- Chat uses session keys shared with other clients (iOS/macOS/WebChat/Android).
- Supports **Voice Wake** - hands-free activation by saying trigger words (default: "openclaw").
- **Floating Crab Mascot** - visual indicator overlay on all screens (requires "Draw over other apps" permission).
- Supports Android TV (`minSdk 31`, Kotlin + Jetpack Compose + Leanback UI).

## Demo

https://github.com/user-attachments/assets/ff1e0a87-3917-4ff1-b953-21120658841d

## Open in Android Studio
- Open the folder `openclaw-android-tv`.

## Build / Run

```bash
cd openclaw-android-tv
./gradlew :app:assembleDebug
./gradlew :app:installDebug
./gradlew :app:testDebugUnitTest
```

`gradlew` auto-detects the Android SDK at `~/Library/Android/sdk` (macOS default) if `ANDROID_SDK_ROOT` / `ANDROID_HOME` are unset.

## Gateway Setup

### 1. Configure Gateway for LAN Access

By default, the gateway binds to `loopback` (localhost only). For TV access over your local network, you need to bind to LAN:

```bash
# Set gateway to bind to LAN interface
openclaw config set gateway.bind lan

# Or bind to all interfaces
openclaw config set gateway.bind all
```

### 2. Enable Node Commands

The gateway filters which commands nodes can use. Add the TV commands to the allowlist:

Edit `~/.openclaw/openclaw.json`:
```json
{
  "gateway": {
    "nodes": {
      "allowCommands": [
        "agent.notify",
        "agent.clear",
        "media.play",
        "screen.snapshot",
        "canvas.eval",
        "canvas.present",
        "canvas.navigate",
        "canvas.a2ui.push",
        "canvas.a2ui.reset"
      ]
    }
  }
}
```

Or via CLI:
```bash
openclaw config set gateway.nodes.allowCommands '["agent.notify","agent.clear","media.play","screen.snapshot","canvas.eval","canvas.present","canvas.navigate","canvas.a2ui.push","canvas.a2ui.reset"]'
```

### 3. Start the Gateway

```bash
openclaw gateway run --port 18789 --verbose
```

Or restart if already running:
```bash
openclaw gateway restart
```

## Connect / Pair

1) Ensure gateway is running with LAN binding (see above)

2) In the Android TV app:
- Open **Settings** (⚙️ button in top-right)
- Either select a discovered gateway under **Discovered Gateways**, or use **Advanced → Manual Gateway** (host + port)
- For manual connection, enter your gateway machine's LAN IP (e.g., `192.168.1.100`) and port (`18789`)

3) Approve pairing (on the gateway machine):

The TV app registers two connections - a **node** (for receiving commands) and an **operator** (for chat/control). You need to approve both:

```bash
openclaw devices list
openclaw devices approve <deviceId1>
openclaw devices approve <deviceId2>
```

### Manual Token Setup (Optional)

If auto-discovery doesn't work, you can manually configure a gateway token:

```bash
# On gateway machine - get the token
openclaw config get gateway.token

# On TV app - enter in Advanced Settings → Manual Gateway → Token
```

More details: `docs/platforms/android.md`.

## Key Features

### Chat (💬)
- Full-screen chat interface optimized for 10-foot UI
- Voice wake integration - toggle mic button to enable hands-free commands
- Streaming responses from AI assistant
- Tool call visualization

### Voice Wake
Three modes available in Settings:
- **Off** - No listening (default for privacy)
- **Foreground** - Only listens when app is visible
- **Always** - Listens even when app is in background (requires foreground service)

Say trigger words (default: "openclaw" or "claude") followed by your command.

### Floating Crab Mascot
- Visual indicator showing connection status and emotions
- Appears on all screens when enabled in Settings
- Click to open connection panel
- Requires "Draw over other apps" permission in Android Settings

### Screen Recording
- Agent can request screen recordings via `screen.record` capability
- Recording indicator shown in UI
- Requires screen recording permission

## Permissions

- Discovery:
  - Android 13+ (`API 33+`): `NEARBY_WIFI_DEVICES`
  - Android 12 and below: `ACCESS_FINE_LOCATION` (required for NSD scanning)
- Foreground service notification (Android 13+): `POST_NOTIFICATIONS`
- Microphone: `RECORD_AUDIO` (for voice wake and chat voice input)
- System overlay: `SYSTEM_ALERT_WINDOW` (for floating crab mascot)
- Screen Recording: Special permission requested at runtime

## TV-Specific Settings

### Gateway Canvas
Toggle in Settings → Gateway Canvas:
- **ON**: Shows gateway A2UI dashboard when connected (default)
- **OFF**: Shows screensaver on startup, canvas only appears when agent draws

### Canvas Focus Mode
Click the 🖼️ button to enter canvas focus mode:
- Full-screen canvas with D-pad navigation enabled
- Status bar and overlays hidden
- Press **Back** button to exit focus mode
- Useful for interacting with A2UI content

### DVD Mode (Easter Egg)
Toggle in Settings → DVD Mode:
- Classic bouncing logo screensaver
- Color changes on edge hits
- "CORNER HIT!" celebration when logo hits a corner
- Corner hit counter in bottom-right

### Screensaver
When canvas is empty/inactive:
- Bouncing crab mascot with emotion changes
- Ambient particle effects
- Clock display
- Subtle grid overlay
- Click crab to open connection panel

## TV-Specific Notes

- Optimized for D-pad/remote navigation
- All UI elements are focusable with visual focus indicators
- Chat input supports on-screen keyboard and voice
- Large touch targets for 10-foot UI
- Background voice wake keeps listening even when watching other content
- GPU-accelerated animations for smooth performance on low-end devices

## Troubleshooting

### TV Can't Find Gateway

1. **Check gateway binding**: Must be `lan` or `all`, not `loopback`
   ```bash
   openclaw config get gateway.bind
   # Should show "lan" or "all"
   ```

2. **Check firewall**: Port 18789 must be open for TCP connections

3. **Same network**: TV and gateway machine must be on the same LAN/WiFi network

4. **Try manual connection**: Use gateway machine's IP address directly instead of discovery

### TV Connected But Not Fully Working

The TV app needs **two approvals** (node + operator). If only one is approved:
- Chat may not work (missing operator approval)
- Commands may not work (missing node approval)

Check and approve both:
```bash
openclaw devices list
# Approve any pending devices from the TV
```

### Commands Not Working

1. **Check allowlist**: Verify commands are in `gateway.nodes.allowCommands`
   ```bash
   openclaw config get gateway.nodes.allowCommands
   ```

2. **Restart gateway** after config changes:
   ```bash
   openclaw gateway restart
   ```

3. **Verify device registration**:
   ```bash
   openclaw devices list
   ```

### Agent Drawing Not Showing (Screensaver Stays)

The screensaver hides when the canvas receives content. If agent claims to draw but nothing shows:

1. **Check command used**: Agent must use `canvas.eval`, `canvas.present`, or A2UI push commands
2. **Verify allowlist**: Ensure `canvas.present`, `canvas.eval` are in `allowCommands`
3. **Check canvas.present params**: Both `url` and `target` parameters are supported

### Floating Crab Not Appearing

1. **Enable in Settings**: Settings → Floating Crab → ON
2. **Grant permission**: Android Settings → Apps → OpenClaw → "Draw over other apps"
3. **Check overlay window**: The crab appears at top-left corner (24px from edge)

### Laggy Animations on Low-End Devices

The app uses GPU-accelerated `graphicsLayer` animations, but if still laggy:
1. Disable DVD mode screensaver (more CPU intensive)
2. Reduce particle effects by closing/reopening app
3. Check available RAM (2GB minimum recommended)

## See Also

- `COMMANDS.md` - Full command reference with examples
- `CLAUDE.md` - Development guide for AI assistants
- `docs/platforms/android.md` - General Android platform docs
