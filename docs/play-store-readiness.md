# Google Play Store Technical Readiness Guide (`v2.1.0`)

This document describes the technical compliance architecture, Data Safety declarations, Android 16 (API 36) target configuration, and secure release signing/AAB build workflow for **Rescue Cam: Sensor Recovery** (`v2.1.0`, `versionCode = 7`).

---

## 1. Identity & Trademark Compliance

| Attribute | Value | Notes |
| --- | --- | --- |
| **Play Store Title** | `Rescue Cam: Sensor Recovery` | 27 characters (`<= 30` char limit, trademark-free) |
| **Short Launcher Label** | `Rescue Cam` | Defined in `CameraConfig.APP_SHORT_NAME` & `AndroidManifest.xml` |
| **Public `applicationId`** | `io.github.benedetto597.rescuecam` | Reverse-DNS lowercase, no protected brand segments |
| **Internal Java `namespace`** | `com.s23plus.camera` | Preserved for `.json` diagnostic profile & internal action backward compatibility |
| **Version** | `2.1.0` (`versionCode = 7`) | Synchronized across `CameraConfig`, `app/build.gradle.kts`, and build scripts |
| **Adaptive Launcher Icon** | `res/mipmap-anydpi-v26/ic_launcher*.xml` | Vector lens + medical rescue cross (`ic_launcher_foreground.xml` / `ic_launcher_background.xml`), trademark-free |
| **MediaStore Paths** | `Pictures/RescueCam` · `Movies/RescueCam` | Neutral output directories without legacy device/brand strings |

### Independent Utility Disclaimer
`CameraConfig.TRADEMARK_DISCLAIMER` (rendered in-app via the Privacy & Legal card/dialog and mirrored in `docs/privacy-policy.html`) explicitly states:
- The application is an independent utility tool not affiliated with, authorized, endorsed, or connected with **Samsung Electronics Co., Ltd.**
- Terms such as `Samsung`, `Galaxy`, and `One UI` are used solely for descriptive technical hardware-compatibility purposes.

All identity, title, `applicationId`, disclaimer, permission, and MediaStore path invariants are enforced by automated pure-Java policy checks in `PlayStoreCompliancePolicy` and `PlayStoreCompliancePolicyTest`.

---

## 2. Architecture Decision Record (ADR): Zero-Accessibility Architecture

### Decision
Remove `RearCaptureService` (`AccessibilityService`), `accessibility_service_config.xml`, and `BIND_ACCESSIBILITY_SERVICE` completely, replacing screen-capture/overlay automation with direct native camera modes and an integrated universal `Camera2` viewfinder.

### Context
Google Play's Accessibility API policy restricts `AccessibilityService` declarations strictly to assistive tools designed for users with disabilities. Declaring `BIND_ACCESSIBILITY_SERVICE` for camera HUD automation or screen capture triggers rejection during Google Play review.

### Replacement Architecture
Instead of accessibility overlays, rear camera isolation and local capture operate through four clean, policy-compliant paths:
1. **Pro Photo (`ShootingMode 3`)**: Launches Samsung Camera directly into `CameraConfig.MODE_PRO_PHOTO` (`com.sec.android.app.camera.shootingmode.pro`), opening standalone sensors (`CameraId.BACK` device `56` / `BACK_TELE` device `52`) and bypassing the multi-camera seamless zoom group (`device 20`).
2. **Pro Video (`ShootingMode 36`)**: Launches Samsung Camera directly into `CameraConfig.MODE_PRO_VIDEO` (`com.sec.android.app.camera.shootingmode.provideo`) on standalone sensors (`56` / `52`) with stabilization and audio support.
3. **Direct 1x QR Scanner (`ShootingMode 40`)**: Invokes the exported intent `com.sec.android.app.camera.action.SCAN_QR_CODE` (`REAR_QR_DIRECT`), isolating the 1x primary sensor (`device 56`) directly without any `AccessibilityService` or screen overlays.
4. **Universal `Camera2` Viewfinder**: In-app native `Camera2` preview and capture pipeline (`MainActivity` + `MediaStoreSaver`) supporting functional front and rear public camera IDs, JPEG (`Pictures/RescueCam`) and MP4 (`Movies/RescueCam`) transactional capture, zoom (`CONTROL_ZOOM_RATIO` / `SCALER_CROP_REGION`), EV compensation, flash/torch, timer, grid, and variable aperture where supported.

