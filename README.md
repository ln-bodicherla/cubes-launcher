# Cubes Launcher

An Android home screen launcher inspired by Windows Phone, with resizable app tiles, plus a set of companion apps (phone, messaging, people, clock, weather, calculator and a keyboard) that share its theme.

## Features

### Launcher (`launcher`)

- Start screen of resizable tiles for pinned apps, with long-press actions to unpin, resize, open app info or uninstall.
- Swipe over to an alphabetical list of all installed apps, with sticky letter headers and search.
- Three tile modes that switch off one another: show every app as a tile, show only pinned apps and apps with notifications ("minimal tiles"), or resize tiles automatically by how often you open each app ("usage tracking"). Each mode keeps its own tile sizes.
- Notification count badges on tiles, using a notification listener service.
- A pull-down panel with a notification list (dismiss, clear all) and quick settings toggles for Wi-Fi, Bluetooth, flashlight, airplane mode, location, rotation, mobile data, quiet hours and brightness.
- Settings for dark or light theme, accent color, tile transparency, a four- or six-column grid, and a custom background image.
- Theme and accent color changes are broadcast to the companion apps so they stay in sync.

### Companion apps

Each one is a separate APK with its own application ID (`com.cubeslauncher.*`).

- **Phone** (`phone`): speed dial, call history, keypad and voicemail tabs; an in-call screen through `InCallService` (answer, decline, mute, speaker, hold, audio route); SIM selection; call details per number; can ask to become the default phone app. It can also start WhatsApp voice and video calls by handing off to the installed WhatsApp app.
- **Messaging** (`messages`): an SMS/MMS app that can be set as the default. It has messages, groups, archived and deleted tabs, delivery status, reply from the notification, swipe actions and text size settings. An optional WhatsApp tab links a WhatsApp account by QR code or phone pairing code and supports text, photo, video, audio and document messages, reactions, edits, deletes, read receipts and typing indicators, group info (name, topic, invite link, leave), a blocklist and privacy settings. This uses a Go bridge built on the [whatsmeow](https://github.com/tulir/whatsmeow) library (see `whatsmeow-android/`).
- **People** (`contacts`): contacts, favorites and groups tabs; view, add, edit and delete contacts; sort and name display options.
- **Clock** (`clock`): alarms (repeat days, labels, snooze, vibrate, re-armed after reboot), world clock, stopwatch with laps, and timer. The stopwatch and timer run as foreground services.
- **Weather** (`weather`): current conditions, hourly and 5-day forecasts and details (humidity, wind, UV index, pressure, dew point) for saved cities, using the [Open-Meteo](https://open-meteo.com/) forecast and geocoding APIs (no API key needed). Units can be °F/°C and mph/km/h.
- **Calculator** (`calculator`): a standard calculator with memory keys (MC, MR, M+, M−, MS) and functions such as 1/x, x², √x and ±.
- **Cubes Keyboard** (`keyboard`): an input method with word suggestions, auto-correction, auto-capitalization, double-space period, glide typing with an optional trail, an optional number row, sound and vibration, three height settings, voice typing and recent clipboard items.

## Screenshots

<!-- Add screenshots to docs/screenshots/ -->

## Tech stack

- Kotlin, Android Views with ViewBinding (no Compose)
- Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Gradle 8.11.1 wrapper
- compileSdk / targetSdk 35, minSdk 26, Java 17
- AndroidX: AppCompat, Material Components, ConstraintLayout, RecyclerView, ViewPager2, Lifecycle, DataStore Preferences
- Glide for images, ZXing for WhatsApp pairing QR codes
- Go + gomobile for the WhatsApp bridge (`whatsmeow`)

## Getting started

### Prerequisites

- Android Studio or the Android SDK command-line tools with SDK platform 35
- JDK 17
- A device or emulator running Android 8.0 (API 26) or later

### Build and install

The project is one Gradle build with one module per app. Debug builds need no extra setup:

```sh
# Build every app
./gradlew assembleDebug

# Build and install a single app on a connected device
./gradlew :launcher:installDebug
./gradlew :phone:installDebug
./gradlew :messages:installDebug
# ...and likewise for :contacts, :clock, :weather, :calculator, :keyboard
```

After installing, choose Cubes Launcher as the home app. Set Phone and Messaging as the default phone and SMS apps if you want to use them that way, and turn on Cubes Keyboard under system input method settings. Grant the launcher notification access for badges and the notification panel.

### Release builds

Release builds are minified and signed with a key described in a `keystore.properties` file at the repo root. That file is git-ignored and must contain:

```properties
storeFile=...
storePassword=...
keyAlias=...
keyPassword=...
```

Then run `./gradlew assembleRelease`.

### Rebuilding the WhatsApp bridge (optional)

A prebuilt `messages/libs/whatsmeow.aar` is checked in. To rebuild it from `whatsmeow-android/bridge.go` you need Go (the module targets Go 1.25), gomobile and the Android NDK. `whatsmeow-android/build.sh` uses `ANDROID_HOME` to find the NDK and falls back to the default macOS SDK path if it is not set. It writes the `.aar` to `messages/libs/`.

## Project structure

```
.
├── launcher/            Home screen: tiles, app list, notification panel, quick settings, settings
├── phone/               Dialer, call history, voicemail, in-call UI
├── messages/            SMS/MMS app and WhatsApp integration
│   └── libs/            Prebuilt whatsmeow.aar
├── contacts/            People app
├── clock/               Alarms, world clock, stopwatch, timer
├── weather/             Open-Meteo forecast app
├── calculator/          Calculator
├── keyboard/            Input method service
├── common/              Shared theme/accent color helpers, preferences and inter-app intent constants
├── whatsmeow-android/   Go source and build script for the WhatsApp bridge
└── gradle/              Version catalog (libs.versions.toml) and wrapper
```

## Status

Personal project, in active development. Some pieces are still in progress. For example, a lock screen background can be chosen in settings but nothing uses it yet, and scheduled SMS has a model and service but no way to schedule a message from the UI yet.

The WhatsApp integration is an unofficial client built on whatsmeow and is not affiliated with WhatsApp or Meta.
