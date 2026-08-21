# Pixel 11 Standalone Charge Screensaver Gating Mechanism Analysis

## Overview

This document details the multi-layered gating architecture implemented by Google to restrict the standalone **Charge Screensaver (`com.google.android.apps.dreamliner/.kitt.dream.fuelgauge.ChargingDreamService`)** exclusively to Pixel 11 series devices, and how each barrier is systematically bypassed.

---

## The 5 Gating Layers

```
+-----------------------------------------------------------------------------------+
| 1. Static Manifest Disable                                                        |
|    AndroidManifest.xml: android:enabled="false" by default                        |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| 2. Cloud-Managed Numeric Phenotype Flags                                           |
|    GMS syncs obfuscated numeric IDs ("45767319", "10", etc.) = false on non-P11   |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| 3. Aggressive Lifecycle Receivers & Workers                                       |
|    - ChargingExperienceReceiver (Power/Charging changes)                          |
|    - LockedBootCompletedReceiver / BootCompletedReceiver (Boot/Unlock)            |
|    - BatteryAndAlignStateChangeManager (Screen Lock / Posture change)             |
|    - PhenotypeBroadcastReceiver / PhenotypeCommitAsyncWorker (Daily sync)        |
|    -> Auto-invokes setComponentEnabledSetting(ChargingDreamService, DISABLED)     |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| 4. Internal IPC Binder Invocations                                                |
|    Calls IPackageManager$Stub$Proxy directly to bypass ApplicationPackageManager  |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
| 5. Settings UI Double-Filtering                                                   |
|    DreamPickerController checks Build.MODEL & upright_charging_dreams_setup       |
+-----------------------------------------------------------------------------------+
```

---

### Layer 1: Static Manifest Gating

In `com.google.android.apps.dreamliner`'s `AndroidManifest.xml`, `ChargingDreamService` is declared with:
```xml
<service
    android:name="com.google.android.apps.dreamliner.kitt.dream.fuelgauge.ChargingDreamService"
    android:permission="android.permission.BIND_DREAM_SERVICE"
    android:enabled="false"
    android:exported="true">
    <intent-filter>
        <action android:name="android.service.dreams.DreamService" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <meta-data
        android:name="android.service.dream"
        android:resource="@xml/charging_dream_metadata" />
</service>
```
Upon clean installation or OS package cache rebuilds, `PackageManagerService` automatically registers this service in `disabledComponents`.

---

### Layer 2: Obfuscated Server-Side Phenotype Flags

Google does not use human-readable flag names (such as `enable_charging_dream`). Instead, flags are represented as numeric IDs stored in `/data/data/com.google.android.apps.dreamliner/shared_prefs/phenotypeFlags.xml`:
- Flags: `"1"`, `"7"`, `"10"`, `"11"`, `"12"`, `"13"`, `"14"`, `"15"`, `"16"`, `"17"`, `"18"`, `"19"`, `"45358970"`, `"45350195"`, `"45767319"`, `"45777967"`
- When Google Play Services connects to Google servers, it transmits `ro.product.model`. Devices reporting non-Pixel 11 models receive `false` for all associated feature IDs.

---

### Layer 3: Lifecycle Broadcast Receivers & Background Workers

`com.google.android.apps.dreamliner` registers broadcast receivers and WorkManager jobs covering every phone lifecycle event:

| Component | Trigger Event | Action upon Non-P11 Detection |
| :--- | :--- | :--- |
| `ChargingExperienceReceiver` | Charger connected / disconnected, battery level updates | `setComponentEnabledSetting(ChargingDreamService, DISABLED)` |
| `LockedBootCompletedReceiver` | Device boot / credentials unlocked | Disables `ChargingDreamService` |
| `BatteryAndAlignStateChangeManager` | Screen lock (`SCREEN_OFF`), posture/dock changes | Disables `ChargingDreamService` |
| `PhenotypeBroadcastReceiver` | Periodic server flag synchronization | Re-reads flags and disables `ChargingDreamService` |

---

### Layer 4: Direct IPC Binder Invocation

To bypass standard framework abstractions, the disabling routines call `AppGlobals.getPackageManager().setComponentEnabledSetting(...)` and `ActivityThread.getPackageManager().setComponentEnabledSetting(...)` directly via `android.content.pm.IPackageManager$Stub$Proxy`, attempting to circumvent `ApplicationPackageManager` method hooks.

---

### Layer 5: Settings Controller Filtering

`com.android.settings.dream.DreamPickerController` and `DreamBackend` independently evaluate `Build.MODEL` and system feature flags (`upright_charging_dreams_setup`) during UI construction, filtering out `ChargingDreamService` from the preference list even if the service is enabled in the package manager.

---

## Bypass Strategy in PixelChargeDream

1. **Process-Level Model Spoofing**:
   - Spoofs `Build.MODEL = "Pixel 11 Pro XL"` and `SystemProperties.get("ro.product.model")` inside `com.google.android.apps.dreamliner` and `com.android.settings`.
2. **Unconditional Phenotype Override**:
   - Hooks `SharedPreferencesImpl.getBoolean` in `Dreamliner` to return `true` for all numeric flag queries.
3. **IPC & PackageManager Interception**:
   - Hooks `ApplicationPackageManager.setComponentEnabledSetting` and `getComponentEnabledSetting` to lock the state to `COMPONENT_ENABLED_STATE_ENABLED`.
4. **Settings UI Injection**:
   - Hooks `DreamPickerController` to ensure the Charge card is permanently rendered in Settings.
