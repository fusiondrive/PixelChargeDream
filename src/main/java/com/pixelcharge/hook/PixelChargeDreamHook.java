package com.pixelcharge.hook;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public final class PixelChargeDreamHook implements IXposedHookLoadPackage {
    private static final String TAG = "PixelChargeDreamHook";

    private static final String PKG_DREAMLINER = "com.google.android.apps.dreamliner";
    private static final String PKG_SETTINGS = "com.android.settings";
    private static final String PKG_SYSTEMUI = "com.android.systemui";

    private static final String CHARGING_DREAM_PKG = "com.google.android.apps.dreamliner";
    private static final String CHARGING_DREAM_CLASS = "com.google.android.apps.dreamliner.kitt.dream.fuelgauge.ChargingDreamService";
    private static final ComponentName CHARGING_DREAM_COMPONENT = new ComponentName(CHARGING_DREAM_PKG, CHARGING_DREAM_CLASS);

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

        Log.i(TAG, "PixelChargeDreamHook loaded for package: " + lpparam.packageName);

        try {
            // 1. Process-level Model Spoofing
            spoofPixel11Identity(lpparam);

            // 2. Target specific hooks
            if (PKG_DREAMLINER.equals(lpparam.packageName)) {
                hookDreamlinerProcess(lpparam);
            } else if (PKG_SETTINGS.equals(lpparam.packageName)) {
                hookSettingsProcess(lpparam);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in handleLoadPackage for " + lpparam.packageName, t);
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

    private void hookDreamlinerProcess(LoadPackageParam lpparam) {
        // Block all rogue receivers & workers from disabling ChargingDreamService
        neutralizeClassMethod(lpparam.classLoader, "com.google.android.apps.dreamliner.settings.kitt.ChargingExperienceReceiver", "onReceive");
        neutralizeClassMethod(lpparam.classLoader, "com.google.android.apps.dreamliner.dock.receiver.BootCompletedReceiver", "onReceive");
        neutralizeClassMethod(lpparam.classLoader, "com.google.android.apps.dreamliner.experiment.PhenotypeBroadcastReceiver", "onReceive");

        // Hook SharedPreferences in Dreamliner
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

        // Hook PhenotypeFlag in Dreamliner
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

        // Hook PackageManager calls inside Dreamliner
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
            Log.w(TAG, "Dreamliner PackageManager hook skipped: " + t.getMessage());
        }
    }

    private void hookSettingsProcess(LoadPackageParam lpparam) {
        Log.i(TAG, "Hooking Settings DreamBackend and DreamPickerController...");

        // 1. Hook DreamBackend.getDreamInfoList() to guarantee ChargingDreamService is always present
        try {
            Class<?> dreamBackendClass = XposedHelpers.findClassIfExists("com.android.settingslib.dream.DreamBackend", lpparam.classLoader);
            if (dreamBackendClass != null) {
                XposedHelpers.findAndHookMethod(dreamBackendClass, "getDreamInfoList", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object result = param.getResult();
                        if (!(result instanceof List)) return;

                        @SuppressWarnings("unchecked")
                        List<Object> dreamList = (List<Object>) result;

                        boolean foundCharging = false;
                        for (Object info : dreamList) {
                            if (info == null) continue;
                            ComponentName cn = (ComponentName) XposedHelpers.getObjectField(info, "componentName");
                            if (cn != null && CHARGING_DREAM_CLASS.equals(cn.getClassName())) {
                                foundCharging = true;
                                break;
                            }
                        }

                        if (!foundCharging) {
                            Log.i(TAG, "Injecting Charge DreamInfo directly into Settings DreamBackend list!");
                            try {
                                Object backend = param.thisObject;
                                Context context = (Context) XposedHelpers.getObjectField(backend, "mContext");
                                ComponentName activeDream = (ComponentName) XposedHelpers.callMethod(backend, "getActiveDream");
                                boolean isActive = CHARGING_DREAM_COMPONENT.equals(activeDream);

                                Class<?> dreamInfoClass = XposedHelpers.findClass("com.android.settingslib.dream.DreamBackend$DreamInfo", lpparam.classLoader);
                                Constructor<?> constructor = dreamInfoClass.getDeclaredConstructors()[0];
                                constructor.setAccessible(true);

                                Object chargeInfo = null;
                                if (constructor.getParameterTypes().length == 0) {
                                    chargeInfo = constructor.newInstance();
                                    XposedHelpers.setObjectField(chargeInfo, "caption", "Charge");
                                    XposedHelpers.setObjectField(chargeInfo, "description", "See current charge and when it'll be full");
                                    XposedHelpers.setObjectField(chargeInfo, "componentName", CHARGING_DREAM_COMPONENT);
                                    XposedHelpers.setBooleanField(chargeInfo, "isActive", isActive);
                                } else {
                                    // Instantiate with context and ResolveInfo if needed
                                    PackageManager pm = context.getPackageManager();
                                    Intent intent = new Intent(android.service.dreams.DreamService.SERVICE_INTERFACE).setComponent(CHARGING_DREAM_COMPONENT);
                                    List<ResolveInfo> resolves = pm.queryIntentServices(intent, PackageManager.GET_META_DATA);
                                    if (resolves != null && !resolves.isEmpty()) {
                                        ResolveInfo ri = resolves.get(0);
                                        CharSequence caption = ri.loadLabel(pm);
                                        if (caption == null || caption.length() == 0) caption = "Charge";
                                        CharSequence description = "See current charge and when it'll be full";
                                        Drawable icon = ri.loadIcon(pm);
                                        chargeInfo = XposedHelpers.newInstance(dreamInfoClass, caption, description, icon, isActive, CHARGING_DREAM_COMPONENT, (ComponentName) null);
                                    }
                                }

                                if (chargeInfo != null) {
                                    List<Object> newList = new ArrayList<>(dreamList);
                                    newList.add(chargeInfo);
                                    param.setResult(newList);
                                }
                            } catch (Throwable t) {
                                Log.w(TAG, "Could not synthesize Charge DreamInfo: " + t.getMessage());
                            }
                        }
                    }
                });
            }
        } catch (Throwable t) {
            Log.w(TAG, "DreamBackend hook skipped: " + t.getMessage());
        }

        // 2. Hook DreamPickerController
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
            Log.w(TAG, "Settings DreamPickerController hook skipped: " + t.getMessage());
        }
    }

    private void neutralizeClassMethod(ClassLoader classLoader, String className, String methodName) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, classLoader);
            if (clazz != null) {
                for (Method m : clazz.getDeclaredMethods()) {
                    if (methodName.equals(m.getName())) {
                        XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(null));
                        Log.d(TAG, "Neutralized " + className + "." + methodName);
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to neutralize " + className + "." + methodName + ": " + t.getMessage());
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
