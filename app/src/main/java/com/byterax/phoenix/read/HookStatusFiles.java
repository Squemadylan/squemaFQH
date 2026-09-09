package com.byterax.phoenix.read;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;

public final class HookStatusFiles {

    private static final String TAG = "SquemaFQHook";
    private static final String TMP_DIR = "/data/local/tmp";
    private static final File SYSTEM_TMP = new File(TMP_DIR, "squema_fq_hook.system");
    private static final File FANQIE_TMP = new File(TMP_DIR, "squema_fq_hook.fanqie");
    private static final File HONGGUO_TMP = new File(TMP_DIR, "squema_fq_hook.hongguo");
    private static final File QUARK_TMP = new File(TMP_DIR, "squema_fq_hook.quark");
    private static final File XIAOX_TMP = new File(TMP_DIR, "squema_fq_hook.xiaox");
    private static final File CHERRYGRAM_TMP = new File(TMP_DIR, "squema_fq_hook.cherrygram");

    private HookStatusFiles() {}

    public static void markSystemReady() {
        writeFile(SYSTEM_TMP, "1");
    }

    public static void markTargetHooked(String pkg) {
        if (Constants.PKG_FANQIE.equals(pkg)) {
            writeFile(FANQIE_TMP, "1");
        } else if (Constants.PKG_HONGGUO.equals(pkg)) {
            writeFile(HONGGUO_TMP, "1");
        } else if (Constants.PKG_QUARK.equals(pkg)) {
            writeFile(QUARK_TMP, "1");
        } else if (Constants.PKG_XIAOX.equals(pkg)) {
            writeFile(XIAOX_TMP, "1");
        } else if (Constants.PKG_CHERRYGRAM.equals(pkg)) {
            writeFile(CHERRYGRAM_TMP, "1");
        }
    }

    public static boolean isSystemReady() {
        return SYSTEM_TMP.exists() && SYSTEM_TMP.length() > 0;
    }

    public static boolean isFanqieHooked() {
        return FANQIE_TMP.exists() && FANQIE_TMP.length() > 0;
    }

    public static boolean isHongguoHooked() {
        return HONGGUO_TMP.exists() && HONGGUO_TMP.length() > 0;
    }

    public static boolean isQuarkHooked() {
        return QUARK_TMP.exists() && QUARK_TMP.length() > 0;
    }

    public static boolean isXiaoxHooked() {
        return XIAOX_TMP.exists() && XIAOX_TMP.length() > 0;
    }

    public static boolean isCherrygramHooked() {
        return CHERRYGRAM_TMP.exists() && CHERRYGRAM_TMP.length() > 0;
    }

    public static void clearTarget(String pkg) {
        if (Constants.PKG_FANQIE.equals(pkg)) {
            deleteQuietly(FANQIE_TMP);
        } else if (Constants.PKG_HONGGUO.equals(pkg)) {
            deleteQuietly(HONGGUO_TMP);
        } else if (Constants.PKG_QUARK.equals(pkg)) {
            deleteQuietly(QUARK_TMP);
        } else if (Constants.PKG_XIAOX.equals(pkg)) {
            deleteQuietly(XIAOX_TMP);
        } else if (Constants.PKG_CHERRYGRAM.equals(pkg)) {
            deleteQuietly(CHERRYGRAM_TMP);
        }
    }

    private static void deleteQuietly(File file) {
        try {
            if (file != null && file.exists() && !file.delete()) {
                writeFile(file, "");
                // empty file still counts as hooked via length()>0 check — force delete
                // by truncating then rename; if delete failed, overwrite with zero bytes
                // and rely on length check: length 0 → not hooked. Good.
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean isChannelAlive() {
        return isSystemReady() || isFanqieHooked() || isHongguoHooked()
                || isQuarkHooked() || isXiaoxHooked();
    }

    public static String getTargetsSummary() {
        return "\u756a\u8304 " + (isFanqieHooked() ? "\u2713" : "\u2014")
                + "  /  \u7ea2\u679c " + (isHongguoHooked() ? "\u2713" : "\u2014")
                + "  /  \u5938\u514b " + (isQuarkHooked() ? "\u2713" : "\u2014")
                + "  /  \u5c0fX " + (isXiaoxHooked() ? "\u2713" : "\u2014");
    }

    private static void writeFile(File file, String content) {
        try {
            try (FileOutputStream out = new FileOutputStream(file, false)) {
                out.write(content.getBytes());
            }
            chmod(file, 0666);
        } catch (Throwable t) {
            Log.w(TAG, "writeFile " + file + " failed: " + t);
        }
    }

    private static void chmod(File file, int mode) {
        try {
            Class<?> os = Class.forName("android.system.Os");
            Method chmod = os.getMethod("chmod", String.class, int.class);
            chmod.invoke(null, file.getAbsolutePath(), mode);
        } catch (Throwable ignored) {
        }
    }
}
