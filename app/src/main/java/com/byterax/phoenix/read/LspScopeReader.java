package com.byterax.phoenix.read;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fallback scope reader for Magisk / Zygisk LSPosed when
 * {@link io.github.libxposed.service.XposedService} is not pushed to the module app.
 *
 * <p>Many Chinese modules do the same: copy {@code modules_config.db} with {@code su}
 * and query the {@code scope} table. Primary path remains XposedService.getScope().
 */
public final class LspScopeReader {

    private static final String TAG = "SquemaFQHook";
    private static final String MODULE = Constants.MODULE_PACKAGE;
    private static final long CACHE_TTL_MS = 3000L;

    private static final AtomicReference<Set<String>> cached = new AtomicReference<>();
    private static final AtomicLong cachedAt = new AtomicLong();

    private LspScopeReader() {}

    public static Set<String> readScope(Context context) {
        long now = System.currentTimeMillis();
        Set<String> hit = cached.get();
        if (hit != null && now - cachedAt.get() < CACHE_TTL_MS) {
            return hit;
        }
        Set<String> fresh = readScopeUncached(context);
        cached.set(fresh);
        cachedAt.set(now);
        return fresh;
    }

    public static boolean isEnabled(Context context) {
        return readModuleEnabled(context);
    }

    public static void invalidate() {
        cached.set(null);
        cachedAt.set(0L);
    }

    private static Set<String> readScopeUncached(Context context) {
        File local = copyDb(context);
        if (local == null || !local.isFile()) {
            return Collections.emptySet();
        }
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = SQLiteDatabase.openDatabase(
                    local.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            cursor = db.rawQuery(
                    "SELECT app_pkg_name FROM scope WHERE module_pkg_name=? AND user_id=0",
                    new String[]{MODULE});
            Set<String> out = new HashSet<>();
            while (cursor.moveToNext()) {
                String pkg = cursor.getString(0);
                if (pkg != null && !pkg.isEmpty()) {
                    out.add(pkg);
                }
            }
            Log.i(TAG, "LspScopeReader scope=" + out);
            return out;
        } catch (Throwable t) {
            Log.w(TAG, "LspScopeReader query failed: " + t);
            return Collections.emptySet();
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable ignored) {
                }
            }
            if (db != null) {
                try {
                    db.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static boolean readModuleEnabled(Context context) {
        File local = copyDb(context);
        if (local == null || !local.isFile()) {
            return false;
        }
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = SQLiteDatabase.openDatabase(
                    local.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            cursor = db.rawQuery(
                    "SELECT enabled FROM modules_state WHERE module_pkg_name=? AND user_id=0",
                    new String[]{MODULE});
            if (cursor.moveToFirst()) {
                return cursor.getInt(0) == 1;
            }
        } catch (Throwable t) {
            Log.w(TAG, "LspScopeReader enabled failed: " + t);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Throwable ignored) {
                }
            }
            if (db != null) {
                try {
                    db.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    private static File copyDb(Context context) {
        if (context == null) {
            return null;
        }
        File dest = new File(context.getCacheDir(), "lsp_modules_config.db");
        String cmd = "cp /data/adb/lspd/config/modules_config.db '"
                + dest.getAbsolutePath()
                + "' && chmod 666 '"
                + dest.getAbsolutePath()
                + "'";
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            drain(process.getInputStream());
            drain(process.getErrorStream());
            int code = process.waitFor();
            if (code != 0 || !dest.isFile() || dest.length() == 0) {
                Log.w(TAG, "LspScopeReader copy failed code=" + code);
                return null;
            }
            return dest;
        } catch (Throwable t) {
            Log.w(TAG, "LspScopeReader su copy failed: " + t);
            return null;
        }
    }

    private static void drain(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            byte[] buf = new byte[1024];
            while (in.read(buf) >= 0) {
                // discard
            }
            in.close();
        } catch (Throwable ignored) {
        }
    }
}
