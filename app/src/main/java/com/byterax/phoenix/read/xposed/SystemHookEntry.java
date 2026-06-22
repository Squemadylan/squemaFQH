package com.byterax.phoenix.read.xposed;

import com.byterax.phoenix.read.Constants;
import com.byterax.phoenix.read.HookStatusFiles;

import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Classic Xposed entry registered via {@code META-INF/xposed/java_init.list}. Handles
 * system_server bootstrap when the module loads into the {@code android} package.
 */
public class SystemHookEntry implements IXposedHookLoadPackage {

    private static final String TAG = "SquemaFQHook";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (Constants.ANDROID_PACKAGE.equals(lpparam.packageName)) {
            Log.i(TAG, "SystemHookEntry: handleLoadPackage(android)");
            HookStatusFiles.markSystemReady();
            try {
                SystemBootstrap.start(lpparam.classLoader);
            } catch (Throwable t) {
                Log.w(TAG, "SystemBootstrap failed: " + Log.getStackTraceString(t));
            }
        }
    }
}
