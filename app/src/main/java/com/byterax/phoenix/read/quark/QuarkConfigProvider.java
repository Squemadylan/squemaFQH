package com.byterax.phoenix.read.quark;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.byterax.phoenix.read.Constants;
import com.byterax.phoenix.read.HookStatusReporter;

import java.util.Map;

/**
 * Cross-process config + active heartbeat for Quark hooks.
 */
public class QuarkConfigProvider extends ContentProvider {

    public static final String AUTHORITY = Constants.MODULE_PACKAGE + ".quark";
    public static final String METHOD_MARK = "mark";
    public static final String METHOD_GET = "get";
    public static final String METHOD_GET_ALL = "get_all";
    public static final String METHOD_SET = "set";
    public static final String METHOD_SET_STRING = "set_string";
    public static final String KEY_STAMP = "stamp";

    private static final String PREFS = "pref_quark_config_v1";
    private static final String ACTIVE_PREFS = "pref_quark_active_v1";
    private static final String ACTIVE_KEY = "stamp";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        if (METHOD_MARK.equals(method)) {
            long now = System.currentTimeMillis();
            context.getSharedPreferences(ACTIVE_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(ACTIVE_KEY, now)
                    .apply();
            HookStatusReporter.reportTargetHooked(Constants.PKG_QUARK);
            Bundle reply = new Bundle();
            reply.putLong(KEY_STAMP, now);
            return reply;
        }
        if (METHOD_GET.equals(method)) {
            Bundle reply = new Bundle();
            reply.putLong(KEY_STAMP, readStamp(context));
            return reply;
        }
        if (METHOD_GET_ALL.equals(method)) {
            return dumpPrefs(context);
        }
        if (METHOD_SET.equals(method)) {
            String key = arg;
            if ((key == null || key.isEmpty()) && extras != null) {
                key = extras.getString("key");
            }
            boolean value = extras != null && extras.getBoolean("value", false);
            if (key != null && !key.isEmpty()) {
                prefs(context).edit().putBoolean(key, value).apply();
                Config.invalidate();
            }
            Bundle reply = new Bundle();
            reply.putBoolean("ok", true);
            return reply;
        }
        if (METHOD_SET_STRING.equals(method)) {
            String key = arg;
            String value = null;
            if (extras != null) {
                if (key == null || key.isEmpty()) {
                    key = extras.getString("key");
                }
                value = extras.getString("value");
            }
            if (key != null && value != null) {
                prefs(context).edit().putString(key, value).apply();
                Config.invalidate();
            }
            Bundle reply = new Bundle();
            reply.putBoolean("ok", true);
            return reply;
        }
        return null;
    }

    public static long readStamp(Context context) {
        return context.getSharedPreferences(ACTIVE_PREFS, Context.MODE_PRIVATE)
                .getLong(ACTIVE_KEY, -1L);
    }

    public static boolean isActive(Context context) {
        long stamp = readStamp(context);
        return stamp > 0 && System.currentTimeMillis() - stamp < 90_000L;
    }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean getBool(Context context, String key, boolean def) {
        SharedPreferences p = prefs(context);
        if (!p.contains(key)) {
            return def;
        }
        try {
            return p.getBoolean(key, def);
        } catch (ClassCastException ignored) {
            return Boolean.parseBoolean(p.getString(key, String.valueOf(def)));
        }
    }

    private static Bundle dumpPrefs(Context context) {
        Bundle reply = new Bundle();
        Map<String, ?> all = prefs(context).getAll();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Boolean) {
                reply.putBoolean(e.getKey(), (Boolean) v);
            } else if (v instanceof String) {
                reply.putString(e.getKey(), (String) v);
            } else if (v != null) {
                reply.putString(e.getKey(), String.valueOf(v));
            }
        }
        return reply;
    }
}
