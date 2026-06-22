package com.byterax.phoenix.read;

import android.os.Process;

import java.io.File;

/**
 * Trivial aliveness checks. If the libxposed / Zygisk runtime is loaded in the current
 * process, or if we have root, the channel is considered alive and every target is ✓.
 */
public final class RuntimeDetector {

    private static final String[] FRAMEWORK_MARKERS = {
            "/system/lib64/liblspd.so",
            "/system/lib/liblspd.so",
            "/vendor/lib64/liblspd.so",
            "/system/lib64/libzygisk.so",
            "/system/lib/libzygisk.so",
            "/vendor/lib64/libzygisk.so"
    };

    private RuntimeDetector() {}

    /** Any libxposed / Zygisk marker present on the system. */
    public static boolean isFrameworkInstalled() {
        for (String path : FRAMEWORK_MARKERS) {
            if (new File(path).exists()) {
                return true;
            }
        }
        return false;
    }

    /** Detect a loaded libxposed runtime inside the current process. */
    public static boolean isLibxposedLoaded() {
        try {
            Class.forName("io.github.libxposed.api.XposedInterface");
            return true;
        } catch (Throwable ignored) {
        }
        try {
            Class.forName("de.robv.android.xposed.XposedBridge");
            return true;
        } catch (Throwable ignored) {
        }
        try {
            Class.forName("de.robv.android.xposed.XposedHelpers");
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** The process uid is 0, or we can run commands as root. */
    public static boolean hasRoot() {
        if (Process.myUid() == 0) {
            return true;
        }
        String[] commands = {
                "/system/bin/su", "/system/xbin/su", "/vendor/bin/su", "/sbin/su"
        };
        for (String cmd : commands) {
            if (new File(cmd).exists()) {
                return true;
            }
        }
        return false;
    }

    /** True if the framework is installed and the app can speak libxposed. */
    public static boolean isModuleRuntime() {
        return isFrameworkInstalled() || isLibxposedLoaded() || hasRoot();
    }
}
