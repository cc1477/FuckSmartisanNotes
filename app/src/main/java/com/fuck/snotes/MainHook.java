package com.fuck.snotes;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.UsingFieldMatcher;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.matchers.FieldMatcher;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.Collections;
import java.lang.reflect.*;
import de.robv.android.xposed.*;
import android.app.*;

/**
 * @Author Cong @Date 2024/11/14 09:21
 */
public class MainHook implements IXposedHookLoadPackage {

    static {
        try {
            System.loadLibrary("dexkit");
        } catch (Throwable e) {
            XposedBridge.log("加载dexkit库失败: " + e);
        }
    }

    private ClassLoader hostClassLoader;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.smartisan.notes")) {
            return;
        }

        hostClassLoader = lpparam.classLoader;
        try (DexKitBridge bridge = DexKitBridge.create(lpparam.appInfo.sourceDir)) {
            if (bridge != null) {
                hookVipFields(bridge);
            }
        }
    }

    private void hookVipFields(DexKitBridge bridge) {
        // Hook VIP
        hookField(bridge, "isValid", int.class, 1);
        hookField(bridge, "vipDaysLeft", int.class, 0x7fffffff);
        hookField(bridge, "vipEndTime", long.class, 0x7fffffffL);
        hookIsVipMethod(bridge);

        // Hook 开屏广告
        hookDisableShowMethod(bridge);

        // Hook 更新弹窗
        hookShowUpdateDialogMethod(bridge);

        // Hook 分享敏感词限制
        hookSensitiveWordManager(bridge);
    }

    private void hookField(
            DexKitBridge bridge, String fieldName, Class<?> fieldType, Object value) {
        try {
            Field field =
                    bridge.findField(
                                    FindField.create()
                                            .matcher(
                                                    FieldMatcher.create()
                                                            .name(fieldName)
                                                            .type(fieldType)))
                            .single()
                            .getFieldInstance(hostClassLoader);

            String getterName =
                    fieldName.equals("isValid")
                            ? "isValid"
                            : "get"
                                    + fieldName.substring(0, 1).toUpperCase()
                                    + fieldName.substring(1);

            Method getter = field.getDeclaringClass().getMethod(getterName);
            XposedBridge.hookMethod(getter, XC_MethodReplacement.returnConstant(value));
            XposedBridge.log("Hook " + fieldName + " 成功");
        } catch (Exception e) {
            XposedBridge.log("Hook " + fieldName + " 失败: " + e);
        }
    }

    // Vip方法，可能不需要
    private void hookIsVipMethod(DexKitBridge bridge) {
        try {
            UsingFieldMatcher fieldMatcher =
                    UsingFieldMatcher.create().field(FieldMatcher.create().name("isValid"));

            Method method =
                    bridge.findMethod(
                                    FindMethod.create()
                                            .matcher(
                                                    MethodMatcher.create()
                                                            .name("isVip")
                                                            .usingFields(
                                                                    Collections.singleton(
                                                                            fieldMatcher))))
                            .single()
                            .getMethodInstance(hostClassLoader);

            XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(true));
            XposedBridge.log("Hook isVip 方法成功");
        } catch (Exception e) {
            XposedBridge.log("Hook isVip 方法失败: " + e);
        }
    }

    // 开屏广告 4.1.4以下
    private void hookDisableShowMethod(DexKitBridge bridge) {
        try {
            Method method =
                    bridge.findMethod(
                                    FindMethod.create()
                                            .searchPackages("com.smartisan.notes")
                                            .matcher(
                                                    MethodMatcher.create()
                                                            .declaredClass(
                                                                    "com.smartisan.notes.Splash")
                                                            .usingStrings("disable_show")))
                            .single()
                            .getMethodInstance(hostClassLoader);
            if (method != null) {
                XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(false));
                XposedBridge.log("Hook disable_show 字符串所在方法成功");
            }
        } catch (Exception e) {
            XposedBridge.log("Hook disable_show 所在方法失败: " + e);
        }
    }

    // 更新弹窗
    private void hookShowUpdateDialogMethod(DexKitBridge bridge) {
        try {
            Method method =
                    bridge.findMethod(
                                    FindMethod.create()
                                            .searchPackages("com.ss.android.update")
                                            .matcher(
                                                    MethodMatcher.create()
                                                            .name("showUpdateDialog")
                                                            .declaredClass(
                                                                    "com.ss.android.update.UpdateServiceImpl")))
                            .single()
                            .getMethodInstance(hostClassLoader);

            XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING);
            XposedBridge.log("Hook showUpdateDialog 方法成功");
        } catch (Exception e) {
            XposedBridge.log("Hook showUpdateDialog 方法失败: " + e);
        }
    }

    // 分享敏感词限制
    private void hookSensitiveWordManager(DexKitBridge bridge) {
        try {
            Method method =
                    bridge.findMethod(
                                    FindMethod.create()
                                            .matcher(
                                                    MethodMatcher.create()
                                                            .name("checkSensitiveWords")
                                                            .declaredClass(
                                                                    "com.smartisanos.notes.widget.SensitiveWordManager")))
                            .single()
                            .getMethodInstance(hostClassLoader);

            XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING);
            XposedBridge.log("Hook checkSensitiveWords 方法成功");
        } catch (Exception e) {
            XposedBridge.log("Hook checkSensitiveWords 方法失败: " + e);
        }
    }
}