### Consequences
- Zero `AccessibilityService` or `SYSTEM_ALERT_WINDOW` declarations in `AndroidManifest.xml`.
- Automated manifest and badging checks verify that `RearCaptureService` and `BIND_ACCESSIBILITY_SERVICE` remain absent.

---

## 3. Google Play Data Safety Form Matrix

| Data Safety Question | Answer | Technical Justification |
| --- | --- | --- |
| **Does your app collect or share any of the required user data types?** | **No — No data collected, No data shared** | `android.permission.INTERNET` and `ACCESS_NETWORK_STATE` are not declared. Zero analytics, zero crash telemetry, zero third-party network SDKs. |
| **Is all user data encrypted in transit?** | **N/A (100% offline)** | No network transmission exists. |
| **Do you provide a way for users to request data deletion?** | **Local-only control** | Users clear `SharedPreferences` (`camera_rescue_prefs`) via Android App Settings or uninstalling; local media/profiles can be deleted via Gallery/Files. |
| **Privacy Policy URL** | `https://benedetto597.github.io/camera-rescue/privacy-policy.html` | Self-contained static HTML5 bilingual policy (`docs/privacy-policy.html`). |

### Permission & Storage Audit

| Component | Declaration / Mechanism | Purpose & Scope |
| --- | --- | --- |
| `android.permission.CAMERA` | Manifest + Runtime request | Local `Camera2` viewfinder and on-device sensor canary probing. |
| `android.permission.RECORD_AUDIO` | Manifest + Optional runtime request | Requested only when initiating MP4 video recording; if denied, recording cleanly degrades to video-only MP4. |
| `android.permission.BIND_QUICK_SETTINGS_TILE` | `<service>` permission on `CameraRescueTileService` | Optional Quick Settings tile shortcut to launch the app. |
| `INTERNET` / `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE` / `READ_MEDIA_*` | **Not declared** | Media writes use scoped `MediaStore` (`Pictures/RescueCam`, `Movies/RescueCam`); profile import/export uses Storage Access Framework (SAF) `.json` picker. |
| `android:allowBackup="false"` | `<application>` attribute | Prevents unencrypted automatic cloud backup of `camera_rescue_prefs`. |

---

## 4. API 36 (Android 16) Technical Compliance

Configured in `app/build.gradle.kts` and `MainActivity.java`:
- **SDK Levels**: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 30`.
- **Edge-to-Edge (`WindowInsets`)**: `MainActivity` registers `setOnApplyWindowInsetsListener` consuming `WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()` so content avoids status bar, navigation bar, and display cutout overlap on Android 15/16 edge-to-edge enforcement.
- **Adaptive Resizing & Large Screens**: `MainActivity` declares `android:resizeableActivity="true"` and `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden"` with **no** `android:screenOrientation="portrait"` lock, satisfying Android 16 adaptive layout expectations on foldables/tablets.
- **R8 Shrinking & Resource Optimization**: `release` build type enables `isMinifyEnabled = true` and `isShrinkResources = true` with `app/proguard-rules.pro` keeping `MainActivity`, `CameraRescueTileService`, and enum names serialized by `DiagnosticProfileCodec` / `RescuePreferences`.
- **App Bundle Configuration**: `bundle` block enables `language`, `density`, and `abi` splits.

---

## 5. Secure Release Signing & AAB Build Guide

Never store Upload Keystore files, signing properties, or credentials in Git (`.gitignore` excludes `*.jks`, `*.keystore`, `*.p12`, `keystore.properties`, `signing.properties`, and `*.aab`).

### Step 1: Generate an External Upload Keystore
Create the Upload Keystore outside the repository directory using JDK 17 `keytool`:

```powershell
keytool -genkeypair -v `
  -keystore C:\secure-keys\rescue-cam-upload.jks `
  -storetype PKCS12 `
  -keyalg RSA -keysize 2048 -validity 10000 `
  -alias rescue-cam-upload
```

