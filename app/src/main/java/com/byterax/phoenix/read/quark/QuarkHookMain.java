package com.byterax.phoenix.read.quark;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/* JADX INFO: loaded from: /private/tmp/quark_apk/unpacked/classes.dex */
public class QuarkHookMain {
    // Mirrors Config.TAG (which is private to that class) so this class can use
    // the same tag for android.util.Log calls. Single source of truth lives in
    // Config; update both if you change the logcat tag for this feature.
    private static final String TAG = "SquemaQuark";
    private static final String TARGET = "com.quark.browser";
    private static volatile Context sAppContext;
    private static final AtomicBoolean TICKER_STARTED = new AtomicBoolean(false);
    private static final java.util.Set<Integer> INSTALLED_LOADERS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Boolean> sInVideoLoginGate = new ThreadLocal<Boolean>() { // from class: com.quark.bypass.QuarkHookMain.1
        /* JADX INFO: Access modifiers changed from: protected */
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // java.lang.ThreadLocal
        public Boolean initialValue() {
            return Boolean.FALSE;
        }
    };
    private static final String[] LOCKED_KEYWORDS = {"guide", "home_navigation", "navigation_setting", "navi_more", "more_navigation", "navigation_switch", "bottom_msg", "notification", "msg_setting", "ai_task", "screenshot", "push", "novel_mode", "comics_mode", "netdisk", "pan_entry", "integrate", "home_toolbar", "recent_navi", "ai_navi", "auto_wake", "smart_protect", "toolbar_menu_style"};
    private static final String[] NAVI_BLOCK_TITLES = {"书城", "小说", "书旗", "阅读", "生成创作", "高效办公", "学习教育", "个性解读", "边界生活", "AI写作", "Ai写作", "ai写作", "扫描王", "夸克PPT", "夸克文档", "学习", "夸克高考", "夸克学习", "夸克日报", "日报"};
    private static final String[] NAVI_BLOCK_BIZ = {"novel", "bookstore", "shuqi", "book", "ai_tool", "office", "study", "learn", "xuexi", "saomiao", "aippt", "ppt", "gaokao", "wps", "excel", "word", "wangpan"};

    /** Same install path as original {@code HookMain.handleLoadPackage}. */
    public static void installFromModern(XposedModule module, ClassLoader classLoader) {
        installFromModern(module, classLoader, "modern");
    }

    public static void installFromModern(XposedModule module, ClassLoader classLoader, String via) {
        QuarkHooks.bind(module);
        new QuarkHookMain().install(classLoader, via);
    }

    private void install(ClassLoader classLoader, String via) {
        if (classLoader == null) {
            QuarkHooks.log("[SquemaQuark] skip install via=" + via + " reason=nullClassLoader");
            return;
        }
        if (!INSTALLED_LOADERS.add(System.identityHashCode(classLoader))) {
            QuarkHooks.log("[SquemaQuark] already installed loader via=" + via);
            return;
        }
        // Read the same cfg as original. Do not rewrite it from the Quark
        // process — original HookMain never did, and a failed write can leave
        // an empty file that turns every flag into false.
        QuarkHooks.log("[SquemaQuark] hooking com.quark.browser via=" + via
                + " enable=" + Config.masterEnabled()
                + " loader=" + classLoader.getClass().getName());
        android.util.Log.i(TAG, "hooking com.quark.browser via=" + via);
        Config.logI("QuarkHook", "模块已加载，开始 hook com.quark.browser via=" + via);
        hookVoid(classLoader, "com.ucpro.feature.urlsecurity.UrlScanManager", "u");
        hookFalse(classLoader, "com.ucpro.feature.urlsecurity.UrlScanManager", "p");
        hookFalse(classLoader, "com.ucpro.feature.webwindow.webview.qualitydetect.c", "e");
        hookVoid(classLoader, "com.ucpro.feature.webwindow.nezha.plugin.f0", "f");
        hookMissileDisable(classLoader);
        hookMslHandlers(classLoader);
        hookSettingLock(classLoader);
        hookNavigationFilter(classLoader);
        hookHomeToolbarFilter(classLoader);
        hookNaviMoreFilter(classLoader);
        hookUserCenterAdFilter(classLoader);
        hookSnifferBarFilter(classLoader);
        hookBlockUpdate(classLoader);
        hookUnlockAv(classLoader);
        hookSummerTaskFilter(classLoader);
        hookActiveMarker(classLoader);
        if (TICKER_STARTED.compareAndSet(false, true)) {
            startActiveStampTicker();
        }
        try {
            com.byterax.phoenix.read.HookStatusReporter.reportTargetHooked(TARGET);
        } catch (Throwable ignored) {
        }
        // Toast is shown after Application.attach (same timing as original active mark).
        QuarkHooks.log("[SquemaQuark] hooks installed via=" + via);
        Config.logI("SquemaQuark", "所有 hook 安装完成 via=" + via);
    }

