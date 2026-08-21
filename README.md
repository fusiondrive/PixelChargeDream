# PixelChargeDream

A dedicated, lightweight LSPosed module to unlock the standalone **Pixel 11 "Charge" ScreenSaver (`ChargingDreamService`)** on older Google Pixel devices (e.g. Pixel 10 Pro XL) running Android 15+.

## Features

- **Pixel 11 Charge ScreenSaver Unlocked**: Unlocks the dedicated charging screensaver with large battery percentage, 80%/100% milestone progress bar, and estimated completion time.
- **Process-Level Model Isolation**: Spoofs `Build.MODEL` and `SystemProperties` exclusively inside `com.google.android.apps.dreamliner` and `com.android.settings`, keeping the global system properties 100% native.
- **Anti-Component-Disable Protection**: Intercepts `PackageManager.setComponentEnabledSetting` calls to prevent background receivers from disabling `ChargingDreamService`.
- **Zero System Partition Modifications**: Pure memory-level hook without modifying `/system`, `/vendor`, or `/product`.

## Building

```bash
./build.sh
```

The compiled and signed debug APK will be placed at `build/pixel-charge-dream-debug.apk`.

## Installation

1. Install the built APK:
   ```bash
   adb install -r -d build/pixel-charge-dream-debug.apk
   ```
2. Enable the module in LSPosed Manager.
3. Select scope:
   - `Pixel Stand` (`com.google.android.apps.dreamliner`)
   - `Settings` (`com.android.settings`)
4. Reboot or restart target apps.
5. Go to **Settings > Display & touch > Screen saver** and select **"Charge"**!
