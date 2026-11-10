# Status Saver - Video & Image Downloader

> A native, high-performance Android application built in Kotlin (ViewBinding & MVVM) for downloading, viewing, sharing, and saving WhatsApp and WhatsApp Business statuses directly to the device Gallery.

[![Android Compile SDK](https://img.shields.io/badge/Compile%20SDK-37-brightgreen.svg)](#)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-37-brightgreen.svg)](#)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-blue.svg)](#)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Coroutines-orange.svg)](#)
[![Monetization](https://img.shields.io/badge/Monetization-Unity%20Ads%20v4.19-purple.svg)](#)

---

## 📱 App Overview

**Status Saver - Video & Image Downloader** is inspired by top Play Store status utilities. It provides a seamless, edge-to-edge Material Design 3 interface with dark/light theme switching, dual WhatsApp support, Scoped Storage SAF compliance on Android 11+ (API 30–35+), full-screen swipeable media viewer with hardware-accelerated Media3 ExoPlayer, and non-intrusive Unity Ads monetization.

- **App Name:** `Status Saver` (Launcher: `Status Saver - Video & Image Downloader`)
- **Package Name:** `com.statussaver.videoimagesaver.downloader`
- **Namespace:** `com.example`
- **Language:** Kotlin 2.0+
- **Minimum SDK:** 24 (Android 7.0 Nougat)
- **Compile & Target SDK:** 37 (Android 15+)

---

## ✨ Key Features

1. **Dual WhatsApp Integration**
   - Toggle instantly between **WhatsApp** (`com.whatsapp`) and **WhatsApp Business** (`com.whatsapp.w4b`) via the header toggle in the Top App Bar.
   - Separate persistent SAF folder permissions stored for each app variant.

2. **Scoped Storage & SAF (Android 11 to Android 15+)**
   - Uses `StorageManager.createOpenDocumentTreeIntent()` targeting `.Statuses` folder directly.
   - Saves persistent folder permissions via `contentResolver.takePersistableUriPermission`.
   - Backward-compatible legacy file fallback for Android 10 and below.

3. **MediaStore Gallery Exporter**
   - Saves photos to `Pictures/StatusSaver` with `MIME_TYPE = "image/jpeg"`.
   - Saves videos to `Movies/StatusSaver` with `MIME_TYPE = "video/mp4"`.
   - Statuses appear immediately in the system Gallery/Google Photos without requiring device reboot.

4. **Swipeable Full-Screen Media Viewer**
   - Built on `ViewPager2` allowing users to swipe through all statuses without navigating back.
   - High-performance image loading with Glide (`R.drawable.ic_image_placeholder`).
   - Integrated `androidx.media3.ui.PlayerView` and `ExoPlayer` for smooth video playback.
   - Quick action bar: **Save to Gallery**, **Repost to WhatsApp**, **Share**, and **Delete** (in Saved tab).

5. **Multi-Select Batch Operations**
   - Long-press any status card to enter contextual multi-select mode.
   - Batch Save multiple items directly to Gallery in background coroutines.
   - Batch Share multiple files via Android system share sheet.
   - Select All / Clear selection with live counter.

6. **First-Time Welcome & Onboarding**
   - 3-slide visual onboarding guide with custom animated emerald pill indicators (`bg_indicator_active.xml` / `bg_indicator_inactive.xml`).
   - Illustrates saving statuses, dual WhatsApp support, and swipe previewing.
   - Persisted via `SharedPreferences` so it only displays on the first launch.

7. **Theming & System Bar Polish**
   - Full Light and Dark Mode support with a one-tap menu toggle.
   - Unified `#008069` WhatsApp dark emerald status bar with edge-to-edge system scrim.
   - Transparent navigation bar with safe WindowInsets handling.

8. **In-App Review & Smart Exit Dialog**
   - Google Play In-App Review API (`com.google.android.play:review-ktx`) automatically prompts after the user successfully saves statuses.
   - Custom exit confirmation dialog with "Share App" shortcut.
   - Direct Share action in the toolbar to easily recommend the app to friends.

9. **Unity Ads Monetization**
   - **Sticky Centered Banner Ad (320x50):** Automatically anchored above system navigation. Includes queued initialization and automatic test mode fallback on `NO_FILL`.
   - **Interstitial Ad:** Preloaded with automatic reloading on ad completion.
   - **App Resume Interstitial:** Displays when returning from WhatsApp with a 30-second cooldown timer.

---

## 🗂 Project Structure & Architecture

The application strictly adheres to Clean MVVM (Model-View-ViewModel) architecture:

```
app/src/main/
├── AndroidManifest.xml                  # Permissions, activities, Application class
├── java/com/example/
│   ├── MainActivity.kt                  # Main screen with tabs, dual WA toggle, banner
│   ├── MyApplication.kt                 # ProcessLifecycleOwner & App Resume Ad observer
│   ├── ads/
│   │   └── UnityAdsManager.kt           # Singleton for Unity Banner, Interstitial, & Init
│   ├── data/
│   │   ├── StatusMedia.kt               # Parcelable data class for status items
│   │   └── StatusRepository.kt          # SAF DocumentFile & MediaStore data operations
│   ├── ui/
│   │   ├── adapter/
│   │   │   ├── StatusAdapter.kt         # RecyclerView adapter with grid & selection mode
│   │   │   └── MediaPagerAdapter.kt     # Full-screen ViewPager2 adapter (Glide + ExoPlayer)
│   │   ├── fragment/
│   │   │   └── StatusListFragment.kt    # Images, Videos, and Saved tabs
│   │   ├── onboarding/
│   │   │   ├── OnboardingActivity.kt    # First-run welcome carousel & custom pill indicator
│   │   │   └── OnboardingAdapter.kt     # ViewPager2 adapter for onboarding slides
│   │   └── viewer/
│   │       └── MediaViewerActivity.kt   # Full-screen swipeable viewer with ExoPlayer
│   ├── util/
│   │   ├── ReviewHelper.kt              # In-App Review API & Direct Share helper
│   │   └── StorageHelper.kt             # Android 11+ Scoped Storage & SAF folder manager
│   └── viewmodel/
│       └── StatusViewModel.kt           # StateFlow for statuses, multi-selection, and tabs
└── res/
    ├── drawable/                        # Vectors, indicators, card backgrounds, illustrations
    ├── layout/                          # XML layouts using ViewBinding
    ├── menu/                            # Toolbar menu items (Direct Share, Theme, Legal)
    ├── values/                          # Strings, colors, styles, themes
    └── values-night/                    # Dark mode overrides
```

---

## 📂 Scoped Storage & Folder Paths

On Android 11+ (API level 30 and higher), direct file system access to other apps' external files is restricted by Android security policies. The app uses the **Storage Access Framework (SAF)** to request user consent for the specific hidden directory:

### WhatsApp Paths
- **Android 11+ (Scoped Storage):**  
  `Android/media/com.whatsapp/WhatsApp/Media/.Statuses`
- **Android 10 and below:**  
  `WhatsApp/Media/.Statuses`

### WhatsApp Business Paths
- **Android 11+ (Scoped Storage):**  
  `Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses`
- **Android 10 and below:**  
  `WhatsApp Business/Media/.Statuses`

### SAF Launch Flow
```kotlin
val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
}
```
Upon selection, persistent permissions are granted via:
```kotlin
contentResolver.takePersistableUriPermission(
    uri,
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
)
```

---

## 💰 Unity Ads Monetization Configuration

Configured in `UnityAdsManager.kt`:

| Setting | Default Value | Description |
| :--- | :--- | :--- |
| **Game ID** | `5270635` | Unity Dashboard Android Game ID |
| **Banner Placement** | `Banner_Android` | 320x50 Banner View centered at screen bottom |
| **Interstitial Placement** | `Interstitial_Android` | Full-screen Interstitial |
| **Cooldown Period** | `30,000 ms` | Minimum interval between resume interstitial ads |
| **Test Mode Fallback** | `Enabled` | Automatically falls back to test fill when production returns `NO_FILL` |

---

## 📜 Complete App Text & Strings Guide

Below are all user-facing strings and descriptions used in the app (from `res/values/strings.xml`):

### Navigation & Header
- **App Name:** `Status Saver`
- **Full Title:** `Status Saver - Video & Image Downloader`
- **Tabs:** `Images`, `Videos`, `Saved`
- **App Toggle:** `WhatsApp` | `WA Business`

### Actions & Buttons
- **Save:** `Save`
- **Saved to Gallery:** `Saved to Gallery!`
- **Failed to Save:** `Failed to save status`
- **Repost:** `Repost` / `Repost to WhatsApp` / `Repost to WA Business`
- **Share:** `Share`
- **Delete:** `Delete`
- **Grant Permission:** `Grant Permission`
- **Open WhatsApp:** `Open WhatsApp` / `Open WA Business`

### Multi-Select Mode
- **Count:** `%1$d Selected`
- **Select All:** `Select All`
- **Clear Selection:** `Clear`
- **Batch Save:** `Save (%1$d)`
- **Batch Share:** `Share (%1$d)`

### Empty States & Guidance
- **No Statuses Found:**  
  *Open WhatsApp, view recent friend statuses, and they will automatically appear here!*
- **No Saved Statuses:**  
  *Save statuses to your Gallery to see them here anytime, even after 24 hours.*
- **Permission Required:**  
  *Android 11+ requires folder access to view statuses. Click below and tap "Use this folder" to grant permission.*

### Onboarding Slides
1. **Download & Save Statuses:**  
   *Save photos and videos from your friends' WhatsApp statuses directly into your phone's Gallery in original HD quality.*
2. **Dual WhatsApp Support:**  
   *Seamlessly switch between Normal WhatsApp and WhatsApp Business with a single tap to manage all your media.*
3. **Swipe Preview & Share:**  
   *Swipe left and right to view full-screen media with built-in player. Repost to your status or share with friends in seconds.*

### User Guidelines ("How to Use")
1. Open WhatsApp or WhatsApp Business and view your contacts' statuses.
2. Return to Status Saver. All viewed images and videos will automatically appear in their respective tabs.
3. Tap any status thumbnail to view it full-screen. Swipe left and right to browse all statuses without going back!
4. Tap the Save button to save directly to your phone's Gallery, or tap Repost / Share to post directly to your own status or social apps.
5. Long-press any item to enter multi-select mode to save or share multiple statuses at once.

### In-App Review & Rating
- **Prompt:** *If you find Status Saver helpful, please take a moment to rate us on Google Play! Your feedback helps us improve.*
- **Actions:** `Rate Now`, `Later`, `No Thanks`

### Privacy Policy Summary
- Status Saver operates 100% locally on the user's device. No personal photos, videos, or contact data are harvested, collected, or uploaded to external servers.
- Ads are served using the Unity Ads SDK in accordance with Unity's privacy guidelines.

---

## 🛠 Dependencies (`app/build.gradle.kts`)

```kotlin
// Unity Ads SDK
implementation("com.unity3d.ads:unity-ads:4.19.0")

// App Lifecycle Process (App-Resume Interstitial)
implementation("androidx.lifecycle:lifecycle-process:2.8.7")

// Storage Access Framework
implementation("androidx.documentfile:documentfile:1.0.1")

// Jetpack Media3 ExoPlayer for status video playback
implementation("androidx.media3:media3-exoplayer:1.4.1")
implementation("androidx.media3:media3-ui:1.4.1")

// Glide Image Loader
implementation("com.github.bumptech.glide:glide:4.16.0")

// Material Design 3
implementation("com.google.android.material:material:1.12.0")

// Google Play In-App Review API
implementation("com.google.android.play:review-ktx:2.0.2")
```

---

## 🚀 Building & Testing

To assemble and verify the debug build:
```bash
gradle assembleDebug
```

To run local unit and Robolectric tests:
```bash
gradle :app:testDebugUnitTest
```

---

## 🔑 App Signing & Manual Keystore Guide

### Important Note on AI Studio Signing
In Google AI Studio, the `signingConfigs` block inside `app/build.gradle.kts` is required by the cloud build environment to compile and install apps onto the browser streaming emulator. **Do not remove or alter this block within AI Studio** as it will break emulator deployments and cloud updates.

Once you export your project as a ZIP or push it to GitHub, you can generate an unsigned APK and sign it with your own private key using the instructions below.

---

### Step 1: Generating an Unsigned APK

You can produce an unsigned APK using either of the following approaches on your local computer:

#### Option A: Strip Existing Signature from APK
If you already built or downloaded an APK:
```bash
# Removes all signatures from the APK package
zip -d app-release.apk 'META-INF/*'
```
Renaming it for clarity:
```bash
mv app-release.apk app-release-unsigned.apk
```

#### Option B: Build Unsigned via Local Gradle
In your local exported copy of `app/build.gradle.kts`, comment out the signingConfig line in `buildTypes`:
```kotlin
buildTypes {
    release {
        // signingConfig = signingConfigs.getByName("release")
    }
}
```
Then run:
```bash
gradle :app:assembleRelease
```
The output will be placed in:
`app/build/outputs/apk/release/app-release-unsigned.apk`

---

### Step 2: Create Your Own Private Keystore
Use the Java `keytool` utility (included with any JDK) to create a private signing key:
```bash
keytool -genkeypair -v \
  -keystore my-release-key.jks \
  -alias my-key-alias \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```
> **Security Tip:** Store your `my-release-key.jks` in a secure location and backup your passwords. If you lose this key, you cannot update your app on Google Play!

---

### Step 3: Align the Unsigned APK (`zipalign`)
Before signing, the APK must be 4-byte aligned using Android SDK's `zipalign` tool (found in `android-sdk/build-tools/<version>/`):
```bash
zipalign -v -p 4 app-release-unsigned.apk app-release-aligned.apk
```

---

### Step 4: Sign the APK (`apksigner`)
Sign your aligned APK with your private keystore using `apksigner`:
```bash
apksigner sign \
  --ks my-release-key.jks \
  --ks-key-alias my-key-alias \
  --out app-release-signed.apk \
  app-release-aligned.apk
```
You will be prompted to enter your keystore password.

---

### Step 5: Verify the Signature
Verify that the APK is correctly signed and meets Android signature schemes (v1, v2, v3):
```bash
apksigner verify --verbose app-release-signed.apk
```
Expected output:
```
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
```
Your `app-release-signed.apk` is now ready for Google Play Console or direct distribution!

---

### Alternative: Automated Signing via Environment Variables
If you prefer Gradle to automatically sign your release build using your custom key without editing code:
```bash
export KEYSTORE_PATH="/path/to/my-release-key.jks"
export STORE_PASSWORD="your_keystore_password"
export KEY_PASSWORD="your_key_password"

gradle :app:assembleRelease
```

---

## ⚖️ Legal Disclaimer

*Status Saver is an independent utility application developed to manage and preserve media locally. Status Saver is not affiliated with, authorized, maintained, sponsored, or endorsed by WhatsApp LLC, WhatsApp Business, Meta Platforms, Inc., or any of their affiliates.*
