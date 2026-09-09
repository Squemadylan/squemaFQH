package com.byterax.phoenix.read;

import android.os.Process;

import java.io.File;

/**
 * Runtime aliveness checks for the module app's status UI.
 *
 * <p>The channel is considered alive if <b>any</b> of these hold:
 * <ul>
 *   <li>libxposed / Zygisk native markers present on the system</li>
 *   <li>a libxposed / classic Xposed runtime class is loaded in the current process</li>
 *   <li>the process runs as root (uid 0) or {@code su} binary is present</li>
 * </ul>
 *
 * <p>None of these checks are authoritative — they only drive the UI hint; the
 * actual hook activation is proven by {@link HookStatusFiles} markers on disk
 * plus the {@code XposedService} round-trip (see {@code HookApp}).
 */
public final class RuntimeDetector {

    private static final String[] FRAMEWORK_MARKERS = {
            "/system/lib64/liblspd.so",
            "/system/lib/liblspd.so",
            "/vendor/lib64/liblspd.so",
            "/system/lib64/libzygisk.so",
            "/system/lib/libzygisk.so",
            "/vendor/lib64/libzygisk.so",
    };

    private static final String[] SU_BINARIES = {
            "/system/bin/su",
            "/system/xbin/su",
            "/vendor/bin/su",
            "/sbin/su",
    };

    /** Sentinel class names whose presence in the class loader proves a runtime is
     *  loaded. {@code Class.forName} triggers the loader without forcing init. */
    private static final String[] RUNTIME_CLASS_PROBES = {
            "io.github.libxposed.api.XposedInterface",
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XposedHelpers",
    };

    private RuntimeDetector() {}

    public static boolean isFrameworkInstalled() {
        for (String path : FRAMEWORK_MARKERS) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    public static boolean isLibxposedLoaded() {
        for (String className : RUNTIME_CLASS_PROBES) {
            try {
                Class.forName(className);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static boolean hasRoot() {
        if (Process.myUid() == 0) return true;
        for (String cmd : SU_BINARIES) {
            if (new File(cmd).exists()) return true;
        }
        return false;
    }

    public static boolean isModuleRuntime() {
        return isFrameworkInstalled() || isLibxposedLoaded() || hasRoot();
    }
}
