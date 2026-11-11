package app.web2app.shell;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

import org.json.JSONObject;

/** Google AdMob: an adaptive banner and an optional full-screen ad every N page loads. */
final class Ads {
    private static JSONObject options;
    private static InterstitialAd interstitial;
    private static int pages;
    private static boolean loading;

    private Ads() {
    }

    static void init(Activity activity, JSONObject opts, ViewGroup topSlot, ViewGroup bottomSlot) {
        if (opts == null) return;
        options = opts;
        MobileAds.initialize(activity, status -> { });
        String bannerId = opts.optString("bannerId", "");
        if (!bannerId.isEmpty()) {
            ViewGroup slot = "top".equals(opts.optString("bannerPosition", "bottom")) ? topSlot : bottomSlot;
            AdView banner = new AdView(activity);
            banner.setAdUnitId(bannerId);
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            int widthDp = (int) (dm.widthPixels / dm.density);
            banner.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp));
            slot.addView(banner, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            slot.setVisibility(View.VISIBLE);
            banner.loadAd(new AdRequest.Builder().build());
        }
        loadInterstitial(activity);
    }

    private static void loadInterstitial(Activity activity) {
        String id = options == null ? "" : options.optString("interstitialId", "");
        if (id.isEmpty() || loading || interstitial != null) return;
        loading = true;
        InterstitialAd.load(activity, id, new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(InterstitialAd ad) {
                loading = false;
                interstitial = ad;
            }

            @Override
            public void onAdFailedToLoad(LoadAdError error) {
                loading = false;
            }
        });
    }

    static void onPageLoaded(Activity activity) {
        if (options == null) return;
        pages++;
        int every = Math.max(2, options.optInt("interstitialEvery", 6));
        if (pages % every == 0 && interstitial != null) {
            InterstitialAd ad = interstitial;
            interstitial = null;
            ad.show(activity);
        }
        loadInterstitial(activity);
    }
}
