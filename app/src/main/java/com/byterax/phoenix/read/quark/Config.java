package com.byterax.phoenix.read.quark;

import android.os.SystemClock;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.Properties;

/**
 * Same config path / semantics as original QuarkHook ({@code com.quark.bypass.Config}):
 * {@code /storage/emulated/0/Download/QuarkHook/quark_bypass.cfg}.
 */
public final class Config {
    public static final String ACTIVE_FILE =
            "/storage/emulated/0/Download/QuarkHook/quark_bypass_active";
    public static final String CFG_FILE =
            "/storage/emulated/0/Download/QuarkHook/quark_bypass.cfg";
    public static final String LOG_FILE =
            "/storage/emulated/0/Download/QuarkHook/quark_bypass.log";

    public static final String K_ENABLE = "enable";
    public static final String K_ENABLE_LOG = "enable_log";
    public static final String K_LOG_LEVEL = "log_level";
    public static final String K_BLOCK_SECURITY = "block_security";
    public static final String K_BLOCK_BOOK = "block_book";
    public static final String K_BLOCK_NAVI = "block_navi";
    public static final String K_BLOCK_USER_CENTER = "block_user_center";
    public static final String K_LOCK_SETTINGS = "lock_settings";
    public static final String K_BLOCK_SNIFF = "block_sniff";
    public static final String K_BLOCK_UPDATE = "block_update";
    public static final String K_UNLOCK_AV = "unlock_av";
    public static final String K_BLOCK_SUMMER_TASK = "block_summer_task";

    private static final String TAG = "SquemaQuark";
    private static final long CACHE_VALID_MS = 5000L;

    private static volatile Properties sCachedProperties;
    private static volatile long sCacheTimestamp;

    private Config() {}

    /** Kept for call sites; file config needs no Context. */
    public static void bindContext(android.content.Context context) {
        // no-op — original QuarkHook also does not bind Context for flags
    }

    public static void invalidate() {
        sCachedProperties = null;
        sCacheTimestamp = 0L;
    }

    public static boolean masterEnabled() {
        return isEnabled(K_ENABLE);
    }

    public static boolean isEnabled(String key) {
        Properties props = cached();
        String property = props.getProperty(key);
        return property != null && Boolean.parseBoolean(property);
    }

    public static boolean isLogEnabled() {
        return isEnabled(K_ENABLE_LOG);
    }

    public static int getLogLevel() {
        String property = cached().getProperty(K_LOG_LEVEL);
        if (property == null) {
            return 3;
        }
        try {
            return Integer.parseInt(property);
        } catch (NumberFormatException ignored) {
            return 3;
        }
    }

    public static void set(String key, boolean value) {
        Properties props = load();
        props.setProperty(key, String.valueOf(value));
        save(props);
        sCachedProperties = props;
        sCacheTimestamp = SystemClock.elapsedRealtime();
    }

    public static void setLogLevel(int level) {
        Properties props = load();
        props.setProperty(K_LOG_LEVEL, String.valueOf(level));
        save(props);
        sCachedProperties = props;
        sCacheTimestamp = SystemClock.elapsedRealtime();
    }

    /** Ensure cfg exists with original-style defaults (all features on). */
    public static void ensureDefaults() {
        File file = new File(CFG_FILE);
        if (file.exists() && file.length() > 0) {
            return;
        }
        Properties props = new Properties();
        applyDefaults(props);
        save(props);
        invalidate();
    }

    public static void logI(String tag, String msg) {
        log(tag, 3, msg);
    }

