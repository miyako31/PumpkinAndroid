# 🎃 Pumpkin Android

Run a [Pumpkin](https://github.com/Pumpkin-MC/Pumpkin) Minecraft server natively on Android.

[![Build](https://github.com/<YOUR_USER>/<YOUR_REPO>/actions/workflows/build.yml/badge.svg)](../../actions/workflows/build.yml)

---

## How it works

```
APK
└── assets/pumpkin/
      ├── arm64-v8a/pumpkin   ← Rust binary (physical devices)
      └── x86_64/pumpkin      ← Rust binary (emulator)
```

On first launch `BinaryInstaller` copies the binary from assets into the app's
private storage (`/data/data/com.pumpkinmc.android/files/pumpkin/`), then
`PumpkinService` starts it as a foreground service via `ProcessBuilder`.

---

## Build with GitHub Actions (recommended)

No local toolchain required – everything runs on GitHub's free runners.

### Quick start

1. Fork or push this repository to GitHub.
2. Open the **Actions** tab → **Build Pumpkin Android APK** → **Run workflow**.
3. Wait ~15 minutes for the build to finish.
4. Download the APK from the **Artifacts** section of the completed run.

### Workflows

| File | Trigger | Output |
|------|---------|--------|
| `build.yml` | push / manual / daily | Debug + unsigned Release APK (Artifacts) |
| `release.yml` | tag push `v*.*.*` / manual | Signed APK published to GitHub Releases |

### Build time

| Step | First run | Cached |
|------|-----------|--------|
| Cross-compile arm64 | ~10 min | ~3 min |
| Cross-compile x86_64 | ~10 min | ~3 min |
| Android APK | ~3 min | ~1 min |
| **Total** | **~15 min** | **~5 min** |

arm64 and x86_64 compile in parallel, so wall-clock time is roughly half the
per-job time.

### Signed releases

To produce a signed APK that can be distributed or uploaded to the Play Store:

**Step 1 – Generate a keystore (one time)**

```bash
chmod +x scripts/generate_keystore.sh
./scripts/generate_keystore.sh
```

The script prints the exact values to register as Secrets.

**Step 2 – Add Secrets to the repository**

`Settings → Secrets and variables → Actions → New repository secret`

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | base64-encoded `.jks` file |
| `KEY_ALIAS` | key alias (default: `pumpkin`) |
| `KEY_PASSWORD` | key password |
| `STORE_PASSWORD` | keystore password |

**Step 3 – Push a tag**

```bash
git tag v1.0.0
git push origin v1.0.0
```

`release.yml` runs automatically and publishes a signed APK to GitHub Releases.

---

## Build locally

### Requirements

| Tool | Version |
|------|---------|
| Rust (rustup) | latest stable |
| Android NDK | r26 or newer |
| Android Studio | Hedgehog or newer |
| JDK | 17+ |

### Step 1 – Cross-compile Pumpkin

```bash
export ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/26.3.11579264
chmod +x cross_compile.sh
./cross_compile.sh
```

### Step 2 – Build the APK

```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

---

## Server data location

```
/data/data/com.pumpkinmc.android/files/pumpkin/
├── bin/pumpkin          ← executable binary
├── configuration.toml   ← Pumpkin config
└── world/               ← world data
```

---

## License

Android wrapper code: MIT  
Pumpkin: GPL-3.0
