# 🚗⏱️ CalmDrive — Android Auto & Phone Visual Timer

A neurodivergent-friendly visual timer application built specifically for **Android Auto "Coolwalk" Split-Screen** and Android phones. It runs inside the secondary media tile (~35% of the car display) so Google Maps or navigation remains 100% visible on the main portion of the screen, with a companion controller on the phone.

---

## 🌟 Key Features

1. **Android Auto Coolwalk Split-Screen (MediaSession Architecture)**:
   - Occupies the ~35% side media tile in Android Auto's Coolwalk dashboard alongside Google Maps.
   - Live 512×512 dynamic artwork (`METADATA_KEY_ALBUM_ART`).
   - Car transport buttons: **`-1m`**, **`Play/Pause`**, **`+1m`**, **`+5m`**, and **`⏭ Skip to Then`**.
   - Car-screen browsing drawer (`MediaBrowserServiceCompat`) to switch routines and styles from the dashboard.
   - Silent audio keep-alive stream ensures Android Auto holds the media tile active without cutting off.

2. **3 Visual Timer Graphic Styles**:
   - **🟢 Time Timer Shrinking Disc**: Classic high-contrast disappearing color wedge with center activity icon.
   - **🚗 Car Journey Path**: Moving car traveling along a road toward the destination milestone.
   - **🧩 Calm Blocks**: 10 discrete countdown blocks that step down with zero continuous motion (prevents ticking anxiety).

3. **Low-Anxiety Sensory Customizations**:
   - **Number Modes**: *Calm Rounded* ("12 min left", switching to 30s/10s steps under 1 minute), *Exact MM:SS*, or *No Numbers* (shape & color only).
   - **Color Progression**: *Traffic Light* (Green → Amber → Soft Coral), *Calm Ocean* (Blue → Teal → Lavender), or *High-Contrast Sunshine*.
   - **Gentle Transition Alerts**: Synthesized warm C-major pentatonic harmonic chimes (non-startling) and optional calm spoken voice alerts (TTS) at **Halfway**, **5 min**, **2 min**, **1 min**, and **Arrived**.

4. **"First → Then" Routine Support**:
   - Neurodivergent kids thrive with predictability. Displays the current step and previews the next step (e.g. `First: 🚗 Drive to Park (15m) → Then: 🛝 Swings & Playground (30m)`).
   - Automatically advances to the "Then" activity when the first timer completes.

5. **Google Maps Live ETA Sync**:
   - Reads active Google Maps turn-by-turn navigation ETA notifications so traffic delays smoothly adjust the visual timer, ensuring the countdown doesn't end before you actually arrive.

---

## 🚀 How to Run & Build

### 1. Interactive Web Simulator (Test immediately)
You can test the exact Android Auto Coolwalk split-screen layout and phone companion app in any web browser:
- Open [`index.html`](file:///home/sglenn21/Downloads/visual%20timer/index.html) or browse to `http://localhost:8090`
- Use the toolbar controls for **1×**, **10×**, or **30× Demo** speed, standard (`800×480`) vs widescreen (`1280×720`) layouts, and simulate Google Maps traffic delays (`+3m`).

### 2. Build via GitHub Actions (Zero Local Setup)
This repository includes a pre-configured GitHub Actions workflow [`.github/workflows/build-apk.yml`](file:///home/sglenn21/Downloads/visual%20timer/.github/workflows/build-apk.yml):
1. Push this folder to a GitHub repository (`git init && git add . && git commit -m "Initial commit" && git push`).
2. Go to the **Actions** tab on your GitHub repository.
3. The `Build CalmDrive Android Auto APK` workflow will automatically compile the APK on Ubuntu with JDK 17.
4. Download the ready-to-install `CalmDrive-AndroidAuto-Debug-APK` artifact and install it directly on your Android phone!

### 3. Open in Android Studio
1. Open Android Studio.
2. Select **Open an Existing Project** and choose this folder (`/home/sglenn21/Downloads/visual timer`).
3. Android Studio will automatically sync the Gradle files (`settings.gradle.kts` and `app/build.gradle.kts`).
4. Click **Run ▶** to install and run the app on your phone or Android emulator.
5. In Android Auto (or the Android Auto Desktop Head Unit / DHU emulator), open the app launcher to see **CalmDrive Timer**. When started, it automatically displays inside the Coolwalk split-screen media card alongside Google Maps!

---

## 📁 Codebase Structure

```
.
├── index.html                           # Interactive Android Auto Coolwalk & Phone Simulator
├── styles.css                           # Coolwalk & Material 3 simulator styling
├── app.js                               # Simulator artwork canvas & logic engine
├── settings.gradle.kts                  # Root Gradle settings
├── build.gradle.kts                     # Root Gradle plugins
├── gradle.properties                    # AndroidX & JVM flags
├── gradlew                              # Gradle wrapper script
├── .github/workflows/build-apk.yml      # Automated GitHub Actions APK builder
└── app/
    ├── build.gradle.kts                 # Jetpack Compose, MediaSession, Core dependencies
    ├── proguard-rules.pro               # Proguard rules
    └── src/main/
        ├── AndroidManifest.xml          # Automotive app descriptor & service declarations
        ├── res/
        │   ├── xml/automotive_app_desc.xml # Declares media app for Android Auto
        │   ├── values/strings.xml, colors.xml, themes.xml
        │   └── drawable/ic_*.xml        # Vector icons for transport controls
        └── java/com/calmdrive/visualtimer/
            ├── CalmDriveApp.kt          # Application class (Notification channels)
            ├── MainActivity.kt          # Jetpack Compose phone companion UI
            ├── model/Routine.kt         # Routine data classes & style enums
            ├── data/PreferencesRepository.kt # SharedPreferences storage
            ├── engine/
            │   ├── TimerArtworkRenderer.kt # 512x512 Bitmap canvas artwork generator
            │   ├── HarmonicChimePlayer.kt  # Non-startling AudioTrack pentatonic chime synth
            │   ├── SilentKeepAlivePlayer.kt# Silent AudioTrack PCM keep-alive loop
            │   ├── SpokenAlertEngine.kt   # Android TextToSpeech milestone engine
            │   └── TimerEngine.kt         # Countdown ticker, milestone tracker, Maps sync
            └── service/
                ├── CalmDriveMediaService.kt # MediaBrowserServiceCompat + MediaSessionCompat
                └── MapsNotificationListenerService.kt # Google Maps ETA listener
```
