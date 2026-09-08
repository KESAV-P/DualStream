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
| **LEFT earbud** | Phone A (Receiver) — system audio | Mixed locally on Phone A |
| **RIGHT earbud** | Phone B (Sender) — system audio | Received over Wi-Fi |

Phone B captures its system audio via `AudioPlaybackCapture` (MediaProjection) and streams it over local Wi-Fi to Phone A. Phone A also captures its own system audio. Phone A acts as a **master mixer** — it combines its own audio into the LEFT channel and Phone B's incoming stream into the RIGHT channel. To prevent native stereo bleeding, Phone A drops its media volume to 1 and outputs the final mixed stereo stream over the **Alarm** volume channel directly into the Bluetooth earbuds.

---

## ✨ Features

- 🎵 **Stereo channel isolation** — complete left/right separation with no audio bleed
- 📡 **Google Nearby Connections** — Wi-Fi P2P streaming, no internet required
- 🎙️ **System-wide audio capture** — stream any app (Spotify, Chrome) using MediaProjection
- 🎧 **Alarm channel mixing** — isolates mixed audio to the Alarm stream to prevent system stereo bleed
- 🗜️ **Opus codec** — 64 kbps compressed audio via Android MediaCodec
- 🔄 **Jitter buffer** — smooth playback with 15-frame adaptive packet smoothing
- 📊 **Live telemetry** — real-time RTT latency (PING/PONG), buffer health, packet stats
- 🔁 **Auto-reconnection** — automatically resumes after Wi-Fi interruption
- 🛡️ **DRM warning system** — detects DRM-enforced silence and notifies users
- 🔋 **Foreground services** — wakelock-backed services ensure uninterrupted streaming
- 🎨 **Dark glassmorphism UI** — premium dark-mode Jetpack Compose interface

---

## 🏗️ Architecture

```text
┌─────────────────────────────────┐     Wi-Fi (Nearby Connections)    ┌──────────────────────────────────┐
│        PHONE B (Sender)         │ ─────────────────────────────────> │       PHONE A (Receiver)          │
│                                 │                                    │                                   │
│  System Audio                   │    Opus-compressed PCM frames      │  System Audio                     │
│      │                          │       (20ms @ 64kbps)             │      │                            │
│  AudioPlaybackCapture           │                                    │  AudioPlaybackCapture             │
│  (MediaProjection)              │                                    │  (MediaProjection)                │
│      │                          │                                    │      │                            │
│  OpusCodec (encode)             │                                    │  LEFT CH  ◄──  Local PCM          │
│      │                          │                                    │  RIGHT CH ◄──  Decoded Remote     │
│  StreamSender ──────────────────┤                                    │                                   │
└─────────────────────────────────┘                                    │      AudioMixer (stereo merge)    │
                                                                       │      AudioTrack (Alarm Stream)    │
                                                                       └──────────────────────────────────┘
```

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | 100% Kotlin 2.0.21 |
| UI | Jetpack Compose + Material3 |
| Architecture | MVVM + Hilt (DI) |
| Networking | Google Nearby Connections API |
| Audio Encoding | Android MediaCodec (Opus) |
| Concurrency | Kotlin Coroutines + Flow |
| Services | Android Foreground Services |
| Build | Gradle 9.1.0 + AGP 9.0.1 |

---

## 📁 Project Structure

