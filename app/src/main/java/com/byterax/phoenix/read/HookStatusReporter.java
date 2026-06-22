package com.byterax.phoenix.read;

import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Method;

public final class HookStatusReporter {
    private static final String TAG = "SquemaFQHook";

    private HookStatusReporter() {}

    public static void reportTargetHooked(String pkg) {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method getService = sm.getMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, Constants.SERVICE_NAME);
            if (binder == null) {
                return;
            }
            IHookStatusService service = IHookStatusService.Stub.asInterface(binder);
            service.reportTargetHooked(pkg);
        } catch (Throwable t) {
            Log.w(TAG, "reportTargetHooked failed for " + pkg + ": " + t);
        }
    }
}
