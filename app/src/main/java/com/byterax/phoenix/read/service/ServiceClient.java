package com.byterax.phoenix.read.service;

import android.os.IBinder;

import com.byterax.phoenix.read.Constants;
import com.byterax.phoenix.read.HookStatusFiles;
import com.byterax.phoenix.read.IHookStatusService;

public final class ServiceClient implements IBinder.DeathRecipient {

    private static final ServiceClient INSTANCE = new ServiceClient();

    private volatile IHookStatusService service;

    private ServiceClient() {}

    public static ServiceClient get() {
        return INSTANCE;
    }

    public void linkService(IBinder binder) {
        if (binder == null) {
            return;
        }
        service = IHookStatusService.Stub.asInterface(binder);
        try {
            binder.linkToDeath(this, 0);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void binderDied() {
        service = null;
    }

    public int getServiceVersion() {
        IHookStatusService s = service;
        if (s == null) {
            return 0;
        }
        try {
            return s.getServiceVersion();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public boolean isTargetHooked(String pkg) {
        IHookStatusService s = service;
        if (s == null) {
            return false;
        }
        try {
            return s.isTargetHooked(pkg);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public String getHookedTargetsSummary() {
        IHookStatusService s = service;
        if (s == null) {
            return "";
        }
        try {
            String summary = s.getHookedTargetsSummary();
            return summary != null ? summary : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    public boolean isSystemChannelReady() {
        return getServiceVersion() > 0;
    }

    /** Try binding directly from ServiceManager (works on some ROMs). */
    public boolean tryConnect() {
        if (isSystemChannelReady()) {
            return true;
        }
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            java.lang.reflect.Method getService = sm.getMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, Constants.SERVICE_NAME);
            if (binder == null) {
                return false;
            }
            linkService(binder);
            return isSystemChannelReady();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
