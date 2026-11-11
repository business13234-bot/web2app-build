package app.web2app.shell;

import android.app.Activity;

import org.json.JSONObject;

/** Push notifications are off in this build. (The build swaps in the Firebase version when they are on.) */
final class Push {
    private Push() {
    }

    static void init(Activity activity, JSONObject options) {
    }

    static String token() {
        return "";
    }
}
