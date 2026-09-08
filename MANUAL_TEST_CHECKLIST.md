# DualStream Manual Test Checklist

This document details manual testing procedures that cannot be fully verified via automated tests in CI due to the physical nature of the features (Bluetooth, spatial audio, physical distance, etc).

## QA Test Environment Setup
- **Phone A (Receiver / Master)**: Android 10+ device (API 29+) connected to the local Wi-Fi.
- **Phone B (Sender)**: Android 10+ device (API 29+) connected to the same local Wi-Fi.
- **Hardware**: A pair of True Wireless Stereo (TWS) Bluetooth earbuds (e.g., AirPods, Pixel Buds) paired and actively connected to **Phone A only**.

---

## 1. End-to-End Dual-Audio Pipeline
**Goal:** Verify true L/R audio isolation through a single Bluetooth connection.

- [ ] **Step 1:** Launch DualStream on Phone A, select "I am the Receiver", and tap "Start Sender". Accept screen capture permissions.
- [ ] **Step 2:** Launch DualStream on Phone B, select "I am the Sender", and tap "Start Streaming". Accept screen capture permissions.
- [ ] **Step 3:** Wait for the UI on both devices to display **"Connected (Live)"** indicating the `AUDIO_FLOW_CONFIRMED` handshake completed.
- [ ] **Step 4:** On Phone A, play a distinct, easily identifiable audio track (e.g. a 440Hz tone from Chrome).
- [ ] **Step 5:** On Phone B, play a completely different audio track (e.g. a 880Hz tone or spoken podcast).
- [ ] **Expected Result:** Put the Left earbud in your left ear and Right earbud in your right ear. You must clearly hear Phone A's audio in the LEFT channel and Phone B's audio in the RIGHT channel. 
- [ ] **Expected Result (No bleed):** There should be absolutely no Phone A audio bleeding into the Right earbud, and no Phone B audio bleeding into the Left earbud.

## 2. Self-Leak via Speaker (Regression Check)
**Goal:** Verify that neither phone plays the audio aloud from its physical loudspeaker.

- [ ] **Step 1:** While the streaming session from the previous test is active, remove the earbuds from your ears.
- [ ] **Step 2:** Place your ear ~30cm away from Phone A's bottom/top speakers.
- [ ] **Step 3:** Place your ear ~30cm away from Phone B's bottom/top speakers.
- [ ] **Expected Result:** Neither phone's physical loudspeaker should be emitting the music. Android's native stereo should be correctly routed or silenced via Strategy A (hardware route) or Strategy B (Gain Fallback).

## 3. Audio Quality & Gain Fallback
**Goal:** Verify the uncompressed PCM stream and Gain Makeup math doesn't distort audio.

- [ ] **Step 1:** Disconnect any wired or dummy Bluetooth devices from Phone B so that Strategy B (Gain Makeup) activates.
- [ ] **Step 2:** Stream music from Phone B to Phone A.
- [ ] **Expected Result:** The right earbud should sound loud, clear, and without significant hiss, crackle, or clipping. It should not sound 6dB quieter than playing the music directly.

## 4. Latency and Stress Test
**Goal:** Verify JitterBuffer adaptability and Wi-Fi link degradation.

- [ ] **Step 1:** Check the live telemetry panel on Phone A. Note the latency.
- [ ] **Step 2:** Tap a button on Phone B that makes a UI sound. Measure the perceptible delay until you hear it in the right earbud.
- [ ] **Step 3:** Walk Phone B ~10-15 meters away from Phone A (or into a different room) to weaken the Wi-Fi signal.
- [ ] **Expected Result:** The audio may stutter and buffer health will drop, but the app should not hard crash. When returning into range, the audio stream must gracefully recover to real-time.

## 5. DRM Detection & Warning
**Goal:** Verify that the system correctly catches and flags DRM-protected silent frames.

- [ ] **Step 1:** Start an active session.
- [ ] **Step 2:** On Phone A (Receiver), open Spotify or Apple Music and play a protected track.
- [ ] **Expected Result:** DualStream on Phone A should detect the continuous stream of 0x00 silence and pop up a **"Silence Detected"** DRM warning card explaining that the user needs to use an unlocked app like Chrome.

## 6. Permissions and First-Run UX
**Goal:** Verify proper rationales and graceful rejection handling.

- [ ] **Step 1:** Fresh install the app on an Android 13+ device.
- [ ] **Step 2:** Deny the `RECORD_AUDIO` permission when prompted.
- [ ] **Expected Result:** The app should display a clear in-app error/rationale dialog (`PermissionRationaleDialog.kt`) and not silently fail or crash.
- [ ] **Step 3:** Go to Android Settings and revoke `NEARBY_WIFI_DEVICES` mid-stream.
- [ ] **Expected Result:** The foreground service should shut down gracefully with a notification/toast rather than a hard crash.

## 7. Foreground Service Reliability
**Goal:** Verify that Android Doze/Battery Optimization does not kill the stream.

- [ ] **Step 1:** Start an active session with audio flowing.
- [ ] **Step 2:** Press the Home button on both phones to background the apps.
- [ ] **Step 3:** Lock the screens of both phones and leave them on a table for 15 minutes while audio continues playing.
- [ ] **Expected Result:** The audio stream should continue uninterrupted. The wakelocks and foreground service notifications must keep the process alive.