Store a secure offline backup of this Upload Keystore. Google Play App Signing will use it to verify every `.aab` upload.

### Step 2A: Build & Verify Signed AAB Locally (`scripts/build-aab-local.ps1`)
Configure signing via **either**:
- An ignored `keystore.properties` file in the project root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`), **or**
- Process environment variables: `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.

Then run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-aab-local.ps1
```

The script automatically runs:
1. `git ls-files` audit confirming zero tracked keystores, properties, or `.aab` artifacts.
2. All 11 pure-Java unit test suites (`scripts/test-local.ps1`).
3. `gradle bundleRelease --no-daemon --stacktrace` producing `app/build/outputs/bundle/release/app-release.aab`.
4. `jarsigner -verify -verbose -certs` confirming `jar verified.`.

### Step 2B: Build & Verify Signed AAB via GitHub Actions (`.github/workflows/build-play-aab.yml`)
1. Configure repository secrets in GitHub Settings (`Secrets and variables -> Actions`):
   - `ANDROID_KEYSTORE_BASE64` (base64-encoded upload `.jks`/`.keystore`)
   - `ANDROID_KEYSTORE_PASSWORD`
   - `ANDROID_KEY_ALIAS`
   - `ANDROID_KEY_PASSWORD`
2. Trigger **Build Play Store AAB (Manual Dispatch Only)** (`workflow_dispatch`) from the Actions tab.
3. The workflow runs all 11 unit test suites, decodes the keystore exclusively to `$RUNNER_TEMP`, runs `gradle bundleRelease`, verifies `app-release.aab` with `jarsigner -verify`, uploads `app-release.aab` + `mapping.txt` as workflow artifacts, and removes temporary keystore files in `if: always()`.

### Step 3: Manual Artifact Verification Commands
Verify a signed AAB:
```powershell
jarsigner -verify -verbose -certs app\build\outputs\bundle\release\app-release.aab
```

Verify a signed APK built by `scripts/build-local.ps1`:
```powershell
java -jar <sdk>\build-tools\36.0.0\lib\apksigner.jar verify --print-certs dist\camera-rescue-v2.1.0.apk
<sdk>\build-tools\36.0.0\aapt2.exe dump badging dist\camera-rescue-v2.1.0.apk
```
Confirm `aapt2 dump badging` prints:
- `name='io.github.benedetto597.rescuecam'`
- `versionCode='7'`
- `versionName='2.1.0'`
- `targetSdkVersion:'36'`
- `application-label:'Rescue Cam: Sensor Recovery'`
- Zero occurrences of `android.permission.INTERNET` or `BIND_ACCESSIBILITY_SERVICE`.

---

## 6. Pre-Submission Technical Checklist

- [ ] **GitHub Pages Privacy Policy**: Enable GitHub Pages serving `/docs` on `main` and verify `https://benedetto597.github.io/camera-rescue/privacy-policy.html` loads cleanly over HTTPS (HTTP 200).
- [ ] **Pure-Java Compliance & Unit Suites**: Run `powershell -ExecutionPolicy Bypass -File .\scripts\test-local.ps1` and verify all 11 suites pass (`PlayStoreCompliancePolicyTest` `11/11`).
- [ ] **Signed Release AAB Verified**: Generate `app/build/outputs/bundle/release/app-release.aab` via `scripts/build-aab-local.ps1` or `.github/workflows/build-play-aab.yml` and confirm `jarsigner -verify` outputs `jar verified.`.
- [ ] **R8 Mapping Symbolication**: Upload `app/build/outputs/mapping/release/mapping.txt` alongside the `.aab` in Play Console (or attach from the workflow artifact).
- [ ] **Play Console App Content Declarations**:
  - Privacy Policy URL set to `https://benedetto597.github.io/camera-rescue/privacy-policy.html`.
  - Data Safety set to **No data collected** / **No data shared**.
  - App Access declared as fully functional without login credentials.
- [ ] **Closed Testing Track Readiness**: Upload the signed `.aab` to the Play Console **Closed testing** track, review the Pre-launch Report across Android 11–16 devices, and confirm no policy warnings for Accessibility API, storage permissions, or API 36 edge-to-edge layout.
