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
 * <p><b>核心策略：</b>R8 字典混淆 + 死绑 Unicode 方法名都是 cat-and-mouse 死循环。
 * 真正稳的是去 GitHub 找源码，定位判定函数真名（{@code DonatesManager.didUserDonate*} /
 * {@code checkAllDonatedAccounts*}），然后用 DexKit 按方法名特征扫描目标进程 dex，
 * 把那些方法全部 hook 强制返回 true，覆盖 R8 类重命名。
 *
 * <p>判定链（基于 arsLan4k1390/Cherrygram 12.10.1 源码）：
 * <pre>
 *   UI / settings
 *      → DonatesManager.didUserDonateForFeature()
 *          = checkAllDonatedAccounts() || checkAllDonatedAccountsForMarketplace()
 *      → checkAllDonatedAccounts() 遍历 UserConfig.MAX_ACCOUNT_COUNT
 *          → didUserDonate(userId)
 *              = verifiedUserIds.contains(userId) || didUserDonateForMarketplace(userId)
 * </pre>
 *
 * <p>Hook 方案：
 * <ol>
 *   <li><b>主</b>：DexKit 按方法名（{@code didUserDonate*} / {@code checkAllDonatedAccounts*}）扫，
 *       所有返回 boolean / Boolean 的方法 hook 强制返回 {@code true}</li>
 *   <li><b>兜底</b>：SimpleHookR 12.10.1/70380 的 6 条 Unicode 规则（类 {@code m} + 字典方法名），
 *       版本变了就当 NOP。当前设备实测 6/6 命中</li>
 * </ol>
 *
 * <p>由 {@code HookInit.onPackageReady} 按包名分发后调用
 * {@link #installFromModern(XposedModule, ClassLoader)} 安装。
 */
public final class CherryGramVipHook {

    private static final String TAG = "SquemaFQHook";
    private static final String TARGET_PKG = "uz.unnarsx.cherrygram";

    /** DonatesManager 源码里的判定函数真名（被 keep，R8 类重命名不影响）。 */
    private static final String[] PRIMARY_NAMES = {
            "didUserDonate",
            "didUserDonate2",
            "didUserDonateForMarketplace",
            "didUserDonateForFeature",
            "checkAllDonatedAccounts",
            "checkAllDonatedAccountsForMarketplace",
    };

    /** SimpleHookR 12.10.1/70380 规则（兜底，类 {@code m} 内的字典方法名）。
     *  <p>历史坑：原始规则写的是 U+0A99（ઙ），实测 Cherrygram 编译产物是
     *  <strong>U+0AB9（હ）</strong>——Gujarati Letter HA with nukta。两个字符
     *  长得几乎一样，UTF-8 只差末位字节（{@code 99} vs {@code b9}）。 */
    private static final String LEGACY_CLASS = "m";
    private static final String[] LEGACY_METHOD_NAMES = {
            "\u2CD3", // ⳓ
            "\u0AB9", // હ (was U+0A99 — wrong codepoint, see class javadoc)
            "\u3415", // 㐕
            "\u3879", // 㡹
            "\u0446", // ц
            "\u3ADC", // 㫜
    };
    private static final Class<?>[][] LEGACY_PARAM_TYPES = {
            new Class<?>[0],
            new Class<?>[]{long.class},
            new Class<?>[]{long.class},
            new Class<?>[]{long.class},
            new Class<?>[0],
            new Class<?>[0],
    };

    /** 强制返回的常量。集中在这里方便审计 —— 这就是 hook 的实际行为。 */
    private static final Object RETURN_TRUE = Boolean.TRUE;

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private CherryGramVipHook() {}

    /**
     * 入口：被 {@code HookInit.onPackageReady} 调用，整个进程生命周期一次。
     *
     * @param module       libxposed 提供的入口 module 句柄
     * @param classLoader  Cherrygram 主进程 ClassLoader（可为 null — null 时直接 NOP）
     */
    public static void installFromModern(XposedModule module, ClassLoader classLoader) {
        if (classLoader == null) {
            Log.i(TAG, "[Cherrygram] skip install: null ClassLoader");
            return;
        }
        if (!INSTALLED.compareAndSet(false, true)) {
            Log.i(TAG, "[Cherrygram] already installed");
            return;
        }
        Log.i(TAG, "[Cherrygram] hooking " + TARGET_PKG
                + " loader=" + classLoader.getClass().getName());

        ensureDexKitLoaded();
        int primaryHits = installPrimary(module, classLoader);
        int legacyHits  = installLegacy(module, classLoader);

        Log.i(TAG, "[Cherrygram] result: primary=" + primaryHits
                + " hook(s)  legacy=" + legacyHits + "/6");

        if (primaryHits > 0) {
            // Toast removed — status surfaced via MainActivity card + logcat only.
        } else if (legacyHits == 0) {
            Log.w(TAG, "[Cherrygram] !!! ALL HOOKS FAILED — Cherrygram 源码可能改版，"
                    + "需要到 github.com/arsLan4k1390/Cherrygram 重新对 didUserDonate* 签名");
        }
    }

    /** 确保 libdexkit.so 加载。HookInit 主流程已 load 过一次；二次进入再 load 是幂等的。 */
    private static void ensureDexKitLoaded() {
        try {
            System.loadLibrary("dexkit");
        } catch (Throwable ignored) {
            // 已经加载过或不在 classpath — 失败由后续 DexKitBridge.create 抛错时再处理
        }
    }

    /**
     * 主 hook：DexKit 按方法名特征（{@code didUserDonate*} / {@code checkAllDonatedAccounts*}）
     * 扫目标进程 dex；任何返回 boolean / Boolean 的方法都 hook 强制返回 true。
     */
    private static int installPrimary(XposedModule module, ClassLoader classLoader) {
        DexKitBridge bridge = tryCreateDexKit(classLoader);
        if (bridge == null) return 0;

        int hits = 0;
        try {
            for (String name : PRIMARY_NAMES) {
                hits += hookAllMatches(module, classLoader, bridge, name);
            }
        } finally {
            closeQuietly(bridge);
        }
        return hits;
    }

    private static DexKitBridge tryCreateDexKit(ClassLoader classLoader) {
        try {
            return DexKitBridge.create(classLoader, true);
        } catch (Throwable t) {
            Log.w(TAG, "[Cherrygram] primary: DexKit bridge failed: " + t);
            return null;
        }
    }

    /** 用 DexKit 按方法名找，匹配所有返回 boolean 的重载，全部 hook。 */
    private static int hookAllMatches(XposedModule module, ClassLoader classLoader,
                                       DexKitBridge bridge, String methodName) {
        MethodDataList list;
        try {
            list = bridge.findMethod(new FindMethod().matcher(
                    MethodMatcher.create().name(methodName)));
        } catch (Throwable t) {
            Log.w(TAG, "[Cherrygram] primary: findMethod(" + methodName + ") failed: " + t);
            return 0;
        }
        if (list.isEmpty()) return 0;

        Log.i(TAG, "[Cherrygram] primary: " + methodName + " -> " + list.size() + " candidate(s)");

        int hits = 0;
        for (MethodData md : list) {
            if (md.isConstructor()) continue;
            if (hookReturningBoolean(module, classLoader, md, methodName)) hits++;
        }
        return hits;
    }

    /** 把单个 MethodData 钩上，校验返回类型，失败容错并打日志。 */
    private static boolean hookReturningBoolean(XposedModule module, ClassLoader classLoader,
                                                MethodData md, String methodName) {
        Method m;
        try {
            m = md.getMethodInstance(classLoader);
        } catch (Throwable t) {
            Log.w(TAG, "[Cherrygram]   ✗ primary " + methodName + ": getMethodInstance failed: " + t);
            return false;
        }
        Class<?> ret = m.getReturnType();
        if (ret != boolean.class && ret != Boolean.class) return false;

        try {
            m.setAccessible(true);
            module.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> RETURN_TRUE);
        } catch (Throwable t) {
            Log.w(TAG, "[Cherrygram]   ✗ primary " + methodName + " hook failed: " + t);
            return false;
        }
        Log.i(TAG, "[Cherrygram]   ✓ primary "
                + m.getDeclaringClass().getName() + "." + methodName
                + "(" + paramSig(m.getParameterTypes()) + ") -> true");
        return true;
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
            if (hookForceTrue(module, cls, LEGACY_METHOD_NAMES[i], LEGACY_PARAM_TYPES[i])) {
                hits++;
            }
        }
        return hits;
    }

    /** 在指定类上钩指定方法，强制返回 true。失败（方法不存在 / hook 抛错）返回 false。 */
    private static boolean hookForceTrue(XposedModule module, Class<?> cls,
                                          String methodName, Class<?>[] paramTypes) {
        Method method;
        try {
            method = cls.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
        } catch (NoSuchMethodException e) {
            return false; // 版本变更后该方法名消失 — 静默跳过
        } catch (Throwable t) {
            Log.w(TAG, "[Cherrygram]   ✗ legacy " + methodName + " lookup failed: " + t);
            return false;
        }
        try {
            module.hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> RETURN_TRUE);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "[Cherrygram]   ✗ legacy " + methodName + " hook failed: " + t);
            return false;
        }
    }

    private static void closeQuietly(DexKitBridge bridge) {
        try { bridge.close(); } catch (Throwable ignored) {}
    }

    /** Toast 跨进程提示已删除 —— 状态通过 MainActivity 状态卡片 + logcat 输出。 */

    /** 把参数类型数组渲染成简短签名（"long, Context"），用于日志可读性。 */
    private static String paramSig(Class<?>[] ps) {
        if (ps == null || ps.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (Class<?> p : ps) sb.append(p.getSimpleName()).append(", ");
        return sb.substring(0, sb.length() - 2);
    }

    // 静态检查：保证 LEGACY_METHOD_NAMES / LEGACY_PARAM_TYPES 长度对齐
    static {
        if (LEGACY_METHOD_NAMES.length != LEGACY_PARAM_TYPES.length) {
            throw new ExceptionInInitializerError(
                    "LEGACY_METHOD_NAMES and LEGACY_PARAM_TYPES length mismatch");
        }
    }
}