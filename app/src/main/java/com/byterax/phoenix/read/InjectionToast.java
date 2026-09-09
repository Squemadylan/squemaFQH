package com.byterax.phoenix.read;

import android.annotation.SuppressLint;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-shot Toast after a target process is successfully injected (FQ style).
 * Retries briefly if Application is not ready yet at install time.
 */
public final class InjectionToast {

    private static final AtomicBoolean SHOWN = new AtomicBoolean(false);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private InjectionToast() {}

    public static void showOnce(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        if (!SHOWN.compareAndSet(false, true)) {
            return;
        }
        tryShow(message, 0);
    }

    private static void tryShow(final String message, final int attempt) {
        Application application = currentApplication();
        if (application != null) {
            MAIN.post(() -> Toast.makeText(application, message, Toast.LENGTH_SHORT).show());
            return;
        }
        if (attempt >= 25) {
            SHOWN.set(false);
            return;
        }
        MAIN.postDelayed(() -> tryShow(message, attempt + 1), 200L);
    }

    @SuppressLint("PrivateApi")
    private static Application currentApplication() {
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null);
            if (app instanceof Application) {
                return (Application) app;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
