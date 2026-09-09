package com.byterax.phoenix.read;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CopyOnWriteArrayList;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * Application entry that exposes the module's activation status to the UI.
 *
 * <p>Modern libxposed API detects activation via {@link XposedService}.
 * When bound, the module app can read {@link XposedService#getScope()} and
 * (API 102+) {@code getRunningTargets()} — the same channel other modern
 * modules use to light their status UI.
 *
 * <p>Threading: {@link #getXposedService()} is safe to call from any thread;
 * {@link #addStatusListener}/{@link #removeStatusListener} are intended for
 * the main thread; the underlying {@link CopyOnWriteArrayList} is also thread-safe.
 */
public class HookApp extends Application {

    private static final String TAG = "SquemaFQHook";

    /** Sink for UI fragments that should refresh when the LSPosed service comes/goes. */
    public interface StatusListener {
        void onXposedStatusChanged();
    }

    private static volatile HookApp instance;

    private final CopyOnWriteArrayList<StatusListener> listeners = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    private volatile XposedService xposedService;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        try {
            XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
                @Override
                public void onServiceBind(@NonNull XposedService service) {
                    xposedService = service;
                    Log.i(TAG, "XposedService bound: " + service.getFrameworkName()
                            + " v" + service.getFrameworkVersion()
                            + " scope=" + safeScope(service));
                    notifyStatusChanged();
                }

                @Override
                public void onServiceDied(@NonNull XposedService service) {
                    xposedService = null;
                    Log.w(TAG, "XposedService died");
                    notifyStatusChanged();
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "XposedServiceHelper.registerListener failed: " + t);
        }
    }

    public static void addStatusListener(StatusListener listener) {
        HookApp app = instance;
        if (app != null && listener != null) app.listeners.addIfAbsent(listener);
    }

    public static void removeStatusListener(StatusListener listener) {
        HookApp app = instance;
        if (app != null && listener != null) app.listeners.remove(listener);
    }

    @Nullable
    public static XposedService getXposedService() {
        HookApp app = instance;
        if (app != null) return app.xposedService;
        // Fallback: another process's app singleton; only useful in non-main-thread calls.
        try {
            Application current = (Application) Class.forName("android.app.ActivityThread")
                    .getDeclaredMethod("currentApplication")
                    .invoke(null);
            return (current instanceof HookApp) ? ((HookApp) current).xposedService : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void notifyStatusChanged() {
        mainHandler.post(() -> {
            for (StatusListener listener : listeners) {
                try {
                    listener.onXposedStatusChanged();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static String safeScope(XposedService service) {
        try {
            return String.valueOf(service.getScope());
        } catch (Throwable t) {
            return "<error>";
        }
    }
}
