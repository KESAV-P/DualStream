# Changelog

## [1.1.0] - 2026-09-08

### Fixed
- **True Dual-Channel Audio Isolation**: Resolved the root cause of the audio leakage bug where both phones audibly leaked their own audio from the speaker instead of maintaining a clean L/R split through the Bluetooth earbuds.
    - **Strategy A (Hardware Routing):** When a dummy wired headset or Bluetooth device is detected on Phone B, the app no longer mutes the main `STREAM_MUSIC`. Android's native audio routing handles the output silently.
    - **Strategy B (Gain Compensation):** If no alternate output device is found, the app displays a blocking dialog warning about leakage. If the user proceeds, it sets the volume to 1 (to keep the stream alive without full speaker bleed) and applies a sophisticated Exponential Moving Average (EMA) gain-makeup algorithm to digitally restore the stream volume in the capture buffer.
- **Network Backpressure Stalls**: Fixed an issue where the `StreamSender` blocked the active capture coroutine when writing to the finite `ParcelFileDescriptor` kernel buffer. Introduced a bounded Kotlin `Channel` (capacity=25, dropping oldest) to decouple the capture loop from the network I/O block, preventing latency spikes from halting audio capture.

### Added
- **Audio Flow Handshake**: Implemented a two-way `AUDIO_FLOW_CONFIRMED` JSON control message handshake. The UI now explicitly distinguishes between "Connected (Handshake)" and "Connected (Live)", preventing user confusion before audio packets actually arrive.
- **Real Bitrate Telemetry**: Replaced the hardcoded `768 kbps` label with a real-time sliding window byte counter that dynamically computes and displays the true network throughput.
- **Adaptive Jitter Buffer**: Enhanced the `JitterBuffer` to detect starvation (underruns) and prolonged congestion (buffer health > 80% for more than 2 seconds). The buffer will now selectively drop frames to reduce built-up latency rather than remaining perpetually full.
- **Debug Telemetry Overlay**: Added a `BuildConfig.DEBUG` guarded overlay to the UI that exposes the active audio routing strategy, handshake status, and real-time bitrate metrics to assist in future debugging.

### Changed
- Removed Opus encoding and decoding in favor of uncompressed 16-bit 48kHz mono PCM to guarantee latency and fidelity. Updated `README.md` to accurately reflect the true pipeline architecture.
