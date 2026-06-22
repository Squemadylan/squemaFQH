package com.byterax.phoenix.read;

public final class Constants {
    public static final String MODULE_PACKAGE = "com.byterax.phoenix.read";
    public static final String PKG_FANQIE = "com.dragon.read";
    public static final String PKG_HONGGUO = "com.phoenix.read";
    public static final String ANDROID_PACKAGE = "android";

    /** Registered in system_server via Xposed; target hooks report status here. */
    public static final String SERVICE_NAME = MODULE_PACKAGE + ".status";

    /** ContentProvider authority; system pushes the status Binder here. */
    public static final String PROVIDER_AUTHORITY = MODULE_PACKAGE + ".ServiceProvider";

    /** Bump when the system-side service protocol changes. */
    public static final int SERVICE_VERSION = 1;

    private Constants() {}
}