    public static void log(String tag, int level, String msg) {
        if (!isLogEnabled() || level > getLogLevel()) {
            return;
        }
        try {
            Log.println(level == 1 ? Log.ERROR : level == 2 ? Log.WARN
                    : level == 4 ? Log.DEBUG : Log.INFO, tag, msg);
            File file = new File(LOG_FILE);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write(("[" + System.currentTimeMillis() + "] " + msg + "\n")
                        .getBytes("UTF-8"));
            }
        } catch (Exception ignored) {
        }
    }

    public static void writeActiveStamp(long stamp) {
        try {
            File file = new File(ACTIVE_FILE);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            try (FileOutputStream out = new FileOutputStream(file, false)) {
                out.write(String.valueOf(stamp).getBytes("UTF-8"));
            }
        } catch (Exception e) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{
                        "su", "-c",
                        "mkdir -p /storage/emulated/0/Download/QuarkHook && echo "
                                + stamp + " > " + ACTIVE_FILE
                });
                p.waitFor();
            } catch (Exception ignored) {
            }
        }
    }

    public static long readActiveStamp() {
        File file = new File(ACTIVE_FILE);
        if (file.exists()) {
            try {
                return Long.parseLong(new String(Files.readAllBytes(file.toPath()), "UTF-8").trim());
            } catch (Exception ignored) {
            }
        }
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                    "su", "-c", "cat " + ACTIVE_FILE
            });
            String trim = new String(readAll(process.getInputStream()), "UTF-8").trim();
            process.waitFor();
            return Long.parseLong(trim);
        } catch (Exception ignored) {
            return -1L;
        }
    }

    public static boolean isActive() {
        long stamp = readActiveStamp();
        return stamp > 0 && System.currentTimeMillis() - stamp < 90_000L;
    }

    private static Properties cached() {
        Properties hit = sCachedProperties;
        if (hit != null && SystemClock.elapsedRealtime() - sCacheTimestamp <= CACHE_VALID_MS) {
            return hit;
        }
        return loadAndCache();
    }

    private static Properties loadAndCache() {
        Properties props = load();
        sCachedProperties = props;
        sCacheTimestamp = SystemClock.elapsedRealtime();
        return props;
    }

    private static Properties load() {
        Properties properties = new Properties();
        File file = new File(CFG_FILE);
        boolean loaded = false;
        if (file.exists() && file.length() > 0) {
            try (FileInputStream in = new FileInputStream(file)) {
                properties.load(in);
                loaded = !properties.isEmpty();
            } catch (Exception ignored) {
            }
        }
        if (!loaded) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{
                        "su", "-c", "cat " + CFG_FILE
                });
                properties.load(process.getInputStream());
                process.waitFor();
                loaded = !properties.isEmpty();
            } catch (Exception ignored) {
            }
        }
        // Quark process often cannot write Download/; missing file must not
        // silently disable every feature (original treats missing key = false).
        if (!loaded) {
            applyDefaults(properties);
        }
        return properties;
    }

    private static void applyDefaults(Properties props) {
        props.setProperty(K_ENABLE, "true");
        props.setProperty(K_BLOCK_SECURITY, "true");
        props.setProperty(K_BLOCK_BOOK, "true");
        props.setProperty(K_BLOCK_NAVI, "true");
        props.setProperty(K_BLOCK_USER_CENTER, "true");
        props.setProperty(K_LOCK_SETTINGS, "true");
        props.setProperty(K_BLOCK_SNIFF, "true");
        props.setProperty(K_BLOCK_UPDATE, "true");
        props.setProperty(K_UNLOCK_AV, "true");
        props.setProperty(K_BLOCK_SUMMER_TASK, "true");
        props.setProperty(K_ENABLE_LOG, "false");
        props.setProperty(K_LOG_LEVEL, "3");
    }

    private static void save(Properties properties) {
        try {
            File file = new File(CFG_FILE);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                properties.store(out, "QuarkBypass config");
            }
        } catch (Exception ignored) {
            try {
                StringWriter writer = new StringWriter();
                properties.store(writer, "QuarkBypass config");
                Process process = Runtime.getRuntime().exec(new String[]{
                        "su", "-c", "cat > " + CFG_FILE
                });
                OutputStream output = process.getOutputStream();
                output.write(writer.toString().getBytes("UTF-8"));
                output.flush();
                output.close();
                process.waitFor();
            } catch (Exception e) {
                Log.w(TAG, "save cfg failed: " + e);
            }
        }
    }

    private static byte[] readAll(InputStream inputStream) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = inputStream.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
