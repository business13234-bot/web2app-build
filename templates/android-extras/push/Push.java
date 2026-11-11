package app.web2app.shell;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

/** Firebase Cloud Messaging: joins the topic and keeps the device token for the JavaScript bridge. */
final class Push {
    static final String CHANNEL = "w2a_push";
    private static volatile String token = "";

    private Push() {
    }

    static void init(Activity activity, JSONObject options) {
        if (options == null) return;
        createChannel(activity, options.optString("channelName", "General"));
        try {
            FirebaseMessaging fm = FirebaseMessaging.getInstance();
            fm.getToken().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) token = task.getResult();
            });
            String topic = options.optString("topic", "all").trim();
            if (!topic.isEmpty()) fm.subscribeToTopic(topic);
        } catch (Exception ignored) {
            // Firebase not available on this device
        }
    }

    static void createChannel(Context context, String name) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                    name == null || name.isEmpty() ? "General" : name, NotificationManager.IMPORTANCE_HIGH));
        }
    }

    static void setToken(String t) {
        if (t != null) token = t;
    }

    static String token() {
        return token;
    }
}
