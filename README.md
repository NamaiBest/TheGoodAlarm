# The Good Alarm

A glass-styled Android alarm clock that wakes you up with **any song from Apple Music**.

Samsung's Clock app can only use Spotify for alarms. Good Alarm searches the Apple Music catalogue, and at alarm time it tells the Apple Music app, signed in with your own account, to play the track you picked on repeat.

## Features

- iOS-style wheel time picker, repeat days, labels, snooze (1–30 min), vibration
- Search the Apple Music catalogue with artwork and 30-second previews
- Full-screen ringing screen over the lock screen with Snooze and **slide to stop**
- Gradual volume increase, configurable alarm volume and ring duration
- Survives reboots, time changes and timezone changes
- Fallback chain if Apple Music can't start: song preview, then the system alarm tone

## How the Apple Music part works

1. **Search** uses Apple's public iTunes Search API, so no developer token or login is needed.
2. **Playback** goes through Apple Music's Android media session. The app binds to Apple Music's playback service, which wakes it up, then finds its session through notification access. It sends `playFromUri` / `playFromSearch` for the chosen track and sets repeat-one. On Stop, it pauses and restores your repeat mode and volume.
3. A watchdog checks that music is actually playing. If Apple Music doesn't start within a few seconds, or gets paused, the alarm switches to the fallback sound so you still wake up.

### One-time setup on the phone

Open **Settings (gear) → Permissions** and allow:

- **Media control** (notification access). Apple Music only lets the app control it through this.
- **Unrestricted battery**, so One UI doesn't put the app to sleep
- **Full-screen alarms** and **Notifications**

## Build and install

Requires JDK 17 and the Android SDK (compileSdk 36).

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
