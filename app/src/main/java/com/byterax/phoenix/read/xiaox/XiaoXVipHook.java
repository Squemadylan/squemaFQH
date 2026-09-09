package com.byterax.phoenix.read.xiaox;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * 小X分身 (com.bly.dkplat) VIP 解锁 Hook。
 *
 * <p>适配本模块的 Modern libxposed API 102（本仓库 README 明确要求：target API 102
 * 后不调用 de.robv.android.xposed.* 旧 API），由 {@code HookInit.onPackageReady}
 * 按包名分发后调用 {@link #installFromModern(XposedModule, ClassLoader)} 安装。</p>
 *
 * <p>目标 App 逆向结论（由 HookVip 与 jadx 分析得出）：</p>
 * <ul>
 *   <li>VIP 判定集中在 {@code com.bly.dkplat.cache.UserCache}（单例 {@code UserCache.get()}）</li>
 *   <li>关键方法全部被 vmppro 虚拟化保护（native），smali 硬改无效，但 hook 返回值可绕过</li>
 *   <li>用户中心（UserActivity）整体 native：会<b>直读字段</b>（不调 getter）。\n *       跨进程会员信息通过 AIDL {@code MemberInfo}（Parcelable）传递，每次\n *       {@code MemberInfo(Parcel)} 反序列化都是新实例，native 直读其 {@code vipType}/\n *       {@code expiredTime} 字段。因此必须 hook 构造函数在对象诞生时写字段。</li>\n *   <li>{@code CRuntime.f6809b} 是宿主进程静态缓存（会员可用标志），在\n *       {@code CPluginManagerService} 构造时提前缓存 {@code isVipUser()} 旧值。</li>\n * </ul>\n *
 * <p><b>性能/稳定策略</b>：本类<b>不拦截任何启动关键路径</b>。只 hook 纯 getter\n * 判定方法 + {@code MemberInfo(Parcel)} 构造。字段/静态缓存补丁用延迟后台线程。\n * 绝不 hook {@code CRuntime} 类初始化器、{@code CPluginManagerService} 构造、\n * {@code UserCache.get()} 等启动路径 native 方法。</p>\n *
 * <p>唯一需要真机微调的值：{@code getVipType()} 先给 3（超级会员），若 UI 异常/崩溃\n * 就试 2（普通会员）、1（vmppro 把等级语义藏了，得真机定档）。</p>\n */
public final class XiaoXVipHook {

    private static final String TAG = "SquemaFQHook";
    private static final String TARGET_PKG = "com.bly.dkplat";

    /** 2099-09-12 00:00:00 UTC 毫秒（北京时间 2099-09-12 08:00）。 */
    private static final long FAR_FUTURE_MS = 4092854400000L;
    /**
     * VIP 等级：默认 2（普通会员，用户中心识别为「会员用户」。
     * 之前的 3（超级会员）实测导致用户中心走进「普通用户」显示分支，
     * 降到 2 后应与别人模块行为一致。异常可继续调 1）。
     */
    private static final int VIP_LEVEL = 2;

    private static final AtomicBoolean TOAST_SHOWN = new AtomicBoolean(false);
    private static final AtomicBoolean INSTALLED_LOADER = new AtomicBoolean(false);
    private static final AtomicBoolean LAZY_STARTED = new AtomicBoolean(false);

    private XiaoXVipHook() {}

    /** 与夸克模块一致的安装入口：校验 ClassLoader + 防重复安装。 */
    public static void installFromModern(XposedModule module, ClassLoader classLoader) {
        if (classLoader == null) {
            Log.i(TAG, "[XiaoX] skip install reason=nullClassLoader");
            return;
        }
        if (!INSTALLED_LOADER.compareAndSet(false, true)) {
            Log.i(TAG, "[XiaoX] already installed loader=" + classLoader.getClass().getName());
            return;
        }
        Log.i(TAG, "[XiaoX] hooking " + TARGET_PKG + " loader=" + classLoader.getClass().getName());
        try {
            install(module, classLoader);
            Log.i(TAG, "[XiaoX] hooks installed");
            com.byterax.phoenix.read.HookStatusReporter.reportTargetHooked(TARGET_PKG);
            showHookSuccessToast();
        } catch (Throwable t) {
            Log.e(TAG, "[XiaoX] install failed", t);
        }
    }

    private static void install(XposedModule module, ClassLoader cl) throws Throwable {
        // ============ UserCache：VIP 判定（native 实现，vmppro 保护，返回值覆盖） ============
        Class<?> userCache = Class.forName("com.bly.dkplat.cache.UserCache", false, cl);

        hookBool(module, userCache, "isVipUser", true);        // VIP 总开关 -> true
        hookBool(module, userCache, "isExpired", false);       // 未过期（注意要 false）
        hookBool(module, userCache, "isForbidBannerAd", true); // 去横幅广告 -> true
        hookBool(module, userCache, "isVipShowAdd", true);     // 会员身份展示
        hookBool(module, userCache, "isShowAd", false);        // 不开广告
        hookBool(module, userCache, "isPayOn", false);         // 关支付引导

        hookAll(module, userCache, "getVipType", VIP_LEVEL);   // 会员等级（先 3，异常改 2/1）
        hookAll(module, userCache, "getExpiredTime", FAR_FUTURE_MS); // 2099 年过期

        // 登录态：native 判定「未登录 = 普通用户」。固定返回已登录。
        hookAll(module, userCache, "getUserId", 10001);        // 非 0 userId
        hookAll(module, userCache, "getToken", "fq-hook-token");
        hookAll(module, userCache, "getMobile", "13800138000");
        hookBool(module, userCache, "isInitOk", true);          // 初始化完成

        // ============ UserCache.<init> 构造函数（smali 逆向：纯 Java，对象诞生即写字段） ============
        // 单例 get()/getInstance() 是 native，但 <init> 是纯 Java（apktool 反编译亲眼确认：
        // 字段初值全在构造函数里，且 token/userId 默认 null/0 —— 未登录根源）。
        // hook 它 after：每次 new UserCache 出来立刻写会员字段，native 之后直读永远会员。
        hookUserCacheConstructor(module, userCache);

        // ============ 数据刷新入口：before 短路 + after 写回双保险 ============
        // smali 逆向：CPluginManagerService 构造链无条件调 initCacheByLocal() 覆盖字段，
        // 把我们的初值打回真实值。hook 里等价做法：
        //   initCacheByLocal      -> before 短路（不执行 native 刷新，字段保持会员）
        //   initCacheByApiResult  -> after 写回（服务器数据可能真的有会员，保留覆盖后兜底）
        hookSkipBefore(module, userCache, "initCacheByLocal");
        hookAfterPatchFields(module, userCache, "initCacheByApiResult");

        Log.i(TAG, "[XiaoX] UserCache hooks installed");
        showHookSuccessToast();

        // ============ 跨进程会员信息 AIDL：MemberInfo (Parcelable) ============
        // 用户中心 native 直读字段而非 getter，且 MemberInfo 每次 Parcel 反序列化都是新实例。
        // 必须 hook MemberInfo(Parcel) 构造 + CREATOR.createFromParcel，对象诞生即刻写字段。
        try {
            Class<?> memberInfo = Class.forName("com.bly.dkplat.aidl.MemberInfo", false, cl);
            hookAll(module, memberInfo, "getVipType", VIP_LEVEL);       // 兜底 getter
            hookAll(module, memberInfo, "getExpiredTime", FAR_FUTURE_MS);
            hookAll(module, memberInfo, "getShowAd", 0);                // 去广告
            hookMemberInfoConstructors(module, memberInfo);
            Log.i(TAG, "[XiaoX] MemberInfo hooks installed");
        } catch (Throwable t) {
            Log.w(TAG, "[XiaoX] MemberInfo hook failed: " + t);
        }

        // ============ UserCache setter 写回兜底 ============
        // native 每次从服务端/本地读真实数据都会通过 setter 覆写字段，我们 hook
        // 所有会员相关 setter 的 after，在写入完成后立刻把字段改回会员值，
        // 保证 UserCache 单例上的字段永远保持「已会员」状态。
        hookSettersAfterPatch(module, userCache);

        // ============ MemberUtils.b 跨进程会员数据兜底 ============
        // 用户中心 native 大概率用 MemberUtils.b(context) 返回的 MemberInfo 判级，
        // hook 它 after，返回前把 MemberInfo 实例字段改成会员。
        hookMemberUtils(module, cl);

        // ============ 用户中心 UI 兜底（最后防线） ============
        // UserActivity 整体 native，若 native 判定不走任何 Java 层（字段/方法），
        // 就在 onResume 之后直接把布局里的 tv_user / ll_vip / tv_expired 刷成会员。
        hookUserActivityUi(module, cl);

        // ============ ActivityUserBinding.<init> 黄金兜底 ============
        // smali 确认：构造函数是纯 Java，参数第 22~27 个就是 6 个 TextView
        // （tv_user/tv_expired/tv_bind/tv_mobile/tv_switch_style/标题）。
        // 对象一创建就设置会员文本，不依赖反射找 binding 字段，无死角。
        hookActivityUserBindingInit(module, cl);

        // ============ 字段直读兜底：延迟后台线程（不在启动路径上拦截） ============
        startLazyPatcher(cl);
    }

    /**
     * Hook {@code UserCache} 的 {@code <init>} 构造函数（smali 逆向结论：纯 Java 方法）。
     * 对象创建后立即写会员字段，native 之后直读字段永远是会员值。
     * 比延迟线程（启动后 5 秒）早得多，且覆盖所有实例。
     */
    private static void hookUserCacheConstructor(XposedModule module, Class<?> userCache) {
        try {
            for (Constructor<?> ctor : userCache.getDeclaredConstructors()) {
                try {
                    ctor.setAccessible(true);
                } catch (Throwable ignored) {
                }
                module.hook(ctor)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object r = chain.proceed();
                            patchVipFields(chain.getThisObject());
                            return r;
                        });
                Log.i(TAG, "[XiaoX] UserCache.<init> hooked " + ctor);
            }
        } catch (Throwable t) {
            Log.w(TAG, "[XiaoX] hook UserCache.<init> failed: " + t);
        }
    }

    /**
     * Hook 方法调用前短路（不执行原逻辑）。smali 逆向：CPluginManagerService 构造链
     * 无条件调 {@code initCacheByLocal()} 覆盖会员字段，hook before 直接返回跳过，
     * 字段保持构造函数/懒补丁写的会员值。
     */
    private static void hookSkipBefore(XposedModule module, Class<?> clazz,
                                       String methodName) {
        try {
            int hooked = 0;
            for (Method method : clazz.getDeclaredMethods()) {
                if (!methodName.equals(method.getName())) {
                    continue;
                }
                if (Modifier.isAbstract(method.getModifiers())) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                } catch (Throwable ignored) {
                }
                module.hook(method)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> null); // 不 proceed，直接返回
                hooked++;
            }
            Log.i(TAG, "[XiaoX] " + clazz.getName() + "#" + methodName
                    + " beforeSkip hooks=" + hooked);
        } catch (Throwable t) {
            Log.w(TAG, "[XiaoX] hookSkipBefore " + methodName + " failed: " + t);
        }
    }

    /**
     * Hook {@code MemberInfo} 的 {@code MemberInfo(Parcel)} 构造（反序列化入口），
     * 对象创建后立即写 vip 字段。也兜底 hook {@code CREATOR.createFromParcel}。
     */
    private static void hookMemberInfoConstructors(XposedModule module, Class<?> memberInfo) {
        try {
            for (Constructor<?> ctor : memberInfo.getDeclaredConstructors()) {
                Class<?>[] ptypes = ctor.getParameterTypes();
                // MemberInfo(Parcel) —— AIDL 反序列化必经
                boolean parcelCtor = ptypes.length == 1
                        && android.os.Parcel.class.isAssignableFrom(ptypes[0]);
                // 无参构造也可能被 native 用
                boolean noArg = ptypes.length == 0;
                if (!parcelCtor && !noArg) {
                    continue;
                }
                try {
                    ctor.setAccessible(true);
                } catch (Throwable ignored) {
                }
                module.hook(ctor)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object r = chain.proceed();
                            patchMemberInfoFields(r);
                            return r;
                        });
                Log.i(TAG, "[XiaoX] MemberInfo ctor hooked " + ctor);
            }

            // CREATOR.createFromParcel 双保险
            try {
                Field creatorField = findField(memberInfo, "CREATOR");
                creatorField.setAccessible(true);
                Object creator = creatorField.get(null);
                if (creator != null) {
                    for (Method m : creator.getClass().getDeclaredMethods()) {
                        if (!"createFromParcel".equals(m.getName())) {
                            continue;
                        }
                        try {
                            m.setAccessible(true);
                        } catch (Throwable ignored) {
                        }
                        module.hook(m)
                                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                                .intercept(chain -> {
                                    Object r = chain.proceed();
                                    patchMemberInfoFields(r);
                                    return r;
                                });
                    }
                    Log.i(TAG, "[XiaoX] MemberInfo.CREATOR.createFromParcel hooked");
                }
            } catch (Throwable t) {
                Log.w(TAG, "[XiaoX] MemberInfo.CREATOR hook failed: " + t);
            }
        } catch (Throwable t) {
            Log.w(TAG, "[XiaoX] hook MemberInfo ctor failed: " + t);
        }
    }

    /** 反射改写 MemberInfo 实例的 vip 字段（绕过 native getter 直读）。 */
    private static void patchMemberInfoFields(Object inst) {
        if (inst == null) {
            return;
        }
        try {
            setField(inst, "vipType", VIP_LEVEL);
        } catch (Throwable ignored) {
        }
        try {
            setField(inst, "expiredTime", FAR_FUTURE_MS);
        } catch (Throwable ignored) {
        }
        try {
            setField(inst, "showAd", 0);
        } catch (Throwable ignored) {
        }
        try {
            setField(inst, "serverCheckTime", FAR_FUTURE_MS);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 延迟后台线程（daemon）：先休眠 5 秒让首屏/宿主启动稳定（不压启动路径），
     * 然后开始高频守护循环（每 1 秒一轮）：
     * <ol>
     *   <li>反射调用 {@code UserCache.get()}/{@code getInstance()} 拿单例 → 改写 vip 字段</li>
     *   <li>反射把 {@code CRuntime.f6809b} 静态字段强制置 true</li>
     *   <li>反射扫描当前 Activity，若是用户中心则强制刷卡片 UI</li>
     * </ol>
     */
    private static void startLazyPatcher(final ClassLoader cl) {
        if (!LAZY_STARTED.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(5000L);
            } catch (InterruptedException ignored) {
                return;
            }
            // 高频循环：native/initCacheByLocal 会在启动后从服务器/本地读真实数据覆盖字段，
            // 我们以 1s 间隔持续写回会员值，直到进程结束。daemon 线程开销极小。
            while (!Thread.currentThread().isInterrupted()) {
                patchUserCacheFieldsOnce(cl);
                patchRuntimeVipCacheOnce(cl);
                patchCurrentUserCenterUi(cl);  // UI 兜底：用户中心卡片强制刷成会员
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        }, "XiaoX-LazyPatcher");
        thread.setDaemon(true);
        thread.start();
    }

    /** 反射扫描当前 Activity（后台线程），若是用户中心则强制刷卡片 UI。 */
    private static void patchCurrentUserCenterUi(ClassLoader cl) {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread", false, cl);
            Method currentActivityThread = activityThread.getDeclaredMethod("currentActivityThread");
            currentActivityThread.setAccessible(true);
            Object at = currentActivityThread.invoke(null);
            if (at == null) {
                return;
            }
            Field activitiesField = findField(activityThread, "mActivities");
            activitiesField.setAccessible(true);
            Object activities = activitiesField.get(at);
            if (!(activities instanceof java.util.Map)) {
                return;
            }
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) activities;
            for (Object value : map.values()) {
                Object activity = null;
                // API 26+: mActivities 的 value 是 ActivityClientRecord，字段名 mActivity
                try {
                    Field mActivity = findField(value.getClass(), "mActivity");
                    mActivity.setAccessible(true);
                    activity = mActivity.get(value);
                } catch (Throwable ignored) {
                }
                if (activity == null) {
                    continue;
                }
                String clsName = activity.getClass().getName();
                if ("com.bly.dkplat.widget.UserActivity".equals(clsName)
                        || "com.bly.dkplat.widget.BasicActivity".equals(clsName)) {
                    patchUserCenterUi(activity);
                    return;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "[XiaoX] patchCurrentUserCenterUi failed: " + t);
        }
    }

    /**
     * Hook {@code UserCache} 所有会员相关 setter 的 after，native 写完真实值后立刻改回会员值。
     * 这样即使 native 后续直读字段（不走 getter），也永远是会员状态。
     */
    private static void hookSettersAfterPatch(XposedModule module, Class<?> userCache) {
        String[] setterNames = {
                "setVipType", "setExpiredTime", "setShowAd", "setVipShowAdd",
                "setPayOn", "setPayTip", "setHaveFeedback", "setUserId", "setToken",
                "setIsSign", "setNewUser", "setpModel", "setMobile"
        };
        for (String name : setterNames) {
            try {
                for (Method method : userCache.getDeclaredMethods()) {
                    if (!name.equals(method.getName())) {
                        continue;
                    }
                    if (Modifier.isAbstract(method.getModifiers())) {
                        continue;
                    }
                    try {
                        method.setAccessible(true);
                    } catch (Throwable ignored) {
                    }
                    module.hook(method)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                Object r = chain.proceed();
                                Object inst = chain.getThisObject();
                                if (inst != null) {
                                    patchVipFields(inst);
                                }
                                return r;
                            });
                }
            } catch (Throwable t) {
                Log.w(TAG, "[XiaoX] hook setter " + name + " failed: " + t);
            }
        }
        Log.i(TAG, "[XiaoX] UserCache setters afterPatch installed");
    }

    /**
     * Hook {@code com.bly.dkplat.utils.plugin.MemberUtils} 的 {@code b(Context)} / {@code getMemberInfo}
     * 等返回 {@code MemberInfo} 的方法，after 里把返回实例的 vip 字段改写成会员。
     * 用户中心/会员卡 native 大概率通过这里拿跨进程会员数据。
     */
    private static void hookMemberUtils(XposedModule module, ClassLoader cl) {
        try {
            Class<?> memberUtils = Class.forName("com.bly.dkplat.utils.plugin.MemberUtils", false, cl);
            for (Method method : memberUtils.getDeclaredMethods()) {
                if (!"b".equals(method.getName()) && !"getMemberInfo".equals(method.getName())
                        && !"getVipInfo".equals(method.getName())) {
                    continue;
                }
                if (Modifier.isAbstract(method.getModifiers())) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                } catch (Throwable ignored) {
                }
                module.hook(method)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object r = chain.proceed();
                            // 返回类型可能是 MemberInfo 或具体实现，直接反射写字段（避免编译期依赖目标类）
                            if (r != null) {
                                try {
                                    setField(r, "vipType", VIP_LEVEL);
                                    setField(r, "expiredTime", FAR_FUTURE_MS);
                                    setField(r, "serverCheckTime", FAR_FUTURE_MS);
                                } catch (Throwable ignored) {
                                }
                            }
                            return r;
                        });
                Log.i(TAG, "[XiaoX] MemberUtils." + method.getName() + " hooked");
            }
        } catch (Throwable t) {
            Log.w(TAG, "[XiaoX] hook MemberUtils failed: " + t);
        }
    }

    /**
     * Hook {@code UserActivity} / {@code BasicActivity} 的 {@code onResume} / {@code onWindowFocusChanged}，
     * after 里直接改 UI：{@code tv_user} 刷成「会员用户」、{@code ll_vip} 有效期行显示、
     * {@code tv_expired} 填 2099-09-12、{@code iv_user} 会员图标。
     * 这是最后防线——即使 native 判定不走任何 Java 数据源，进页面也会被刷成会员展示。
     */
    private static void hookUserActivityUi(XposedModule module, ClassLoader cl) {
        String[] classes = {"com.bly.dkplat.widget.UserActivity", "com.bly.dkplat.widget.BasicActivity"};
        String[] methods = {"onResume", "onWindowFocusChanged"};
        for (String clsName : classes) {
            try {
                Class<?> cls = Class.forName(clsName, false, cl);
                for (String mName : methods) {
                    for (Method method : cls.getDeclaredMethods()) {
                        if (!mName.equals(method.getName())) {
                            continue;
                        }
                        try {
                            method.setAccessible(true);
                        } catch (Throwable ignored) {
                        }
                        module.hook(method)
                                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                                .intercept(chain -> {
                                    Object r = chain.proceed();
                                    Object act = chain.getThisObject();
                                    if (act != null) {
                                        patchUserCenterUi(act);
                                    }
                                    return r;
                                });
                        Log.i(TAG, "[XiaoX] " + clsName + "." + mName + " UI patch hooked");
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "[XiaoX] hook " + clsName + " UI failed: " + t);
            }
        }
    }

    /**
     * Hook {@code ActivityUserBinding.<init>}（纯 Java 构造，参数直接是全部 View）。
     * 第 22~27 个参数是 6 个 TextView：tv_user / tv_expired / tv_bind / tv_mobile /
     * tv_switch_style / 标题。对象创建后立刻设置会员文本 + 显示有效期行，
     * 不依赖反射找 binding 字段（根治 jadx 混淆名 f8728z vs 真实 z 的坑）。
     */
    private static void hookActivityUserBindingInit(XposedModule module, ClassLoader cl) {
        try {
            Class<?> binding = Class.forName("com.bly.dkplat.databinding.ActivityUserBinding", false, cl);
            for (Constructor<?> ctor : binding.getDeclaredConstructors()) {
                try {
                    ctor.setAccessible(true);
                } catch (Throwable ignored) {
                }
                module.hook(ctor)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object r = chain.proceed();
                            java.util.List<Object> args = chain.getArgs();
                            // 布局 TextView 顺序：tv_user, tv_expired, tv_bind, tv_mobile, 标题...？
                            // 实际顺序按 smali 构造参数，需要精确对齐，这里按类型+文本兜底
                            if (args != null) {
                                for (Object arg : args) {
                                    if (arg instanceof android.widget.TextView) {
                                        android.widget.TextView tv = (android.widget.TextView) arg;
                                        // 按资源 id 名精确判定（native 改文本后也能命中）
                                        int id = tv.getId();
                                        String name = null;
                                        try {
                                            name = tv.getResources().getResourceEntryName(id);
                                        } catch (Throwable ignored) {
                                        }
                                        if ("tv_user".equals(name)) {
                                            tv.setText("会员用户");
                                        } else if ("tv_expired".equals(name)) {
                                            tv.setText("2099-09-12");
                                        }
                                    } else if (arg instanceof android.widget.ImageView) {
                                        // iv_user：会员图标。构造参数里就带上，页面一创建
                                        // 就是金色，不用等 1s 轮询的兜底线程。
                                        android.widget.ImageView iv = (android.widget.ImageView) arg;
                                        String name = null;
                                        try {
                                            name = iv.getResources()
                                                    .getResourceEntryName(iv.getId());
                                        } catch (Throwable ignored) {
                                        }
                                        if ("iv_user".equals(name)) {
                                            restoreVipIcon(iv);
                                        }
                                    }
                                }
                            }
                            return r;
                        });
                Log.i(TAG, "[XiaoX] ActivityUserBinding.<init> hooked");
            }
        } catch (Throwable t) {
            Log.w(TAG, "[XiaoX] hook ActivityUserBinding.<init> failed: " + t);
        }
    }

    /**
     * Hook {@code ActivityUserBinding} 构造完成后的 View 遍历兜底
     * （ll_vip 的 visible 无法通过构造函数参数改，需要遍历根视图）。
     * 由 {@link #patchUserCenterUi} 完成。
     */

    /** 反射改用户中心布局：tv_user → 会员用户、ll_vip 显示、tv_expired → 2099-09-12。 */
    private static void patchUserCenterUi(Object activity) {
        try {
            // jadx 把混淆字段显示为 f8728z，运行时真实字段名是 z（apktool smali 确认）
            Object binding = getFieldValue(activity, "z");
            if (binding == null) {
                // 兜底：按 ViewBinding 类型找（getFieldValue 模糊匹配已实现）
                binding = getFieldValue(activity, "f8728z");
            }
            if (binding == null) {
                return;
            }
            Object root = null;
            try {
                Method getRoot = binding.getClass().getMethod("getRoot");
                root = getRoot.invoke(binding);
            } catch (Throwable ignored) {
            }
            if (!(root instanceof android.view.ViewGroup)) {
                return;
            }
            // 递归遍历：按资源 id 名定位 tv_user / ll_vip / tv_expired / iv_user
            // （不能按文本匹配！native 可能已把 tv_user 动态改成「普通用户」等，
            //   按文本就找不到目标 TextView 了。资源 id 名字在编译期保留，稳定。）
            android.view.ViewGroup vg = (android.view.ViewGroup) root;
            android.widget.TextView tvUser = null;
            android.widget.TextView tvExpired = null;
            android.view.View llVip = null;
            android.widget.ImageView ivUser = null;
            android.widget.TextView tvBind = null;
            for (android.view.View v : allViews(vg)) {
                int id = v.getId();
                if (id == 0) {
                    continue;
                }
                String name = null;
                try {
                    name = v.getResources().getResourceEntryName(id);
                } catch (Throwable ignored) {
                }
                if (name == null) {
                    continue;
                }
                if ("tv_user".equals(name) && v instanceof android.widget.TextView) {
                    tvUser = (android.widget.TextView) v;
                } else if ("ll_vip".equals(name)) {
                    llVip = v;
                } else if ("tv_expired".equals(name) && v instanceof android.widget.TextView) {
                    tvExpired = (android.widget.TextView) v;
                } else if ("iv_user".equals(name) && v instanceof android.widget.ImageView) {
                    ivUser = (android.widget.ImageView) v;
                } else if ("tv_bind".equals(name) && v instanceof android.widget.TextView) {
                    tvBind = (android.widget.TextView) v;
                }
            }
            if (tvUser != null) {
                tvUser.setText("会员用户");
            }
            if (llVip != null) {
                llVip.setVisibility(android.view.View.VISIBLE);
            }
            if (tvExpired != null) {
                tvExpired.setText("2099-09-12");
            }
            if (tvBind != null) {
                tvBind.setText("会员登录");
            }
            // 图标修复：原生对非会员的 iv_user 会套灰色滤镜（ColorMatrix 去饱和），
            // user_vip.png 本体是金色（主色 224,224,160 淡金）。强制还原：
            // 1) 清所有滤镜（setColorFilter(null)）
            // 2) 重新 setImageResource(user_vip) 保证 src 是金色原图
            if (ivUser != null) {
                restoreVipIcon(ivUser);
            }
            Log.i(TAG, "[XiaoX] UserCenter UI patched: tvUser=" + (tvUser != null)
                    + " llVip=" + (llVip != null) + " tvExpired=" + (tvExpired != null)
                    + " ivUser=" + (ivUser != null));
        } catch (Throwable t) {
            Log.w(TAG, "[XiaoX] patchUserCenterUi failed: " + t);
        }
    }

    /**
     * 还原会员图标为金色。
     *
     * <p>实测结论：{@code res/drawable-xxxhdpi/user_vip.png} 本体就是金色系
     * （主色 224,224,160 淡金，平均饱和度 0.37，金色像素 18%，<b>不是黑白资源</b>）。
     * 用户看到黑白 = native（vmppro 虚拟化，UserActivity 全部 UI 方法都是 native）
     * 对非会员状态的 {@code iv_user} 套了灰度滤镜。所以还原手段三管齐下：</p>
     * <ol>
     *   <li>清颜色滤镜（{@code clearColorFilter} + {@code setColorFilter(null)} 双保险）</li>
     *   <li>重设 {@code src} 为 {@code user_vip}（万一 native 换过图也能拉回金色原图）</li>
     *   <li>恢复 {@code alpha}，排除被半透明/遮罩压暗的情况</li>
     * </ol>
     */
    private static void restoreVipIcon(android.widget.ImageView iv) {
        try {
            iv.clearColorFilter();
        } catch (Throwable ignored) {
        }
        try {
            iv.setColorFilter(null);
        } catch (Throwable ignored) {
        }
        try {
            int resId = iv.getResources().getIdentifier("user_vip", "drawable", TARGET_PKG);
            if (resId != 0) {
                iv.setImageResource(resId);
            }
        } catch (Throwable ignored) {
        }
        try {
            iv.setAlpha(1.0f);
        } catch (Throwable ignored) {
        }
    }

    /** BFS 遍历 ViewGroup 内所有 View。 */
    private static java.util.List<android.view.View> allViews(android.view.ViewGroup root) {
        java.util.List<android.view.View> out = new java.util.ArrayList<>();
        java.util.ArrayDeque<android.view.View> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            android.view.View v = queue.poll();
            if (v instanceof android.view.ViewGroup) {
                android.view.ViewGroup vg = (android.view.ViewGroup) v;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    queue.add(vg.getChildAt(i));
                }
            }
            out.add(v);
        }
        return out;
    }

    /** 反射拿对象字段值（含父类）。优先精确名，失败后按类型/后缀模糊匹配。 */
    private static Object getFieldValue(Object target, String fieldName) {
        try {
            return getFieldValueExact(target, fieldName);
        } catch (Throwable t) {
            // try 模糊匹配
        }
        // 按 ViewBinding 类型找（混淆后字段名可能是 a/b/z 等，但类型不变）。
        // 不能用 androidx.viewbinding.ViewBinding.class（模块编译期无此依赖），
        // 改为按运行时类名特征匹配 ViewBinding / ActivityUserBinding。
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                for (Field f : current.getDeclaredFields()) {
                    Class<?> ft = f.getType();
                    String n = ft.getName();
                    if ("androidx.viewbinding.ViewBinding".equals(n)
                            || n.endsWith("ViewBinding")) {
                        f.setAccessible(true);
                        return f.get(target);
                    }
                }
            } catch (Throwable ignored) {
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object getFieldValueExact(Object target, String fieldName)
            throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void patchUserCacheFieldsOnce(ClassLoader cl) {
        try {
            Class<?> userCache = Class.forName("com.bly.dkplat.cache.UserCache", false, cl);
            for (Method method : userCache.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                String name = method.getName();
                if (!"get".equals(name) && !"getInstance".equals(name)) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                } catch (Throwable ignored) {
                }
                Object instance = method.invoke(null);
                if (instance != null) {
                    patchVipFields(instance);
                }
                Log.i(TAG, "[XiaoX] lazy patch UserCache." + name + " instance="
                        + (instance != null));
            }
        } catch (Throwable t) {
            Log.w(TAG, "[XiaoX] lazy patch UserCache failed: " + t);
        }
    }

    private static void patchRuntimeVipCacheOnce(ClassLoader cl) {
        try {
            Class<?> cRuntime = Class.forName("com.bly.chaos.os.CRuntime", false, cl);
            setStaticBoolean(cRuntime, "f6809b", true);
        } catch (Throwable t) {
            Log.w(TAG, "[XiaoX] lazy patch CRuntime.f6809b failed: " + t);
        }
    }

    /** 反射改写 UserCache 单例上的 vip 字段。 */
    private static void patchVipFields(Object inst) {
        try {
            setField(inst, "vipType", VIP_LEVEL);
        } catch (Throwable ignored) {
        }
        try {
            setField(inst, "expiredTime", FAR_FUTURE_MS);
        } catch (Throwable ignored) {
        }
        try {
            setField(inst, "isVipShowAdd", Boolean.TRUE);
        } catch (Throwable ignored) {
        }
        try {
            setField(inst, "isShowAd", Boolean.FALSE);
        } catch (Throwable ignored) {
        }
        try {
            setField(inst, "isPayOn", Boolean.FALSE);
        } catch (Throwable ignored) {
        }
        try {
            setField(inst, "isPayTip", Boolean.FALSE);
        } catch (Throwable ignored) {
        }
        try {
            setField(inst, "isHaveFeedback", Boolean.TRUE);
        } catch (Throwable ignored) {
        }
        // 登录态：native 判定「未登录 = 普通用户」，伪造已登录
        try {
            setField(inst, "userId", 10001);
        } catch (Throwable ignored) {
        }
        try {
            setField(inst, "token", "fq-hook-token");
        } catch (Throwable ignored) {
        }
        try {
            setField(inst, "mobile", "13800138000");
        } catch (Throwable ignored) {
        }
        try {
            setIntField(inst, "isInitOk", 1); // boolean 字段
        } catch (Throwable ignored) {
        }
    }

    /** 反射写 int/boolean 字段（兼容两种类型）。 */
    private static void setIntField(Object target, String fieldName, int value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            if (field.getType() == boolean.class) {
                field.setBoolean(target, value != 0);
            } else if (field.getType() == int.class) {
                field.setInt(target, value);
            }
        } catch (Throwable t) {
            Log.w(TAG, "[XiaoX] setIntField " + fieldName + " failed: " + t);
        }
    }

    private static void setStaticBoolean(Class<?> clazz, String fieldName, boolean value) {
        try {
            Field field = findField(clazz, fieldName);
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == boolean.class) {
                field.setAccessible(true);
                field.setBoolean(null, value);
                Log.i(TAG, "[XiaoX] static " + clazz.getName() + "." + fieldName + " = "
                        + value);
            }
        } catch (Throwable t) {
            Log.w(TAG, "[XiaoX] setStaticBoolean " + fieldName + " failed: " + t);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Throwable t) {
            Log.w(TAG, "[XiaoX] setField " + fieldName + " failed: " + t);
        }
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    /**
     * Hook 数据刷新入口，调用完成后（after）把 UserCache 实例字段强制改回会员值。
     * 只 after 不 before，避免阻塞启动路径。
     */
    private static void hookAfterPatchFields(XposedModule module, Class<?> clazz,
                                             String methodName) {
        try {
            int hooked = 0;
            for (Method method : clazz.getDeclaredMethods()) {
                if (!methodName.equals(method.getName())) {
                    continue;
                }
                if (Modifier.isAbstract(method.getModifiers())) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                } catch (Throwable ignored) {
                }
                module.hook(method)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object r = chain.proceed();
                            Object inst = chain.getThisObject();
                            if (inst != null) {
                                patchVipFields(inst);
                            }
                            return r;
                        });
                hooked++;
            }
            Log.i(TAG, "[XiaoX] " + clazz.getName() + "#" + methodName
                    + " afterPatch hooks=" + hooked);
        } catch (Throwable t) {
            Log.w(TAG, "[XiaoX] hookAfterPatchFields " + methodName + " failed: " + t);
        }
    }

    /** Hook 所有名为 methodName 的实例方法，返回固定 boolean。 */
    private static void hookBool(XposedModule module, Class<?> clazz, String methodName,
                                 boolean result) {
        hookAll(module, clazz, methodName, result);
    }

    /** Hook 所有名为 methodName 的实例方法，返回固定值（int/long/boolean 装箱后统一处理）。 */
    private static void hookAll(XposedModule module, Class<?> clazz, String methodName,
                                Object result) {
        try {
            int hooked = 0;
            for (Method method : clazz.getDeclaredMethods()) {
                if (!methodName.equals(method.getName())) {
                    continue;
                }
                if (Modifier.isAbstract(method.getModifiers())) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                } catch (Throwable ignored) {
                }
                hook(module, method, result);
                hooked++;
            }
            Log.i(TAG, "[XiaoX] " + clazz.getName() + "#" + methodName
                    + " hooks=" + hooked);
        } catch (Throwable t) {
            Log.w(TAG, "[XiaoX] hook " + clazz.getName() + "#" + methodName + " failed: " + t);
        }
    }

    /**
     * 仿 HookInit 的既有写法（已验证可编译）：{@code hook(member).setExceptionMode(PROTECTIVE)
     * .intercept(...)}。PROTECTIVE 模式下目标方法自身抛异常不影响本 hook 链；
     * 返回值由 native (vmppro) 实际计算完成后覆盖。
     */
    private static void hook(XposedModule module, Method method, Object result) {
        module.hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> result);
    }

    private static void showHookSuccessToast() {
        if (!TOAST_SHOWN.compareAndSet(false, true)) {
            return;
        }
        try {
            com.byterax.phoenix.read.InjectionToast.showOnce("\u5c0fX\u5206\u8eab VIP Hook \u6210\u529f");
        } catch (Throwable ignored) {
        }
    }
}
