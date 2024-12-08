package com.fuck.snotes;

import android.app.Activity;
import android.view.View;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XC_MethodHook;
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
import android.app.Activity;
import de.robv.android.xposed.*;

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
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.smartisan.notes")) {
            return;
        }
        // 其他布局隐藏
        hookHideViews(lpparam);
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
        
        // Hook Ai 按钮
        hideAiButton(bridge);
        
        // Hook 隐藏开通 Vip 弹窗
        hookVipDialog(bridge);
    }

    private void hookField(DexKitBridge bridge, String fieldName, Class<?> fieldType, Object value) {
        try {
            Field field = bridge.findField(FindField.create()
                    .matcher(FieldMatcher.create().name(fieldName).type(fieldType)))
                    .single()
                    .getFieldInstance(hostClassLoader);

            String getterName = fieldName.equals("isValid") ? "isValid" : "get" +
                    fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);

            Method getter = field.getDeclaringClass().getMethod(getterName);
            XposedBridge.hookMethod(getter, XC_MethodReplacement.returnConstant(value));
            XposedBridge.log("Hook " + fieldName + " 成功");
        } catch (Exception e) {
            XposedBridge.log("Hook " + fieldName + " 失败: " + e);
        }
    }

    private void hookIsVipMethod(DexKitBridge bridge) {
        try {
            UsingFieldMatcher fieldMatcher = UsingFieldMatcher.create()
                    .field(FieldMatcher.create().name("isValid"));

            Method method = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create().name("isVip").usingFields(Collections.singleton(fieldMatcher))))
                    .single()
                    .getMethodInstance(hostClassLoader);

            XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(true));
            XposedBridge.log("Hook isVip 方法成功");
        } catch (Exception e) {
            XposedBridge.log("Hook isVip 方法失败: " + e);
        }
    }

    private void hookDisableShowMethod(DexKitBridge bridge) {
        try {
            Method method = bridge.findMethod(FindMethod.create()
                    .searchPackages("com.smartisan.notes")
                    .matcher(MethodMatcher.create().declaredClass("com.smartisan.notes.Splash").usingStrings("disable_show")))
                    .single()
                    .getMethodInstance(hostClassLoader);
            if (method != null) {
                XposedBridge.hookMethod(method, XC_MethodReplacement.returnConstant(false));
                XposedBridge.log("Hook disable_show 字符串所在方法成功");
            }
        } catch (Exception e) {
            XposedBridge.log("未找到 disable_show 所在方法，低于 4.1.4 可忽略: " + e);
        }
    }

    private void hookShowUpdateDialogMethod(DexKitBridge bridge) {
        try {
            Method method = bridge.findMethod(FindMethod.create()
                    .searchPackages("com.ss.android.update")
                    .matcher(MethodMatcher.create().name("showUpdateDialog").declaredClass("com.ss.android.update.UpdateServiceImpl")))
                    .single()
                    .getMethodInstance(hostClassLoader);

            XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING);
            XposedBridge.log("Hook showUpdateDialog 方法成功");
        } catch (Exception e) {
            XposedBridge.log("Hook showUpdateDialog 方法失败: " + e);
        }
    }

    private void hookSensitiveWordManager(DexKitBridge bridge) {
        try {
            Method method = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create().name("checkSensitiveWords").declaredClass("com.smartisanos.notes.widget.SensitiveWordManager")))
                    .single()
                    .getMethodInstance(hostClassLoader);

            XposedBridge.hookMethod(method, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    XposedHelpers.callMethod(param.args[1], "onSuccess");
                    return null;
                }
            });
            XposedBridge.log("Hook checkSensitiveWords 方法成功");
        } catch (Exception e) {
            XposedBridge.log("Hook checkSensitiveWords 方法失败: " + e);
        }
    }
    
    private void hookVipDialog(DexKitBridge bridge) {
        try {
            Method method = bridge.findMethod(FindMethod.create()
                    .searchPackages("com.smartisanos.notes")
                    .matcher(MethodMatcher.create().name("onCreate").declaredClass("com.smartisanos.notes.ad.RecommendDialog")))
                    .single()
                    .getMethodInstance(hostClassLoader);
    
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    param.thisObject.getClass().getMethod("dismiss").invoke(param.thisObject);
                }
            });
            XposedBridge.log("Hook 隐藏未登录 Vip 弹窗");
        } catch (Exception e) {
            XposedBridge.log("Hook 隐藏未登录 Vip 弹窗失败: " + e);
        }
    }
    
    private void hideAiButton(DexKitBridge bridge) {
        try {
            Method method = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .declaredClass("com.smartisanos.notes.widget.DefaultNotesTitleBar")
                        .name("findBtnById")
                        .returnType(View.class)
                        .paramTypes("com.smartisanos.notes.widget.IActionBar$ButtonID")))
                    .single()
                    .getMethodInstance(hostClassLoader);
    
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.getResult() != null) {
                        Class<?> buttonIDClass = hostClassLoader.loadClass("com.smartisanos.notes.widget.IActionBar$ButtonID");
                        Object aiButtonID = Enum.valueOf((Class<Enum>) buttonIDClass, "AI");
                        Object ragButtonID = Enum.valueOf((Class<Enum>) buttonIDClass, "RAG");
    
                        if (param.args[0] == aiButtonID || param.args[0] == ragButtonID) {
                            param.setResult(null);
                        }
                    }
                }
            });
            XposedBridge.log("隐藏 AIButton 和 RAGButton 成功");
        } catch (Exception e) {
            XposedBridge.log("隐藏 AIButton 和 RAGButton 失败: " + e);
        }
    }

    
    private void hookHideViews(LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                
                // 隐藏 rag_button
                int ragButtonResId = activity.getResources().getIdentifier("rag_button", "id", "com.smartisan.notes");
                if (ragButtonResId != 0) {
                    View ragButton = activity.findViewById(ragButtonResId);
                    if (ragButton != null) {
                        ragButton.setVisibility(View.GONE);
                    }
                }
    
                //隐藏 setting_banner
                int settingBannerResId = activity.getResources().getIdentifier("setting_banner", "id", "com.smartisan.notes");
                if (settingBannerResId != 0) {
                    View settingBanner = activity.findViewById(settingBannerResId);
                    if (settingBanner != null) {
                        settingBanner.setVisibility(View.GONE);
                        XposedBridge.log("隐藏 setting_banner 成功");
                    }
                }
            }
        });
    }
}

