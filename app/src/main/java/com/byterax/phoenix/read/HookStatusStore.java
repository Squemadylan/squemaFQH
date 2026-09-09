package com.byterax.phoenix.read;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent hook status visible to the module app UI.
 *
 * <p>Written from target processes via {@link com.byterax.phoenix.read.service.ServiceProvider}.
 * Each target gets a short alias key in {@code hook_status_v1} SharedPreferences;
 * unknown targets fall back to a hash-derived key.
 */
public final class HookStatusStore {

    private static final String PREFS = "hook_status_v1";

    /** pkg name → prefs key. Insertion order is irrelevant; map lookups are O(1). */
    private static final Map<String, String> PKG_KEYS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(Constants.PKG_FANQIE,     "fanqie");
        m.put(Constants.PKG_HONGGUO,    "hongguo");
        m.put(Constants.PKG_QUARK,      "quark");
        m.put(Constants.PKG_XIAOX,      "xiaox");
        m.put(Constants.PKG_CHERRYGRAM, "cherrygram");
        PKG_KEYS = Collections.unmodifiableMap(m);
    }

    private HookStatusStore() {}

    public static void markHooked(Context context, String pkg) {
        if (!isValidArgs(context, pkg)) return;
        SharedPreferences prefs = prefs(context);
        prefs.edit()
                .putBoolean(key(pkg), true)
                .putLong(key(pkg) + "_ts", System.currentTimeMillis())
                .apply();
    }

    public static boolean isHooked(Context context, String pkg) {
        if (!isValidArgs(context, pkg)) return false;
        return prefs(context).getBoolean(key(pkg), false);
    }

    public static void clear(Context context, String pkg) {
        if (!isValidArgs(context, pkg)) return;
        prefs(context).edit()
                .remove(key(pkg))
                .remove(key(pkg) + "_ts")
                .apply();
    }

    /** Short prefs key for known targets; hash-derived fallback otherwise. */
    private static String key(String pkg) {
        String k = PKG_KEYS.get(pkg);
        return k != null ? k : "pkg_" + pkg.hashCode();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean isValidArgs(Context context, String pkg) {
        return context != null && pkg != null && !pkg.isEmpty();
    }
}
