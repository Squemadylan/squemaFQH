package com.byterax.phoenix.read;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * Application entry that exposes the module's activation status to the UI.
 *
 * <p>Modern libxposed API 102 detects activation via the {@link XposedService}
 * IPC channel. The LSPosed Manager app (or any compatible manager that
 * implements the service registration) sends a binder to the module app
 * when the framework loads the module into a scoped process.
 *
 * <p>Limitation: if the framework variant doesn't ship a manager app (e.g.
 * APatch + Zygisk LSPosed without the LSPosed Manager package), the service
 * is never bound and the module app cannot programmatically detect activation.
 * In that case the user must verify via logcat or the activation Toast.
 */
public class HookApp extends Application {
    private static final String TAG = "SquemaFQHook";

    @Nullable
    private volatile XposedService xposedService;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
                @Override
                public void onServiceBind(@NonNull XposedService service) {
                    xposedService = service;
                    Log.i(TAG, "XposedService bound: " + service.getFrameworkName()
                            + " v" + service.getFrameworkVersion()
                            + " scope=" + service.getScope());
                }

                @Override
                public void onServiceDied(@NonNull XposedService service) {
                    xposedService = null;
                    Log.w(TAG, "XposedService died");
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "XposedServiceHelper.registerListener failed: " + t);
        }
    }

    @Nullable
    public static XposedService getXposedService() {
        try {
            HookApp app = (HookApp) Class.forName("android.app.ActivityThread")
                    .getDeclaredMethod("currentApplication")
                    .invoke(null);
            return app != null ? app.xposedService : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
