package com.pixelcharge.hook;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public final class PixelChargeDreamHook implements IXposedHookLoadPackage {
    private static final String TAG = "PixelChargeDreamHook";

    private static final String PKG_DREAMLINER = "com.google.android.apps.dreamliner";
    private static final String PKG_SETTINGS = "com.android.settings";
    private static final String PKG_SYSTEMUI = "com.android.systemui";

    private static final Set<String> TARGET_PACKAGES = new HashSet<>(Arrays.asList(
            PKG_DREAMLINER,
            PKG_SETTINGS,
            PKG_SYSTEMUI
    ));

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (lpparam.packageName == null || !TARGET_PACKAGES.contains(lpparam.packageName)) {
            return;
        }

        Log.i(TAG, "PixelChargeDreamHook attached to: " + lpparam.packageName);

        try {
            // 1. Process-level Model Spoofing to Pixel 11 Pro XL
            spoofPixel11Identity(lpparam);

            // 2. Prevent Dreamliner from disabling ChargingDreamService
            preventComponentDisabling(lpparam);

            // 3. App specific hooks
            if (PKG_DREAMLINER.equals(lpparam.packageName)) {
                hookDreamliner(lpparam);
            } else if (PKG_SETTINGS.equals(lpparam.packageName)) {
                hookSettings(lpparam);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in PixelChargeDreamHook for " + lpparam.packageName, t);
        }
    }

    private void spoofPixel11Identity(LoadPackageParam lpparam) {
        try {
            setFinalStaticField(Build.class, "MODEL", "Pixel 11 Pro XL");
            setFinalStaticField(Build.class, "PRODUCT", "kodiak");
            setFinalStaticField(Build.class, "DEVICE", "kodiak");
            setFinalStaticField(Build.class, "BOARD", "kodiak");
            setFinalStaticField(Build.class, "HARDWARE", "malibu");
            setFinalStaticField(Build.class, "SOC_MODEL", "Tensor G6");
            setFinalStaticField(Build.class, "SOC_MANUFACTURER", "Google");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to spoof Build fields: " + t.getMessage());
        }

        try {
            Class<?> sysPropClass = XposedHelpers.findClassIfExists("android.os.SystemProperties", lpparam.classLoader);
            if (sysPropClass != null) {
                XC_MethodHook propHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        if (key == null) return;
                        if (key.endsWith(".model") || "ro.product.model".equals(key) || "ro.product.product.model".equals(key)) {
                            param.setResult("Pixel 11 Pro XL");
                        } else if (key.endsWith(".device") || "ro.product.device".equals(key) || key.endsWith(".name") || "ro.product.name".equals(key)) {
                            param.setResult("kodiak");
                        } else if ("ro.soc.model".equals(key)) {
                            param.setResult("Tensor G6");
                        }
                    }
                };
                XposedHelpers.findAndHookMethod(sysPropClass, "get", String.class, propHook);
                XposedHelpers.findAndHookMethod(sysPropClass, "get", String.class, String.class, propHook);
            }
        } catch (Throwable t) {
            Log.w(TAG, "SystemProperties hook skipped: " + t.getMessage());
        }
    }

    private void preventComponentDisabling(LoadPackageParam lpparam) {
        try {
            Class<?> pmClass = XposedHelpers.findClassIfExists("android.app.ApplicationPackageManager", lpparam.classLoader);
            if (pmClass != null) {
                // Prevent background receivers from disabling ChargingDreamService
                XposedHelpers.findAndHookMethod(pmClass, "setComponentEnabledSetting",
                        ComponentName.class, int.class, int.class, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                ComponentName cn = (ComponentName) param.args[0];
                                if (cn != null && cn.getClassName().contains("ChargingDreamService")) {
                                    param.args[1] = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                                }
                            }
                        });

                // Always report enabled state for ChargingDreamService
                XposedHelpers.findAndHookMethod(pmClass, "getComponentEnabledSetting",
                        ComponentName.class, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                ComponentName cn = (ComponentName) param.args[0];
                                if (cn != null && cn.getClassName().contains("ChargingDreamService")) {
                                    param.setResult(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
                                }
                            }
                        });
            }
        } catch (Throwable t) {
            Log.w(TAG, "PackageManager hook skipped: " + t.getMessage());
        }
    }

    private void hookDreamliner(LoadPackageParam lpparam) {
        try {
            Class<?> spClass = XposedHelpers.findClassIfExists("android.app.SharedPreferencesImpl", lpparam.classLoader);
            if (spClass != null) {
                XposedHelpers.findAndHookMethod(spClass, "getBoolean", String.class, boolean.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        if (key == null) return;
                        String lowerKey = key.toLowerCase();
                        if (lowerKey.contains("charge")
                                || lowerKey.contains("charging")
                                || lowerKey.contains("fuelgauge")
                                || lowerKey.contains("kitt")
                                || lowerKey.contains("upright")
                                || lowerKey.contains("milestone")) {
                            param.setResult(true);
                        }
                    }
                });
            }
        } catch (Throwable t) {
            Log.w(TAG, "Dreamliner SharedPreferences hook skipped: " + t.getMessage());
        }

        try {
            Class<?> phenotypeFlagClass = XposedHelpers.findClassIfExists("com.google.android.libraries.phenotype.client.PhenotypeFlag", lpparam.classLoader);
            if (phenotypeFlagClass != null) {
                for (Method m : phenotypeFlagClass.getDeclaredMethods()) {
                    if ("get".equals(m.getName()) && m.getParameterTypes().length == 0) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                Object result = param.getResult();
                                if (result instanceof Boolean) {
                                    param.setResult(true);
                                }
                            }
                        });
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Dreamliner PhenotypeFlag hook skipped: " + t.getMessage());
        }
    }

    private void hookSettings(LoadPackageParam lpparam) {
        try {
            Class<?> dreamPickerClass = XposedHelpers.findClassIfExists("com.android.settings.dream.DreamPickerController", lpparam.classLoader);
            if (dreamPickerClass != null) {
                for (Method m : dreamPickerClass.getDeclaredMethods()) {
                    if (m.getReturnType() == boolean.class && m.getParameterTypes().length == 0) {
                        String name = m.getName().toLowerCase();
                        if (name.contains("available") || name.contains("supported") || name.contains("enabled") || name.contains("visible")) {
                            XposedBridge.hookMethod(m, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    param.setResult(true);
                                }
                            });
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Settings hook skipped: " + t.getMessage());
        }
    }

    private static void setFinalStaticField(Class<?> clazz, String fieldName, Object value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);

            Field modifiersField = null;
            try {
                modifiersField = Field.class.getDeclaredField("accessFlags");
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            } catch (Throwable ignored) {
            }

            field.set(null, value);
        } catch (Throwable t) {
            Log.w(TAG, "Could not set static field " + fieldName + ": " + t.getMessage());
        }
    }
}
