package com.byterax.phoenix.read.xposed;

import android.util.Log;

import com.byterax.phoenix.read.Constants;
import com.byterax.phoenix.read.HookStatusFiles;

/**
 * Starts the system_server status service. Invoked from {@link com.byterax.phoenix.read.HookInit}
 * via {@code onSystemServerStarting}.
 */
public final class SystemBootstrap {

    private static final String TAG = "SquemaFQHook";
    private static volatile boolean started;

    private SystemBootstrap() {}

    public static void start(ClassLoader classLoader) {
        if (started) {
            return;
        }
        synchronized (SystemBootstrap.class) {
            if (started) {
                return;
            }
            started = true;
        }

        log("System bootstrap (Modern API)");

        new Thread(() -> {
            try {
                SystemAmCompat.waitForActivityService();
                HookStatusService service = new HookStatusService();
                SystemAmCompat.registerSystemService(Constants.SERVICE_NAME, service);
                SystemUserService.register(service, classLoader);
                HookStatusFiles.markSystemReady();
                log("System status service ready");
            } catch (Throwable t) {
                log("System bootstrap failed: " + Log.getStackTraceString(t));
            }
        }, "SquemaFQHook-SystemInit").start();
    }

    public static void log(String message) {
        Log.i(TAG, message);
    }
}