    private void hookActiveMarker(ClassLoader classLoader) {
        try {
            QuarkHooks.hookAllMethods(Application.class, "attach", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.2
                protected void afterHookedMethod(QuarkHooks.Param methodHookParam) {
                    try {
                        if (methodHookParam.thisObject instanceof Context) {
                            Context unused = QuarkHookMain.sAppContext = (Context) methodHookParam.thisObject;
                            Config.bindContext(QuarkHookMain.sAppContext);
                            QuarkHookMain.this.markActiveViaProvider();
                        }
                    } catch (Throwable unused2) {
                    }
                }
            });
            QuarkHooks.log("[SquemaQuark] active marker hooked (Application.attach)");
            tryMarkIfApplicationReady();
        } catch (Throwable th) {
            QuarkHooks.log("[SquemaQuark] active marker hook failed: " + th);
            tryMarkIfApplicationReady();
        }
    }

    private void tryMarkIfApplicationReady() {
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null);
            if (app instanceof Context) {
                sAppContext = (Context) app;
                Config.bindContext(sAppContext);
                markActiveViaProvider();
            }
        } catch (Throwable ignored) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX INFO: Access modifiers changed from: private */
    public void markActiveViaProvider() {
        Context context = sAppContext;
        if (context == null) {
            try {
                context = (Context) Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication")
                        .invoke(null);
                if (context != null) {
                    sAppContext = context;
                }
            } catch (Throwable ignored) {
            }
        }
        long now = System.currentTimeMillis();
        try {
            Config.writeActiveStamp(now);
        } catch (Throwable ignored) {
        }
        if (context != null) {
            try {
                context.getContentResolver().call(
                        Uri.parse("content://com.byterax.phoenix.read.quark"),
                        QuarkConfigProvider.METHOD_MARK, null, null);
            } catch (Throwable th) {
                QuarkHooks.log("[SquemaQuark] active provider mark failed: " + th);
            }
            try {
                com.byterax.phoenix.read.HookStatusReporter.reportTargetHooked(TARGET);
            } catch (Throwable ignored) {
            }
        }
    }

    private void startActiveStampTicker() {
        Thread thread = new Thread(new Runnable() { // from class: com.quark.bypass.QuarkHookMain.3
            @Override // java.lang.Runnable
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(60000L);
                        QuarkHookMain.this.markActiveViaProvider();
                    } catch (Throwable unused) {
                        return;
                    }
                }
            }
        }, "quarkhook-active-ticker");
        thread.setDaemon(true);
        thread.start();
    }

    private void hookVoid(ClassLoader classLoader, String str, String str2) {
        try {
            QuarkHooks.hookAllMethods(QuarkHooks.findClass(str, classLoader), str2, new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.4
                protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                    if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SECURITY)) {
                        methodHookParam.setResult((Object) null);
                    }
                }
            });
            QuarkHooks.log("[SquemaQuark] hooked " + str + "#" + str2);
        } catch (Throwable th) {
            QuarkHooks.log("[SquemaQuark] hook " + str + "#" + str2 + " failed: " + th);
        }
    }

    private void hookFalse(ClassLoader classLoader, String str, String str2) {
        try {
            QuarkHooks.hookAllMethods(QuarkHooks.findClass(str, classLoader), str2, new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.5
                protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                    if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SECURITY)) {
                        methodHookParam.setResult(false);
                    }
                }
            });
            QuarkHooks.log("[SquemaQuark] hooked " + str + "#" + str2);
        } catch (Throwable th) {
            QuarkHooks.log("[SquemaQuark] hook " + str + "#" + str2 + " failed: " + th);
        }
    }

    private void hookMissileDisable(final ClassLoader classLoader) {
        try {
            Class clsFindClass = QuarkHooks.findClass("com.uc.base.net.unet.impl.UnetEngineFactory$Builder", classLoader);
            final Method[] methodArr = new Method[1];
            final AtomicBoolean atomicBoolean = new AtomicBoolean(false);
            QuarkHooks.hookAllMethods(clsFindClass, "nativeInit", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.6
                protected void afterHookedMethod(QuarkHooks.Param methodHookParam) {
                    if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SECURITY) && atomicBoolean.compareAndSet(false, true)) {
                        try {
                            if (methodArr[0] == null) {
                                Method declaredMethod = QuarkHooks.findClass("com.alibaba.mbg.unet.internal.UNetSettingsJni", classLoader).getDeclaredMethod("native_set_missile_enable", Boolean.TYPE);
                                declaredMethod.setAccessible(true);
                                methodArr[0] = declaredMethod;
                            }
                            methodArr[0].invoke(null, false);
                            QuarkHooks.log("[SquemaQuark] missile disabled");
                        } catch (Throwable th) {
                            QuarkHooks.log("[SquemaQuark] missile disable failed: " + th);
                        }
                    }
                }
            });
        } catch (Throwable th) {
            QuarkHooks.log("[SquemaQuark] hook nativeInit failed: " + th);
        }
    }

    private void hookMslHandlers(ClassLoader classLoader) {
        try {
            QuarkHooks.findAndHookMethod("com.uc.base.net.unet.impl.c5", classLoader, "set", new Object[]{Long.TYPE, new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.7
                protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                    if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SECURITY)) {
                        methodHookParam.args[0] = 0L;
                    }
                }
            }});
            QuarkHooks.log("[SquemaQuark] hooked c5.set(policy=0)");
        } catch (Throwable th) {
            QuarkHooks.log("[SquemaQuark] hook c5 failed: " + th);
        }
        String[] strArr = {"com.uc.base.net.unet.impl.d5", "com.uc.base.net.unet.impl.e5"};
        for (int i = 0; i < 2; i++) {
            String str = strArr[i];
            try {
                QuarkHooks.findAndHookMethod(str, classLoader, "set", new Object[]{Object.class, new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.8
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SECURITY)) {
                            methodHookParam.setResult((Object) null);
                        }
                    }
                }});
                QuarkHooks.log("[SquemaQuark] hooked " + str + ".set");
            } catch (Throwable th2) {
                QuarkHooks.log("[SquemaQuark] hook " + str + " failed: " + th2);
            }
        }
    }

    private void hookSettingLock(ClassLoader classLoader) {
        try {
            Class clsFindClass = QuarkHooks.findClass("ik3.a", classLoader);
            QuarkHooks.hookAllMethods(clsFindClass, "a", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.9
                protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                    if (methodHookParam.args.length > 0) {
                        String strValueOf = String.valueOf(methodHookParam.args[0]);
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_LOCK_SETTINGS) && QuarkHookMain.isLockedKey(strValueOf)) {
                            QuarkHooks.log("[SquemaQuark] settings getBoolean locked: " + strValueOf);
                            methodHookParam.setResult(false);
                        }
                    }
                }
            });
            QuarkHooks.hookAllMethods(clsFindClass, "g", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.10
                protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                    if (methodHookParam.args.length > 1) {
                        String strValueOf = String.valueOf(methodHookParam.args[0]);
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_LOCK_SETTINGS) && QuarkHookMain.isLockedKey(strValueOf)) {
                            QuarkHooks.log("[SquemaQuark] settings putBoolean locked: " + strValueOf);
                            methodHookParam.args[1] = false;
                        }
                    }
                }
            });
            QuarkHooks.hookAllMethods(clsFindClass, "f", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.11
                protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                    if (methodHookParam.args.length > 0) {
                        String strValueOf = String.valueOf(methodHookParam.args[0]);
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_LOCK_SETTINGS) && QuarkHookMain.isLockedKey(strValueOf)) {
                            QuarkHooks.log("[SquemaQuark] settings getString locked: " + strValueOf);
                            methodHookParam.setResult(strValueOf.contains("toolbar_menu_style") ? "3" : "false");
                        }
                    }
                }
            });
            QuarkHooks.hookAllMethods(clsFindClass, "k", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.12
                protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                    if (methodHookParam.args.length > 1) {
                        String strValueOf = String.valueOf(methodHookParam.args[0]);
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_LOCK_SETTINGS) && QuarkHookMain.isLockedKey(strValueOf)) {
                            QuarkHooks.log("[SquemaQuark] settings putString locked: " + strValueOf);
                            methodHookParam.args[1] = strValueOf.contains("toolbar_menu_style") ? "3" : "false";
                        }
                    }
                }
            });
            QuarkHooks.log("[SquemaQuark] setting lock hooked (getBoolean/putBoolean/getString/putString)");
        } catch (Throwable th) {
            QuarkHooks.log("[SquemaQuark] setting lock hook failed: " + th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isLockedKey(String str) {
        if (str == null) {
            return false;
        }
        String lowerCase = str.toLowerCase();
        for (String str2 : LOCKED_KEYWORDS) {
            if (lowerCase.contains(str2)) {
                return true;
            }
        }
        return false;
    }

    private void hookNavigationFilter(ClassLoader classLoader) {
        String[] strArr = {"com.ucpro.feature.navigation.view.LauncherGridAdapter", "com.ucpro.feature.navigation.view.c", "com.ucpro.feature.navigation.view.u"};
        for (int i = 0; i < 3; i++) {
            String str = strArr[i];
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass(str, classLoader), "f", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.13
                    protected void afterHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled()) {
                            Object result = methodHookParam.getResult();
                            if (result instanceof List) {
                                QuarkHookMain.filterNavigationList((List) result);
                                methodHookParam.setResult(result);
                            }
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] navigation filter hooked (" + str + ".f)");
            } catch (Throwable th) {
                QuarkHooks.log("[SquemaQuark] navigation filter hook " + str + " failed: " + th);
            }
        }
    }

    private void hookHomeToolbarFilter(ClassLoader classLoader) {
        try {
                Class clsFindClass = QuarkHooks.findClass("com.ucpro.feature.webwindow.HomeToolbar", classLoader);
                QuarkHooks.hookAllMethods(clsFindClass, "e", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.14
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_BOOK) && methodHookParam.args.length > 0 && (methodHookParam.args[0] instanceof List)) {
                            QuarkHookMain.filterHomeToolbarList((List) methodHookParam.args[0]);
                        }
                    }
                });
                QuarkHooks.hookAllMethods(clsFindClass, "d", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.15
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_BOOK) && methodHookParam.args.length > 0 && "novel".equals(methodHookParam.args[0])) {
                            methodHookParam.setResult((Object) null);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] home toolbar filter hooked (HomeToolbar.e/d)");
            } catch (Throwable th) {
                QuarkHooks.log("[SquemaQuark] home toolbar filter hook failed: " + th);
            }
            try {
                QuarkHooks.hookAllConstructors(QuarkHooks.findClass("com.ucpro.feature.webwindow.NovelToolbarItemView", classLoader), new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.16

                    /* JADX INFO: renamed from: com.quark.bypass.QuarkHookMain$16$1, reason: invalid class name */
                    class AnonymousClass1 extends QuarkHooks.Hook {
                        AnonymousClass1() {
                        }

                        protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                            if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_NAVI) && methodHookParam.args.length > 0) {
                                String strValueOf = String.valueOf(methodHookParam.args[0]);
                                if (strValueOf.contains("user_center_bussiness_banner") || strValueOf.contains("user_center_welfare_farm") || strValueOf.contains("user_center_daily_fortune")) {
                                    methodHookParam.setResult("");
                                }
                            }
                        }
                    }

                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_BOOK)) {
                            methodHookParam.setResult((Object) null);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] novel toolbar item constructors hooked");
            } catch (Throwable th2) {
                QuarkHooks.log("[SquemaQuark] novel toolbar item hook failed: " + th2);
            }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void filterHomeToolbarList(List<?> list) {
        String str;
        StringBuilder sb = new StringBuilder("home toolbar tabs: ");
        Iterator<?> it = list.iterator();
        int i = 0;
        while (it.hasNext()) {
            Object next = it.next();
            if (next != null) {
                try {
                    str = (String) next.getClass().getMethod("f", new Class[0]).invoke(next, new Object[0]);
                } catch (Throwable unused) {
                    str = null;
                }
                sb.append(str);
                sb.append(" ");
                if ("choice".equals(str) || "novel".equals(str)) {
                    it.remove();
                    i++;
                }
            }
        }
        QuarkHooks.log("[SquemaQuark] " + sb.toString().trim() + " (removed=" + i + ")");
    }

    private void hookNaviMoreFilter(ClassLoader classLoader) {
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("qj2.a", classLoader), "H", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.17

                    /* JADX INFO: renamed from: com.quark.bypass.QuarkHookMain$17$1, reason: invalid class name */
                    class AnonymousClass1 extends QuarkHooks.Hook {
                        AnonymousClass1() {
                        }

                        protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                            if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_NAVI) && methodHookParam.args.length > 0) {
                                String strValueOf = String.valueOf(methodHookParam.args[0]);
                                if (strValueOf.contains("user_center_bussiness_banner") || strValueOf.contains("user_center_welfare_farm") || strValueOf.contains("user_center_daily_fortune")) {
                                    QuarkHooks.log("[SquemaQuark] cms json blocked: " + strValueOf);
                                    methodHookParam.setResult("");
                                }
                            }
                        }
                    }

                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        int iFilterBianjieItems;
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_NAVI) && methodHookParam.args.length > 0) {
                            int i = 0;
                            if (methodHookParam.args[0] instanceof List) {
                                Iterator it = ((List) methodHookParam.args[0]).iterator();
                                while (it.hasNext()) {
                                    Object next = it.next();
                                    String strSafeCall = QuarkHookMain.safeCall(next, "getCategory");
                                    String strSafeCall2 = QuarkHookMain.safeCall(next, "getKey");
                                    if (!QuarkHookMain.isNaviMoreBlockCate(strSafeCall, strSafeCall2)) {
                                        if (QuarkHookMain.isBianjieCate(strSafeCall, strSafeCall2) && (iFilterBianjieItems = QuarkHookMain.filterBianjieItems(next)) > 0) {
                                            QuarkHooks.log("[SquemaQuark] bianjie cate filtered " + iFilterBianjieItems + " items (keep 热搜/打电话/夸克健康)");
                                        }
                                    } else {
                                        it.remove();
                                        i++;
                                    }
                                }
                                QuarkHooks.log("[SquemaQuark] navi more cms filtered " + i + " cates (keep 便捷生活/home/mine)");
                            }
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] navi more filter hooked (qj2.a.H)");
            } catch (Throwable th) {
                QuarkHooks.log("[SquemaQuark] navi more filter hook failed: " + th);
            }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isBianjieCate(String str, String str2) {
        if (str != null && str.contains("便捷生活")) {
            return true;
        }
        if (str2 != null) {
            return str2.contains("bianjie") || str2.contains("convenient") || str2.contains("life");
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Code duplicated, block: B:31:0x0080  */
    public static int filterBianjieItems(Object obj) {
        try {
            Object objInvoke = obj.getClass().getMethod("getList", new Class[0]).invoke(obj, new Object[0]);
            if (!(objInvoke instanceof List)) {
                return 0;
            }
            Iterator it = ((List) objInvoke).iterator();
            int i = 0;
            while (it.hasNext()) {
                Object next = it.next();
                String itemTitle = readItemTitle(next);
                String strSafeCall = safeCall(next, "getKey");
                boolean z = true;
                boolean z2 = itemTitle != null && (itemTitle.contains("热搜") || itemTitle.contains("打电话") || itemTitle.contains("夸克健康"));
                if (strSafeCall != null) {
                    String lowerCase = strSafeCall.toLowerCase();
                    if (!lowerCase.contains("resou") && !lowerCase.contains("hot") && !lowerCase.contains("phone") && !lowerCase.contains("call") && !lowerCase.contains("health")) {
                        z = z2;
                    }
                } else {
                    z = z2;
                }
                if (!z) {
                    it.remove();
                    i++;
                }
            }
            return i;
        } catch (Throwable unused) {
            return 0;
        }
    }

    private static String readItemTitle(Object obj) {
        if (obj == null) {
            return null;
        }
        String[] strArr = {"getTitle", "getName", "getText", "getTitleName", "getDisplayTitle", "getShowName"};
        for (int i = 0; i < 6; i++) {
            try {
                Object objInvoke = obj.getClass().getMethod(strArr[i], new Class[0]).invoke(obj, new Object[0]);
                if (objInvoke != null && !String.valueOf(objInvoke).isEmpty()) {
                    return String.valueOf(objInvoke);
                }
            } catch (Throwable unused) {
            }
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isNaviMoreBlockCate(String str, String str2) {
        String[] strArr = {"生成创作", "高效办公", "学习教育", "个性解读", "边界生活"};
        for (int i = 0; i < 5; i++) {
            String str3 = strArr[i];
            if (str != null && str.contains(str3)) {
                return true;
            }
            if (str2 != null && str2.contains(str3)) {
                return true;
            }
        }
        return false;
    }

    private void hookUserCenterAdFilter(ClassLoader classLoader) {
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("com.ucpro.feature.deeplink.handler.a5", classLoader), "handle", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.18

                    /* JADX INFO: renamed from: com.quark.bypass.QuarkHookMain$18$1, reason: invalid class name */
                    class AnonymousClass1 extends QuarkHooks.Hook {
                        AnonymousClass1() {
                        }

                        protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                            if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_NAVI) && methodHookParam.args.length > 0) {
                                String strValueOf = String.valueOf(methodHookParam.args[0]);
                                if (strValueOf.contains("user_center") || strValueOf.contains("UserCenter")) {
                                    QuarkHooks.log("[SquemaQuark] cms read blocked: " + methodHookParam.method.getName() + " key=" + strValueOf);
                                    methodHookParam.setResult((Object) null);
                                }
                            }
                        }
                    }

                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        String strValueOf;
                        try {
                            strValueOf = String.valueOf(QuarkHooks.callMethod(methodHookParam.args[0], "c", new Object[0]));
                        } catch (Throwable unused) {
                            strValueOf = "";
                        }
                        QuarkHooks.log("[SquemaQuark] a5.handle called, url=" + strValueOf);
                    }
                });
                QuarkHooks.log("[SquemaQuark] a5.handle debug hooked");
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("com.ucpro.feature.flutter.FlutterAppController", classLoader), "openFlutterApp", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.19

                    /* JADX INFO: renamed from: com.quark.bypass.QuarkHookMain$19$1, reason: invalid class name */
                    class AnonymousClass1 extends QuarkHooks.Hook {
                        AnonymousClass1() {
                        }

                        protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                            if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_USER_CENTER) && methodHookParam.args.length > 0) {
                                String strValueOf = String.valueOf(methodHookParam.args[0]);
                                if (strValueOf.contains("user_center") || strValueOf.contains("UserCenter")) {
                                    QuarkHooks.log("[SquemaQuark] cms read blocked: " + methodHookParam.method.getName() + " key=" + strValueOf);
                                    methodHookParam.setResult((Object) null);
                                }
                            }
                        }
                    }

                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (methodHookParam.args.length > 0) {
                            QuarkHooks.log("[SquemaQuark] openFlutterApp: " + String.valueOf(methodHookParam.args[0]));
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] openFlutterApp debug hooked");
            } catch (Throwable th) {
                QuarkHooks.log("[SquemaQuark] a5.handle debug hook failed: " + th);
            }
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("el3.a", classLoader), "c", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.20

                    /* JADX INFO: renamed from: com.quark.bypass.QuarkHookMain$20$1, reason: invalid class name */
                    class AnonymousClass1 extends QuarkHooks.Hook {
                        AnonymousClass1() {
                        }

                        protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                            if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_USER_CENTER) && methodHookParam.args.length > 0) {
                                String strValueOf = String.valueOf(methodHookParam.args[0]);
                                if (strValueOf.contains("user_center") || strValueOf.contains("UserCenter")) {
                                    QuarkHooks.log("[SquemaQuark] cms read blocked: " + methodHookParam.method.getName() + " key=" + strValueOf);
                                    methodHookParam.setResult((Object) null);
                                }
                            }
                        }
                    }

                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_USER_CENTER) && methodHookParam.args.length > 0) {
                            String strValueOf = String.valueOf(methodHookParam.args[0]);
                            if (strValueOf.contains("user_center")) {
                                QuarkHooks.log("[SquemaQuark] cms bool blocked: " + strValueOf);
                                methodHookParam.setResult(false);
                            }
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] user center CMS bool blocked (el3.a.c)");
            } catch (Throwable th2) {
                QuarkHooks.log("[SquemaQuark] user center CMS bool hook failed: " + th2);
            }
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("com.uc.sdk.cms.CMSService", classLoader), "getInstance", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.21
                    private volatile boolean hookedImpl = false;

                    protected void afterHookedMethod(QuarkHooks.Param methodHookParam) {
                        Object result;
                        if (!Config.masterEnabled() || !Config.isEnabled(Config.K_BLOCK_USER_CENTER) || this.hookedImpl || (result = methodHookParam.getResult()) == null) {
                            return;
                        }
                        try {
                            QuarkHooks.Hook xC_MethodHook = new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.21.1
                                protected void beforeHookedMethod(QuarkHooks.Param methodHookParam2) {
                                    if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_USER_CENTER) && methodHookParam2.args.length > 0) {
                                        String strValueOf = String.valueOf(methodHookParam2.args[0]);
                                        if (strValueOf.contains("user_center") || strValueOf.contains("UserCenter")) {
                                            QuarkHooks.log("[SquemaQuark] cms read blocked: " + methodHookParam2.method.getName() + " key=" + strValueOf);
                                            methodHookParam2.setResult((Object) null);
                                        }
                                    }
                                }
                            };
                            String[] strArr = {"getDataConfigJson", "getOriginCMSDataJson", "getCMSDataItem", "getDataConfig", "getMultiDataConfig", "getAllEffectiveCMSData"};
                            for (int i = 0; i < 6; i++) {
                                try {
                                    QuarkHooks.hookAllMethods(result.getClass(), strArr[i], xC_MethodHook);
                                } catch (Throwable unused) {
                                }
                            }
                            this.hookedImpl = true;
                            QuarkHooks.log("[SquemaQuark] user center CMS impl reads blocked (all methods)");
                        } catch (Throwable th3) {
                            QuarkHooks.log("[SquemaQuark] user center CMS impl hook failed: " + th3);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] user center CMS json hook armed (getInstance)");
            } catch (Throwable th3) {
                QuarkHooks.log("[SquemaQuark] user center CMS json hook failed: " + th3);
            }
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("t92.a", classLoader), "c", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.22
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_USER_CENTER) && methodHookParam.args.length > 0) {
                            String strValueOf = String.valueOf(methodHookParam.args[0]);
                            if (strValueOf.contains("w_user_center_welfare_farm") || strValueOf.contains("w_user_center_daily_fortune") || strValueOf.contains("UserCenterNovelShowStatus") || strValueOf.contains("w_user_center_agent_task")) {
                                methodHookParam.setResult("");
                            }
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] user center flutter prefs blocked (t92.a.c)");
            } catch (Throwable th4) {
                QuarkHooks.log("[SquemaQuark] user center flutter prefs hook failed: " + th4);
            }
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("com.ucpro.feature.flutter.plugin.common.CommonDataSourceCallHandler", classLoader), "onMethodCall", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.23
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        try {
                            Object obj = methodHookParam.args[0];
                            QuarkHooks.log("[SquemaQuark] flutter dataSource: method=" + ((String) QuarkHooks.getObjectField(obj, "method")) + " type=" + QuarkHooks.callMethod(obj, "argument", new Object[]{"type"}));
                        } catch (Throwable unused) {
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] flutter dataSource debug hooked");
            } catch (Throwable th5) {
                QuarkHooks.log("[SquemaQuark] flutter dataSource hook failed: " + th5);
            }
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("com.ucpro.feature.flutter.plugin.mtop.MTopPlugin", classLoader), "onMethodCall", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.24
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        Object obj;
                        try {
                            Object obj2 = methodHookParam.args[0];
                            String str = (String) QuarkHooks.getObjectField(obj2, "method");
                            Object objectField = QuarkHooks.getObjectField(obj2, "arguments");
                            String strValueOf = "";
                            try {
                                if ((objectField instanceof Map) && (obj = ((Map) objectField).get("url")) != null) {
                                    strValueOf = String.valueOf(obj);
                                }
                            } catch (Throwable unused) {
                            }
                            QuarkHooks.log("[SquemaQuark] mtop request: method=" + str + " url=" + strValueOf);
                        } catch (Throwable unused2) {
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] mtop request debug hooked (MTopPlugin.onMethodCall)");
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("com.ucpro.feature.flutter.plugin.mtop.core.MTRequest", classLoader), "create", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.25
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (methodHookParam.args.length > 0) {
                            QuarkHooks.log("[SquemaQuark] mtop MTRequest url=" + methodHookParam.args[0]);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] mtop request debug hooked (MTRequest.create)");
            } catch (Throwable th6) {
                QuarkHooks.log("[SquemaQuark] mtop request hook failed: " + th6);
            }
            try {
                Class clsFindClass = QuarkHooks.findClass("com.ucpro.feature.navigation.cms.model.o", classLoader);
                QuarkHooks.hookAllMethods(clsFindClass, "a", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.26
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_USER_CENTER) && methodHookParam.args.length > 0) {
                            String strValueOf = String.valueOf(methodHookParam.args[0]);
                            QuarkHooks.log("[SquemaQuark] cms o.a() key=" + strValueOf);
                            if (strValueOf.contains("user_center") || strValueOf.contains("UserCenter") || QuarkHookMain.isUserCenterBlockKey(strValueOf)) {
                                QuarkHooks.log("[SquemaQuark] cms o.a() blocked: " + strValueOf);
                                methodHookParam.setResult((Object) null);
                            }
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] cms model o.a() hooked (read blocked)");
                QuarkHooks.hookAllMethods(clsFindClass, "onMultiDataChanged", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.27
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_USER_CENTER) && methodHookParam.args.length > 0) {
                            String strValueOf = String.valueOf(methodHookParam.args[0]);
                            if (strValueOf.contains("user_center") || strValueOf.contains("UserCenter") || QuarkHookMain.isUserCenterBlockKey(strValueOf)) {
                                QuarkHooks.log("[SquemaQuark] cms onMultiDataChanged blocked: " + strValueOf);
                                methodHookParam.setResult((Object) null);
                            }
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] cms model o.onMultiDataChanged hooked (cache write blocked)");
            } catch (Throwable th7) {
                QuarkHooks.log("[SquemaQuark] cms model o hook failed: " + th7);
            }
            try {
                Class clsFindClass2 = QuarkHooks.findClass("io.flutter.plugins.sharedpreferences.MethodCallHandlerImpl", classLoader);
                QuarkHooks.hookAllMethods(clsFindClass2, "getAllPrefs", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.28
                    protected void afterHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_USER_CENTER)) {
                            Object result = methodHookParam.getResult();
                            if (result instanceof Map) {
                                int i = 0;
                                Map map = (Map) result;
                                for (Object obj : map.entrySet()) {
                                    Map.Entry entry = (Map.Entry) obj;
                                    if (QuarkHookMain.isUserCenterPrefsKey(String.valueOf(entry.getKey()))) {
                                        Object value = entry.getValue();
                                        if (value instanceof Boolean) {
                                            entry.setValue(Boolean.FALSE);
                                        } else if (value instanceof String) {
                                            entry.setValue("");
                                        } else if ((value instanceof Long) || (value instanceof Integer)) {
                                            entry.setValue(0L);
                                        }
                                        i++;
                                    }
                                }
                                if (i > 0) {
                                    QuarkHooks.log("[SquemaQuark] flutter prefs getAll zeroed " + i + " keys");
                                }
                            }
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] flutter shared_prefs getAll zeroed (getAllPrefs)");
                QuarkHooks.hookAllMethods(clsFindClass2, "onMethodCall", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.29
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_USER_CENTER)) {
                            try {
                                Object obj = methodHookParam.args[0];
                                String str = (String) QuarkHooks.getObjectField(obj, "method");
                                Object objCallMethod = QuarkHooks.callMethod(obj, "argument", new Object[]{"key"});
                                String strValueOf = objCallMethod == null ? "" : String.valueOf(objCallMethod);
                                if (QuarkHookMain.isUserCenterPrefsKey(strValueOf)) {
                                    QuarkHooks.log("[SquemaQuark] flutter prefs zeroed: method=" + str + " key=" + strValueOf);
                                    Object objectField = QuarkHooks.getObjectField(obj, "arguments");
                                    if (objectField instanceof Map) {
                                        Map map = (Map) objectField;
                                        if (str.equals("setBool")) {
                                            map.put("value", Boolean.FALSE);
                                            return;
                                        }
                                        if (!str.equals("setString") && !str.equals("setStringList")) {
                                            if (str.equals("setInt") || str.equals("setDouble")) {
                                                map.put("value", 0);
                                                return;
                                            }
                                            return;
                                        }
                                        map.put("value", "");
                                    }
                                }
                            } catch (Throwable unused) {
                            }
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] flutter shared_prefs write zeroed (onMethodCall)");
            } catch (Throwable th8) {
                QuarkHooks.log("[SquemaQuark] flutter shared_prefs hook failed: " + th8);
            }
    }

    private void hookSnifferBarFilter(ClassLoader classLoader) {
            try {
                Class clsFindClass = QuarkHooks.findClass("na2.c", classLoader);
                QuarkHooks.hookAllMethods(clsFindClass, "b", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.30
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SNIFF)) {
                            methodHookParam.setResult(false);
                        }
                    }
                });
                QuarkHooks.hookAllMethods(clsFindClass, "h", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.31
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SNIFF)) {
                            methodHookParam.setResult(false);
                        }
                    }
                });
                QuarkHooks.hookAllMethods(clsFindClass, "k", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.32
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SNIFF)) {
                            methodHookParam.setResult(false);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] sniffer bar switches hooked (na2.c b/h/k -> false)");
                QuarkHooks.Hook xC_MethodHook = new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.33
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SNIFF)) {
                            methodHookParam.setResult("");
                        }
                    }
                };
                String[] strArr = {"g", "d", "c", "f", "m", "l"};
                for (int i = 0; i < 6; i++) {
                    QuarkHooks.hookAllMethods(clsFindClass, strArr[i], xC_MethodHook);
                }
                QuarkHooks.log("[SquemaQuark] sniffer bar texts hooked (na2.c g/d/c/f/m/l -> \"\")");
            } catch (Throwable th) {
                QuarkHooks.log("[SquemaQuark] sniffer bar hook failed: " + th);
            }
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("com.ucpro.feature.video.player.manipulator.centermanipulator.PlayerCenterView", classLoader), "B", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.34
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SNIFF)) {
                            methodHookParam.setResult((Object) null);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] player center strong tips blocked (PlayerCenterView.B)");
            } catch (Throwable th2) {
                QuarkHooks.log("[SquemaQuark] player center strong tips hook failed: " + th2);
            }
            try {
                Class clsFindClass2 = QuarkHooks.findClass("com.ucpro.feature.video.cloudcms.buffer.BufferNewTipsData", classLoader);
                QuarkHooks.Hook xC_MethodHook2 = new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.35
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SNIFF)) {
                            methodHookParam.setResult("");
                        }
                    }
                };
                QuarkHooks.Hook xC_MethodHook3 = new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.36
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SNIFF)) {
                            methodHookParam.setResult(false);
                        }
                    }
                };
                QuarkHooks.Hook xC_MethodHook4 = new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.37
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SNIFF)) {
                            methodHookParam.setResult(0);
                        }
                    }
                };
                QuarkHooks.hookAllMethods(clsFindClass2, "getCloudSaveButtonText", xC_MethodHook2);
                QuarkHooks.hookAllMethods(clsFindClass2, "getShowSvipPlusTipsEnable", xC_MethodHook3);
                QuarkHooks.hookAllMethods(clsFindClass2, "isStyleA", xC_MethodHook3);
                QuarkHooks.hookAllMethods(clsFindClass2, "isStyleB", xC_MethodHook3);
                QuarkHooks.hookAllMethods(clsFindClass2, "isStyleNew", xC_MethodHook3);
                QuarkHooks.hookAllMethods(clsFindClass2, "getStyle", xC_MethodHook4);
                QuarkHooks.hookAllMethods(clsFindClass2, "getTipStyle", xC_MethodHook4);
                QuarkHooks.hookAllMethods(clsFindClass2, "getSvipPlusTipsEnable", xC_MethodHook4);
                QuarkHooks.hookAllMethods(clsFindClass2, "getSvipPlusTipsStart", xC_MethodHook4);
                QuarkHooks.log("[SquemaQuark] buffer new tips data zeroed (BufferNewTipsData)");
            } catch (Throwable th3) {
                QuarkHooks.log("[SquemaQuark] buffer new tips data hook failed: " + th3);
            }
            try {
                Class clsFindClass3 = QuarkHooks.findClass("com.ucpro.feature.video.cloudcms.buffer.AbsBufferStrongTipsData", classLoader);
                QuarkHooks.Hook xC_MethodHook5 = new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.38
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SNIFF)) {
                            methodHookParam.setResult("");
                        }
                    }
                };
                QuarkHooks.hookAllMethods(clsFindClass3, "getStrongTipsTitle", xC_MethodHook5);
                QuarkHooks.hookAllMethods(clsFindClass3, "getStrongTipsSubTitle", xC_MethodHook5);
                QuarkHooks.hookAllMethods(clsFindClass3, "getStrongTipsAction", xC_MethodHook5);
                QuarkHooks.log("[SquemaQuark] abs buffer strong tips texts zeroed");
            } catch (Throwable th4) {
                QuarkHooks.log("[SquemaQuark] abs buffer strong tips hook failed: " + th4);
            }
            try {
                Class clsFindClass4 = QuarkHooks.findClass("com.ucpro.feature.video.player.manipulator.centermanipulator.PlayerCenterView", classLoader);
                QuarkHooks.hookAllMethods(clsFindClass4, "I", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.39
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SNIFF)) {
                            methodHookParam.setResult((Object) null);
                        }
                    }
                });
                QuarkHooks.hookAllMethods(clsFindClass4, "A", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.40
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SNIFF)) {
                            methodHookParam.setResult((Object) null);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] precloud/entry tips blocked (PlayerCenterView.I/A)");
            } catch (Throwable th5) {
                QuarkHooks.log("[SquemaQuark] precloud/entry tips hook failed: " + th5);
            }
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("com.ucpro.feature.video.player.view.PreCloudLoadingView", classLoader), "a", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.41
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SNIFF)) {
                            if (methodHookParam.args.length > 0) {
                                methodHookParam.args[0] = "";
                            }
                            if (methodHookParam.args.length > 1) {
                                methodHookParam.args[1] = "";
                            }
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] PreCloudLoadingView.a texts zeroed (safe)");
                Class clsFindClass5 = QuarkHooks.findClass("com.ucpro.feature.video.player.manipulator.centermanipulator.PlayerCenterView", classLoader);
                QuarkHooks.hookAllMethods(clsFindClass5, "k", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.42
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SNIFF)) {
                            methodHookParam.setResult((Object) null);
                        }
                    }
                });
                QuarkHooks.hookAllMethods(clsFindClass5, "isPreCloudLoadingViewShow", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.43
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SNIFF)) {
                            methodHookParam.setResult(false);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] PlayerCenterView precloud state blocked (safe)");
                try {
                    QuarkHooks.hookAllMethods(QuarkHooks.findClass("com.ucpro.feature.video.player.manipulator.preshow.PreMiniManipulatorView", classLoader), "getPreCloudLoadingView", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.44
                        protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                            if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SNIFF)) {
                                methodHookParam.setResult((Object) null);
                            }
                        }
                    });
                    QuarkHooks.log("[SquemaQuark] PreMiniManipulatorView.getPreCloudLoadingView -> null");
                } catch (Throwable th6) {
                    QuarkHooks.log("[SquemaQuark] PreMiniManipulatorView hook failed: " + th6);
                }
                try {
                    Class clsFindClass6 = QuarkHooks.findClass("com.ucpro.feature.video.player.FunctionSwitch", classLoader);
                    QuarkHooks.hookAllMethods(clsFindClass6, "n", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.45
                        protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                            if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SNIFF)) {
                                methodHookParam.setResult(false);
                            }
                        }
                    });
                    QuarkHooks.hookAllMethods(clsFindClass6, "s", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.46
                        protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                            if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SNIFF)) {
                                methodHookParam.setResult(false);
                            }
                        }
                    });
                    QuarkHooks.log("[SquemaQuark] FunctionSwitch precloud toggles blocked (n/s -> false)");
                } catch (Throwable th7) {
                    QuarkHooks.log("[SquemaQuark] FunctionSwitch hook failed: " + th7);
                }
            } catch (Throwable th8) {
                QuarkHooks.log("[SquemaQuark] PreCloudLoadingView safe hook failed: " + th8);
            }
    }

    private void hookBlockUpdate(ClassLoader classLoader) {
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("com.ucpro.feature.upgrade.n", classLoader), "b", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.47
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_UPDATE)) {
                            QuarkHooks.log("[SquemaQuark] upgrade dialog blocked (upgrade.n.b)");
                            methodHookParam.setResult((Object) null);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] upgrade dialog blocked (upgrade.n.b)");
            } catch (Throwable th) {
                QuarkHooks.log("[SquemaQuark] upgrade dialog hook failed: " + th);
            }
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("com.ucpro.feature.upgrade.n", classLoader), "a", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.48
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_UPDATE)) {
                            QuarkHooks.log("[SquemaQuark] upgrade download blocked (upgrade.n.a)");
                            methodHookParam.setResult((Object) null);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] upgrade download blocked (upgrade.n.a)");
            } catch (Throwable th2) {
                QuarkHooks.log("[SquemaQuark] upgrade download hook failed: " + th2);
            }
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("com.ucpro.feature.upgrade.UpgradeController", classLoader), "onReceiveNewDataFromServer", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.49
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_UPDATE)) {
                            QuarkHooks.log("[SquemaQuark] upgrade server data dropped (UpgradeController)");
                            methodHookParam.setResult((Object) null);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] upgrade server data dropped (UpgradeController)");
            } catch (Throwable th3) {
                QuarkHooks.log("[SquemaQuark] upgrade controller hook failed: " + th3);
            }
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("com.uc.pars.ParsImpl", classLoader), "checkUpgrade", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.50
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_UPDATE)) {
                            QuarkHooks.log("[SquemaQuark] pars checkUpgrade blocked");
                            methodHookParam.setResult((Object) null);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] pars checkUpgrade blocked (ParsImpl)");
            } catch (Throwable th4) {
                QuarkHooks.log("[SquemaQuark] pars checkUpgrade hook failed: " + th4);
            }
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("com.uc.pars.upgrade.UpgradeService", classLoader), "upgradeBundles", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.51
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_UPDATE)) {
                            QuarkHooks.log("[SquemaQuark] pars upgradeBundles blocked");
                            methodHookParam.setResult((Object) null);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] pars upgradeBundles blocked (UpgradeService)");
            } catch (Throwable th5) {
                QuarkHooks.log("[SquemaQuark] pars upgradeBundles hook failed: " + th5);
            }
    }

    private void hookUnlockAv(ClassLoader classLoader) {
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("com.ucpro.feature.video.player.resolution.b", classLoader), "i", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.52
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_UNLOCK_AV)) {
                            methodHookParam.setResult(true);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] resolution unlocked (resolution.b.i -> true)");
            } catch (Throwable th) {
                QuarkHooks.log("[SquemaQuark] resolution unlock hook failed: " + th);
            }
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("com.ucpro.feature.clouddrive.member.MemberModel", classLoader), "w", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.53
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_UNLOCK_AV)) {
                            methodHookParam.setResult(true);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] svip check unlocked (MemberModel.w -> true)");
            } catch (Throwable th2) {
                QuarkHooks.log("[SquemaQuark] svip check hook failed: " + th2);
            }
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("com.ucpro.feature.clouddrive.member.MemberModel", classLoader), "A", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.54
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_UNLOCK_AV)) {
                            methodHookParam.setResult(true);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] vip check unlocked (MemberModel.A -> true)");
            } catch (Throwable th3) {
                QuarkHooks.log("[SquemaQuark] vip check hook failed: " + th3);
            }
            try {
                Class clsFindClass = QuarkHooks.findClass("com.ucpro.feature.clouddrive.member.MemberModel", classLoader);
                QuarkHooks.hookAllMethods(clsFindClass, "x", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.55
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_UNLOCK_AV)) {
                            methodHookParam.setResult(true);
                        }
                    }
                });
                QuarkHooks.hookAllMethods(clsFindClass, "v", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.56
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_UNLOCK_AV)) {
                            methodHookParam.setResult(true);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] zvip check unlocked (MemberModel.x/v -> true)");
            } catch (Throwable th4) {
                QuarkHooks.log("[SquemaQuark] zvip check hook failed: " + th4);
            }
            try {
                Class clsFindClass2 = QuarkHooks.findClass("com.quark.kmp.kmp_clouddrive.video.util.k1", classLoader);
                QuarkHooks.hookAllMethods(clsFindClass2, "f", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.57
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_UNLOCK_AV)) {
                            methodHookParam.setResult(false);
                        }
                    }
                });
                QuarkHooks.hookAllMethods(clsFindClass2, "e", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.58
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_UNLOCK_AV)) {
                            methodHookParam.setResult(false);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] kmp guest dialog blocked (k1.e/f -> false)");
            } catch (Throwable th5) {
                QuarkHooks.log("[SquemaQuark] kmp guest dialog hook failed: " + th5);
            }
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("com.ucpro.feature.video.y", classLoader), "run", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.59
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_UNLOCK_AV)) {
                            QuarkHookMain.sInVideoLoginGate.set(Boolean.TRUE);
                        }
                    }

                    protected void afterHookedMethod(QuarkHooks.Param methodHookParam) {
                        QuarkHookMain.sInVideoLoginGate.set(Boolean.FALSE);
                    }
                });
                QuarkHooks.log("[SquemaQuark] video login-gate marked (y.run -> isLogin=true in gate)");
            } catch (Throwable th6) {
                QuarkHooks.log("[SquemaQuark] video y.run hook failed: " + th6);
            }
            try {
                QuarkHooks.hookAllMethods(QuarkHooks.findClass("com.ucpro.feature.account.AccountManager", classLoader), "isLogin", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.60
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_UNLOCK_AV) && Boolean.TRUE.equals(QuarkHookMain.sInVideoLoginGate.get())) {
                            methodHookParam.setResult(Boolean.TRUE);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] account isLogin gate-hooked (video only)");
            } catch (Throwable th7) {
                QuarkHooks.log("[SquemaQuark] account isLogin hook failed: " + th7);
            }
    }

    private void hookSummerTaskFilter(ClassLoader classLoader) {
            try {
                Class clsFindClass = QuarkHooks.findClass("com.uc.application.novel.readerbusiness.opera.readtask.summer.manager.SummerVacationManager", classLoader);
                QuarkHooks.hookAllMethods(clsFindClass, "i", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.61
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SUMMER_TASK)) {
                            methodHookParam.setResult(false);
                        }
                    }
                });
                QuarkHooks.hookAllMethods(clsFindClass, "h", new QuarkHooks.Hook() { // from class: com.quark.bypass.QuarkHookMain.62
                    protected void beforeHookedMethod(QuarkHooks.Param methodHookParam) {
                        if (Config.masterEnabled() && Config.isEnabled(Config.K_BLOCK_SUMMER_TASK)) {
                            methodHookParam.setResult(false);
                        }
                    }
                });
                QuarkHooks.log("[SquemaQuark] summer task floating blocked (SummerVacationManager.h/i -> false)");
            } catch (Throwable th) {
                QuarkHooks.log("[SquemaQuark] summer task hook failed: " + th);
            }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isUserCenterBlockKey(String str) {
        if (str == null) {
            return false;
        }
        String lowerCase = str.toLowerCase();
        return lowerCase.contains("game") || lowerCase.contains("cloud_drive") || lowerCase.contains("clouddrive") || lowerCase.contains("wangpan") || lowerCase.contains("scan") || lowerCase.contains("novel") || lowerCase.contains("welfare") || lowerCase.contains("farm") || lowerCase.contains("banner") || lowerCase.contains("fortune");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean isUserCenterPrefsKey(String str) {
        if (str == null) {
            return false;
        }
        String lowerCase = str.toLowerCase();
        return lowerCase.contains("usercenter") || lowerCase.contains("user_center");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void filterNavigationList(List<?> list) {
        boolean zIsEnabled = Config.isEnabled(Config.K_BLOCK_BOOK);
        boolean zIsEnabled2 = Config.isEnabled(Config.K_BLOCK_NAVI);
        if (zIsEnabled || zIsEnabled2) {
            StringBuilder sb = new StringBuilder("nav items: ");
            for (Object obj : list) {
                if (obj != null) {
                    String strSafeCall = safeCall(obj, "getTitle");
                    String strSafeCall2 = safeCall(obj, "getDisplayTitle");
                    String strSafeCall3 = safeCall(obj, "getBizId");
                    if (strSafeCall3 == null || strSafeCall3.isEmpty()) {
                        try {
                            strSafeCall3 = safeCall(obj, "bizId");
                        } catch (Throwable unused) {
                        }
                    }
                    sb.append("[t=");
                    sb.append(strSafeCall);
                    sb.append(" d=");
                    sb.append(strSafeCall2);
                    sb.append(" b=");
                    sb.append(strSafeCall3);
                    sb.append("] ");
                }
            }
            QuarkHooks.log("[SquemaQuark] " + sb.toString().trim());
            Iterator<?> it = list.iterator();
            int i = 0;
            while (it.hasNext()) {
                Object next = it.next();
                if (next != null) {
                    String strSafeCall4 = safeCall(next, "getTitle");
                    String strSafeCall5 = safeCall(next, "getDisplayTitle");
                    String strSafeCall6 = safeCall(next, "getBizId");
                    if (strSafeCall6 == null || strSafeCall6.isEmpty()) {
                        try {
                            strSafeCall6 = safeCall(next, "bizId");
                        } catch (Throwable unused2) {
                        }
                    }
                    boolean z = zIsEnabled && matchesAny(strSafeCall4, strSafeCall5, strSafeCall6, NAVI_BLOCK_TITLES, NAVI_BLOCK_BIZ);
                    boolean z2 = zIsEnabled2 && matchesGroup(strSafeCall4, strSafeCall5);
                    if (z || z2) {
                        it.remove();
                        i++;
                    }
                }
            }
            if (i > 0) {
                QuarkHooks.log("[SquemaQuark] navigation filtered " + i + " items");
            }
        }
    }

    private static boolean matchesAny(String str, String str2, String str3, String[] strArr, String[] strArr2) {
        for (String str4 : strArr) {
            if (containsAny(str, str2, str4)) {
                return true;
            }
        }
        if (str3 != null) {
            String lowerCase = str3.toLowerCase();
            for (String str5 : strArr2) {
                if (lowerCase.contains(str5)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesGroup(String str, String str2) {
        String[] strArr = {"生成创作", "高效办公", "学习教育", "个性解读", "边界生活"};
        for (int i = 0; i < 5; i++) {
            if (containsAny(str, str2, strArr[i])) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String str, String str2, String str3) {
        if (str3 == null || str3.isEmpty()) {
            return false;
        }
        return (str != null && str.contains(str3)) || (str2 != null && str2.contains(str3));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String safeCall(Object obj, String str) {
        try {
            Object objInvoke = obj.getClass().getMethod(str, new Class[0]).invoke(obj, new Object[0]);
            if (objInvoke == null) {
                return null;
            }
            return String.valueOf(objInvoke);
        } catch (Throwable unused) {
            return null;
        }
    }
}
