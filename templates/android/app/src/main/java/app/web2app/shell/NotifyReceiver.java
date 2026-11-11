package app.web2app.shell;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Local notifications the page schedules with Web2AppNotify.schedule(title, text, seconds, url).
 * The list is kept in preferences so it survives restarts and is set up again after a reboot.
 */
public class NotifyReceiver extends BroadcastReceiver {
    static final String CHANNEL = "w2a_local";
    private static final String ACTION = "app.web2app.NOTIFY";
    private static final String PREFS = "w2a_notify";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            for (JSONObject item : items(context)) arm(context, item);
            return;
        }
        if (!ACTION.equals(action)) return;
        int id = intent.getIntExtra("id", -1);
        JSONObject item = take(context, id);
        if (item != null) show(context, item);
    }

    /** Adds a notification; returns its id, or -1 when too many are waiting or the input is bad. */
    static synchronized int add(Context c, String title, String text, long delayMs, String url, int max) {
        if (title == null || title.trim().isEmpty() || delayMs < 0) return -1;
        JSONArray list = load(c);
        if (list.length() >= max) return -1;
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int id = p.getInt("next", 100);
        try {
            JSONObject item = new JSONObject()
                    .put("id", id)
                    .put("at", System.currentTimeMillis() + Math.max(1000, delayMs))
                    .put("title", clip(title, 80))
                    .put("text", clip(text, 300))
                    .put("url", url != null && (url.startsWith("https://") || url.startsWith("http://")) ? url : "");
            list.put(item);
            p.edit().putInt("next", id + 1 >= 1000000 ? 100 : id + 1).putString("list", list.toString()).apply();
            arm(c, item);
            return id;
        } catch (Exception e) {
            return -1;
        }
    }

    static synchronized void cancel(Context c, int id) {
        take(c, id);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pending(c, id));
    }

    static synchronized void cancelAll(Context c) {
        for (JSONObject item : items(c)) cancel(c, item.optInt("id"));
    }

    static synchronized int pendingCount(Context c) {
        return load(c).length();
    }

    private static String clip(String s, int max) {
        if (s == null || "undefined".equals(s) || "null".equals(s)) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static PendingIntent pending(Context c, int id) {
        Intent i = new Intent(c, NotifyReceiver.class).setAction(ACTION).putExtra("id", id);
        return PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void arm(Context c, JSONObject item) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = Math.max(item.optLong("at"), System.currentTimeMillis() + 1000);
        PendingIntent pi = pending(c, item.optInt("id"));
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    private static JSONArray load(Context c) {
        try {
            JSONArray all = new JSONArray(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", "[]"));
            // Drop ones that should have fired long ago (the phone was off, or the app was updated).
            JSONArray keep = new JSONArray();
            long stale = System.currentTimeMillis() - 24 * 3600 * 1000L;
            for (int i = 0; i < all.length(); i++) {
                JSONObject o = all.optJSONObject(i);
                if (o != null && o.optLong("at") > stale) keep.put(o);
            }
            return keep;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static java.util.List<JSONObject> items(Context c) {
        java.util.List<JSONObject> out = new java.util.ArrayList<>();
        JSONArray list = load(c);
        for (int i = 0; i < list.length(); i++) out.add(list.optJSONObject(i));
        return out;
    }

    private static synchronized JSONObject take(Context c, int id) {
        JSONArray list = load(c);
        JSONArray rest = new JSONArray();
        JSONObject found = null;
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o.optInt("id") == id && found == null) found = o;
            else rest.put(o);
        }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("list", rest.toString()).apply();
        return found;
    }

    private static void show(Context context, JSONObject item) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        JSONObject f = Config.feature(Config.load(context), "notifyBridge");
        if (Build.VERSION.SDK_INT >= 26) {
            String name = f != null ? f.optString("channelName", "Reminders") : "Reminders";
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, name.isEmpty() ? "Reminders" : name,
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
        int id = item.optInt("id");
        Intent open = new Intent(context, MainActivity.class);
        String url = item.optString("url", "");
        if (!url.isEmpty()) {
            open.setAction(Intent.ACTION_VIEW);
            open.setData(Uri.parse(url));
        }
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = item.optString("text", "");
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        b.setSmallIcon(R.drawable.w2a_notifications)
                .setColor(Config.themeColor(context))
                .setContentTitle(item.optString("title", ""))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pi);
        try {
            nm.notify(8000 + id, b.build());
        } catch (SecurityException ignored) {
            // notification permission not granted
        }
    }
}
