package com.byterax.dragon.read;

import android.annotation.SuppressLint;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * 番茄免费小说（{@code com.dragon.read}）hook 入口。
 *
 * <p>还原 HookVip <b>v4.1.6</b> 中 {@code top.hookvip.pro.core.C1078.mo656()} 注册的全部 hook 挂载点
 * （分析报告：{@code Anti/HookVip/com.dragon.read-hook-analysis.md}，回调分发器
 * {@code C1992} 的 case 12–16 属于番茄小说）。目标方法大多在番茄小说被混淆，故用 DexKit 按
 * "方法名 / 方法体内引用字符串 / 字段类型"特征签名定位，实现"全版本通杀"。
 *
 * <p>下表把本模块的 6 条 hook 与 HookVip v4.1.6 的注册流程一一对应。每行的"定位特征"即 v4.1.6
 * {@code C0827(int)} DexKit 查询构建器（经委托链）解析到的最终签名，已通过解密 v4.1.6 的
 * AES-CBC+XOR 字符串常量逐条核对一致：
 *
 * <table>
 *   <tr><th>#</th><th>目标</th><th>定位特征（= v4.1.6 C0827 最终签名）</th><th>行为</th><th>v4.1.6 回调</th></tr>
 *   <tr><td>①</td><td>关闭 Lynx 横幅广告</td><td>name == "willShowLynxBanner"（C1078 先调 C0827(19)→case19→m2229→case11）</td><td>返回 false</td><td>—（注册前 deoptimize 标记，C0827(19)）</td></tr>
 *   <tr><td>②</td><td>解锁会员</td><td>com.dragon.read.user.model.VipInfoModel 全部构造函数</td><td>篡改构造参数</td><td>C1992 case 12</td></tr>
 *   <tr><td>④</td><td>伪造关注/粉丝/获赞</td><td>引用 "followUserNum = %d, fansNum = %d, ..."（C0827(17)→case17→m2227→case12→m2223）</td><td>before 改字段</td><td>C1992 case 13</td></tr>
 *   <tr><td>⑤a</td><td>伪造昵称/头像（同步入口）</td><td>引用 "doSyncInitUserInfo:%s"（C0827(18)→case18→m2228→case13→m2224）</td><td>before 改字段</td><td>C1992 case 14</td></tr>
 *   <tr><td>⑤b</td><td>伪造昵称/头像（评论用户）</td><td>含 com.dragon.read.rpc.model.CommentUserStrInfo 字段的类（C0355(2) 字段类型查询，该类全部方法）</td><td>before 改字段</td><td>C1992 case 15</td></tr>
 *   <tr><td>③</td><td>屏蔽个人页推广广告位</td><td>name == "canThisPositionShow"（C1078 step3：C0827(10)→m2225 命名匹配）</td><td>after：返回对象 leftTime 拉满、extraInfo←新建 VipPromotionStrategyExtraInfo、text 清空</td><td>C0826(5)=m2202 + C0068.m711</td></tr>
 *   <tr><td>⑥</td><td>清空推荐用户</td><td>引用 "获取推荐用户数据成功"（C0827(20)→case20→m2230→case21→m2231）</td><td>before args[0] = null</td><td>C1992 case 16</td></tr>
 * </table>
 *
 * <p>字段级核对（解密自 C1992.m457x）：case 13 写 {@code followUserNum/fansNum/recvDiggNum}=
 * 5200000/13140000/9990000；case 14 写 {@code userName/avatarUrl}；case 15 写
 * {@code userName/userAvatar}。本模块 {@link #FAKE_FOLLOW_NUM}/{@link #FAKE_FANS_NUM}/
 * {@link #FAKE_DIGG_NUM} 与上述社交数值一致；昵称/头像用本模块自有占位值替代 v4.1.6 的品牌定制串。
 */
public class HookInit extends XposedModule {
    private static final String TAG = "FanQieHook";
    private static final String TARGET_PACKAGE = "com.dragon.read";
    private static final AtomicBoolean TOAST_SHOWN = new AtomicBoolean(false);

    // —— 关键常量（魔法数字，源自 HookVip 报告）——
    // 113143670061000L 毫秒 ≈ 5355 年。原始 hook 把构造参数 expireTime/leftTime 设为该值的秒级形式。
    private static final long FAKE_EXPIRE_MS = 113143670061000L;
    private static final String FAKE_EXPIRE_SEC = String.valueOf(FAKE_EXPIRE_MS / 1000L);

    // 个人页社交数据炫耀值（报告 §1）
    private static final int FAKE_FOLLOW_NUM = 5200000;
    private static final int FAKE_FANS_NUM = 13140000;
    private static final int FAKE_DIGG_NUM = 9990000;

    // hook ⑤ 伪造的昵称/头像。HookVip 原用其自带的 l90.x()/l90.s() 品牌定制字符串，
    // 这里用本模块自有占位值替代（属装饰性，非功能关键）。
    private static final String FAKE_USER_NAME = "番茄VIP用户";
    private static final String FAKE_AVATAR_URL = "https://p3-pc.douyinpic.com/img/fanqie-hook-avatar-placeholder.png";

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        installAllHooks(param.getClassLoader());
    }

    private void installAllHooks(ClassLoader classLoader) {
        // 每条 hook 独立 try/catch：一条失败不影响其余。DexKit 桥用于 ①③④⑤⑥（②靠具名类反射即可）。
        DexKitBridge dexKit = null;
        try {
            dexKit = createDexKitBridge(classLoader);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to init DexKit bridge", t);
        }

        safeHook("VipInfoModel (解锁会员)", () -> hookVipModel(classLoader));
        if (dexKit != null) {
            final DexKitBridge bridge = dexKit;
            safeHook("willShowLynxBanner (关闭横幅广告)", () -> hookLynxBanner(bridge, classLoader));
            safeHook("canThisPositionShow (屏蔽推广广告位)", () -> hookAdPosition(bridge, classLoader));
            safeHook("社交数据伪造", () -> hookSocialStats(bridge, classLoader));
            safeHook("昵称/头像伪造", () -> hookUserNameAvatar(bridge, classLoader));
            safeHook("推荐用户清空", () -> hookRecommendUsers(bridge, classLoader));
        }
        log(Log.INFO, TAG, "FanQieHook install finished");
    }

    // ============================ Hook ② —— 解锁会员（核心）============================

    /**
     * 对 {@code com.dragon.read.user.model.VipInfoModel} 的全部构造函数挂 hook：篡改构造参数。
     * 忠于报告 g5.f()：固定 expireTime/leftTime = 113143670061、isVip="1"，再把所有 boolean
     * 参数置 true、所有 int 参数置 1000000。
     */
    private void hookVipModel(ClassLoader classLoader) throws Throwable {
        Class<?> vipInfoModelClass = Class.forName(
                "com.dragon.read.user.model.VipInfoModel", false, classLoader);

        for (Constructor<?> ctor : vipInfoModelClass.getDeclaredConstructors()) {
            ctor.setAccessible(true);
            final Class<?>[] paramTypes = ctor.getParameterTypes();
            hook(ctor)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object[] args = chain.getArgs().toArray();
                        // 仅当至少有 3 个参数时按位置改写（报告的前三个参数语义）。
                        if (args.length >= 3) {
                            args[0] = FAKE_EXPIRE_SEC; // expireTime
                            args[1] = "1";             // isVip
                            args[2] = FAKE_EXPIRE_SEC; // leftTime
                        }
                        // 按类型批量改写：所有 boolean→true，所有 int→1000000（额度/次数类配额拉满）。
                        for (int i = 0; i < args.length && i < paramTypes.length; i++) {
                            Class<?> t = paramTypes[i];
                            if (t == boolean.class) {
                                args[i] = Boolean.TRUE;
                            } else if (t == int.class) {
                                args[i] = 1000000;
                            }
                        }
                        showHookSuccessToast();
                        return chain.proceed(args);
                    });
        }
        log(Log.INFO, TAG, "VipInfoModel hooks installed");
    }

    // ============================ Hook ① —— 关闭 Lynx 横幅广告 ============================

    private void hookLynxBanner(DexKitBridge bridge, ClassLoader classLoader) throws Throwable {
        Method method = findFirstNonAbstractMethodByName(bridge, classLoader, "willShowLynxBanner");
        if (method == null) {
            log(Log.WARN, TAG, "willShowLynxBanner not found");
            return;
        }
        hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> Boolean.FALSE); // 永不展示 Lynx 横幅广告
        log(Log.INFO, TAG, "willShowLynxBanner -> false installed");
    }

    // ============================ Hook ③ —— 屏蔽个人页推广广告位 ============================

    /**
     * 对 {@code canThisPositionShow} 挂 after 回调（v4.1.6 {@code C1078.mo656} step3：
     * {@code C0827(10)}→m2225 命名匹配 → {@code m3233}(after-hook) + {@code C0826(5)=m2202}）：
     * 当 {@code args[0]=="PromotionFromUserPage" && args[1]==true} 时，把返回对象的
     * {@code leftTime} 拉满、{@code extraInfo} 指向一个新建的
     * {@code com.dragon.read.rpc.model.VipPromotionStrategyExtraInfo}（其 {@code leftTime} 同样拉满）、
     * {@code text} 清空，等效屏蔽该页面推广广告。详见 {@link #patchPromotionResult}。
     */
    private void hookAdPosition(DexKitBridge bridge, ClassLoader classLoader) throws Throwable {
        Method method = findFirstNonAbstractMethodByName(bridge, classLoader, "canThisPositionShow");
        if (method == null) {
            log(Log.WARN, TAG, "canThisPositionShow not found");
            return;
        }
        hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    // canThisPositionShow(pageName, show, ...)：命中个人页推广位时处理其返回对象。
                    if (matchesPromotionPage(chain.getArgs())) {
                        patchPromotionResult(result, classLoader);
                    }
                    return result;
                });
        log(Log.INFO, TAG, "canThisPositionShow ad-shield installed");
    }

    private static boolean matchesPromotionPage(java.util.List<Object> args) {
        // args[0]=pageName, args[1]=是否展示（报告：pageName=="PromotionFromUserPage" && args[1]==true）。
        if (args.isEmpty() || !"PromotionFromUserPage".equals(args.get(0))) {
            return false;
        }
        return args.size() > 1 && Boolean.TRUE.equals(args.get(1));
    }

    private static void patchPromotionResult(Object result, ClassLoader classLoader) {
        if (result == null) {
            return;
        }
        try {
            // 忠于 v4.1.6 C0068.m711（由 C0826.m2202 在命中 PromotionFromUserPage 时触发）：
            //   1) 新建一个 com.dragon.read.rpc.model.VipPromotionStrategyExtraInfo（无参构造），
            //      把它的 leftTime 字段拉满；
            //   2) 把返回对象的 extraInfo 指向这个新对象（v4.1.6 的标记位）、leftTime 拉满、text 清空。
            // 原实现误把 extraInfo 指向 result 自身（自引用），既非 v4.1.6 行为也无标记语义，故修正。
            Object extraInfo = newExtraInfo(classLoader);
            if (extraInfo != null) {
                setField(extraInfo, "leftTime", FAKE_EXPIRE_MS / 1000L); // VipPromotionStrategyExtraInfo.leftTime
                setField(result, "extraInfo", extraInfo);                // 标记位（指向新建的 ExtraInfo）
            }
            setField(result, "leftTime", FAKE_EXPIRE_MS / 1000L); // 剩余时间拉满
            setField(result, "text", "");                         // 广告文案清空
        } catch (ReflectiveOperationException ignored) {
        }
    }

    /**
     * 反射新建一个 {@code com.dragon.read.rpc.model.VipPromotionStrategyExtraInfo}（无参构造）。
     * 与 v4.1.6 {@code C0826.m2202} 里 {@code AbstractC1392.m3216(Class.forName(name), new Object[0], ...)}
     * 等价：用目标进程 ClassLoader 加载该类、走无参构造、返回实例。类不存在时返回 null（降级为不写 extraInfo）。
     */
    private static Object newExtraInfo(ClassLoader classLoader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.dragon.read.rpc.model.VipPromotionStrategyExtraInfo", false, classLoader);
            return clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // ============================ Hook ④ —— 伪造关注/粉丝/获赞 ============================

    private void hookSocialStats(DexKitBridge bridge, ClassLoader classLoader) throws Throwable {
        Method method = findFirstMethodByUsingString(bridge, classLoader,
                "followUserNum = %d, fansNum = %d, recDiggNum = %d, ugcReadBookCount = ");
        if (method == null) {
            log(Log.WARN, TAG, "social stats method not found");
            return;
        }
        hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    // before：参数对象（args[0]）上的社交计数字段改成炫耀值。
                    Object[] args = chain.getArgs().toArray();
                    if (args.length > 0 && args[0] != null) {
                        try {
                            setIntField(args[0], "followUserNum", FAKE_FOLLOW_NUM);
                            setIntField(args[0], "fansNum", FAKE_FANS_NUM);
                            setIntField(args[0], "recvDiggNum", FAKE_DIGG_NUM);
                        } catch (ReflectiveOperationException ignored) {
                        }
                    }
                    return chain.proceed(args);
                });
        log(Log.INFO, TAG, "social stats forge installed");
    }

    // ============================ Hook ⑤ —— 伪造昵称/头像 =============================

    /**
     * 两条挂载点合并：
     * <ol>
     *   <li>引用 "doSyncInitUserInfo:%s" 的方法 → 改 userName/avatarUrl</li>
     *   <li>含字段 com.dragon.read.rpc.model.CommentUserStrInfo 的类上的方法 → 改 userName/userAvatar</li>
     * </ol>
     * 对每个命中方法挂 before 回调，把参数对象里的昵称/头像字段替换为占位值。
     */
    private void hookUserNameAvatar(DexKitBridge bridge, ClassLoader classLoader) throws Throwable {
        // (1) 用户信息同步方法
        Method syncMethod = findFirstMethodByUsingString(bridge, classLoader, "doSyncInitUserInfo:%s");
        if (syncMethod != null) {
            hookNameAvatar(syncMethod, "userName", "avatarUrl");
            log(Log.INFO, TAG, "doSyncInitUserInfo name/avatar forge installed");
        } else {
            log(Log.WARN, TAG, "doSyncInitUserInfo method not found");
        }

        // (2) 含 CommentUserStrInfo 字段的类上的方法（DexKit 按字段所属类匹配）
        try {
            Class<?> commentInfoClass = Class.forName(
                    "com.dragon.read.rpc.model.CommentUserStrInfo", false, classLoader);
            for (Class<?> holder : findClassesWithFieldType(bridge, commentInfoClass, classLoader)) {
                for (Method m : holder.getDeclaredMethods()) {
                    // 仅 hook 取该对象为参数的方法（避免无差别 hook 静态初始化等）。
                    if (Modifier.isAbstract(m.getModifiers())) {
                        continue;
                    }
                    Class<?>[] ptypes = m.getParameterTypes();
                    for (Class<?> pt : ptypes) {
                        if (pt == holder) {
                            hookNameAvatar(m, "userName", "userAvatar");
                            break;
                        }
                    }
                }
            }
            log(Log.INFO, TAG, "CommentUserStrInfo name/avatar forge installed");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "CommentUserStrInfo hook skipped: " + t.getMessage());
        }
    }

    private void hookNameAvatar(Method method, String nameField, String avatarField) {
        hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object[] args = chain.getArgs().toArray();
                    for (Object arg : args) {
                        if (arg == null) {
                            continue;
                        }
                        try {
                            setField(arg, nameField, FAKE_USER_NAME);
                            setField(arg, avatarField, FAKE_AVATAR_URL);
                        } catch (ReflectiveOperationException ignored) {
                        }
                    }
                    return chain.proceed(args);
                });
    }

    // ============================ Hook ⑥ —— 清空推荐用户 =============================

    private void hookRecommendUsers(DexKitBridge bridge, ClassLoader classLoader) throws Throwable {
        Method method = findFirstMethodByUsingString(bridge, classLoader, "获取推荐用户数据成功");
        if (method == null) {
            log(Log.WARN, TAG, "recommend users method not found");
            return;
        }
        hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object[] args = chain.getArgs().toArray();
                    if (args.length > 0) {
                        args[0] = null; // 清空推荐用户列表数据 → 推荐流不展示
                    }
                    return chain.proceed(args);
                });
        log(Log.INFO, TAG, "recommend users clear installed");
    }

    // ============================ DexKit 辅助 =============================

    private DexKitBridge createDexKitBridge(ClassLoader classLoader) {
        // DexKit 2.x 不会自动加载 libdexkit.so —— 必须由调用方先 System.loadLibrary("dexkit")。
        // 否则 nativeInitDexKitByClassLoader 抛 UnsatisfiedLinkError（"is the library loaded?"）。
        // 在 Xposed 目标进程里加载模块自带的 native lib 有两条路径：
        //   1) System.loadLibrary("dexkit")：多数框架（LSPosed）会把模块 APK 的 lib 加入目标进程库搜索路径；
        //   2) 失败回退：从模块 APK 的 nativeLibraryDir 用绝对路径 System.load。
        ensureDexKitNativeLoaded();
        // create(ClassLoader, boolean)：直接用目标进程 ClassLoader 解析 dex，无需 APK 路径。
        return DexKitBridge.create(classLoader, true);
    }

    private static final AtomicBoolean DEXKIT_LIB_LOADED = new AtomicBoolean(false);

    private void ensureDexKitNativeLoaded() {
        if (DEXKIT_LIB_LOADED.get()) {
            return;
        }
        synchronized (DEXKIT_LIB_LOADED) {
            if (DEXKIT_LIB_LOADED.get()) {
                return;
            }
            try {
                System.loadLibrary("dexkit");
                DEXKIT_LIB_LOADED.set(true);
                log(Log.INFO, TAG, "libdexkit.so loaded via loadLibrary");
                return;
            } catch (UnsatisfiedLinkError primary) {
                // 回退：取模块 APK 的 nativeLibraryDir，按绝对路径加载本机库。
                try {
                    String dir = getModuleApplicationInfo().nativeLibraryDir;
                    System.load(dir + "/libdexkit.so");
                    DEXKIT_LIB_LOADED.set(true);
                    log(Log.INFO, TAG, "libdexkit.so loaded via absolute path: " + dir);
                } catch (Throwable fallback) {
                    log(Log.ERROR, TAG, "Failed to load libdexkit.so", fallback);
                    throw new UnsatisfiedLinkError(
                            "Could not load libdexkit.so: " + primary.getMessage());
                }
            }
        }
    }

    /** 按方法名定位，返回首个非抽象实现（报告 v10.u() 的 firstNonAbstractMethod 语义）。 */
    private Method findFirstNonAbstractMethodByName(DexKitBridge bridge, ClassLoader cl, String name)
            throws Throwable {
        FindMethod query = new FindMethod().matcher(new MethodMatcher().name(name));
        MethodDataList list = bridge.findMethod(query);
        for (MethodData data : list) {
            Member member = toMember(data, cl);
            if (member instanceof Method && !Modifier.isAbstract(((Method) member).getModifiers())) {
                return (Method) member;
            }
        }
        return null;
    }

    /** 按方法体内引用的字符串定位，返回首个匹配。 */
    private Method findFirstMethodByUsingString(DexKitBridge bridge, ClassLoader cl, String usingString)
            throws Throwable {
        FindMethod query = new FindMethod().matcher(
                new MethodMatcher().addUsingString(usingString));
        MethodDataList list = bridge.findMethod(query);
        for (MethodData data : list) {
            Member member = toMember(data, cl);
            if (member instanceof Method) {
                return (Method) member;
            }
        }
        return null;
    }

    /** 找出"含指定类型字段"的全部类（用于 hook ⑤′ 的 CommentUserStrInfo 持有类）。 */
    private java.util.List<Class<?>> findClassesWithFieldType(DexKitBridge bridge, Class<?> fieldType,
                                                              ClassLoader classLoader) {
        java.util.List<Class<?>> out = new java.util.ArrayList<>();
        try {
            org.luckypray.dexkit.query.FindClass query =
                    new org.luckypray.dexkit.query.FindClass().matcher(
                            new org.luckypray.dexkit.query.matchers.ClassMatcher()
                                    .fields(new org.luckypray.dexkit.query.matchers.FieldsMatcher()
                                            .addForType(fieldType)));
            for (org.luckypray.dexkit.result.ClassData cd : bridge.findClass(query)) {
                try {
                    out.add(cd.getInstance(classLoader));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "findClassesWithFieldType failed: " + t.getMessage());
        }
        return out;
    }

    private static Member toMember(MethodData data, ClassLoader classLoader) throws Throwable {
        if (data.isConstructor()) {
            return data.getConstructorInstance(classLoader);
        }
        return data.getMethodInstance(classLoader);
    }

    // ============================ 反射 / UI 辅助 =============================

    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private void safeHook(String name, ThrowingRunnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook [" + name + "] failed", t);
        }
    }

    private static void showHookSuccessToast() {
        if (!TOAST_SHOWN.compareAndSet(false, true)) {
            return;
        }
        Application application = getCurrentApplication();
        if (application == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(application, "番茄小说 VIP Hook 成功", Toast.LENGTH_SHORT).show());
    }

    private static Application getCurrentApplication() {
        try {
            @SuppressLint("PrivateApi")
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            @SuppressLint("DiscouragedPrivateApi")
            Object application = activityThreadClass
                    .getDeclaredMethod("currentApplication")
                    .invoke(null);
            if (application instanceof Application) {
                return (Application) application;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static void setField(Object target, String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setIntField(Object target, String fieldName, int value)
            throws ReflectiveOperationException {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
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
}
