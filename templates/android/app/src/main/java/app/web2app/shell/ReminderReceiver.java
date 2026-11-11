package app.web2app.shell;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONObject;

import java.util.Calendar;

/** The daily reminder: schedules itself and shows the notification. */
public class ReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL = "w2a_reminder";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            schedule(context);
            return;
        }
        JSONObject f = Config.feature(Config.load(context), "reminder");
        if (f == null) return;
        // Set up tomorrow's reminder first, so one failure never stops the daily chain.
        schedule(context);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 7, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        b.setSmallIcon(R.drawable.w2a_notifications)
                .setColor(Config.themeColor(context))
                .setContentTitle(f.optString("title", ""))
                .setContentText(f.optString("text", ""))
                .setStyle(new Notification.BigTextStyle().bigText(f.optString("text", "")))
                .setAutoCancel(true)
                .setContentIntent(pi);
        try {
            nm.notify(7001, b.build());
        } catch (SecurityException ignored) {
            // notification permission not granted
        }
    }

    /** Sets (or resets) the repeating daily alarm. Safe to call many times. */
    static void schedule(Context context) {
        JSONObject f = Config.feature(Config.load(context), "reminder");
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(context, ReminderReceiver.class).setAction("app.web2app.REMINDER");
        PendingIntent pi = PendingIntent.getBroadcast(context, 7, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        if (f == null) {
            am.cancel(pi);
            return;
        }
        String[] hm = f.optString("time", "19:00").split(":");
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, parse(hm, 0, 19));
        c.set(Calendar.MINUTE, parse(hm, 1, 0));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1);
        // A 10 minute window keeps the reminder close to the chosen time without needing the exact-alarm
        // permission (a repeating inexact alarm may arrive many hours late). Each alarm schedules the next.
        am.cancel(pi);
        am.setWindow(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), 10 * 60 * 1000L, pi);
    }

    private static int parse(String[] parts, int i, int fallback) {
        try {
            return Integer.parseInt(parts[i].trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
