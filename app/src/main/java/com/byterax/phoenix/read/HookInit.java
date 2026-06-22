package com.byterax.phoenix.read;

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

import com.byterax.phoenix.read.xposed.SystemBootstrap;
import com.byterax.phoenix.read.HookStatusFiles;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * Hook entry for ???? (com.phoenix.read) SVIP unlock + name forge.
 *
 * <p>???? internally reuses ???? (com.dragon.read) core classes ? verified via
 * dexdump on classes13.dex: VipInfoModel, VipInfo, VipCommonSubType all live under
 * com.dragon.read.*. VipInfoModel constructor signature is identical:
 * (String, String, String, boolean, boolean, int, boolean, VipCommonSubType)
 *  i.e. (expireTime, isVip, leftTime, isAutoCharge, isUnionVip, unionSource, isAdVip, subType).
 *
 * <p>This module loads those classes from the target process (com.phoenix.read) ClassLoader
 * and hooks them, equivalent to reusing the FanQieHook logic.
 */
public class HookInit extends XposedModule {
    private static final String TAG = "SquemaFQHook";

    // Target packages: ???? (full hook set) + ???? (SVIP + name only).
    // ???? internally reuses ??'s com.dragon.read.* core classes ? verified by
    // dexdump. The VipInfoModel constructor signature is identical:
    //   (String,String,String,boolean,boolean,int,boolean,VipCommonSubType)
    //  = (expireTime,isVip,leftTime,isAutoCharge,isUnionVip,unionSource,isAdVip,subType)
    private static final String PKG_FANQIE = "com.dragon.read";
    private static final String PKG_HONGGUO = "com.phoenix.read";

    private static final AtomicBoolean TOAST_SHOWN = new AtomicBoolean(false);

    // 113143670061000L ms ? 5355 years. Reported magic number from HookVip.
    private static final long FAKE_EXPIRE_MS = 113143670061000L;
    private static final String FAKE_EXPIRE_SEC = String.valueOf(FAKE_EXPIRE_MS / 1000L);

    // ??? (per user request). Avatar not hooked.
    private static final String FAKE_USER_NAME = "\u8c03\u6559\u53f8";
    private static final String FAKE_AVATAR_URL = "";

