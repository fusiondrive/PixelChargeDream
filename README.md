# PixelChargeDream

An LSPosed module to unlock the Pixel 11 standalone Charge Screensaver (`ChargingDreamService`) on older Pixel devices running Android 15+.

## Overview

Google restricts the new standalone charging screensaver (`com.google.android.apps.dreamliner/.kitt.dream.fuelgauge.ChargingDreamService`) to Pixel 11 devices via runtime model checks and background receiver state enforcement.

This module resolves these restrictions by:
1. Spoofing `Build.MODEL` (`Pixel 11 Pro XL`) and `ro.product.model` exclusively inside `com.google.android.apps.dreamliner` and `com.android.settings`.
2. Intercepting `PackageManager.setComponentEnabledSetting` calls to prevent `ChargingExperienceReceiver` from disabling `ChargingDreamService`.
3. Intercepting `DreamPickerController` in `Settings` to ensure the "Charge" card is rendered in the Screensaver picker.

## Scope

- `com.google.android.apps.dreamliner`
- `com.android.settings`

## Build

```bash
./build.sh
```

Output: `build/pixel-charge-dream-debug.apk`

## Usage

1. Build and install the APK.
2. Enable in LSPosed and assign scope to `Pixel Stand` (`com.google.android.apps.dreamliner`) and `Settings` (`com.android.settings`).
3. Open **Settings > Display & touch > Screen saver** and select **Charge**.
