# 🎧 DualStream — Android Peer-to-Peer Audio Sharing App

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![API Level](https://img.shields.io/badge/API-29%2B-green)](https://developer.android.com/about/versions/10)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![Build](https://img.shields.io/badge/Build-Passing-brightgreen)](#)

> **Share one pair of earbuds. Hear two phones at once.**

---

## 📖 The Problem

Two people share a single pair of True Wireless Stereo (TWS) earbuds. Each person holds one earbud. They each want to hear audio from their **own phone simultaneously** — but TWS hardware physically cannot maintain two Bluetooth connections at the same time.

## ✅ The Solution

DualStream creates a **peer-to-peer Wi-Fi audio bridge** between two phones:

| Channel | Source | Output |
|---------|--------|--------|
| **LEFT earbud** | Phone A (Receiver) — local audio | Direct local playback |
| **RIGHT earbud** | Phone B (Sender) — streamed audio | Received over Wi-Fi |

Phone B captures its system audio and streams it over local Wi-Fi to Phone A. Phone A acts as a **master mixer** — it combines its own audio into the LEFT channel and Phone B's incoming stream into the RIGHT channel, then plays the stereo mix through the single Bluetooth earbud connection with **zero channel bleed**.

---

## ✨ Features

- 🎵 **Stereo channel isolation** — complete left/right separation with no audio bleed
- 📡 **Google Nearby Connections** — Wi-Fi P2P streaming, no internet required
- 🗜️ **Opus codec** — 64 kbps compressed audio via Android MediaCodec
- 🔄 **Jitter buffer** — smooth playback with 15-frame adaptive packet smoothing
- 📊 **Live telemetry** — real-time RTT latency (PING/PONG), buffer health, packet stats
- 🔁 **Auto-reconnection** — automatically resumes after Wi-Fi interruption
- 🎬 **WebView media player** — built-in YouTube Music / streaming service player
- 📂 **Local file playback** — pick any local audio file via system file picker
- 🛡️ **DRM warning system** — detects DRM-enforced silence and notifies users
- 🔋 **Foreground services** — wakelock-backed services ensure uninterrupted streaming
- 🎨 **Dark glassmorphism UI** — premium dark-mode Jetpack Compose interface

---

## 🏗️ Architecture

```
┌─────────────────────────────────┐     Wi-Fi (Nearby Connections)    ┌──────────────────────────────────┐
│        PHONE B (Sender)         │ ─────────────────────────────────> │       PHONE A (Receiver)          │
│                                 │                                    │                                   │
│  System Audio                   │    Opus-compressed PCM frames      │  LEFT CH  ◄──  Local ExoPlayer   │
│      │                          │       (20ms @ 64kbps)             │                                   │
│  AudioPlaybackCapture           │                                    │  RIGHT CH ◄──  Decoded Remote    │
│  (MediaProjection)              │                                    │                                   │
│      │                          │                                    │      AudioMixer (stereo merge)    │
│  OpusCodec (encode)             │                                    │      AudioTrack (BT output)       │
│      │                          │                                    └──────────────────────────────────┘
│  StreamSender ──────────────────┤
└─────────────────────────────────┘
```

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | 100% Kotlin 2.0.21 |
| UI | Jetpack Compose + Material3 |
| Architecture | MVVM + Hilt (DI) |
| Networking | Google Nearby Connections API |
| Audio Encoding | Android MediaCodec (Opus) |
| Local Playback | Media3 (ExoPlayer 1.3.1) |
| Concurrency | Kotlin Coroutines + Flow |
| Services | Android Foreground Services |
| Build | Gradle 9.1.0 + AGP 9.0.1 |

---

## 📁 Project Structure

```
app/src/main/java/com/dualstream/
├── audio/
│   ├── AudioConstants.kt           # Sample rate, frame size, bit depth constants
│   ├── AudioMixer.kt               # PCM stereo interleaving (LEFT + RIGHT channels)
│   ├── JitterBuffer.kt             # Thread-safe 15-frame adaptive jitter buffer
│   ├── OpusCodec.kt                # MediaCodec Opus encoder/decoder
│   ├── AudioCaptureManager.kt      # MediaProjection-based system audio capture
│   └── LocalAudioProcessor.kt     # ExoPlayer AudioProcessor for PCM interception
│
├── network/
│   ├── NearbyConnectionManager.kt  # Google Nearby Connections manager
│   ├── StreamSender.kt             # Serializes Opus frames onto network stream
│   └── StreamReceiver.kt           # Deserializes incoming audio packets
│
├── service/
│   ├── SenderForegroundService.kt  # Phone B: capture → encode → stream
│   ├── ReceiverForegroundService.kt# Phone A: receive → decode → mix → play
│   └── StopActionReceiver.kt       # Notification stop action broadcast receiver
│
├── viewmodel/
│   ├── SenderViewModel.kt          # Phone B UI state + business logic
│   └── ReceiverViewModel.kt        # Phone A UI state + business logic
│
├── ui/
│   ├── screens/
│   │   ├── HomeScreen.kt           # Mode selection (Sender / Receiver)
│   │   ├── SenderScreen.kt         # Phone B controls, audio level, permissions
│   │   └── ReceiverScreen.kt       # Phone A controls, telemetry, media player
│   ├── components/
│   │   ├── AudioLevelBar.kt        # Animated real-time audio level bar
│   │   ├── ConnectionStatusCard.kt # Connection state chip/badge
│   │   └── PermissionRationaleDialog.kt
│   └── theme/
│       ├── Color.kt                # Navy / Electric Blue / Purple palette
│       ├── Theme.kt                # Dark MaterialTheme
│       └── Type.kt                 # Roboto / Roboto Mono typography
│
├── model/
│   ├── AppMode.kt                  # SENDER / RECEIVER enum
│   ├── ConnectionState.kt          # Sealed class: Idle/Discovering/Connecting/Connected/Error
│   └── AudioStats.kt               # Latency, buffer health, packet metrics
│
├── di/
│   └── AppModule.kt                # Hilt @Singleton providers
│
├── navigation/
│   └── AppNavGraph.kt              # Compose NavHost (home, sender, receiver)
│
├── DualStreamApp.kt                # @HiltAndroidApp Application class
└── MainActivity.kt                 # Entry point, navigation host
```

---

## 🚀 Getting Started

### Prerequisites

- Android Studio **Ladybug (2024.2)** or later
- JDK 17 (bundled with Android Studio)
- Two physical Android devices (API 29+, Android 10+)
- Both devices on the same **local Wi-Fi network**
- **Google Play Services** installed on both devices

### Build & Install

```bash
# Clone the repository
git clone https://github.com/KESAV-P/DualStream.git
cd DualStream

# Build the debug APK
./gradlew assembleDebug

# Install on both connected devices
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio and run directly on connected devices.

---

## 🔧 Configuration

| File | Purpose |
|------|---------|
| `gradle/libs.versions.toml` | Centralized version catalog |
| `gradle.properties` | JVM args, Kapt/K2 flags, Android flags |
| `app/build.gradle.kts` | App-level build config, dependencies |
| `app/proguard-rules.pro` | R8 keep rules for MediaCodec, Nearby, Hilt |

### Key `gradle.properties` Flags

```properties
kotlin.kapt.use.k2=false          # Use K1 Kapt for Hilt 2.55 compatibility
android.builtInKotlin=false       # Disable AGP 9.0 built-in Kotlin to allow explicit KGP
android.newDsl=false              # Restore legacy DSL for Kotlin plugin bridge
android.enableR8.fullMode=true    # Enable full R8 optimization for release
```

---

## 📱 Usage

### Phone A — Receiver Mode
1. Open DualStream → tap **"I am the Receiver (Phone A)"**
2. Connect your Bluetooth earbuds to Phone A
3. Tap **START RECEIVER** — the app begins advertising over Nearby Connections
4. Select your local audio source:
   - **WebView Player** (default) — browse to YouTube Music or any streaming service
   - **Local File** — pick an audio file from your device
5. Once Phone B connects, tap **STREAM REMOTE** to request Phone B to start sending
6. Audio will be mixed: your local audio → **LEFT earbud**, Phone B's audio → **RIGHT earbud**

### Phone B — Sender Mode
1. Open DualStream → tap **"I am the Sender (Phone B)"**
2. Grant **Record Audio** and **Nearby Wi-Fi** permissions
3. Tap **START STREAMING** → grant the MediaProjection (screen capture) permission
4. Phone B will automatically discover and connect to Phone A
5. The system audio from Phone B is captured, compressed (Opus 64kbps), and streamed

---

## 🔐 Permissions

| Permission | Device | Purpose |
|-----------|--------|---------|
| `RECORD_AUDIO` | Phone B | System audio playback capture |
| `FOREGROUND_SERVICE` | Both | Keep streaming alive in background |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Both | Foreground service type declaration |
| `NEARBY_WIFI_DEVICES` (API 33+) | Both | Nearby Connections Wi-Fi P2P |
| `ACCESS_FINE_LOCATION` (API < 33) | Both | Nearby Connections (legacy) |
| `POST_NOTIFICATIONS` (API 33+) | Both | Ongoing foreground service notification |
| `WAKE_LOCK` | Both | Prevent CPU sleep during streaming |

---

## 🔬 Technical Deep Dive

### Audio Pipeline (Phone A, Receiver)

```
ExoPlayer (local file/WebView)
    │
    ▼
LocalAudioProcessor (AudioProcessor override)
    │  intercepts PCM bytes at sandbox level — zero permissions, zero latency
    ▼
leftChannelBufferQueue (LinkedBlockingQueue, 30-frame capacity)
    │
    ├── LEFT channel PCM ──────────────────────┐
    │                                          │
JitterBuffer (15-frame LinkedBlockingDeque)   AudioMixer.mix(left, right)
    │                                          │
    ├── RIGHT channel PCM (decoded Opus) ──────┘
    │
    ▼
AudioTrack.write() → Bluetooth headset
```

### Network Protocol

```
┌──────────────────────────────────────────────────────┐
│                  Audio Frame Packet                   │
├──────────────┬───────────────────────────────────────┤
│  2 bytes     │  N bytes                              │
│  Frame size  │  Opus-encoded PCM payload             │
│  (big-endian)│  (20ms @ 44.1kHz stereo, 64kbps)     │
└──────────────┴───────────────────────────────────────┘
```

Frames are sent over a `Payload.Type.STREAM` Nearby Connections pipe, enabling continuous low-latency audio without TCP handshake overhead per frame.

### Latency Measurement

Every 5 seconds the Receiver sends a `PING` control message containing a timestamp. The Sender responds with a `PONG` echoing the timestamp. The Receiver calculates `RTT / 2` as the estimated one-way latency, displayed in the live telemetry panel.

---

## 🛠️ Build Compatibility Notes

This project targets a specific toolchain stack due to constraint interactions:

| Component | Version | Reason |
|-----------|---------|--------|
| AGP | 9.0.1 | Requires Gradle 9.1.0 (installed globally) |
| Kotlin | 2.0.21 | Required by AGP 9.0.1 |
| Hilt | 2.55 | First version with Kotlin 2.x metadata support |
| Kapt | K1 mode | Hilt 2.55 annotation processor requires K1 KAPT |
| `android.builtInKotlin` | `false` | Prevent extension conflict between AGP built-in Kotlin and KGP 2.0 |

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes (`git commit -m 'Add some feature'`)
4. Push to the branch (`git push origin feature/your-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

---

## 🙏 Acknowledgements

- [Google Nearby Connections API](https://developers.google.com/nearby/connections/overview) — P2P Wi-Fi transport layer
- [Jetpack Media3 / ExoPlayer](https://developer.android.com/media/media3) — local audio playback and AudioProcessor pipeline
- [Dagger Hilt](https://dagger.dev/hilt/) — dependency injection
- [Jetpack Compose](https://developer.android.com/compose) — modern declarative UI
