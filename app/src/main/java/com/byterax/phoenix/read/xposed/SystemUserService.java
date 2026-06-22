package com.byterax.phoenix.read.xposed;

import com.byterax.phoenix.read.Constants;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class SystemUserService {

    private SystemUserService() {}

    static void register(HookStatusService service, ClassLoader classLoader) {
        int moduleAppUid = SystemAmCompat.getPackageUid(Constants.MODULE_PACKAGE);
        if (moduleAppUid < 0) {
            SystemBootstrap.log("Module app UID not found");
        } else {
            SystemBootstrap.log("Module app uid=" + moduleAppUid);
        }

        hookModuleProcessAttach(service, classLoader);
        scheduleBinderPush(service, "boot");
    }

    private static void hookModuleProcessAttach(HookStatusService service, ClassLoader classLoader) {
        try {
            Class<?> ams = XposedHelpers.findClass(
                    "com.android.server.am.ActivityManagerService", classLoader);
            XposedBridge.hookAllMethods(ams, "attachApplicationLocked", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length == 0) {
                        return;
                    }
                    Object processRecord = param.args[0];
                    if (processRecord == null) {
                        return;
                    }
                    String processName = (String) XposedHelpers.getObjectField(
                            processRecord, "processName");
                    if (Constants.MODULE_PACKAGE.equals(processName)) {
                        scheduleBinderPush(service, "attach");
                    }
                }
            });
            SystemBootstrap.log("attachApplicationLocked hook installed");
        } catch (Throwable t) {
            SystemBootstrap.log("attachApplicationLocked hook failed: " + t);
        }
    }

    private static void scheduleBinderPush(HookStatusService service, String reason) {
        new Thread(() -> {
            for (int i = 0; i < 20; i++) {
                if (SystemAmCompat.pushBinderToModuleApp(service)) {
                    SystemBootstrap.log("Binder push ok (" + reason + ")");
                    return;
                }
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        }, "SquemaFQHook-BinderPush").start();
    }
}
