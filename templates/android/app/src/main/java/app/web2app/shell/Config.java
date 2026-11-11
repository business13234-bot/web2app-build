package app.web2app.shell;

import android.content.Context;
import android.graphics.Color;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Reads assets/config.json, which the build writes. */
final class Config {
    private static JSONObject cached;

    private Config() {
    }

    static synchronized JSONObject load(Context context) {
        if (cached != null) return cached;
        try (InputStream in = context.getAssets().open("config.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            cached = new JSONObject(out.toString("UTF-8"));
        } catch (Exception e) {
            cached = new JSONObject();
        }
        return cached;
    }

    /** The options of an enabled feature, or null when the feature is off. */
    static JSONObject feature(JSONObject cfg, String id) {
        JSONObject all = cfg.optJSONObject("features");
        if (all == null) return null;
        JSONObject f = all.optJSONObject(id);
        return f != null && f.optBoolean("enabled", false) ? f : null;
    }

    static int color(String value, int fallback) {
        if (value == null || value.isEmpty()) return fallback;
        try {
            return Color.parseColor(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    static boolean isLight(int color) {
        return (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) > 160;
    }

    /** Readable text colour on top of the given background. */
    static int onColor(int bg) {
        return isLight(bg) ? 0xFF15181D : Color.WHITE;
    }

    static int themeColor(Context context) {
        return color(load(context).optString("themeColor", "#1565C0"), 0xFF1565C0);
    }
}
