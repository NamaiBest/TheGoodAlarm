# The Good Alarm

I use Apple Music on a Samsung S23, and Samsung's Clock app only lets you wake up to Spotify. So I made my own alarm app that plays whatever song I pick from Apple Music.

It's a normal alarm clock otherwise: repeat days, labels, snooze, vibration, and a slide to stop. The UI is dark with Apple Music red.

## Using it

1. Tap + and set a time.
2. Under Sound, search for any song on Apple Music and pick one. You can preview it first.
3. Save.

That's it. When the alarm goes off, the song plays in the Apple Music app on repeat until you stop or snooze it.

## How it gets Apple Music to play the right song

Apple Music on Android doesn't let other apps say "play this song". If you send it a play command, it just resumes whatever you listened to last. So the app works around that:

- When you save an alarm (or open the app), it opens your song in Apple Music, taps it, clears the old queue, and pauses it right away with the volume muted. Your song is now the "last played" track.
- At alarm time it just hits play. This works even when the phone is locked.
- If you're offline, it plays something you've downloaded in Apple Music. The regular alarm tone is only used if Apple Music can't play anything at all.

The tapping is done by a small accessibility service. It only looks at Apple Music, and only for the few seconds it takes to load the song.

One catch: if you listen to other music after setting the alarm, open the app once before bed so it can load your alarm song again.

## Setup

The first time, open Settings (the gear) and allow everything in the Permissions list: notifications, full-screen alarms, show on top, media control, the Apple Music helper, and unrestricted battery. Samsung puts apps to sleep otherwise.

Android turns the Apple Music helper off whenever the app is updated, so if you reinstall it, switch it back on.

## Building

You need JDK 17 and the Android SDK.

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Search uses Apple's public iTunes Search API, so there's no API key or developer account needed.
