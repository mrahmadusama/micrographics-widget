# Working on this repo

Notes for coding agents (and future me). The README covers what the widgets do; this covers how
to work on them.

## Build

This Mac's default Java (27) and missing `local.properties` both break the build. Use JDK 17 and
the Homebrew Android SDK:

    JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
    ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
    ./gradlew -q assembleRelease

The APK lands in `app/build/outputs/apk/release/app-release.apk`. The "SDK XML version 4"
warning is harmless.

## Test on the phone

A Pixel is usually attached over USB (`adb devices`). It can disconnect mid-session; check
again before blaming the build.

    adb install -r app/build/outputs/apk/release/app-release.apk
    adb shell input keyevent KEYCODE_WAKEUP
    adb shell am broadcast -a com.ahmad.micrographics.REFRESH -n com.ahmad.micrographics/.MicrographicsWidget
    adb exec-out screencap -p > shot.png

- A black screenshot means the screen is off: wake it first.
- To flip a setting, open `.SetupActivity`, find the view's bounds with
  `adb shell uiautomator dump /sdcard/ui.xml`, then `adb shell input tap X Y`. Put the user's
  settings back afterwards (they use Auto colour with accents on).
- Release builds aren't debuggable, so `run-as` can't edit prefs directly.

## Looking at vector drawables

There's no Android Studio. To see icons, convert the vector XML to SVG in an HTML page and
render it with `qlmanage -t -s 1400 -o <dir> page.html` (Quick Look outputs `page.html.png`).

## How the widget is built

- Launchers ignore app fonts in RemoteViews, so all text is drawn to bitmaps (`TextBitmap`) and
  the whole widget is redrawn every minute by `WidgetUpdater`. Never use TextViews for widget text.
- Every vector icon is a single colour (`#fff2f2f2`) and is recoloured with
  `setColorFilter`. When you add an icon ImageView, give it an id and add it to `INK_TINTED`,
  `PRIMARY_TINTED` or `TERTIARY_TINTED` in `WidgetUpdater`, or it stays white in dark mode.
- `Palette` resolves ink/primary/tertiary from the colour setting (Auto reads
  `WallpaperColors.HINT_SUPPORTS_DARK_TEXT`) and Material You system colours.
- Icons must keep their aspect ratio: use `wrap_content` width with `adjustViewBounds` and
  `fitCenter`. Some weather icons are double-wide (two glyphs); a fixed square squashes them.
- Rows below the date are optional and fit to the widget height (`rowsBelowDate`), dropping in
  the order alarm, birthdays, charging, meeting, weather, flashlight, timer. Row toggles live in
  `Prefs.show`.
- Taps: by default only `R.id.root` has a click (clock app); row clicks are set only when
  "rows open their own apps" is on, because a row with any click handler swallows taps meant for
  the root. While the flashlight is on, root (and the Gooey clock) send `ACTION_TORCH_OFF`.
- Timers have no API: `StatusListener` (a NotificationListenerService) parses the Clock app's
  notification. On this phone (`com.android.deskclock`, Android 16) it's a MetricStyle
  notification: `android.metrics[i].value` has `countDown=true` and `zeroTime` = end time. Test
  with `adb shell am start -a android.intent.action.SET_TIMER --ei android.intent.extra.alarm.LENGTH 300 --ez android.intent.extra.alarm.SKIP_UI true`
  and clean up with `android.intent.action.DISMISS_TIMER`. Don't grant notification access via
  adb; the user grants it from the setup screen.
- Flashlight state is read with a register/unregister TorchCallback each redraw
  (`DeviceStatus.torchOn`); the listener service also keeps a callback so changes redraw at once.
- "LABEL (icon) ╰COUNT" rows share one shape: a label bitmap, an icon, and a FrameLayout with
  `_corner`, `_line` and `_count` ImageViews. Copy an existing one and bind it with `bindBracket`.
- Nothing's drawables (`micrographics_*`, gooey font) belong to Nothing; keep the repo private.
  New icons (`mg_icon_*`) are drawn by hand on the same 73.04 viewport in a chunky filled style.

## Conventions

- Commits go straight to `main`; short imperative subject lines.
- Keep comments explaining *why* (launcher limits, Android quirks), as the existing code does.