    // Social stats (used by ?? only ? ?? doesn't expose these methods/strings).
    private static final int FAKE_FOLLOW_NUM = 5200000;
    private static final int FAKE_FANS_NUM = 13140000;
    private static final int FAKE_DIGG_NUM = 9990000;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        Log.i(TAG, "onModuleLoaded process=" + param.getProcessName());
        HookStatusFiles.markSystemReady();
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        String pkg = param.getPackageName();
        if (PKG_FANQIE.equals(pkg)) {
            HookStatusFiles.markTargetHooked(pkg);
            installHooks(PKG_FANQIE, param.getClassLoader(), /*fullSet=*/ true);
        } else if (PKG_HONGGUO.equals(pkg)) {
            HookStatusFiles.markTargetHooked(pkg);
            installHooks(PKG_HONGGUO, param.getClassLoader(), /*fullSet=*/ false);
        }
    }

    /**
     * Install hooks for the given target package.
     *
     * <ul>
     *   <li>fullSet=true  ? ????: all 6 hooks (SVIP + ads + social + name)</li>
     *   <li>fullSet=false ? ????: SVIP unlock + name forge only
     *     (the other 4 hooks' target methods are not exposed in the short-video player UI)</li>
     * </ul>
     */
    private void installHooks(String pkg, ClassLoader classLoader, boolean fullSet) {
        DexKitBridge dexKit = null;
        try {
            dexKit = createDexKitBridge(classLoader);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to init DexKit bridge", t);
        }

        // Activation flag ? write a file the module app reads. This works even when
        // LSPosed's XposedService channel is unavailable (e.g. no LSPosed Manager
        // package installed). The hook process and the module app process are
        // different processes, so we use a public app-specific external file.
        writeActivationFlag(pkg, fullSet);

        // ? Unlock VIP ? works on both targets (VipInfoModel is reused by ??).
        safeHook("[" + pkg + "] VipInfoModel (unlock SVIP)", () -> hookVipModel(classLoader));

        if (fullSet) {
            // ???? only: 6-hook set
            if (dexKit != null) {
                final DexKitBridge bridge = dexKit;
                safeHook("[" + pkg + "] willShowLynxBanner (banner ad)", () -> hookLynxBanner(bridge, classLoader));
                safeHook("[" + pkg + "] canThisPositionShow (ad slot)", () -> hookAdPosition(bridge, classLoader));
                safeHook("[" + pkg + "] social stats forge", () -> hookSocialStats(bridge, classLoader));
                safeHook("[" + pkg + "] name forge", () -> hookUserNameAvatar(bridge, classLoader));
                safeHook("[" + pkg + "] recommend users clear", () -> hookRecommendUsers(bridge, classLoader));
            }
        } else {
            // ???? only: name forge
            if (dexKit != null) {
                final DexKitBridge bridge = dexKit;
                safeHook("[" + pkg + "] name forge (\u8c03\u6559\u53f8)", () -> hookUserNameAvatar(bridge, classLoader));
            }
        }
        log(Log.INFO, TAG, "[" + pkg + "] install finished (fullSet=" + fullSet + ")");
        HookStatusReporter.reportTargetHooked(pkg);
        HookStatusFiles.markTargetHooked(pkg);
    }

    /**
     * Write an activation flag the module app can read. Triggered from
     * {@link #onPackageReady(XposedModuleInterface.PackageReadyParam)} so the
     * timestamp reflects the most recent framework load.
     *
     * <p>We can't use {@code Context.getExternalFilesDir()} from the hook process
     * (no Context) so we hardcode the well-known per-app external dir
     * {@code /sdcard/Android/data/<module-pkg>/files/}. No runtime permission
     * needed (API 24+). Falls back silently if the directory is not writable
     * (some ROMs block even the per-app dir on early boot).
     */
    private void writeActivationFlag(String pkg, boolean fullSet) {
        // NOTE: We run inside the *target* process (e.g. com.dragon.read),
        // not the module app. Any file we write lives in the target app's
        // UID, so the module app cannot read it across the UID boundary on
        // modern Android. This method is intentionally a logcat-only
        // diagnostic ? the timestamp is the source of truth for verifying
        // activation externally (see HookApp / MainActivity fallback).
        log(Log.INFO, TAG,
                "ACTIVATION pkg=" + pkg
                + " fullSet=" + fullSet
                + " ts=" + System.currentTimeMillis()
                + " framework=" + getFrameworkName()
                + " v" + getFrameworkVersion());
    }

    // ============================ Hook ? ? Unlock VIP (core) ============================

    /**
     * Hook all constructors of {@code com.dragon.read.user.model.VipInfoModel}: rewrite
     * constructor args so expireTime/leftTime ? 5355 years, isVip="1", all booleans=true,
     * all ints=1000000.
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
                        if (args.length >= 3) {
                            args[0] = FAKE_EXPIRE_SEC; // expireTime
                            args[1] = "1";             // isVip
                            args[2] = FAKE_EXPIRE_SEC; // leftTime
                        }
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

    // ============================ Hook ? ? Forge name only =============================

    /**
     * Two mount points combined (avatar removed per user request):
     * <ol>
     *   <li>Method referencing "doSyncInitUserInfo:%s" ? set userName (avatar not touched)</li>
     *   <li>Classes holding a field of type com.dragon.read.rpc.model.CommentUserStrInfo
     *       ? set userName (avatar not touched)</li>
     * </ol>
     */
    private void hookUserNameAvatar(DexKitBridge bridge, ClassLoader classLoader) throws Throwable {
        // (1) User info sync method
        Method syncMethod = findFirstMethodByUsingString(bridge, classLoader, "doSyncInitUserInfo:%s");
        if (syncMethod != null) {
            hookName(syncMethod, "userName");
            log(Log.INFO, TAG, "doSyncInitUserInfo name forge installed");
        } else {
            log(Log.WARN, TAG, "doSyncInitUserInfo method not found");
        }

        // (2) Classes holding a CommentUserStrInfo field ? DexKit field-type matcher.
        try {
            Class<?> commentInfoClass = Class.forName(
                    "com.dragon.read.rpc.model.CommentUserStrInfo", false, classLoader);
            for (Class<?> holder : findClassesWithFieldType(bridge, commentInfoClass, classLoader)) {
                for (Method m : holder.getDeclaredMethods()) {
                    if (Modifier.isAbstract(m.getModifiers())) {
                        continue;
                    }
                    Class<?>[] ptypes = m.getParameterTypes();
                    for (Class<?> pt : ptypes) {
                        if (pt == holder) {
                            hookName(m, "userName");
                            break;
                        }
                    }
                }
            }
            log(Log.INFO, TAG, "CommentUserStrInfo name forge installed");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "CommentUserStrInfo hook skipped: " + t.getMessage());
        }
    }

    private void hookName(Method method, String nameField) {
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
                        } catch (ReflectiveOperationException ignored) {
                        }
                    }
                    return chain.proceed(args);
                });
    }

    // ============================ Hook ? ? close Lynx banner ad (fanqie only) ============================

    private void hookLynxBanner(DexKitBridge bridge, ClassLoader classLoader) throws Throwable {
        Method method = findFirstNonAbstractMethodByName(bridge, classLoader, "willShowLynxBanner");
        if (method == null) {
            log(Log.WARN, TAG, "willShowLynxBanner not found");
            return;
        }
        hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> Boolean.FALSE); // never show Lynx banner
        log(Log.INFO, TAG, "willShowLynxBanner -> false installed");
    }

    // ============================ Hook ? ? shield user-page promo ad slot (fanqie only) ============================

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
                    if (matchesPromotionPage(chain.getArgs())) {
                        patchPromotionResult(result, classLoader);
                    }
                    return result;
                });
        log(Log.INFO, TAG, "canThisPositionShow ad-shield installed");
    }

    private static boolean matchesPromotionPage(java.util.List<Object> args) {
        // args[0]=pageName, args[1]=shouldShow. Match: pageName=="PromotionFromUserPage" && args[1]==true.
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
            Object extraInfo = newExtraInfo(classLoader);
            if (extraInfo != null) {
                setField(extraInfo, "leftTime", FAKE_EXPIRE_MS / 1000L);
                setField(result, "extraInfo", extraInfo);
            }
            setField(result, "leftTime", FAKE_EXPIRE_MS / 1000L);
            setField(result, "text", "");
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Object newExtraInfo(ClassLoader classLoader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.dragon.read.rpc.model.VipPromotionStrategyExtraInfo", false, classLoader);
            return clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // ============================ Hook ? ? forge social stats (fanqie only) ============================

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

    // ============================ Hook ? ? clear recommend users (fanqie only) ============================

    private void hookRecommendUsers(DexKitBridge bridge, ClassLoader classLoader) throws Throwable {
        Method method = findFirstMethodByUsingString(bridge, classLoader, "\u83b7\u53d6\u63a8\u8350\u7528\u6237\u6570\u636e\u6210\u529f");
        if (method == null) {
            log(Log.WARN, TAG, "recommend users method not found");
            return;
        }
        hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object[] args = chain.getArgs().toArray();
                    if (args.length > 0) {
                        args[0] = null;
                    }
                    return chain.proceed(args);
                });
        log(Log.INFO, TAG, "recommend users clear installed");
    }

    // ============================ DexKit helpers =============================

    private DexKitBridge createDexKitBridge(ClassLoader classLoader) {
        // DexKit 2.x does not auto-load libdexkit.so ? must call System.loadLibrary("dexkit")
        // first, otherwise nativeInitDexKitByClassLoader throws UnsatisfiedLinkError.
        // Two paths to load the native lib in the Xposed target process:
        //   1) System.loadLibrary("dexkit") ? most frameworks (LSPosed) add the module
        //      APK's lib/ to the target process's library search path.
        //   2) Fallback: System.load with absolute path from the module APK's nativeLibraryDir.
        ensureDexKitNativeLoaded();
        // create(ClassLoader, boolean) uses the target process ClassLoader to resolve dex
        // directly ? no APK path needed.
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

    /** Locate by method name; return the first non-abstract implementation. */
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

    /** Locate by string referenced in method body; return first match. */
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

    /** Find all classes that hold a field of the given type. */
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

    // ============================ Reflection / UI helpers =============================

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
                Toast.makeText(application, "\u756a\u8304\u7ea2\u679c VIP Hook \u6210\u529f", Toast.LENGTH_SHORT).show());
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
