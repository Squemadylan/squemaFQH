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

    private HookStatusFiles() {}

    public static void markSystemReady() {
        writeFile(SYSTEM_TMP, "1");
    }

    public static void markTargetHooked(String pkg) {
        if (Constants.PKG_FANQIE.equals(pkg)) {
            writeFile(FANQIE_TMP, "1");
        } else if (Constants.PKG_HONGGUO.equals(pkg)) {
            writeFile(HONGGUO_TMP, "1");
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

    public static boolean isChannelAlive() {
        return isSystemReady() || isFanqieHooked() || isHongguoHooked();
    }

    public static String getTargetsSummary() {
        return "\u756a\u8304 " + (isFanqieHooked() ? "\u2713" : "\u2014")
                + "  /  \u7ea2\u679c " + (isHongguoHooked() ? "\u2713" : "\u2014");
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
