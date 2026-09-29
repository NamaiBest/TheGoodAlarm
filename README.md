# The Good Alarm

A glass-styled Android alarm clock that wakes you up with **any song from Apple Music**.

Samsung's Clock app can only use Spotify for alarms. Good Alarm searches the Apple Music catalogue, and at alarm time it tells the Apple Music app, signed in with your own account, to play the track you picked on repeat.

## Features

- iOS-style wheel time picker, repeat days, labels, snooze (1–30 min), vibration
- Search the Apple Music catalogue with artwork and 30-second previews
- Full-screen ringing screen over the lock screen with Snooze and **slide to stop**
- Gradual volume increase, configurable alarm volume and ring duration
- Survives reboots, time changes and timezone changes
- Works on the lock screen; offline it plays your downloaded Apple Music songs

## How the Apple Music part works

Apple Music on Android won't let other apps start a specific song. It ignores "play this ID/URI/search" media commands and just resumes its last track, and it refuses the Android Auto browser connection. So Good Alarm works with that behaviour:

1. **Search** uses Apple's public iTunes Search API (no developer token or login). The store region is taken from your SIM.
2. **Pre-load.** When you save an alarm, or open the app, Good Alarm opens the song's `music.apple.com` page in Apple Music. An accessibility helper then taps the song and answers **Clear** to the "keep or clear your queue?" prompt. The song starts muted and is paused straight away, which makes it Apple Music's current track. It's skipped if you're in the middle of listening to something.
3. **At alarm time** the app keeps Apple Music awake (One UI freezes background apps, and a frozen app ignores commands). It resumes the pre-loaded song through Apple Music's media session and sets repeat-one. This works on the lock screen. If the phone is unlocked and the song isn't loaded, it loads it right then.
4. **Fallbacks.** If the song can't play (for example, you listened to something else and you're offline), it plays whatever Apple Music has, which means your downloads when offline. Only if Apple Music can't play anything at all does it use the song preview, then the system alarm tone.
5. On Stop or Snooze it pauses Apple Music and restores your repeat mode and volume.

The helper only receives events from Apple Music, and only acts during those few seconds. Nothing runs between alarms, apart from the alarm itself being scheduled with the system.

### One-time setup on the phone

Open **Settings (gear) → Permissions** and allow everything listed:

- **Show alarm on top**: full-screen alarm even while you're using the phone
- **Media control** (notification access): lets the alarm start, loop and stop Apple Music
- **Apple Music helper** (accessibility): taps your song in Apple Music. Android switches it off whenever the app is updated or force-stopped, so re-enable it after reinstalling.
- **Unrestricted battery**, **Full-screen alarms**, **Notifications**

## Build and install

Requires JDK 17 and the Android SDK (compileSdk 36).

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
