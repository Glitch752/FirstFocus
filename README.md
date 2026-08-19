# focus

A work-in-progress minimal, local-only screen time and focus management tool for Android.

Features:
- Very minimal UI focused on usability
- Select distracting apps that will be used for time tracking, cooldowns, and focus sessions
- App usage prompt with a configurable countdown to open
- When opening apps, ask for a desired usage time (0-30m) and prompt a reminder when the time is up
- [ ] Choose a target daily usage across all distracting apps and prompt reminder when the target is reached
- Focus sessions preventing selected apps from being opened for a set duration through an accessibility service
  - [ ] Option to set screen to grayscale during focus sessions
  - [ ] Reminders to start focus sessions at configurable times of day
- [ ] Time (app and focus) tracking in a local database with graphs and statistics
- [ ] Export and import of app data and settings to/from a simple JSON file

## Architecture
This is my first polished/"real" Android app, so I don't fully know what I'm doing. The app is written in Kotlin and uses Jetpack Compose for the UI and [Room](https://developer.android.com/training/data-storage/room) (a sqlite wrapper) for local data storage.

## Build
Requirements: Android Studio Android SDK with API 35, Java 17, and [just](https://github.com/casey/just).

```text
just debug # build a debug APK
just install # build and install on a connected device
just release # build a release APK
just test # run JVM tests
just clean # remove build outputs
just check # run static analysis and linting
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
