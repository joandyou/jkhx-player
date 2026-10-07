# Building & Deploying JKHX Player

This document outlines how to build, test, and deploy JKHX Player from source on Linux / Android Studio.

---

## 1. Prerequisites

- **Java Development Kit (JDK)**: OpenJDK 17 or 21.
- **Android SDK**: API Level 34 (Android 14) or API Level 35.
- **Android NDK**: Version 26.1.10909125 or newer (CMake 3.22.1+).
- **Node.js**: v18+ (only needed for running plugin test suites via `test-runner`).
- **Android Studio**: Android Studio Hedgehog (2023.1.1) or newer recommended.

---

## 2. Environment Setup

Ensure environment variables are set in your shell (`~/.bashrc` or `~/.zshrc`):

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.1.10909125
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin
```

---

## 3. Project Structure

```
jkhx-player/
├── android/              # Native Android project
│   ├── app/              # Main Compose Application
│   ├── core/             # Core modules (model, network, database, player)
│   ├── plugin/           # Plugin runtime (QuickJS-NG C99, JNI, manager)
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── plugins/              # JavaScript plugins & manifest repository
├── test-runner/          # Automated Node.js verification test harness
└── docs/                 # Complete architecture and API documentation
```

---

## 4. Building with Gradle

Navigate to the `android/` directory:

```bash
cd android
```

### 4.1. Clean & Assemble Debug APK
```bash
./gradlew clean assembleDebug
```
The output APK will be generated at:
```
android/app/build/outputs/apk/debug/app-debug.apk
```

### 4.2. Run Unit Tests
```bash
./gradlew test
```

---

## 5. QuickJS-NG Native Library Compilation

The C99 QuickJS-NG engine is located at:
`android/plugin/runtime/src/main/cpp/quickjs/`

It is automatically compiled into `libquickjs-ng.so` by CMake during Gradle execution. Supported ABIs configured in `build.gradle.kts`:
- `arm64-v8a` (Modern Android devices)
- `armeabi-v7a` (Older 32-bit devices)
- `x86_64` (Android Emulators on x86 PCs)

---

## 6. Testing Plugins with the Node Test Runner

Before packaging plugins into APK assets, you can run automated verification on any host with Node.js:

```bash
# Test all plugins:
for p in plugins/*/; do
  [ -f "$p/manifest.json" ] && node test-runner/test-plugin.js "$p"
done

# Test an individual plugin:
node test-runner/test-plugin.js plugins/kkphim
```

---

## 7. Syncing to Local Mint Machine

If you are developing across a remote server and a local Linux Mint workstation, sync the repository to your Mint workspace:

```bash
rsync -avz --exclude '.gradle' --exclude 'build' --exclude '.idea' \
  /home/jtmy/jkhx-player/ /media/jtmy/JDataL/J-Bot-Code/mint-project/jkhx-player/
```

Or clone directly via Git:
```bash
mkdir -p /media/jtmy/JDataL/J-Bot-Code/mint-project
cd /media/jtmy/JDataL/J-Bot-Code/mint-project
git clone /home/jtmy/jkhx-player jkhx-player
```

