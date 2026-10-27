# Micrographics widget

A home screen widget for Android (built for the Pixel Launcher) that recreates the
Nothing OS 4 "Micrographics" lock screen clock:

```
FRI (25)(JUL)  10:27
I< 23°(C)  * (SUNNY)
(avatar) WEEKLY DESIGN SYNC (->)
STARTING (hand) |_3MIN
```

![Widget on a Pixel 9 Pro](screenshot-meeting.png)

The icons, month pills and weather labels are Nothing's own vector drawables, decoded from
Nothing OS `SystemUI.apk`. They belong to Nothing Technology and are included for personal use
only; this repository is private for that reason. The text font is Geist Mono (SIL Open Font
License) with stylistic set 09 (plain zero) built in.

## Install

Download `Micrographics.apk` from the latest release, plug in the phone with USB debugging on,
then:

    adb install -r Micrographics.apk

Open **Micrographics**, grant calendar and location access (or type a city for weather), then
tap **Add widget to home screen**, or long-press the home screen, tap **Widgets** and add
**Micrographics clock**. Needs Android 12 or newer.

## How it updates

- Day name and time are `TextClock`s and tick on their own.
- The date pill and month change at midnight.
- Weather comes from Open-Meteo (no API key), refreshed every 30 minutes for the location
  set in the app. Tap the weather row to change it or switch to °F.
- The next timed calendar event in the next 12 hours shows on rows 3 and 4. Within an hour of
  it starting, the countdown updates every minute; for the last 10 minutes a hand icon counts
  down too. Once it starts the row shows when it ends. Calendar edits show up within about
  15 seconds.

## Build

Needs JDK 17 and the Android SDK (platform 35). Set `sdk.dir` in `local.properties`, then:

    ./gradlew assembleRelease

Release builds are signed with the local debug key, since the app is only sideloaded.
