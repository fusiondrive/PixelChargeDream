package com.pixelcharge.hook;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public final class PixelChargeDreamHook implements IXposedHookLoadPackage {
    private static final String TAG = "PixelChargeDreamHook";

    private static final String PKG_SYSTEM_SERVER = "android";
    private static final String PKG_DREAMLINER = "com.google.android.apps.dreamliner";
    private static final String PKG_SETTINGS = "com.android.settings";
    private static final String PKG_SYSTEMUI = "com.android.systemui";

    private static final String CHARGING_DREAM_CLASS = "com.google.android.apps.dreamliner.kitt.dream.fuelgauge.ChargingDreamService";

    private static final Set<String> TARGET_PACKAGES = new HashSet<>(Arrays.asList(
            PKG_SYSTEM_SERVER,
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
            if (PKG_SYSTEM_SERVER.equals(lpparam.packageName)) {
                hookSystemServer(lpparam);
            } else if (PKG_DREAMLINER.equals(lpparam.packageName)) {
                spoofPixel11Identity(lpparam);
                preventComponentDisabling(lpparam);
                hookDreamliner(lpparam);
            } else if (PKG_SETTINGS.equals(lpparam.packageName)) {
                spoofPixel11Identity(lpparam);
                hookSettings(lpparam);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in PixelChargeDreamHook for " + lpparam.packageName, t);
        }
    }

    private void hookSystemServer(LoadPackageParam lpparam) {
        Log.i(TAG, "Hooking system_server PackageManagerService and DreamManagerService...");

        // 1. Hook PackageManagerService to prevent disabling ChargingDreamService
        try {
            Class<?> pmsClass = XposedHelpers.findClassIfExists("com.android.server.pm.PackageManagerService", lpparam.classLoader);
            if (pmsClass != null) {
                // Intercept setComponentEnabledSetting in system_server
                for (Method m : pmsClass.getDeclaredMethods()) {
                    if ("setComponentEnabledSetting".equals(m.getName())) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                for (Object arg : param.args) {
                                    if (arg instanceof ComponentName) {
                                        ComponentName cn = (ComponentName) arg;
                                        if (cn.getClassName().contains("ChargingDreamService")) {
                                            // Force enabled
                                            for (int i = 0; i < param.args.length; i++) {
                                                if (param.args[i] instanceof Integer && (Integer) param.args[i] != 0 && (Integer) param.args[i] != 1) {
                                                    param.args[i] = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        });
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "PMS hook skipped: " + t.getMessage());
        }

        // 2. Hook DreamManagerService to ensure ChargingDreamService is always in the available dream components list
        try {
            Class<?> dmsClass = XposedHelpers.findClassIfExists("com.android.server.dreams.DreamManagerService", lpparam.classLoader);
            if (dmsClass != null) {
                for (Method m : dmsClass.getDeclaredMethods()) {
                    if ("getDreamComponentsForUser".equals(m.getName()) || "getDreamComponents".equals(m.getName())) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                ComponentName[] components = (ComponentName[]) param.getResult();
                                if (components != null) {
                                    boolean hasCharging = false;
                                    for (ComponentName cn : components) {
                                        if (cn != null && cn.getClassName().contains("ChargingDreamService")) {
                                            hasCharging = true;
                                            break;
                                        }
                                    }
                                    if (!hasCharging) {
                                        ComponentName[] newComponents = Arrays.copyOf(components, components.length + 1);
                                        newComponents[components.length] = new ComponentName(PKG_DREAMLINER, CHARGING_DREAM_CLASS);
                                        param.setResult(newComponents);
                                    }
                                }
                            }
                        });
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "DMS hook skipped: " + t.getMessage());
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
