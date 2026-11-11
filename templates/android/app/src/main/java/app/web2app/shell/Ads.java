package app.web2app.shell;

import android.app.Activity;
import android.view.ViewGroup;

import org.json.JSONObject;

/** Ads are off in this build. (The build swaps in the AdMob version when they are on.) */
final class Ads {
    private Ads() {
    }

    static void init(Activity activity, JSONObject options, ViewGroup topSlot, ViewGroup bottomSlot) {
    }

    static void onPageLoaded(Activity activity) {
    }
}
