package com.byterax.phoenix.read.cherrygram;

import android.util.Log;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Cherrygram (uz.unnarsx.cherrygram) 捐赠 / 高级功能解锁 Hook。
 *
 * <p><b>核心策略：R8 字典混淆 + 死绑 Unicode 方法名都是 cat-and-mouse 死循环。
 * 真正稳的是：去 GitHub 找源码，定位判定函数真名（DonatesManager.didUserDonate* / checkAllDonated*），
 * 然后用 DexKit 按方法名特征扫描目标进程 dex，定位真正的 R8 重命名类，把那些方法全部 hook 强制返回 true。</b>
 *
 * <p>逻辑链（基于 arsLan4k1390/Cherrygram 12.10.1 源码）:
 * <pre>
 *   UI / settings
 *      → DonatesManager.didUserDonateForFeature()
 *          = checkAllDonatedAccounts() OR checkAllDonatedAccountsForMarketplace()
 *      → checkAllDonatedAccounts() 遍历 UserConfig.MAX_ACCOUNT_COUNT
 *          → didUserDonate(userId)
 *              = verifiedUserIds.contains(userId) || didUserDonateForMarketplace(userId)
 * </pre>
 *
 * <p>Hook 方案：
 * <ol>
 *   <li><b>主 hook</b>：DexKit 按方法名（{@code didUserDonate*} / {@code checkAllDonatedAccounts*}）扫
 *       目标进程 dex，全部 return-boolean 方法都 hook 强制返回 true（覆盖 R8 重命名类）</li>
 *   <li><b>兜底 hook</b>：SimpleHookR 6 条 Unicode 规则（类 m + 字典名）。版本变了就当 NOP。
 *       Cherrygram 12.10.1/70380 上 5/6 命中，作为额外加固层</li>
 * </ol>
 *
 * <p>由 {@code HookInit.onPackageReady} 按包名分发后调用
 * {@link #installFromModern(XposedModule, ClassLoader)} 安装。
 */
public final class CherryGramVipHook {

    private static final String TAG = "SquemaFQHook";
    private static final String TARGET_PKG = "uz.unnarsx.cherrygram";

    /** 判定函数真名（DonatesManager 源码里）。 */
    private static final String[] PRIMARY_NAMES = {
            "didUserDonate",
            "didUserDonate2",
            "didUserDonateForMarketplace",
            "didUserDonateForFeature",
            "checkAllDonatedAccounts",
            "checkAllDonatedAccountsForMarketplace"
    };

    /** SimpleHookR 12.10.1/70380 规则（兜底）。 */
    private static final String LEGACY_CLASS = "m";
    private static final String[] LEGACY_METHOD_NAMES = {
            // 实测 Cherrygram 12.10.1/70380 m 类：\u2CD3() \u0AB9(long) \u3415(long)
            // \u3879(long) \u0446() \u3ADC()，原先 \u0A99 是错的 codepoint（ઙ）
            // — SimpleHookR 写的是 U+0AB9 Gujarati Letter HA with nukta (હ)，
            // 与 U+0A99 Letter HA (ઙ) 是两个不同字符但 UTF-8 字节只差 1 字节
            "\u2CD3", "\u0AB9", "\u3415", "\u3879", "\u0446", "\u3ADC"
    };
    private static final Class<?>[][] LEGACY_PARAM_TYPES = {
            new Class<?>[0],
            new Class<?>[]{long.class},
            new Class<?>[]{long.class},
            new Class<?>[]{long.class},
            new Class<?>[0],
            new Class<?>[0],
    };

    private static final AtomicBoolean TOAST_SHOWN = new AtomicBoolean(false);
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private CherryGramVipHook() {}

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

        // 确保 libdexkit.so 加载（HookInit 已经在主流程 load 过 DexKit，
        // 但我们这里属于非主流程的二次启动，库可能未在该线程加载；保险起见再 load 一次）
        try {
            System.loadLibrary("dexkit");
        } catch (Throwable ignored) {
            // already loaded
        }

        int primaryHits = installPrimary(module, classLoader);
        int legacyHits  = installLegacy(module, classLoader);

        Log.i(TAG, "[Cherrygram] result: primary=" + primaryHits
                + " hook(s)  legacy=" + legacyHits + "/6");

        if (primaryHits > 0) {
            showHookSuccessToast();
        } else if (legacyHits == 0) {
            Log.w(TAG, "[Cherrygram] !!! ALL HOOKS FAILED — Cherrygram 源码可能改版，"
                    + "需要到 github.com/arsLan4k1390/Cherrygram 重新对 didUserDonate* 签名");
        }
    }

    /**
     * 主 hook：DexKit 按方法名特征（{@code didUserDonate*} / {@code checkAllDonatedAccounts*}）
     * 扫目标进程 dex；任何返回 boolean / Boolean 的都强制返回 true。
     */
    private static int installPrimary(XposedModule module, ClassLoader classLoader) {
        DexKitBridge bridge;
        try {
            bridge = DexKitBridge.create(classLoader, true);
        } catch (Throwable t) {
            Log.w(TAG, "[Cherrygram] primary: DexKit bridge failed: " + t);
            return 0;
        }
        int hits = 0;
        try {
            for (String name : PRIMARY_NAMES) {
                FindMethod q = new FindMethod().matcher(
                        MethodMatcher.create().name(name));
                MethodDataList list = bridge.findMethod(q);
                if (list.isEmpty()) continue;
                Log.i(TAG, "[Cherrygram] primary: " + name + " -> " + list.size() + " candidate(s)");
                for (MethodData md : list) {
                    try {
                        // 跳过构造器（同名"<init>"已被 MethodMatcher 排除但保险一下）
                        if (md.isConstructor()) continue;
                        Method m = md.getMethodInstance(classLoader);
                        Class<?> ret = m.getReturnType();
                        if (ret != boolean.class && ret != Boolean.class) continue;
                        m.setAccessible(true);
                        module.hook(m)
                                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                                .intercept(chain -> Boolean.TRUE);
                        Log.i(TAG, "[Cherrygram]   ✓ primary "
                                + m.getDeclaringClass().getName() + "." + name
                                + "(" + paramSig(m.getParameterTypes()) + ") -> true");
                        hits++;
                    } catch (Throwable perMethod) {
                        Log.w(TAG, "[Cherrygram]   ✗ primary " + name + " FAILED: " + perMethod);
                    }
                }
            }
        } finally {
            try { bridge.close(); } catch (Throwable ignored) {}
        }
        return hits;
    }

    /** 兜底：SimpleHookR 6 条 Unicode 规则。 */
    private static int installLegacy(XposedModule module, ClassLoader classLoader) {
        Class<?> cls;
        try {
            cls = Class.forName(LEGACY_CLASS, false, classLoader);
        } catch (Throwable t) {
            // 类 m 不存在是正常的（新版 Cherrygram 可能没这内部类了）
            return 0;
        }
        int hits = 0;
        for (int i = 0; i < LEGACY_METHOD_NAMES.length; i++) {
            if (hookReturnTrue(module, cls, LEGACY_METHOD_NAMES[i], LEGACY_PARAM_TYPES[i])) {
                hits++;
            }
        }
        return hits;
    }

    private static boolean hookReturnTrue(XposedModule module, Class<?> cls,
                                          String methodName, Class<?>[] paramTypes) {
        Method method;
        try {
            method = cls.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
        } catch (Throwable t) {
            return false;
        }
        try {
            module.hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> Boolean.TRUE);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void showHookSuccessToast() {
        if (!TOAST_SHOWN.compareAndSet(false, true)) return;
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object app = at.getDeclaredMethod("currentApplication").invoke(null);
            if (!(app instanceof android.app.Application)) return;
            final android.app.Application a = (android.app.Application) app;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    android.widget.Toast.makeText(a, "CherryGram VIP Hook 成功",
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
}