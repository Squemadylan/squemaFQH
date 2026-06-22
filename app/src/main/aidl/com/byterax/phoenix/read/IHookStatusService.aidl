package com.byterax.phoenix.read;

interface IHookStatusService {
    int getServiceVersion();
    void reportTargetHooked(String pkg);
    boolean isTargetHooked(String pkg);
    String getHookedTargetsSummary();
}
