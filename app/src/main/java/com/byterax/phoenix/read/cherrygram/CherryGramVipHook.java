package com.byterax.phoenix.read.cherrygram;

import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Cherrygram (uz.unnarsx.cherrygram) 捐赠功能解锁 Hook。
 *
 * <p>数据来源：SimpleHookR 规则 (Cherrygram 12.10.1 / build 70380)
 *   - 混淆类名: {@code m}
 *   - 6 个方法（其中 4 个带 long 参数）hookMode=Return, result=true
 *
 * <p>翻译为 Modern libxposed API 102 写法：每条规则对应一个 {@code hook(method).intercept()}
 * 强制返回 {@code Boolean.TRUE}，等价于 SimpleHookR 的 hookMode=Return。
 *
 * <p>由 {@code HookInit.onPackageReady} 按包名分发后调用
 * {@link #installFromModern(XposedModule, ClassLoader)} 安装。
 *
 * <p>⚠️ 版本死绑：类名 m、方法 Unicode 名是 R8 混淆产物，App 一升级就挂。
 * 抗版本方案见 cherrygram_vip_hook/README.md。
 */
public final class CherryGramVipHook {

    private static final String TAG = "SquemaFQHook";
    private static final String TARGET_PKG = "uz.unnarsx.cherrygram";

    /** R8 混淆后的类名。 */
    private static final String TARGET_CLASS = "m";

    /**
     * 6 条 SimpleHookR 规则的方法 Unicode 名 + 参数类型。
     * 无参方法用空数组表示。
     */
    private static final String[] METHOD_NAMES = {
            "\u2CD3",            // 规则 1: m.ⳓ()
            "\u0A99",            // 规则 2: m.હ(long)
            "\u3415",            // 规则 3: m.㐕(long)
            "\u3879",            // 规则 4: m.㡹(long)
            "\u0446",            // 规则 5: m.ц()
            "\u3ADC",            // 规则 6: m.㫜()
    };

    private static final Class<?>[][] PARAM_TYPES = {
            new Class<?>[0],                          // 规则 1: ()
            new Class<?>[]{long.class},               // 规则 2: (long)
            new Class<?>[]{long.class},               // 规则 3: (long)
            new Class<?>[]{long.class},               // 规则 4: (long)
            new Class<?>[0],                          // 规则 5: ()
            new Class<?>[0],                          // 规则 6: ()
    };

    private static final AtomicBoolean TOAST_SHOWN = new AtomicBoolean(false);
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private CherryGramVipHook() {}

    /**
     * 由 {@code HookInit.onPackageReady} 调用。
     * 校验 ClassLoader + 防重复安装 + 装 6 条规则。
     */
    public static void installFromModern(XposedModule module, ClassLoader classLoader) {
        if (classLoader == null) {
            Log.i(TAG, "[Cherrygram] skip install reason=nullClassLoader");
            return;
        }
        if (!INSTALLED.compareAndSet(false, true)) {
            Log.i(TAG, "[Cherrygram] already installed loader=" + classLoader.getClass().getName());
            return;
        }
        Log.i(TAG, "[Cherrygram] hooking " + TARGET_PKG + " loader=" + classLoader.getClass().getName());

        boolean[] results = new boolean[METHOD_NAMES.length];
        try {
            Class<?> cls = Class.forName(TARGET_CLASS, false, classLoader);
            Log.i(TAG, "[Cherrygram] resolved class " + TARGET_CLASS + " (declared methods="
                    + cls.getDeclaredMethods().length + ")");

            for (int i = 0; i < METHOD_NAMES.length; i++) {
                results[i] = hookReturnTrue(module, cls, METHOD_NAMES[i], PARAM_TYPES[i]);
            }

            // 总结
            StringBuilder sb = new StringBuilder("[Cherrygram] hook status: [");
            for (int i = 0; i < results.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(results[i]);
            }
            sb.append("] (= rule1..rule").append(results.length).append(")");
            Log.i(TAG, sb.toString());

            if (!anyTrue(results)) {
                Log.w(TAG, "[Cherrygram] !!! ALL HOOKS FAILED — Cherrygram 版本可能已更新，"
                        + "请用 jadx 重抽类 m 的方法名后修改本类。");
            }
        } catch (Throwable t) {
            Log.e(TAG, "[Cherrygram] failed to load class " + TARGET_CLASS, t);
        }
    }

    /**
     * 等价 SimpleHookR 的 hookMode=Return + result.type=BOOLEAN + value=true。
     * 找到方法 → 用 libxposed {@code hook(method).intercept()} 替换返回值。
     */
    private static boolean hookReturnTrue(XposedModule module, Class<?> cls,
                                          String methodName, Class<?>[] paramTypes) {
        Method method;
        try {
            method = cls.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "[Cherrygram]   ✗ hook " + TARGET_CLASS + "." + methodName
                    + "(" + paramSig(paramTypes) + ") FAILED: not found");
            return false;
        } catch (Throwable t) {
            Log.w(TAG, "[Cherrygram]   ✗ hook " + TARGET_CLASS + "." + methodName
                    + "(" + paramSig(paramTypes) + ") FAILED: " + t);
            return false;
        }

        try {
            module.hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> Boolean.TRUE);
            Log.i(TAG, "[Cherrygram]   ✓ hooked " + TARGET_CLASS + "." + methodName
                    + "(" + paramSig(paramTypes) + ") -> true");
            showHookSuccessToast();
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "[Cherrygram]   ✗ hook " + TARGET_CLASS + "." + methodName
                    + "(" + paramSig(paramTypes) + ") FAILED: " + t);
            return false;
        }
    }

    private static void showHookSuccessToast() {
        if (!TOAST_SHOWN.compareAndSet(false, true)) {
            return;
        }
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object application = activityThreadClass
                    .getDeclaredMethod("currentApplication")
                    .invoke(null);
            if (!(application instanceof android.app.Application)) {
                return;
            }
            final android.app.Application app = (android.app.Application) application;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    android.widget.Toast.makeText(app, "CherryGram VIP Hook 成功",
                            android.widget.Toast.LENGTH_SHORT).show());
        } catch (Throwable ignored) {
        }
    }

    private static String paramSig(Class<?>[] ps) {
        if (ps == null || ps.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (Class<?> p : ps) sb.append(p.getSimpleName()).append(", ");
        return sb.substring(0, sb.length() - 2);
    }

    private static boolean anyTrue(boolean[] arr) {
        for (boolean b : arr) {
            if (b) return true;
        }
        return false;
    }
}