package com.byterax.phoenix.read.xposed;

import android.os.RemoteException;

import com.byterax.phoenix.read.Constants;
import com.byterax.phoenix.read.IHookStatusService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HookStatusService extends IHookStatusService.Stub {

    private static volatile HookStatusService instance;

    private final Map<String, Long> hookedTargets = new ConcurrentHashMap<>();

    public HookStatusService() {
        instance = this;
    }

    public static HookStatusService getInstance() {
        return instance;
    }

    @Override
    public int getServiceVersion() {
        return Constants.SERVICE_VERSION;
    }

    @Override
    public void reportTargetHooked(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return;
        }
        hookedTargets.put(pkg, System.currentTimeMillis());
        SystemBootstrap.log("Target hooked: " + pkg);
    }

    @Override
    public boolean isTargetHooked(String pkg) {
        return pkg != null && hookedTargets.containsKey(pkg);
    }

    @Override
    public String getHookedTargetsSummary() throws RemoteException {
        boolean fanqie = hookedTargets.containsKey(Constants.PKG_FANQIE);
        boolean hongguo = hookedTargets.containsKey(Constants.PKG_HONGGUO);
        return "\u756a\u8304 " + (fanqie ? "\u2713" : "\u2014")
                + "  /  \u7ea2\u679c " + (hongguo ? "\u2713" : "\u2014");
    }
}
