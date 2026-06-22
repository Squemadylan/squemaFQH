package com.byterax.phoenix.read.xposed;

import android.os.IBinder;

import java.lang.reflect.Method;

final class SystemAmCompat {

    private SystemAmCompat() {}

    static void waitForActivityService() {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method waitForService = sm.getMethod("waitForService", String.class);
            waitForService.invoke(null, "activity");
            return;
        } catch (Throwable ignored) {
        }

        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method getService = sm.getMethod("getService", String.class);
            while (getService.invoke(null, "activity") == null) {
                Thread.sleep(250);
            }
        } catch (Throwable t) {
            SystemBootstrap.log("waitForActivityService failed: " + t);
        }
    }

    static void registerSystemService(String name, IBinder binder) {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method addService = sm.getMethod("addService", String.class, IBinder.class);
            addService.invoke(null, name, binder);
            SystemBootstrap.log("Registered system service: " + name);
        } catch (Throwable t) {
            SystemBootstrap.log("registerSystemService failed: " + t);
        }
    }

    static int getPackageUid(String packageName) {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method getService = sm.getMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, "package");
            if (binder == null) {
                return -1;
            }
            Class<?> stub = Class.forName("android.content.pm.IPackageManager$Stub");
            Method asInterface = stub.getMethod("asInterface", IBinder.class);
            Object pms = asInterface.invoke(null, binder);

            for (Method method : pms.getClass().getMethods()) {
                if (!"getPackageUid".equals(method.getName())) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 2
                        && params[0] == String.class
                        && params[1] == long.class) {
                    return (int) method.invoke(pms, packageName, 0L);
                }
                if (params.length == 3
                        && params[0] == String.class
                        && params[1] == long.class
                        && params[2] == int.class) {
                    return (int) method.invoke(pms, packageName, 0L, 0);
                }
            }
        } catch (Throwable t) {
            SystemBootstrap.log("getPackageUid failed: " + t);
        }
        return -1;
    }

    static boolean pushBinderToModuleApp(HookStatusService service) {
        try {
            Object provider = getContentProviderExternal();
            if (provider == null) {
                return false;
            }

            android.os.Bundle extras = new android.os.Bundle();
            extras.putBinder("binder", service);

            Object reply = callContentProvider(provider, extras);
            if (reply == null) {
                return false;
            }
            SystemBootstrap.log("Binder pushed to module app");
            return true;
        } catch (Throwable t) {
            SystemBootstrap.log("pushBinderToModuleApp failed: " + t);
            return false;
        }
    }

    private static Object getActivityManagerService() throws Exception {
        Class<?> amClass = Class.forName("android.app.ActivityManager");
        Method getService = amClass.getDeclaredMethod("getService");
        getService.setAccessible(true);
        return getService.invoke(null);
    }

    private static Object getContentProviderExternal() throws Exception {
        Object am = getActivityManagerService();
        if (am == null) {
            return null;
        }

        String authority = com.byterax.phoenix.read.Constants.PROVIDER_AUTHORITY;
        for (Method method : am.getClass().getMethods()) {
            if (!"getContentProviderExternal".equals(method.getName())) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 4
                    && params[0] == String.class
                    && params[1] == int.class) {
                Object holder = method.invoke(am, authority, 0, null, null);
                return extractProvider(holder);
            }
        }
        return null;
    }

    private static Object extractProvider(Object holder) throws Exception {
        if (holder == null) {
            return null;
        }
        try {
            Method getProvider = holder.getClass().getMethod("getProvider");
            return getProvider.invoke(holder);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            java.lang.reflect.Field providerField = holder.getClass().getField("provider");
            return providerField.get(holder);
        } catch (NoSuchFieldException ignored) {
        }
        return holder;
    }

    private static Object callContentProvider(Object provider, android.os.Bundle extras)
            throws Exception {
        String authority = com.byterax.phoenix.read.Constants.PROVIDER_AUTHORITY;
        int sdk = android.os.Build.VERSION.SDK_INT;

        if (sdk >= android.os.Build.VERSION_CODES.S) {
            Class<?> builderClass = Class.forName("android.content.AttributionSource$Builder");
            Object builder = builderClass.getConstructor(int.class).newInstance(1000);
            Method setPackageName = builderClass.getMethod("setPackageName", String.class);
            builder = setPackageName.invoke(builder, com.byterax.phoenix.read.Constants.ANDROID_PACKAGE);
            Method build = builderClass.getMethod("build");
            Object attr = build.invoke(builder);
            for (Method method : provider.getClass().getMethods()) {
                if ("call".equals(method.getName()) && method.getParameterTypes().length == 5) {
                    return method.invoke(provider, attr, authority, "", null, extras);
                }
            }
        }

        if (sdk == android.os.Build.VERSION_CODES.R) {
            for (Method method : provider.getClass().getMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if ("call".equals(method.getName()) && params.length == 6) {
                    return method.invoke(provider,
                            com.byterax.phoenix.read.Constants.ANDROID_PACKAGE,
                            null, authority, "", null, extras);
                }
            }
        }

        for (Method method : provider.getClass().getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if ("call".equals(method.getName()) && params.length == 5
                    && params[0] == String.class) {
                return method.invoke(provider,
                        com.byterax.phoenix.read.Constants.ANDROID_PACKAGE,
                        authority, "", null, extras);
            }
        }
        return null;
    }
}
