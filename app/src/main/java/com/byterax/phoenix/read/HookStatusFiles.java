package com.byterax.phoenix.read;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * File-based hook status channel.
 *
 * <p>The hook process and the module app are different processes / different UIDs,
 * so we cannot use {@link android.content.Context#getExternalFilesDir} from the
 * hook side. Instead we write a marker file under {@code /data/local/tmp/} (world
 * writable on most ROMs) which the module app reads to drive its UI state.
 *
 * <p>All public methods are <b>process-local and side-effect free</b> besides
 * the file system; safe to call from any hook callback.
 */
public final class HookStatusFiles {

    private static final String TAG = "SquemaFQHook";
    private static final String TMP_DIR = "/data/local/tmp";

    /** LinkedHashMap preserves insertion order so {@link #getTargetsSummary()} reads
     * 番茄→红果→夸克→小X→Cherrygram left-to-right. */
    private static final Map<String, File> TARGET_FILES;
    static {
        Map<String, File> m = new LinkedHashMap<>();
        m.put(Constants.PKG_FANQIE,     new File(TMP_DIR, "squema_fq_hook.fanqie"));
        m.put(Constants.PKG_HONGGUO,    new File(TMP_DIR, "squema_fq_hook.hongguo"));
        m.put(Constants.PKG_QUARK,      new File(TMP_DIR, "squema_fq_hook.quark"));
        m.put(Constants.PKG_XIAOX,      new File(TMP_DIR, "squema_fq_hook.xiaox"));
        m.put(Constants.PKG_CHERRYGRAM, new File(TMP_DIR, "squema_fq_hook.cherrygram"));
        TARGET_FILES = Collections.unmodifiableMap(m);
    }

    private static final File SYSTEM_TMP = new File(TMP_DIR, "squema_fq_hook.system");

    private HookStatusFiles() {}

    public static void markSystemReady() {
        writeFile(SYSTEM_TMP, "1");
    }

    public static void markTargetHooked(String pkg) {
        File marker = TARGET_FILES.get(pkg);
        if (marker != null) writeFile(marker, "1");
    }

    public static boolean isSystemReady() {
        return isNonEmpty(SYSTEM_TMP);
    }

    public static boolean isFanqieHooked()      { return isNonEmpty(TARGET_FILES.get(Constants.PKG_FANQIE));     }
    public static boolean isHongguoHooked()     { return isNonEmpty(TARGET_FILES.get(Constants.PKG_HONGGUO));    }
    public static boolean isQuarkHooked()       { return isNonEmpty(TARGET_FILES.get(Constants.PKG_QUARK));      }
    public static boolean isXiaoxHooked()       { return isNonEmpty(TARGET_FILES.get(Constants.PKG_XIAOX));      }
    public static boolean isCherrygramHooked()  { return isNonEmpty(TARGET_FILES.get(Constants.PKG_CHERRYGRAM)); }

    /** Test if a marker file exists and is non-empty (length > 0). */
    private static boolean isNonEmpty(File f) {
        return f != null && f.exists() && f.length() > 0;
    }

    public static void clearTarget(String pkg) {
        File marker = TARGET_FILES.get(pkg);
        if (marker != null) deleteQuietly(marker);
    }

    /** Delete marker; if delete fails, truncate to 0 bytes so length-based checks
     *  treat it as "not hooked" rather than leaving stale "1" content. */
    private static void deleteQuietly(File file) {
        try {
            if (file != null && file.exists() && !file.delete()) {
                writeFile(file, "");
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean isChannelAlive() {
        return isSystemReady()
                || isFanqieHooked()
                || isHongguoHooked()
                || isQuarkHooked()
                || isXiaoxHooked()
                || isCherrygramHooked();
    }

    /** Compact one-line status summary used by legacy UI paths. */
    public static String getTargetsSummary() {
        StringBuilder sb = new StringBuilder();
        // Insertion order: 番茄 / 红果 / 夸克 / 小X
        appendTarget(sb, "\u756a\u8304", isFanqieHooked());
        sb.append("  /  ");
        appendTarget(sb, "\u7ea2\u679c", isHongguoHooked());
        sb.append("  /  ");
        appendTarget(sb, "\u5938\u514b", isQuarkHooked());
        sb.append("  /  ");
        appendTarget(sb, "\u5c0fX",   isXiaoxHooked());
        return sb.toString();
    }

    private static void appendTarget(StringBuilder sb, String name, boolean hooked) {
        sb.append(name).append(' ').append(hooked ? "\u2713" : "\u2014");
    }

    private static void writeFile(File file, String content) {
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(content.getBytes());
            chmod(file, 0666);
        } catch (Throwable t) {
            Log.w(TAG, "writeFile " + file + " failed: " + t);
        }
    }

    /** {@code android.system.Os.chmod} via reflection — only available on Android,
     *  hence the try/catch swallows {@link ClassNotFoundException} gracefully. */
    private static void chmod(File file, int mode) {
        try {
            Class<?> os = Class.forName("android.system.Os");
            Method chmod = os.getMethod("chmod", String.class, int.class);
            chmod.invoke(null, file.getAbsolutePath(), mode);
        } catch (Throwable ignored) {
        }
    }
}
