package app.web2app.shell;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

import java.util.Map;

/** Shows notifications that arrive while the app is open, and data-only messages. */
public class PushService extends FirebaseMessagingService {

    @Override
    public void onNewToken(String token) {
        Push.setToken(token);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        Map<String, String> data = message.getData();
        String title = null;
        String body = null;
        if (message.getNotification() != null) {
            title = message.getNotification().getTitle();
            body = message.getNotification().getBody();
        }
        if (title == null) title = data.get("title");
        if (body == null) body = data.get("body");
        if (title == null && body == null) return;

        JSONObject options = Config.feature(Config.load(this), "push");
        Push.createChannel(this, options != null ? options.optString("channelName", "General") : "General");

        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        String url = data.get("url");
        if (url != null && options != null && options.optBoolean("openUrl", true)) open.putExtra("url", url);
        PendingIntent pi = PendingIntent.getActivity(this, (int) (System.currentTimeMillis() & 0xFFFF), open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, Push.CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.w2a_notifications)
                .setColor(Config.themeColor(this))
                .setContentTitle(title == null ? getString(R.string.app_name) : title)
                .setContentText(body == null ? "" : body)
                .setStyle(new Notification.BigTextStyle().bigText(body == null ? "" : body))
                .setAutoCancel(true)
                .setContentIntent(pi);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            if (nm != null) nm.notify((int) (System.currentTimeMillis() & 0x7FFFFFFF), b.build());
        } catch (SecurityException ignored) {
            // notification permission not granted
        }
    }
}
