package com.byterax.phoenix.read;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.byterax.phoenix.read.service.ServiceClient;

import io.github.libxposed.service.XposedService;

/**
 * Resolve per-target UI status from real LSPosed scope + live injection proof.
 *
 * <p>Rules (strict):
 * <ul>
 *   <li>Not in module scope → {@link State#OFF}. Stale hook flags never light the card.</li>
 *   <li>In scope, no live proof → {@link State#SCOPED}</li>
 *   <li>In scope + running/report → {@link State#LIVE}</li>
 * </ul>
 */
public final class ScopeStatus {

    private static final String TAG = "SquemaFQHook";

    public enum State {
        /** Not in LSPosed scope. */
        OFF,
        /** Listed in module scope, but not proven running yet. */
        SCOPED,
        /** Scope + running/report proof that hooks are live. */
        LIVE
    }

    private ScopeStatus() {}

    public static State resolve(Context context, String pkg) {
        boolean scoped = isInScope(pkg);
        if (!scoped) {
            // Drop leftover marks from earlier sessions so the UI stays honest
            // after the user unchecks the target in LSPosed.
            clearStaleReports(context, pkg);
            return State.OFF;
        }
        boolean running = isRunningTarget(pkg);
        boolean reported = isReported(context, pkg);
        if (running || reported) {
            return State.LIVE;
        }
        return State.SCOPED;
    }

    public static boolean isLit(Context context, String pkg) {
        State state = resolve(context, pkg);
        return state == State.SCOPED || state == State.LIVE;
    }

    /**
     * Prefer live {@link XposedService#getScope()} when bound. Only fall back to
     * Magisk DB copy when the service is absent or getScope fails — never OR a
     * negative service answer with a stale local DB.
     */
    public static boolean isInScope(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        XposedService service = HookApp.getXposedService();
        if (service != null) {
            try {
                List<String> scope = service.getScope();
                if (scope != null) {
                    return scope.contains(pkg);
                }
            } catch (Throwable t) {
                Log.w(TAG, "getScope failed: " + t);
            }
        }
        Context app = currentApp();
        return app != null && LspScopeReader.readScope(app).contains(pkg);
    }

    public static Set<String> currentScope() {
        XposedService service = HookApp.getXposedService();
        if (service != null) {
            try {
                List<String> scope = service.getScope();
                if (scope != null) {
                    return new HashSet<>(scope);
                }
            } catch (Throwable t) {
                Log.w(TAG, "currentScope failed: " + t);
            }
        }
        Context app = currentApp();
        if (app == null) {
            return Collections.emptySet();
        }
        return LspScopeReader.readScope(app);
    }

    public static boolean isServiceBound() {
        return HookApp.getXposedService() != null || hasLspFallback();
    }

    private static boolean hasLspFallback() {
        Context app = currentApp();
        return app != null && LspScopeReader.isEnabled(app);
    }

    private static Context currentApp() {
        try {
            return (Context) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isRunningTarget(String pkg) {
        XposedService service = HookApp.getXposedService();
        if (service == null || pkg == null) {
            return false;
        }
        try {
            Method method = service.getClass().getMethod("getRunningTargets");
            Object list = method.invoke(service);
            if (!(list instanceof List)) {
                return false;
            }
            for (Object target : (List<?>) list) {
                if (target == null) {
                    continue;
                }
                String processName = readStringProp(target, "getProcessName", "processName");
                String packageName = readStringProp(target, "getPackageName", "packageName");
                if (pkg.equals(packageName) || pkg.equals(processName)) {
                    return true;
                }
                if (processName != null && processName.startsWith(pkg + ":")) {
                    return true;
                }
            }
        } catch (NoSuchMethodException ignored) {
            // service 101 builds — method absent
        } catch (Throwable t) {
            Log.w(TAG, "getRunningTargets failed: " + t);
        }
        return false;
    }

    private static boolean isReported(Context context, String pkg) {
        if (HookStatusStore.isHooked(context, pkg)) {
            return true;
        }
        if (ServiceClient.get().isTargetHooked(pkg)) {
            return true;
        }
        if (Constants.PKG_FANQIE.equals(pkg)) {
            return HookStatusFiles.isFanqieHooked();
        }
        if (Constants.PKG_HONGGUO.equals(pkg)) {
            return HookStatusFiles.isHongguoHooked();
        }
        if (Constants.PKG_QUARK.equals(pkg)) {
            return HookStatusFiles.isQuarkHooked()
                    || com.byterax.phoenix.read.quark.Config.isActive()
                    || com.byterax.phoenix.read.quark.QuarkConfigProvider.isActive(context);
        }
        if (Constants.PKG_XIAOX.equals(pkg)) {
            return HookStatusFiles.isXiaoxHooked();
        }
        return false;
    }

    private static void clearStaleReports(Context context, String pkg) {
        try {
            HookStatusStore.clear(context, pkg);
            HookStatusFiles.clearTarget(pkg);
        } catch (Throwable t) {
            Log.w(TAG, "clearStaleReports failed pkg=" + pkg + " err=" + t);
        }
    }

    private static String readStringProp(Object target, String getter, String field) {
        try {
            Method m = target.getClass().getMethod(getter);
            Object v = m.invoke(target);
            if (v instanceof String) {
                return (String) v;
            }
        } catch (Throwable ignored) {
        }
        try {
            return (String) target.getClass().getField(field).get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