```text
app/src/main/java/com/dualstream/
├── audio/
│   ├── AudioConstants.kt           # Sample rate, frame size, bit depth constants
│   ├── AudioMixer.kt               # PCM stereo interleaving (LEFT + RIGHT channels)
│   ├── JitterBuffer.kt             # Thread-safe 15-frame adaptive jitter buffer
│   ├── OpusCodec.kt                # MediaCodec Opus encoder/decoder
│   ├── AudioCaptureManager.kt      # MediaProjection-based system audio capture
│   └── DualAudioPlayer.kt          # Alarm stream audio player for the mixed output
│
├── network/
│   ├── NearbyConnectionManager.kt  # Google Nearby Connections manager
│   ├── StreamSender.kt             # Serializes Opus frames onto network stream
│   └── StreamReceiver.kt           # Deserializes incoming audio packets
│
├── service/
│   ├── SenderForegroundService.kt  # Phone B: capture → encode → stream
│   ├── ReceiverForegroundService.kt# Phone A: capture → receive → decode → mix → play
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
3. Tap **Start Sender** — the app will request screen capture permission (MediaProjection) to capture system audio.
4. Phone A begins advertising over Nearby Connections and lowers media volume to 1 to prevent native playback bleed.
5. Play audio in any app (Chrome, local player) on Phone A.
6. Once Phone B connects and starts sending, audio will be mixed: Phone A's system audio → **LEFT earbud**, Phone B's audio → **RIGHT earbud**.
7. **Important:** The mixed audio is played via the **Alarm volume stream** to prevent stereo bleeding. **Use your Alarm volume slider** to adjust the earbud volume!

### Phone B — Sender Mode
1. Open DualStream → tap **"I am the Sender (Phone B)"**
2. Grant **Record Audio** and **Nearby Wi-Fi** permissions
3. Tap **START STREAMING** → grant the MediaProjection (screen capture) permission
4. Phone B will automatically discover and connect to Phone A
5. The system audio from Phone B is captured, compressed (Opus 64kbps), and streamed to Phone A.

> **Note on DRM:** Apps like Spotify, Apple Music, and Netflix block system audio capture using DRM flags. When capturing from these apps, the system outputs silent frames. DualStream will detect this and display a warning. Use a browser (like Chrome) or apps that don't enforce DRM for audio streaming.

---

## 🔐 Permissions

| Permission | Device | Purpose |
|-----------|--------|---------|
| `RECORD_AUDIO` | Both | System audio playback capture via MediaProjection |
| `FOREGROUND_SERVICE` | Both | Keep streaming alive in background |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Both | Foreground service type declaration for screen capture |
| `NEARBY_WIFI_DEVICES` (API 33+) | Both | Nearby Connections Wi-Fi P2P |
| `ACCESS_FINE_LOCATION` (API < 33) | Both | Nearby Connections (legacy) |
| `POST_NOTIFICATIONS` (API 33+) | Both | Ongoing foreground service notification |
| `WAKE_LOCK` | Both | Prevent CPU sleep during streaming |

---

## 🔬 Technical Deep Dive

### Audio Pipeline (Phone A, Receiver)

```text
Phone A System Audio
    │
    ▼
AudioCaptureManager (MediaProjection)
    │  intercepts PCM bytes 
    ▼
leftChannelBufferQueue (LinkedBlockingQueue)
    │
    ├── LEFT channel PCM (Phone A) ────────────┐
    │                                          │
JitterBuffer (15-frame LinkedBlockingDeque)   AudioMixer.mix(left, right)
    │                                          │
    ├── RIGHT channel PCM (decoded Opus) ──────┘
    │
    ▼
DualAudioPlayer (Alarm Stream) → Bluetooth headset
```

### Network Protocol

```text
┌──────────────────────────────────────────────────────┐
│                  Audio Frame Packet                   │
├──────────────┬───────────────────────────────────────┤
│  2 bytes     │  N bytes                              │
│  Frame size  │  Opus-encoded PCM payload             │
│  (big-endian)│  (20ms @ 44.1kHz stereo, 64kbps)     │
└──────────────┴───────────────────────────────────────┘
```

Frames are sent over a `Payload.Type.STREAM` Nearby Connections pipe, enabling continuous low-latency audio without TCP handshake overhead per frame. This provides consistent throughput, preventing the Play Services throttling that occurs with discrete payload `BYTES` delivery.

### Latency Measurement

Every 5 seconds the Receiver sends a `PING` control message containing a timestamp. The Sender responds with a `PONG` echoing the timestamp. The Receiver calculates `RTT / 2` as the estimated one-way latency, displayed in the live telemetry panel.

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
- [Dagger Hilt](https://dagger.dev/hilt/) — dependency injection
- [Jetpack Compose](https://developer.android.com/compose) — modern declarative UI
