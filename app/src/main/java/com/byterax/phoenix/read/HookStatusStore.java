package com.byterax.phoenix.read;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persistent hook status visible to the module app UI.
 * Written from target processes via {@link com.byterax.phoenix.read.service.ServiceProvider}.
 */
public final class HookStatusStore {

    private static final String PREFS = "hook_status_v1";

    private HookStatusStore() {}

    public static void markHooked(Context context, String pkg) {
        if (context == null || pkg == null || pkg.isEmpty()) {
            return;
        }
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(key(pkg), true)
                .putLong(key(pkg) + "_ts", System.currentTimeMillis())
                .apply();
    }

    public static boolean isHooked(Context context, String pkg) {
        if (context == null || pkg == null || pkg.isEmpty()) {
            return false;
        }
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(key(pkg), false);
    }

    public static void clear(Context context, String pkg) {
        if (context == null || pkg == null || pkg.isEmpty()) {
            return;
        }
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(key(pkg))
                .remove(key(pkg) + "_ts")
                .apply();
    }

    private static String key(String pkg) {
        if (Constants.PKG_FANQIE.equals(pkg)) {
            return "fanqie";
        }
        if (Constants.PKG_HONGGUO.equals(pkg)) {
            return "hongguo";
        }
        if (Constants.PKG_QUARK.equals(pkg)) {
            return "quark";
        }
        return "pkg_" + pkg.hashCode();
    }
}
