# Implementation Plan - Unsigned APK Generation & Manual Signing Guide

## Overview
This plan outlines the steps to document and configure how to generate an unsigned APK/AAB and manually sign it later using standard Android SDK tools (`keytool`, `zipalign`, and `apksigner`), and adding a dedicated step-by-step guide to `README.md`.

---

## 1. Context & Constraints in AI Studio
- **Platform Constraint:** The `signingConfigs` block inside `app/build.gradle.kts` is required by the AI Studio container to build, install, and update the app inside the streaming emulator. It must not be removed from the repository.
- **Goal:** Provide clear mechanisms and step-by-step instructions so the user can:
  1. Produce an unsigned APK or remove existing signatures after exporting the project.
  2. Generate their own personal keystore using `keytool`.
  3. Align and sign the APK using `zipalign` and `apksigner` for Google Play Store or manual distribution.

---

## 2. Proposed Changes

### A. Add Comprehensive Signing Section to `README.md`
Add a dedicated section titled `## 🔑 App Signing & Manual Keystore Guide` covering:
1. **Understanding the Signing Setup:**
   - Explanation of `signingConfigs` in `app/build.gradle.kts` and why it is preserved for AI Studio builds.
   - How release signing uses environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) or defaults to `${rootDir}/my-upload-key.jks`.
2. **Method 1: Generating an Unsigned APK via Gradle (Local Build)**
   - Creating an unsigned build or removing signing configuration when exporting locally:
     ```bash
     gradle :app:assembleRelease
     ```
   - How to strip debug signatures from any APK to make it completely unsigned:
     ```bash
     zip -d app-release.apk 'META-INF/*'
     ```
3. **Method 2: Creating Your Own Private Keystore**
   - Generating a 2048-bit RSA key with 25-year validity using Java `keytool`:
     ```bash
     keytool -genkeypair -v -keystore my-release-key.jks -alias my-key-alias -keyalg RSA -keysize 2048 -validity 10000
     ```
4. **Method 3: Aligning & Signing with `zipalign` and `apksigner`**
   - Aligning the APK (required for 4-byte boundary alignment on Android):
     ```bash
     zipalign -v -p 4 app-release-unsigned.apk app-release-aligned.apk
     ```
   - Signing with `apksigner` (supports v2, v3, and v4 signatures):
     ```bash
     apksigner sign --ks my-release-key.jks --ks-key-alias my-key-alias --out app-release-signed.apk app-release-aligned.apk
     ```
   - Verifying the signature:
     ```bash
     apksigner verify --verbose app-release-signed.apk
     ```
5. **Method 4: Using Environment Variables in CI/CD or Local Terminals**
   - Setting `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` to automatically sign during `gradle assembleRelease`.

---

## 3. Verification & Safety
- Ensure `app/build.gradle.kts` remains intact and compliant with AI Studio guidelines.
- Verify that `compile_applet` and `gradle :app:testDebugUnitTest` continue to pass without errors.
