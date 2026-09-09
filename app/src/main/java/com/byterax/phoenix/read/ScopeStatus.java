package com.byterax.phoenix.read;

import android.content.Context;
import android.util.Log;

import com.byterax.phoenix.read.quark.Config;
import com.byterax.phoenix.read.quark.QuarkConfigProvider;
import com.byterax.phoenix.read.service.ServiceClient;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.service.XposedService;

/**
 * Resolve per-target UI status from real LSPosed scope + live injection proof.
 *
 * <p>State machine:
 * <ul>
 *   <li>Not in module scope → {@link State#OFF}. Stale hook flags never light the card.</li>
 *   <li>In scope, no live proof → {@link State#SCOPED}</li>
 *   <li>In scope + running/report → {@link State#LIVE}</li>
 * </ul>
 *
 * <p>Live proof is gathered from three independent channels (any one is enough):
 * <ol>
 *   <li>{@link XposedService#getRunningTargets()} — modern API runtime list</li>
 *   <li>{@link HookStatusStore} — SharedPreferences written by hook process</li>
 *   <li>{@link HookStatusFiles} — {@code /data/local/tmp/} marker files</li>
 * </ol>
 */
public final class ScopeStatus {

    private static final String TAG = "SquemaFQHook";

    public enum State {
        /** Not in LSPosed scope. */
        OFF,
        /** Listed in module scope, but not proven running yet. */
        SCOPED,
        /** Scope + running/report proof that hooks are live. */
        LIVE,
    }

    private ScopeStatus() {}

    public static State resolve(Context context, String pkg) {
        if (!isInScope(pkg)) {
            // Drop leftover marks from earlier sessions so the UI stays honest
            // after the user unchecks the target in LSPosed.
            clearStaleReports(context, pkg);
            return State.OFF;
        }
        return (isRunningTarget(pkg) || isReported(context, pkg)) ? State.LIVE : State.SCOPED;
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
        if (pkg == null || pkg.isEmpty()) return false;

        XposedService service = HookApp.getXposedService();
        if (service != null) {
            try {
                List<String> scope = service.getScope();
                if (scope != null) return scope.contains(pkg);
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
                if (scope != null) return new HashSet<>(scope);
            } catch (Throwable t) {
                Log.w(TAG, "currentScope failed: " + t);
            }
        }
        Context app = currentApp();
        return app == null ? Collections.<String>emptySet() : LspScopeReader.readScope(app);
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

    /** Probe modern API for the live target list. Absent on API 101 builds. */
    public static boolean isRunningTarget(String pkg) {
        if (pkg == null) return false;
        XposedService service = HookApp.getXposedService();
        if (service == null) return false;
        try {
            Method method = service.getClass().getMethod("getRunningTargets");
            Object list = method.invoke(service);
            if (!(list instanceof List)) return false;
            for (Object target : (List<?>) list) {
                if (matchesTarget(target, pkg)) return true;
            }
        } catch (NoSuchMethodException ignored) {
            // service 101 builds — method absent
        } catch (Throwable t) {
            Log.w(TAG, "getRunningTargets failed: " + t);
        }
        return false;
    }

    /** A target matches a pkg if its package equals pkg, or its process name
     *  equals pkg (covers single-process apps), or its process name starts with
     *  pkg + ":" (covers multi-process apps like com.quark.browser:push). */
    private static boolean matchesTarget(Object target, String pkg) {
        if (target == null) return false;
        String processName = readStringProp(target, "getProcessName", "processName");
        String packageName = readStringProp(target, "getPackageName", "packageName");
        return pkg.equals(packageName)
                || pkg.equals(processName)
                || (processName != null && processName.startsWith(pkg + ":"));
    }

    /** Any of the three live-report channels says this pkg was actually hooked. */
    private static boolean isReported(Context context, String pkg) {
        if (HookStatusStore.isHooked(context, pkg)) return true;
        if (ServiceClient.get().isTargetHooked(pkg)) return true;
        return isReportedByMarker(pkg, context);
    }

    /** File-marker based check; Quark has two extra sources. */
    private static boolean isReportedByMarker(String pkg, Context context) {
        switch (pkg) {
            case Constants.PKG_FANQIE:     return HookStatusFiles.isFanqieHooked();
            case Constants.PKG_HONGGUO:    return HookStatusFiles.isHongguoHooked();
            case Constants.PKG_QUARK:
                return HookStatusFiles.isQuarkHooked()
                        || Config.isActive()
                        || QuarkConfigProvider.isActive(context);
            case Constants.PKG_XIAOX:      return HookStatusFiles.isXiaoxHooked();
            case Constants.PKG_CHERRYGRAM: return HookStatusFiles.isCherrygramHooked();
            default:                       return false;
        }
    }

    private static void clearStaleReports(Context context, String pkg) {
        try {
            HookStatusStore.clear(context, pkg);
            HookStatusFiles.clearTarget(pkg);
        } catch (Throwable t) {
            Log.w(TAG, "clearStaleReports failed pkg=" + pkg + " err=" + t);
        }
    }

    /** Try the getter first, fall back to the field — covers both Java bean and
     *  raw-field access patterns used by the XposedService API surface. */
    private static String readStringProp(Object target, String getter, String field) {
        try {
            Method m = target.getClass().getMethod(getter);
            Object v = m.invoke(target);
            if (v instanceof String) return (String) v;
        } catch (Throwable ignored) {
        }
        try {
            return (String) target.getClass().getField(field).get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
