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
 * Each target gets a short alias key in {@code PREF_HOOK_STATUS} SharedPreferences;
 * unknown targets fall back to a hash-derived key.
 *
 * <p>Migration: previous builds (v1.9.0 and earlier) used prefs name
 * {@code "hook_status_v1"}; we keep reading that as a fallback so users upgrading
 * don't lose their UI state. New writes always go to {@link #PREFS_NAME}.
 */
public final class HookStatusStore {

    /** Canonical SharedPreferences name. */
    public static final String PREFS_NAME = "pref_hook_status_v1";

    /** Legacy SharedPreferences name from v1.9.0 and earlier. */
    private static final String LEGACY_PREFS_NAME = "hook_status_v1";

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
        prefs(context).edit()
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

    /**
     * Returns the canonical prefs file. If absent (fresh install) but a {@link #LEGACY_PREFS_NAME}
     * file exists from a previous version, lazily migrates its keys over to keep the UI state.
     */
    private static SharedPreferences prefs(Context context) {
        SharedPreferences canonical = prefsOf(context, PREFS_NAME);
        if (canonical.getAll().isEmpty()) {
            SharedPreferences legacy = prefsOf(context, LEGACY_PREFS_NAME);
            if (!legacy.getAll().isEmpty()) {
                SharedPreferences.Editor editor = canonical.edit();
                for (Map.Entry<String, ?> entry : legacy.getAll().entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
                    else if (value instanceof Long)    editor.putLong(entry.getKey(), (Long) value);
                    else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
                    else if (value instanceof Float)   editor.putFloat(entry.getKey(), (Float) value);
                    else if (value instanceof String)  editor.putString(entry.getKey(), (String) value);
                }
                editor.apply();
            }
        }
        return canonical;
    }

    private static SharedPreferences prefsOf(Context context, String name) {
        return context.getApplicationContext()
                .getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    private static boolean isValidArgs(Context context, String pkg) {
        return context != null && pkg != null && !pkg.isEmpty();
    }
}
