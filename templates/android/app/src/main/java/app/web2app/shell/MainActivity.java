package app.web2app.shell;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.HapticFeedbackConstants;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ScrollView;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import android.webkit.MimeTypeMap;
import android.widget.VideoView;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.RippleDrawable;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;
import android.view.ViewTreeObserver;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_STARTUP = 1;
    private static final int REQ_WEB_PERM = 2;
    private static final int REQ_GEO = 3;
    private static final int REQ_FILE = 4;
    private static final int REQ_LOCK = 5;
    private static final int REQ_NOTIFY = 6;

    private JSONObject cfg = new JSONObject();
    private SharedPreferences prefs;
    private String homeUrl = "about:blank";
    private final List<String> internalHosts = new ArrayList<>();
    private final List<String> blockedHosts = new ArrayList<>();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private int theme;
    private int statusColor;
    private int navColor;
    private int pageBg = Color.WHITE;
    private boolean night;

    // views
    private FrameLayout stage;
    private FrameLayout root;
    private BarsDrawable bars;
    private FrameLayout content;
    private WebView web;
    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private ProgressBar spinner;
    private LinearLayout offline;
    private FrameLayout fullscreenHolder;
    private TextView snack;
    private TextView netBannerView;
    private TextView topTitle;
    private LinearLayout bottomNav;
    private final List<View> navItems = new ArrayList<>();
    private final List<String> navUrls = new ArrayList<>();
    private FrameLayout adTop;
    private FrameLayout adBottom;
    private FrameLayout splash;
    private FrameLayout lockView;
    private FrameLayout onboarding;
    private FrameLayout dialog;
    private boolean dialogCancelable;

    // state
    private boolean loadFailed;
    private String failedUrl;
    private volatile String currentUrl = "";
    private boolean firstPageDone;
    private volatile boolean firstPaint; // the first page has something to show
    private long splashShownAt;
    private boolean splashPageReady;
    private boolean splashVideoPlaying;
    private MediaPlayer splashPlayer;
    private boolean splashHidden;
    private boolean locked;
    private long leftAt;
    private boolean skipNextLock;
    private long lastBack;
    private boolean ratePrompted;
    private boolean permsAsked;
    private View swipeHint;
    private boolean wasOffline;
    private ConnectivityManager.NetworkCallback netCallback;
    private String injectCss = "";
    private String injectJs = "";
    private String pageJs = "";
    private static final String LOCAL_HOST = "appassets.androidplatform.net";
    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingSpeech;
    private String pendingLang;
    private ValueAnimator splashPulse;
    private LinearLayout errorView;
    private String httpErrorUrl;
    private long pausedAt;
    private Integer orientationBeforeVideo;

    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private File cameraFile;

    private PermissionRequest pendingWebPerm;
    private String pendingGeoOrigin;
    private GeolocationPermissions.Callback pendingGeoCallback;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    // ---------------------------------------------------------------- setup

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("web2app", MODE_PRIVATE);
        loadConfig();

        if (f("secureScreen") != null) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (f("privacyCover") != null && Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false);
        String orientation = cfg.optString("orientation", "auto");
        if ("portrait".equals(orientation)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if ("landscape".equals(orientation)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        if (cfg.optBoolean("keepScreenOn", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        buildLayout();
        applySystemBars();
        setupWebView();
        registerBackHandler();

        final String start = startUrl(getIntent());
        Runnable load = () -> {
            if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
                web.loadUrl(start);
            }
        };
        if (savedInstanceState == null) clearDataThen(load);
        else load.run();

        if (savedInstanceState == null) {
            showSplash();
            if (f("appLock") != null) lock();
            // Without a splash screen, the page appears complete instead of after a white screen.
            if (splash == null && f("appLock") == null) holdUntilFirstPaint();
        }
        startFeatures();
        maybeAskPermissions();
    }

    /** The first page can be shown: lets the launch screen go (see holdUntilFirstPaint). */
    private void pageReady() {
        if (firstPaint) return;
        firstPaint = true;
        View v = findViewById(android.R.id.content);
        if (v != null) v.post(v::invalidate);
        if (web != null) web.postDelayed(this::learnLaunchColor, 1500);
    }

    /** The colour of the launch screen: the splash colour, or the colour the site showed last time. */
    private int launchColor() {
        if (f("splash") == null && prefs != null && prefs.getBoolean("launchDark", false)) return 0xFF121212;
        return Config.color(cfg.optString("splashColor"), Color.WHITE);
    }

    /**
     * Looks at the page as drawn (the site's own colours, or Android's darkening of it) and keeps
     * whether it is dark, so the next launch screen has the same colour as the page: opening the
     * app then shows one colour, never light then dark or dark then light.
     */
    private void learnLaunchColor() {
        if (f("splash") != null || web == null || web.getWidth() == 0 || web.getHeight() == 0 || isFinishing()) return;
        boolean dark;
        try {
            Bitmap bmp = Bitmap.createBitmap(24, 48, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            c.drawColor(Color.WHITE);
            c.scale(24f / web.getWidth(), 48f / web.getHeight());
            c.translate(-web.getScrollX(), -web.getScrollY());
            web.draw(c);
            double sum = 0;
            int n = 0;
            for (int y = 0; y < 48; y++) {
                for (int x = 0; x < 24; x++) {
                    int p = bmp.getPixel(x, y);
                    sum += (0.2126 * Color.red(p) + 0.7152 * Color.green(p) + 0.0722 * Color.blue(p)) / 255.0;
                    n++;
                }
            }
            bmp.recycle();
            dark = sum / n < 0.4;
        } catch (Throwable t) {
            return;
        }
        if (prefs.getBoolean("launchDark", false) != dark) prefs.edit().putBoolean("launchDark", dark).apply();
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                getSplashScreen().setSplashScreenTheme(dark ? R.style.AppTheme_LaunchDark : R.style.AppTheme_LaunchLight);
            } catch (Throwable ignored) {
                // an older launcher without this: the plain launch colour stays
            }
        }
    }

    /**
     * Keeps the launch screen until the first page has loaded (or 4 seconds, or the offline screen),
     * so the app opens straight onto the site: no blank page, no half-built page with its own loader.
     */
    private void holdUntilFirstPaint() {
        final View v = findViewById(android.R.id.content);
        final long t0 = SystemClock.uptimeMillis();
        v.postDelayed(v::invalidate, 4050); // the check below runs again at the time limit
        v.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (firstPaint || (offline != null && offline.getVisibility() == View.VISIBLE) || SystemClock.uptimeMillis() - t0 > 4000) {
                    v.getViewTreeObserver().removeOnPreDrawListener(this);
                    return true;
                }
                return false;
            }
        });
    }


    private JSONObject f(String id) {
        return Config.feature(cfg, id);
    }

    private void loadConfig() {
        cfg = Config.load(this);
        homeUrl = cfg.optString("url", "about:blank");
        String host = Uri.parse(homeUrl).getHost();
        if (host != null) internalHosts.add(stripWww(host.toLowerCase(Locale.ROOT)));
        addHosts(cfg.optJSONArray("internalHosts"), internalHosts);
        JSONObject deep = f("deepLinks");
        if (deep != null) addHosts(deep.optJSONArray("hosts"), internalHosts);
        JSONObject blocker = f("blocker");
        if (blocker != null) addHosts(blocker.optJSONArray("hosts"), blockedHosts);

        theme = Config.color(cfg.optString("themeColor"), 0xFF1565C0);
        night = f("darkMode") != null && (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        pageBg = night ? 0xFF121212 : Color.WHITE;
        JSONObject sys = f("systemBars");
        JSONObject top = f("topBar");
        int topBg = top != null ? Config.color(top.optString("bg"), theme) : theme;
        statusColor = sys != null ? Config.color(sys.optString("status"), topBg) : topBg;
        int navDefault = f("bottomNav") != null
                ? Config.color(f("bottomNav").optString("bg"), Color.WHITE) : theme;
        if (night && navDefault == Color.WHITE) navDefault = 0xFF1E1F22;
        navColor = sys != null ? Config.color(sys.optString("nav"), navDefault) : navDefault;

        StringBuilder css = new StringBuilder();
        if (f("noSelect") != null) {
            css.append("*{-webkit-user-select:none!important;user-select:none!important;")
                    .append("-webkit-touch-callout:none!important;-webkit-tap-highlight-color:transparent!important}")
                    .append("input,textarea,[contenteditable],[contenteditable] *{-webkit-user-select:text!important;")
                    .append("user-select:text!important}");
        }
        if (f("customCss") != null) css.append(f("customCss").optString("css", ""));
        injectCss = css.toString();
        if (f("customJs") != null) injectJs = f("customJs").optString("js", "");
        pageJs = cfg.optString("pageJs", "");
    }

    private static void addHosts(JSONArray list, List<String> into) {
        if (list == null) return;
        for (int i = 0; i < list.length(); i++) {
            String h = list.optString(i, "").trim().toLowerCase(Locale.ROOT);
            if (!h.isEmpty()) into.add(stripWww(h));
        }
    }

    private static String stripWww(String host) {
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    private Drawable ripple(Drawable content, int color, boolean borderless) {
        int rc = (color & 0x00FFFFFF) | 0x33000000;
        if (borderless) return new RippleDrawable(ColorStateList.valueOf(rc), null, null);
        Drawable mask = content != null ? content : new GradientDrawable();
        if (content == null) ((GradientDrawable) mask).setColor(Color.WHITE);
        return new RippleDrawable(ColorStateList.valueOf(rc), content, mask);
    }

    private static int icon(String name) {
        switch (name == null ? "" : name) {
            case "search": return R.drawable.w2a_search;
            case "shopping_cart": return R.drawable.w2a_shopping_cart;
            case "person": return R.drawable.w2a_person;
            case "favorite": return R.drawable.w2a_favorite;
            case "notifications": return R.drawable.w2a_notifications;
            case "menu": return R.drawable.w2a_menu;
            case "chat": return R.drawable.w2a_chat;
            case "info": return R.drawable.w2a_info;
            case "star": return R.drawable.w2a_star;
            case "settings": return R.drawable.w2a_settings;
            case "call": return R.drawable.w2a_call;
            case "mail": return R.drawable.w2a_mail;
            case "share": return R.drawable.w2a_share;
            case "bookmark": return R.drawable.w2a_bookmark;
            case "location_on": return R.drawable.w2a_location_on;
            case "grid_view": return R.drawable.w2a_grid_view;
            case "play_circle": return R.drawable.w2a_play_circle;
            case "arrow_back": return R.drawable.w2a_arrow_back;
            case "refresh": return R.drawable.w2a_refresh;
            case "lock": return R.drawable.w2a_lock;
            case "wifi_off": return R.drawable.w2a_wifi_off;
            case "close": return R.drawable.w2a_close;
            case "print": return R.drawable.w2a_print;
            case "open_in_new": return R.drawable.w2a_open_in_new;
            case "more_vert": return R.drawable.w2a_more_vert;
            case "fingerprint": return R.drawable.w2a_fingerprint;
            default: return R.drawable.w2a_home;
        }
    }

    private ImageView iconView(String name, int tint, int sizeDp) {
        ImageView v = new ImageView(this);
        v.setImageResource(icon(name));
        v.setImageTintList(ColorStateList.valueOf(tint));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return v;
    }

    /** The app icon with rounded corners, like on the home screen. */
    private ImageView roundLogo(int sizeDp) {
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher_legacy);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        final float r = dp(sizeDp) * 0.23f;
        logo.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        logo.setClipToOutline(true);
        logo.setElevation(dp(4));
        return logo;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    private TextView pillButton(String label, int bg, int fg) {
        TextView b = text(label, 15, fg, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(24), dp(12), dp(24), dp(12));
        b.setMinWidth(dp(120));
        b.setBackground(ripple(rounded(bg, dp(24)), fg, false));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    // --------------------------------------------------------------- layout

    private void buildLayout() {
        stage = new FrameLayout(this);
        root = new FrameLayout(this);
        bars = new BarsDrawable();
        bars.topColor = statusColor;
        bars.bottomColor = navColor;
        bars.midColor = pageBg;
        root.setBackground(bars);
        stage.addView(root, matchParent());

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, matchParent());

        JSONObject top = f("topBar");
        if (top != null) column.addView(buildTopBar(top));

        adTop = new FrameLayout(this);
        adTop.setVisibility(View.GONE);
        column.addView(adTop, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new FrameLayout(this);
        // Behind the page before it draws: the launch colour, so opening the app shows one colour only.
        content.setBackgroundColor(launchColor());
        column.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        adBottom = new FrameLayout(this);
        adBottom.setVisibility(View.GONE);
        column.addView(adBottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        JSONObject nav = f("bottomNav");
        if (nav != null) column.addView(buildBottomNav(nav));

        web = new WebView(this);
        web.setBackgroundColor(launchColor());
        // Password managers (Google, Samsung Pass, 1Password ...) can fill and save logins in the page.
        if (Build.VERSION.SDK_INT >= 26) web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
        web.addJavascriptInterface(new LoginBridge(), "__w2aLogin");
        if (cfg.optBoolean("pullToRefresh", true)) {
            swipe = new SwipeRefreshLayout(this);
            swipe.setColorSchemeColors(loaderColor());
            swipe.addView(web, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            swipe.setOnRefreshListener(() -> {
                tick(swipe);
                web.reload();
            });
            // Most sites scroll inside an element, not the page, so the WebView's own position stays 0.
            // The page tells us (on every touch) whether what is under the finger is at its top;
            // only then can a pull start, and it must be a deliberate pull, like in Chrome.
            swipe.setOnChildScrollUpCallback((parent, child) -> web.getScrollY() > 0 || (!pageAtTop && !loadFailed));
            swipe.setDistanceToTriggerSync(dp(140));
            swipe.setSlingshotDistance(dp(160));
            web.addJavascriptInterface(new PullBridge(), "__w2aPull");
            content.addView(swipe, matchParent());
        } else {
            content.addView(web, matchParent());
        }

        JSONObject loader = f("loader");
        String loaderStyle = loader != null ? loader.optString("style", "bar") : "bar";
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(loaderColor()));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        progress.setVisibility(View.GONE);
        if ("bar".equals(loaderStyle)) {
            content.addView(progress, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP));
        }
        if ("spinner".equals(loaderStyle)) {
            spinner = new ProgressBar(this);
            spinner.setIndeterminateTintList(ColorStateList.valueOf(loaderColor()));
            spinner.setPadding(dp(10), dp(10), dp(10), dp(10));
            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor(night ? 0xFF2A2A2A : Color.WHITE);
            spinner.setBackground(disc);
            spinner.setElevation(dp(4));
            spinner.setVisibility(View.GONE);
            content.addView(spinner, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));
        }

        buildOffline();

        JSONObject fab = f("fab");
        if (fab != null) buildFab(fab);
        if (f("scrollTop") != null) buildToTop(f("scrollTop"));
        if (f("drawer") != null) {
            if (top == null) buildMenuButton();
            buildDrawer(f("drawer"));
        }

        netBannerView = text("", 13, Color.WHITE, true);
        netBannerView.setGravity(Gravity.CENTER);
        netBannerView.setPadding(dp(12), dp(8), dp(12), dp(8));
        netBannerView.setVisibility(View.GONE);
        content.addView(netBannerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        snack = text("", 14, Color.WHITE, false);
        snack.setBackground(rounded(0xF0272A30, dp(14)));
        snack.setPadding(dp(18), dp(12), dp(18), dp(12));
        snack.setGravity(Gravity.CENTER);
        snack.setElevation(dp(6));
        snack.setVisibility(View.GONE);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        slp.bottomMargin = dp(fab != null ? 88 : 24);
        slp.leftMargin = dp(24);
        slp.rightMargin = dp(24);
        content.addView(snack, slp);

        fullscreenHolder = new FrameLayout(this);
        fullscreenHolder.setBackgroundColor(Color.BLACK);
        fullscreenHolder.setVisibility(View.GONE);
        stage.addView(fullscreenHolder, matchParent());

        // Android 15+ draws apps edge to edge: keep the content clear of the system bars and keyboard.
        stage.setOnApplyWindowInsetsListener((v, insets) -> {
            int l, t, r, b;
            boolean ime;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                l = i.left;
                t = i.top;
                r = i.right;
                b = i.bottom;
                ime = insets.isVisible(WindowInsets.Type.ime());
            } else {
                l = insets.getSystemWindowInsetLeft();
                t = insets.getSystemWindowInsetTop();
                r = insets.getSystemWindowInsetRight();
                b = insets.getSystemWindowInsetBottom();
                ime = b > getResources().getDisplayMetrics().heightPixels / 4;
            }
            root.setPadding(l, t, r, b);
            bars.top = t;
            bars.bottom = b;
            bars.invalidateSelf();
            if (bottomNav != null) bottomNav.setVisibility(ime ? View.GONE : View.VISIBLE);
            for (View overlay : new View[]{splash, lockView, onboarding, dialog, gate, sheet}) {
                if (overlay != null) overlay.setPadding(l, t, r, Build.VERSION.SDK_INT >= 30 ? b : 0);
            }
            return insets;
        });

        setContentView(stage);
    }

    private int loaderColor() {
        JSONObject loader = f("loader");
        int fallback = Config.isLight(theme) ? 0xFF3C4148 : theme;
        return loader != null ? Config.color(loader.optString("color"), fallback) : fallback;
    }

    private View buildTopBar(JSONObject o) {
        int bg = Config.color(o.optString("bg"), theme);
        int fg = Config.color(o.optString("fg"), Config.onColor(bg));
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(bg);
        bar.setElevation(dp(3));
        bar.setPadding(dp(4), 0, dp(4), 0);
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        topBarView = bar;
        topBarBg = bg;
        if (f("drawer") != null) bar.addView(barButton("menu", fg, v -> openDrawer()));
        if (o.optBoolean("back", true)) bar.addView(barButton("arrow_back", fg, v -> {
            if (web.canGoBack()) web.goBack();
        }));
        if (o.optBoolean("home", false)) bar.addView(barButton("home", fg, v -> web.loadUrl(homeUrl)));
        topTitle = text(getString(R.string.app_name), 19, fg, true);
        topTitle.setSingleLine(true);
        topTitle.setEllipsize(TextUtils.TruncateAt.END);
        topTitle.setPadding(dp(12), 0, dp(8), 0);
        bar.addView(topTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        // Find, print and share move into a "more" menu when the bar would leave too little room for the title.
        List<String> extraLabels = new ArrayList<>();
        List<Action> extraActions = new ArrayList<>();
        List<String> extraIcons = new ArrayList<>();
        if (f("findInPage") != null) {
            extraLabels.add("Find in page");
            extraActions.add(this::openFind);
            extraIcons.add("search");
        }
        if (f("printPage") != null) {
            extraLabels.add("Print or save as PDF");
            extraActions.add(this::printPage);
            extraIcons.add("print");
        }
        if (o.optBoolean("share", false)) {
            extraLabels.add("Share page");
            extraActions.add(this::sharePage);
            extraIcons.add("share");
        }
        int left = (f("drawer") != null ? 1 : 0) + (o.optBoolean("back", true) ? 1 : 0) + (o.optBoolean("home", false) ? 1 : 0);
        int right = extraLabels.size() + (o.optBoolean("refresh", true) ? 1 : 0);
        boolean overflow = extraLabels.size() >= 2 && left + right > 5;
        if (!overflow) {
            for (int i = 0; i < extraLabels.size(); i++) {
                final Action act = extraActions.get(i);
                if (!"share".equals(extraIcons.get(i))) bar.addView(barButton(extraIcons.get(i), fg, v -> act.run()));
            }
        }
        if (o.optBoolean("refresh", true)) bar.addView(barButton("refresh", fg, v -> {
            v.animate().rotationBy(360).setDuration(500).start();
            web.reload();
        }));
        if (overflow) {
            View more = barButton("more_vert", fg, v -> actionSheet(null, extraLabels, extraActions));
            more.setContentDescription("More");
            bar.addView(more);
        } else if (o.optBoolean("share", false)) {
            bar.addView(barButton("share", fg, v -> sharePage()));
        }
        return bar;
    }

    private View barButton(String name, int fg, View.OnClickListener click) {
        FrameLayout b = new FrameLayout(this);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        b.setBackground(ripple(null, fg, true));
        b.setClickable(true);
        b.setContentDescription(name.replace('_', ' '));
        ImageView i = iconView(name, fg, 24);
        b.addView(i, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        b.setOnClickListener(v -> {
            tick(v);
            click.onClick(v);
        });
        return b;
    }

    private View buildBottomNav(JSONObject o) {
        int bg = Config.color(o.optString("bg"), Color.WHITE);
        if (night && bg == Color.WHITE) bg = 0xFF1E1F22;
        int active = Config.color(o.optString("active"), Config.isLight(bg) ? theme : Color.WHITE);
        int idle = (Config.onColor(bg) & 0x00FFFFFF) | 0x99000000;
        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setBackgroundColor(bg);
        bottomNav.setElevation(dp(8));
        bottomNav.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));
        JSONArray items = o.optJSONArray("items");
        if (items == null) items = new JSONArray();
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
            final String url = it.optString("url", homeUrl);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setBackground(ripple(null, active, true));
            cell.setClickable(true);
            FrameLayout pill = new FrameLayout(this);
            pill.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(30)));
            ImageView ic = iconView(it.optString("icon", "home"), idle, 24);
            pill.addView(ic, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            cell.addView(pill);
            TextView label = text(it.optString("label", ""), 12, idle, true);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(dp(4), dp(3), dp(4), 0);
            cell.addView(label);
            cell.setTag(new int[]{active, idle});
            cell.setOnClickListener(v -> {
                tick(v);
                pill.animate().scaleX(0.86f).scaleY(0.86f).setDuration(90).withEndAction(() ->
                        pill.animate().scaleX(1f).scaleY(1f).setDuration(220)
                                .setInterpolator(new OvershootInterpolator(3f)).start()).start();
                selectNav(navItems.indexOf(v));
                web.loadUrl(url);
            });
            navItems.add(cell);
            navUrls.add(url);
            bottomNav.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        selectNav(0);
        return bottomNav;
    }

    private void selectNav(int index) {
        for (int i = 0; i < navItems.size(); i++) {
            LinearLayout cell = (LinearLayout) navItems.get(i);
            int[] colors = (int[]) cell.getTag();
            boolean on = i == index;
            int c = on ? colors[0] : colors[1];
            FrameLayout pill = (FrameLayout) cell.getChildAt(0);
            ((ImageView) pill.getChildAt(0)).setImageTintList(ColorStateList.valueOf(c));
            ((TextView) cell.getChildAt(1)).setTextColor(c);
            pill.setBackground(on ? rounded((colors[0] & 0x00FFFFFF) | 0x24000000, dp(15)) : null);
        }
    }

    /** Highlights the tab whose link matches the current page best. */
    private void updateNavFor(String url) {
        if (navUrls.isEmpty() || url == null) return;
        int best = -1;
        int bestLen = -1;
        String u = trimSlash(url);
        for (int i = 0; i < navUrls.size(); i++) {
            String n = trimSlash(navUrls.get(i));
            if (linkMatches(u, n) && n.length() > bestLen) {
                best = i;
                bestLen = n.length();
            }
        }
        if (best >= 0) selectNav(best);
    }

    /**
     * True when page u belongs to link n (both without a trailing slash): the same page or one below it.
     * A link to the site root only matches the home page itself, otherwise it would match every page.
     */
    private static boolean linkMatches(String u, String n) {
        String path = Uri.parse(n).getPath();
        boolean root = path == null || path.isEmpty() || path.equals("/");
        if (root) return u.equals(n) || u.startsWith(n + "?") || u.startsWith(n + "#") || u.startsWith(n + "/?")
                || u.startsWith(n + "/#");
        return u.equals(n) || u.startsWith(n + "/") || u.startsWith(n + "?") || u.startsWith(n + "#");
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /** Height of the page's contact bar (callBar) at the bottom, in dp: floating buttons sit above it. */
    private int callBarLift() {
        JSONObject c = f("callBar");
        if (c == null) return 0;
        boolean any = !c.optString("phone").replaceAll("[^0-9+]", "").isEmpty()
                || !c.optString("whatsapp").replaceAll("[^0-9+]", "").isEmpty()
                || !c.optString("address").trim().isEmpty();
        return any ? 52 : 0;
    }

    private void buildFab(JSONObject o) {
        int bg = Config.color(o.optString("color"), theme);
        FrameLayout fab = new FrameLayout(this);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(bg);
        fab.setBackground(ripple(disc, Config.onColor(bg), false));
        fab.setElevation(dp(6));
        fab.setClickable(true);
        fab.setContentDescription(fabLabel(o.optString("action", "url")));
        ImageView ic = iconView(o.optString("icon", "chat"), Config.onColor(bg), 26);
        fab.addView(ic, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        boolean left = "left".equals(o.optString("position", "right"));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(58), dp(58),
                Gravity.BOTTOM | (left ? Gravity.START : Gravity.END));
        lp.setMargins(dp(18), dp(18), dp(18), dp(18 + callBarLift()));
        content.addView(fab, lp);
        fab.setScaleX(0f);
        fab.setScaleY(0f);
        fab.animate().scaleX(1f).scaleY(1f).setStartDelay(700).setDuration(380)
                .setInterpolator(new OvershootInterpolator(2f)).start();
        fab.setOnClickListener(v -> {
            tick(v);
            String action = o.optString("action", "url");
            String value = o.optString("value", "").trim();
            switch (action) {
                case "whatsapp":
                    openExternal(Uri.parse("https://wa.me/" + value.replaceAll("[^0-9]", "")));
                    break;
                case "tel":
                    openExternal(Uri.parse("tel:" + value.replaceAll("[^0-9+]", "")));
                    break;
                case "email":
                    openExternal(Uri.parse("mailto:" + value));
                    break;
                case "share":
                    sharePage();
                    break;
                default:
                    openLink(value);
            }
        });
    }

    private void buildOffline() {
        int fg = night ? 0xFFE8EAED : 0xFF22262B;
        offline = new LinearLayout(this);
        offline.setOrientation(LinearLayout.VERTICAL);
        offline.setGravity(Gravity.CENTER);
        offline.setBackgroundColor(pageBg);
        offline.setPadding(dp(32), dp(32), dp(32), dp(32));
        offline.setVisibility(View.GONE);
        offline.setClickable(true);
        FrameLayout badge = new FrameLayout(this);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor((theme & 0x00FFFFFF) | 0x1F000000);
        badge.setBackground(disc);
        badge.addView(iconView("wifi_off", Config.isLight(theme) ? 0xFF3C4148 : theme, 40),
                new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        offline.addView(badge, new LinearLayout.LayoutParams(dp(96), dp(96)));
        TextView msg = text(cfg.optString("offlineMessage", "No internet connection"), 19, fg, true);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, dp(22), 0, 0);
        offline.addView(msg);
        TextView retry = pillButton(cfg.optString("retryLabel", "Retry"), theme, Config.onColor(theme));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(24);
        offline.addView(retry, rlp);
        retry.setOnClickListener(v -> {
            loadFailed = false;
            web.loadUrl(failedUrl != null ? failedUrl : homeUrl);
        });
        content.addView(offline, matchParent());
    }

    /** Paints the status bar area, the navigation bar area and the sides behind the content. */
    private static final class BarsDrawable extends Drawable {
        int top;
        int bottom;
        int topColor;
        int bottomColor;
        int midColor;
        private final Paint paint = new Paint();

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            paint.setColor(bottomColor);
            c.drawRect(b, paint);
            paint.setColor(midColor);
            c.drawRect(b.left, b.top + top, b.right, b.bottom - bottom, paint);
            paint.setColor(topColor);
            c.drawRect(b.left, b.top, b.right, b.top + top, paint);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    @SuppressWarnings("deprecation")
    private void applySystemBars() {
        Window w = getWindow();
        JSONObject sys = f("systemBars");
        String iconsMode = sys != null ? sys.optString("icons", "auto") : "auto";
        boolean darkIcons = "dark".equals(iconsMode) || ("auto".equals(iconsMode) && Config.isLight(statusColor));
        boolean darkNavIcons = Config.isLight(navColor);
        if (Build.VERSION.SDK_INT < 35) {
            w.setStatusBarColor(statusColor);
            w.setNavigationBarColor(navColor);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.setSystemBarsAppearance(darkIcons ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                c.setSystemBarsAppearance(darkNavIcons ? WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            int flags = 0;
            if (darkIcons) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (darkNavIcons && Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            w.getDecorView().setSystemUiVisibility(flags);
        }
        if (cfg.optBoolean("fullscreen", false)) applyImmersive(true);
    }

    @SuppressWarnings("deprecation")
    private void applyImmersive(boolean on) {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c == null) return;
            if (on) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                c.show(WindowInsets.Type.systemBars());
            }
        } else {
            View d = w.getDecorView();
            int flags = d.getSystemUiVisibility();
            int immersive = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            d.setSystemUiVisibility(on ? (flags | immersive) : (flags & ~immersive));
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && (cfg.optBoolean("fullscreen", false) || customView != null)) applyImmersive(true);
    }

    // ------------------------------------------------------------- overlays

    private void showSplash() {
        JSONObject o = f("splash");
        if (o == null) return;
        int bg = Config.color(cfg.optString("splashColor"), Color.WHITE);
        splash = new FrameLayout(this);
        splash.setBackgroundColor(bg);
        splash.setClickable(true);
        // Optional full-screen image or short video behind the logo.
        boolean contain = "contain".equals(o.optString("fit", "cover"));
        String background = o.optString("background", "color");
        if ("image".equals(background)) {
            int id = getResources().getIdentifier("w2a_splash_bg", "drawable", getPackageName());
            if (id != 0) {
                ImageView art = new ImageView(this);
                art.setImageResource(id);
                art.setScaleType(contain ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
                splash.addView(art, matchParent());
            }
        } else if ("video".equals(background)) {
            int id = getResources().getIdentifier("w2a_splash", "raw", getPackageName());
            if (id != 0) splash.addView(splashVideo(id, contain), matchParent());
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        String style = o.optString("style", "logo_name");
        String anim = o.optString("animation", "pop");
        if ("logo".equals(style) || "logo_name".equals(style)) {
            final ImageView logo = roundLogo(112);
            animateSplashLogo(logo, anim);
            box.addView(logo);
        }
        if ("name".equals(style) || "logo_name".equals(style)) {
            TextView name = text(getString(R.string.app_name), 24, Config.color(o.optString("textColor"), Config.onColor(bg)), true);
            name.setGravity(Gravity.CENTER);
            name.setPadding(dp(24), dp(18), dp(24), 0);
            if (!"none".equals(anim)) {
                name.setAlpha(0f);
                name.setTranslationY(dp(10));
                name.animate().alpha(1f).translationY(0).setStartDelay(220).setDuration(460)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
            box.addView(name);
        }
        splash.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        stage.addView(splash, matchParent());
        splashShownAt = SystemClock.uptimeMillis();
        ui.postDelayed(this::hideSplash, Math.max(1000, o.optInt("maxMs", 4000)));
    }

    /** The logo's entrance on the splash screen: pop in, a gentle pulse, rise and fade in, or none. */
    private void animateSplashLogo(final ImageView logo, String anim) {
        switch (anim) {
            case "none":
                return;
            case "rise":
                logo.setAlpha(0f);
                logo.setTranslationY(dp(28));
                logo.animate().alpha(1f).translationY(0).setDuration(650).setInterpolator(new DecelerateInterpolator(2f)).start();
                return;
            default:
                logo.setAlpha(0f);
                logo.setScaleX(0.6f);
                logo.setScaleY(0.6f);
                logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(560)
                        .setInterpolator(new OvershootInterpolator(1.8f)).start();
                if ("pulse".equals(anim)) {
                    splashPulse = ValueAnimator.ofFloat(1f, 1.07f);
                    splashPulse.setDuration(720);
                    splashPulse.setStartDelay(600);
                    splashPulse.setRepeatMode(ValueAnimator.REVERSE);
                    splashPulse.setRepeatCount(ValueAnimator.INFINITE);
                    splashPulse.addUpdateListener(a -> {
                        float v = (float) a.getAnimatedValue();
                        logo.setScaleX(v);
                        logo.setScaleY(v);
                    });
                    splashPulse.start();
                }
        }
    }

    /** Plays the splash video once, muted, filling or fitting the screen; the splash waits for its end. */
    private View splashVideo(int rawId, boolean contain) {
        final FrameLayout holder = new FrameLayout(this);
        final VideoView video = new VideoView(this);
        holder.addView(video, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        video.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + rawId));
        video.setOnPreparedListener(mp -> {
            splashPlayer = mp;
            mp.setVolume(0f, 0f);
            fitVideo(holder, video, mp.getVideoWidth(), mp.getVideoHeight(), contain);
            video.start();
        });
        video.setOnCompletionListener(mp -> splashVideoEnded());
        video.setOnErrorListener((mp, what, extra) -> {
            splashVideoEnded();
            return true;
        });
        splashVideoPlaying = true;
        return holder;
    }

    /** Cover: the video is sized larger than the screen and centred (the splash clips it). */
    private void fitVideo(FrameLayout holder, VideoView video, int vw, int vh, boolean contain) {
        if (vw <= 0 || vh <= 0) return;
        int w = holder.getWidth();
        int h = holder.getHeight();
        if (w == 0 || h == 0) {
            holder.post(() -> fitVideo(holder, video, vw, vh, contain));
            return;
        }
        float scale = contain ? Math.min((float) w / vw, (float) h / vh) : Math.max((float) w / vw, (float) h / vh);
        video.setLayoutParams(new FrameLayout.LayoutParams(Math.round(vw * scale), Math.round(vh * scale), Gravity.CENTER));
    }

    private void splashVideoEnded() {
        splashVideoPlaying = false;
        maybeHideSplash();
    }

    private void releaseSplashVideo() {
        if (splashPlayer != null) {
            try { splashPlayer.stop(); } catch (Exception ignored) { } // VideoView frees it when removed
            splashPlayer = null;
        }
    }

    private void maybeHideSplash() {
        JSONObject o = f("splash");
        if (splash == null || o == null || splashHidden) return;
        if (splashVideoPlaying) return; // the video's end hides it (or the "at most" time)
        if (!splashPageReady) return;
        long wait = o.optInt("minMs", 600) - (SystemClock.uptimeMillis() - splashShownAt);
        ui.postDelayed(this::hideSplash, Math.max(0, wait));
    }

    private void hideSplash() {
        if (splash == null || splashHidden) return;
        splashHidden = true;
        if (splashPulse != null) {
            splashPulse.cancel();
            splashPulse = null;
        }
        final View s = splash;
        s.animate().alpha(0f).scaleX(1.04f).scaleY(1.04f).setDuration(320).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                stage.removeView(s);
                releaseSplashVideo();
                splash = null;
                if (locked) authenticate();
                else maybeAskPermissions();
            }
        }).start();
    }

    private void showOnboarding() {
        JSONObject o = f("onboarding");
        if (o == null || prefs.getBoolean("onboarded", false)) return;
        JSONArray slides = o.optJSONArray("slides");
        if (slides == null || slides.length() == 0) return;
        int bg = Config.color(o.optString("bg"), theme);
        int fg = Config.onColor(bg);
        onboarding = new FrameLayout(this);
        onboarding.setBackgroundColor(bg);
        onboarding.setClickable(true);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setPadding(dp(32), 0, dp(32), dp(80));
        page.addView(roundLogo(88));
        TextView title = text("", 26, fg, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(28), 0, dp(10));
        page.addView(title);
        TextView body = text("", 16, (fg & 0x00FFFFFF) | 0xCC000000, false);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(0, 1.2f);
        page.addView(body);
        onboarding.addView(page, matchParent());

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(16), dp(12), dp(16), dp(20));
        TextView skip = text("Skip", 15, fg, true);
        skip.setPadding(dp(16), dp(12), dp(16), dp(12));
        skip.setBackground(ripple(null, fg, true));
        bottom.addView(skip);
        LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        bottom.addView(dots, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView next = pillButton("Next", fg, bg);
        next.setMinWidth(dp(110));
        bottom.addView(next);
        onboarding.addView(bottom, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        for (int i = 0; i < slides.length(); i++) {
            View d = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(8), dp(8));
            lp.setMargins(dp(4), 0, dp(4), 0);
            dots.addView(d, lp);
        }
        final int[] index = {0};
        final String last = o.optString("button", "Get started");
        Runnable render = () -> {
            JSONObject s = slides.optJSONObject(index[0]);
            title.setText(s == null ? "" : s.optString("title", ""));
            String t = s == null ? "" : s.optString("text", "");
            body.setText(t);
            body.setVisibility(t.isEmpty() ? View.GONE : View.VISIBLE);
            boolean end = index[0] == slides.length() - 1;
            next.setText(end ? last : "Next");
            skip.setVisibility(end ? View.INVISIBLE : View.VISIBLE);
            for (int i = 0; i < dots.getChildCount(); i++) {
                View d = dots.getChildAt(i);
                boolean on = i == index[0];
                d.setBackground(rounded(on ? fg : (fg & 0x00FFFFFF) | 0x55000000, dp(4)));
                ViewGroup.LayoutParams lp = d.getLayoutParams();
                lp.width = dp(on ? 22 : 8);
                d.setLayoutParams(lp);
            }
        };
        render.run();
        Runnable finish = () -> {
            prefs.edit().putBoolean("onboarded", true).apply();
            final View ob = onboarding;
            ob.animate().alpha(0f).translationY(-dp(24)).setDuration(320).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    stage.removeView(ob);
                    onboarding = null;
                    maybeAskPermissions();
                }
            }).start();
        };
        final Runnable[] go = new Runnable[1];
        go[0] = () -> {
            if (index[0] >= slides.length() - 1) {
                finish.run();
                return;
            }
            page.animate().alpha(0f).translationX(-dp(40)).setDuration(160).withEndAction(() -> {
                index[0]++;
                render.run();
                page.setTranslationX(dp(40));
                page.animate().alpha(1f).translationX(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
            }).start();
        };
        next.setOnClickListener(v -> go[0].run());
        skip.setOnClickListener(v -> finish.run());
        final float[] downX = {0};
        onboarding.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) downX[0] = e.getX();
            if (e.getAction() == MotionEvent.ACTION_UP && downX[0] - e.getX() > dp(60)) go[0].run();
            return true;
        });
        // Below the lock and splash screens.
        int at = stage.indexOfChild(lockView != null ? lockView : splash);
        if (at < 0) stage.addView(onboarding, matchParent());
        else stage.addView(onboarding, at, matchParent());
    }

    /** Mixes two colours: amount 0 = a, 1 = b. */
    private static int blend(int a, int b, float amount) {
        float k = 1f - amount;
        return Color.rgb(Math.round(Color.red(a) * k + Color.red(b) * amount),
                Math.round(Color.green(a) * k + Color.green(b) * amount),
                Math.round(Color.blue(a) * k + Color.blue(b) * amount));
    }

    private void lock() {
        locked = true;
        if (lockView == null) {
            boolean light = Config.isLight(theme);
            int top = light ? theme : blend(theme, Color.WHITE, 0.10f);
            int bottom = light ? blend(theme, Color.BLACK, 0.14f) : blend(theme, Color.BLACK, 0.50f);
            int fg = light ? 0xFF15181D : Color.WHITE;
            lockView = new FrameLayout(this);
            lockView.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom}));
            lockView.setClickable(true);

            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            box.setPadding(dp(32), 0, dp(32), 0);

            ImageView logo = roundLogo(92);
            logo.setElevation(dp(12));
            box.addView(logo);

            TextView name = text(getString(R.string.app_name), 24, fg, true);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, dp(22), 0, 0);
            box.addView(name);

            JSONObject o = f("appLock");
            String title = o != null ? o.optString("title", "Unlock") : "Unlock";
            TextView sub = text("Locked for your privacy", 14, (fg & 0x00FFFFFF) | 0xB3000000, false);
            sub.setGravity(Gravity.CENTER);
            sub.setPadding(0, dp(6), 0, 0);
            box.addView(sub);

            // Round fingerprint button, the main way in.
            FrameLayout fp = new FrameLayout(this);
            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor((fg & 0x00FFFFFF) | 0x1F000000);
            disc.setStroke(dp(1), (fg & 0x00FFFFFF) | 0x40000000);
            fp.setBackground(ripple(disc, fg, false));
            fp.addView(iconView("fingerprint", fg, 40), new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
            fp.setContentDescription(title);
            fp.setClickable(true);
            fp.setOnClickListener(v -> authenticate());
            LinearLayout.LayoutParams fpl = new LinearLayout.LayoutParams(dp(84), dp(84));
            fpl.topMargin = dp(56);
            box.addView(fp, fpl);

            TextView unlock = pillButton(title, fg, light ? Color.WHITE : bottom);
            unlock.setMinWidth(dp(220));
            unlock.setOnClickListener(v -> authenticate());
            LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            ul.topMargin = dp(28);
            box.addView(unlock, ul);

            lockView.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            int at = splash != null ? stage.indexOfChild(splash) : -1;
            if (at < 0) stage.addView(lockView, matchParent());
            else stage.addView(lockView, at, matchParent());

            // A calm entrance: the logo settles, the rest fades up after it.
            logo.setScaleX(0.8f);
            logo.setScaleY(0.8f);
            logo.setAlpha(0f);
            logo.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(480).setInterpolator(new OvershootInterpolator(1.4f)).start();
            for (int i = 1; i < box.getChildCount(); i++) {
                View c = box.getChildAt(i);
                c.setAlpha(0f);
                c.setTranslationY(dp(12));
                c.animate().alpha(1f).translationY(0).setStartDelay(120 + i * 50L).setDuration(380)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
        }
        lockView.setAlpha(1f);
        lockView.setVisibility(View.VISIBLE);
        if (splash == null) ui.postDelayed(this::authenticate, 250);
    }

    private void unlock() {
        locked = false;
        maybeAskPermissions();
        if (lockView == null) return;
        final View v = lockView;
        v.animate().alpha(0f).setDuration(260).withEndAction(() -> v.setVisibility(View.GONE)).start();
    }

    @SuppressWarnings("deprecation")
    private void authenticate() {
        if (!locked || isFinishing()) return;
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure()) {
            // No screen lock on this phone, so there is nothing to check against.
            unlock();
            return;
        }
        JSONObject o = f("appLock");
        String title = o != null ? o.optString("title", "Unlock") : "Unlock";
        if (Build.VERSION.SDK_INT >= 29) {
            BiometricPrompt.Builder b = new BiometricPrompt.Builder(this)
                    .setTitle(title)
                    .setSubtitle(getString(R.string.app_name));
            if (Build.VERSION.SDK_INT >= 30) {
                b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            } else {
                b.setDeviceCredentialAllowed(true);
            }
            try {
                b.build().authenticate(new CancellationSignal(), getMainExecutor(),
                        new BiometricPrompt.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                                unlock();
                            }
                        });
            } catch (Exception e) {
                unlock();
            }
        } else {
            Intent i = km.createConfirmDeviceCredentialIntent(title, getString(R.string.app_name));
            if (i == null) {
                unlock();
                return;
            }
            skipNextLock = true;
            startActivityForResult(i, REQ_LOCK);
        }
    }

    private interface Action {
        void run();
    }

    /** Our own dialog: a card over a dimmed page. */
    private void showDialog(String title, String message, String ok, String cancel, boolean cancelable,
                            Action onOk, Action onCancel) {
        closeDialog();
        dialogCancelable = cancelable;
        int fg = night ? 0xFFE8EAED : 0xFF1B1F24;
        dialog = new FrameLayout(this);
        dialog.setBackgroundColor(0x80000000);
        dialog.setClickable(true);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(night ? 0xFF24272B : Color.WHITE, dp(24)));
        card.setPadding(dp(24), dp(24), dp(16), dp(12));
        card.setElevation(dp(12));
        TextView t = text(title, 20, fg, true);
        card.addView(t);
        if (message != null && !message.isEmpty()) {
            TextView m = text(message, 15, (fg & 0x00FFFFFF) | 0xBB000000, false);
            m.setPadding(0, dp(10), dp(8), 0);
            m.setLineSpacing(0, 1.15f);
            card.addView(m);
        }
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        row.setPadding(0, dp(18), 0, 0);
        int accent = Config.isLight(theme) ? 0xFF2B6CB0 : theme;
        if (night && !Config.isLight(theme)) accent = 0xFF8AB4F8;
        if (cancel != null) {
            TextView c = text(cancel, 15, (fg & 0x00FFFFFF) | 0xAA000000, true);
            c.setPadding(dp(16), dp(10), dp(16), dp(10));
            c.setBackground(ripple(null, fg, true));
            c.setOnClickListener(v -> {
                closeDialog();
                if (onCancel != null) onCancel.run();
            });
            row.addView(c);
        }
        TextView o = text(ok, 15, accent, true);
        o.setPadding(dp(16), dp(10), dp(16), dp(10));
        o.setBackground(ripple(null, accent, true));
        o.setOnClickListener(v -> {
            if (cancelable || cancel != null) closeDialog();
            if (onOk != null) onOk.run();
        });
        row.addView(o);
        card.addView(row);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.min(dp(340),
                getResources().getDisplayMetrics().widthPixels - dp(48)), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        dialog.addView(card, lp);
        dialog.setOnClickListener(v -> {
            if (dialogCancelable) closeDialog();
        });
        card.setClickable(true);
        stage.addView(dialog, matchParent());
        dialog.setAlpha(0f);
        dialog.animate().alpha(1f).setDuration(180).start();
        card.setScaleX(0.9f);
        card.setScaleY(0.9f);
        card.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(1.4f)).start();
    }

    private void closeDialog() {
        if (dialog == null) return;
        final View d = dialog;
        dialog = null;
        d.animate().alpha(0f).setDuration(150).withEndAction(() -> stage.removeView(d)).start();
    }

    private final Runnable hideSnack = () -> {
        if (snack != null) snack.animate().alpha(0f).translationY(dp(12)).setDuration(200)
                .withEndAction(() -> snack.setVisibility(View.GONE)).start();
    };

    /** A short message at the bottom of the screen, in the app's own style. */
    void showSnack(String message) {
        if (snack == null || message == null || message.isEmpty()) return;
        ui.removeCallbacks(hideSnack);
        snack.setText(message);
        if (snack.getVisibility() != View.VISIBLE) {
            snack.setAlpha(0f);
            snack.setTranslationY(dp(12));
            snack.setVisibility(View.VISIBLE);
        }
        snack.animate().alpha(1f).translationY(0).setDuration(200).start();
        ui.postDelayed(hideSnack, Math.min(5000, 1800 + message.length() * 40L));
    }

    private void showNetBanner(boolean online) {
        JSONObject o = f("netBanner");
        if (o == null || netBannerView == null) return;
        netBannerView.setText(online ? o.optString("online", "Back online") : o.optString("offline", "You are offline"));
        netBannerView.setBackgroundColor(online ? 0xFF1E8E3E : 0xFFC5221F);
        netBannerView.setVisibility(View.VISIBLE);
        netBannerView.setTranslationY(-dp(40));
        netBannerView.animate().translationY(0).setDuration(220).start();
        if (online) {
            ui.postDelayed(() -> netBannerView.animate().translationY(-dp(40)).setDuration(220)
                    .withEndAction(() -> netBannerView.setVisibility(View.GONE)).start(), 2200);
        }
    }

    // ------------------------------------------------------------- features

    private void startFeatures() {
        if (f("reminder") != null) ReminderReceiver.schedule(this);
        if (f("shortcuts") != null) setupShortcuts(f("shortcuts"));
        if (f("push") != null) Push.init(this, f("push"));
        if (f("admob") != null) Ads.init(this, f("admob"), adTop, adBottom);
        if (f("onboarding") != null) showOnboarding();
        if (f("updatePrompt") != null) checkForUpdate(f("updatePrompt"));
        if (f("rateApp") != null) prefs.edit().putInt("launches", prefs.getInt("launches", 0) + 1).apply();
        if (f("netBanner") != null || f("offlineCache") != null) watchNetwork();
        if (f("noticeBar") != null) loadNotice(f("noticeBar"));
    }

    private void setupShortcuts(JSONObject o) {
        if (Build.VERSION.SDK_INT < 25) return;
        ShortcutManager sm = getSystemService(ShortcutManager.class);
        JSONArray items = o.optJSONArray("items");
        if (sm == null || items == null) return;
        List<ShortcutInfo> list = new ArrayList<>();
        for (int i = 0; i < items.length() && i < 4; i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(it.optString("url", homeUrl)), this, MainActivity.class);
            open.putExtra("w2a_trusted", true);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            String label = it.optString("label", "Open");
            list.add(new ShortcutInfo.Builder(this, "w2a_" + i)
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(shortcutIcon(it.optString("icon", "star")))
                    .setIntent(open)
                    .build());
        }
        try {
            sm.setDynamicShortcuts(list);
        } catch (Exception ignored) {
            // launcher refused (rate limited)
        }
    }

    private Icon shortcutIcon(String name) {
        int size = dp(108);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(theme);
        Drawable d = getDrawable(icon(name));
        if (d != null) {
            d = d.mutate();
            d.setTint(Config.onColor(theme));
            int g = dp(44);
            int off = (size - g) / 2;
            d.setBounds(off, off, off + g, off + g);
            d.draw(c);
        }
        return Build.VERSION.SDK_INT >= 26 ? Icon.createWithAdaptiveBitmap(bmp) : Icon.createWithBitmap(bmp);
    }

    private long appVersionCode() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) {
            return cfg.optLong("versionCode", 1);
        }
    }

    private String appVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return cfg.optString("versionName", "1.0");
        }
    }

    private void checkForUpdate(JSONObject o) {
        final String checkUrl = o.optString("checkUrl", "");
        if (checkUrl.isEmpty()) return;
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(checkUrl).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setUseCaches(false);
                if (c.getResponseCode() != 200) return;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream in = c.getInputStream()) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0 && out.size() < 65536) out.write(buf, 0, n);
                }
                JSONObject info = new JSONObject(out.toString("UTF-8"));
                long latest = info.optLong("versionCode", info.optLong("android", 0));
                if (latest <= appVersionCode()) return;
                String message = info.optString("message", o.optString("message", "A new version is available."));
                String storeUrl = info.optString("url", "https://play.google.com/store/apps/details?id=" + getPackageName());
                boolean force = o.optBoolean("force", false) || info.optBoolean("force", false);
                ui.postDelayed(() -> showUpdate(message, storeUrl, force), 1500);
            } catch (Exception ignored) {
                // offline or bad file: try again next start
            }
        }).start();
    }

    private void showUpdate(String message, String storeUrl, boolean force) {
        if (isFinishing()) return;
        showDialog("Update available", message, "Update", force ? null : "Later", !force, () -> {
            openStore(storeUrl);
            if (force) ui.postDelayed(() -> showUpdate(message, storeUrl, true), 800);
        }, null);
    }

    private void openStore(String url) {
        Uri market = Uri.parse("market://details?id=" + getPackageName());
        if (url != null && url.contains("play.google.com")) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, market);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            } catch (ActivityNotFoundException ignored) {
                // no Play Store: use the web page
            }
        }
        openExternal(Uri.parse(url));
    }

    private void maybeAskRating() {
        JSONObject o = f("rateApp");
        if (o == null || ratePrompted || prefs.getBoolean("rated", false)) return;
        int launches = prefs.getInt("launches", 0);
        int asked = prefs.getInt("rateAsks", 0);
        if (asked >= 3 || launches < o.optInt("launches", 5) * (asked + 1)) return;
        ratePrompted = true;
        ui.postDelayed(() -> {
            if (isFinishing() || dialog != null || locked) return;
            prefs.edit().putInt("rateAsks", prefs.getInt("rateAsks", 0) + 1).putInt("launches", 0).apply();
            showDialog(o.optString("title", "Enjoying the app?"), o.optString("message", ""), "Rate now", "Later",
                    true, () -> {
                        prefs.edit().putBoolean("rated", true).apply();
                        openStore("https://play.google.com/store/apps/details?id=" + getPackageName());
                    }, null);
        }, 2500);
    }

    private void watchNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        netCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                ui.post(() -> {
                    setOfflineMode(false);
                    if (wasOffline) {
                        wasOffline = false;
                        showNetBanner(true);
                        if (offline.getVisibility() == View.VISIBLE) {
                            loadFailed = false;
                            web.loadUrl(failedUrl != null ? failedUrl : homeUrl);
                        }
                    }
                });
            }

            @Override
            public void onLost(Network network) {
                ui.postDelayed(() -> {
                    if (cm.getActiveNetwork() == null && !wasOffline) {
                        wasOffline = true;
                        setOfflineMode(true);
                        showNetBanner(false);
                    }
                }, 600);
            }
        };
        try {
            cm.registerDefaultNetworkCallback(netCallback);
        } catch (Exception e) {
            netCallback = null;
        }
    }

    private void clearDataThen(Runnable next) {
        JSONObject o = f("clearOnStart");
        if (o == null) {
            next.run();
            return;
        }
        web.clearCache(true);
        if (!"all".equals(o.optString("what", "cache"))) {
            next.run();
            return;
        }
        WebStorage.getInstance().deleteAllData();
        prefs.edit().remove("lastUrl").apply();
        CookieManager.getInstance().removeAllCookies(done -> next.run());
    }

    private void sharePage() {
        String url = web.getUrl();
        if (url == null || url.isEmpty()) url = homeUrl;
        shareText(url, web.getTitle());
    }

    private void shareText(String text, String subject) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        if (subject != null) send.putExtra(Intent.EXTRA_SUBJECT, subject);
        skipNextLock = true;
        try {
            startActivity(Intent.createChooser(send, null));
        } catch (Exception ignored) {
            // nothing can share
        }
    }

    // ------------------------------------------------ features added in 1.2

    private View topBarView;
    private int topBarBg;
    private int pageThemeColor;
    private View toTop;
    private boolean toTopShown;
    private FrameLayout drawerScrim;
    private LinearLayout drawerPanel;
    private final List<View> drawerRows = new ArrayList<>();
    private final List<String> drawerUrls = new ArrayList<>();
    private LinearLayout findBar;
    private EditText findInput;
    private TextView findCount;
    private View noticeView;
    private FrameLayout gate;
    private FrameLayout sheet;
    private WebView sheetWeb;
    private TextView sheetTitle;
    private TextView sheetHost;
    private ProgressBar sheetProgress;
    private FrameLayout menuScrim;
    private boolean announcementQueued;
    private boolean offlineMode;
    private boolean clearHistoryNext;
    private boolean brightOn;
    private long lastTouch = SystemClock.uptimeMillis();
    private SensorManager sensors;
    private SensorEventListener shakeListener;

    private static final String TAP_JS = "(function(){if(window.__w2aTapOn)return;window.__w2aTapOn=1;"
            + "document.addEventListener('click',function(e){var t=e.target;"
            + "t=t&&t.closest?t.closest('a,button,[role=button],input[type=submit],input[type=button],summary,label'):null;"
            + "if(t&&window.__w2aTap)window.__w2aTap.tap();},true);})();";

    /** A light tap feel on the app's own buttons (haptic feedback feature). */
    @SuppressWarnings("deprecation")
    private void tick(View v) {
        if (v == null || f("haptics") == null) return;
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
    }

    /** Pull to refresh: is the scroll area under the finger at its top? (see PULL_JS) */
    private volatile boolean pageAtTop = true;

    private class PullBridge {
        @JavascriptInterface
        public void atTop(boolean top) {
            pageAtTop = top;
        }
    }

    /** On each touch: finds the scrolling element under the finger and reports if it is at its top. */
    private static final String PULL_JS = "(function(){if(window.__w2aPullOn)return;window.__w2aPullOn=1;"
            + "function top(t){for(var e=t;e&&e.nodeType===1&&e!==document.body&&e!==document.documentElement;e=e.parentElement){"
            + "var o=getComputedStyle(e).overflowY;if((o==='auto'||o==='scroll'||o==='overlay')&&e.scrollHeight>e.clientHeight+1)"
            + "return e.scrollTop<=0;}var r=document.scrollingElement||document.documentElement;return !r||r.scrollTop<=0;}"
            + "document.addEventListener('touchstart',function(ev){try{__w2aPull.atTop(ev.touches.length===1&&top(ev.target));}"
            + "catch(x){}},{passive:true,capture:true});})();";

    private class TapBridge {
        @JavascriptInterface
        public void tap() {
            ui.post(() -> tick(web));
        }
    }

    private boolean pageTrusted() {
        String u = currentUrl;
        return u != null && !u.isEmpty() && isInternal(Uri.parse(u));
    }

    /** Opens a link: pages of the app's site in the app, others in the in-app browser or the phone's browser. */
    private void openLink(String url) {
        if (url == null || url.trim().isEmpty()) return;
        Uri u = Uri.parse(url.trim());
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
        boolean web2 = scheme.equals("http") || scheme.equals("https");
        if (web2 && isInternal(u)) web.loadUrl(u.toString());
        else if (web2 && f("inAppBrowser") != null) openInApp(u);
        else openExternal(u);
    }

    private void setStatusIcons(boolean dark) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) c.setSystemBarsAppearance(dark ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        } else {
            View d = getWindow().getDecorView();
            int flags = d.getSystemUiVisibility();
            d.setSystemUiVisibility(dark ? flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    : flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private boolean defaultStatusIconsDark() {
        JSONObject sys = f("systemBars");
        String mode = sys != null ? sys.optString("icons", "auto") : "auto";
        return "dark".equals(mode) || ("auto".equals(mode) && Config.isLight(statusColor));
    }

    // ---- back to top

    private void buildToTop(JSONObject o) {
        FrameLayout b = new FrameLayout(this);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(night ? 0xFF2A2D31 : Color.WHITE);
        b.setBackground(ripple(disc, 0xFF000000, false));
        b.setElevation(dp(6));
        b.setClickable(true);
        b.setContentDescription("Back to top");
        ImageView arrow = iconView("arrow_back", Config.isLight(theme) ? 0xFF3C4148 : theme, 22);
        arrow.setRotation(90f);
        b.addView(arrow, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        String pos = o.optString("position", "right");
        int side = "left".equals(pos) ? Gravity.START : "center".equals(pos) ? Gravity.CENTER_HORIZONTAL : Gravity.END;
        JSONObject fab = f("fab");
        boolean fabLeft = fab != null && "left".equals(fab.optString("position", "right"));
        boolean underFab = fab != null && !"center".equals(pos) && fabLeft == "left".equals(pos);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.BOTTOM | side);
        lp.setMargins(dp(24), dp(24), dp(24), dp((underFab ? 92 : 24) + callBarLift()));
        content.addView(b, lp);
        b.setVisibility(View.GONE);
        b.setOnClickListener(v -> {
            tick(v);
            web.evaluateJavascript("window.scrollTo({top:0,behavior:'smooth'})", null);
            ui.postDelayed(() -> {
                if (web.getScrollY() > 0) web.scrollTo(0, 0);
            }, 900);
        });
        toTop = b;
    }

    private void showToTop(boolean show) {
        if (toTop == null || show == toTopShown) return;
        toTopShown = show;
        toTop.animate().cancel();
        if (show) {
            toTop.setVisibility(View.VISIBLE);
            toTop.setAlpha(0f);
            toTop.setScaleX(0.6f);
            toTop.setScaleY(0.6f);
            toTop.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220)
                    .setInterpolator(new OvershootInterpolator(2f)).start();
        } else {
            toTop.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(160)
                    .withEndAction(() -> toTop.setVisibility(View.GONE)).start();
        }
    }

    // ---- side menu

    private void buildMenuButton() {
        FrameLayout b = new FrameLayout(this);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(night ? 0xEE2A2D31 : 0xEEFFFFFF);
        b.setBackground(ripple(disc, 0xFF000000, false));
        b.setElevation(dp(4));
        b.setClickable(true);
        b.setContentDescription("menu");
        b.addView(iconView("menu", night ? 0xFFE8EAED : 0xFF3C4148, 22),
                new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.START);
        lp.setMargins(dp(12), dp(12), 0, 0);
        content.addView(b, lp);
        b.setOnClickListener(v -> {
            tick(v);
            openDrawer();
        });
    }

    private void buildDrawer(JSONObject o) {
        int panelBg = night ? 0xFF1E1F22 : Color.WHITE;
        int fg = night ? 0xFFE8EAED : 0xFF1B1F24;
        drawerScrim = new FrameLayout(this);
        drawerScrim.setBackgroundColor(0x73000000);
        drawerScrim.setVisibility(View.GONE);
        drawerScrim.setClickable(true);
        drawerScrim.setOnClickListener(v -> closeDrawer());

        int width = Math.min(dp(304), (int) (getResources().getDisplayMetrics().widthPixels * 0.82f));
        drawerPanel = new LinearLayout(this);
        drawerPanel.setOrientation(LinearLayout.VERTICAL);
        drawerPanel.setBackgroundColor(panelBg);
        drawerPanel.setElevation(dp(16));
        drawerPanel.setClickable(true);
        if (o.optBoolean("header", true)) {
            int hb = Config.color(o.optString("headerColor"), theme);
            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.VERTICAL);
            head.setBackgroundColor(hb);
            head.setPadding(dp(20), dp(24), dp(20), dp(20));
            head.addView(roundLogo(56));
            TextView name = text(getString(R.string.app_name), 19, Config.onColor(hb), true);
            name.setPadding(0, dp(14), 0, 0);
            head.addView(name);
            drawerPanel.addView(head);
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        scroll.addView(list);
        drawerPanel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        JSONArray items = o.optJSONArray("items");
        int accent = Config.isLight(theme) ? 0xFF2B6CB0 : theme;
        if (night && !Config.isLight(theme)) accent = 0xFF8AB4F8;
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
            final String url = it.optString("url", homeUrl);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), 0, dp(16), 0);
            row.setBackground(ripple(rounded(Color.TRANSPARENT, dp(26)), fg, false));
            row.setClickable(true);
            row.addView(iconView(it.optString("icon", "star"), (fg & 0x00FFFFFF) | 0xB3000000, 22));
            TextView label = text(it.optString("label", ""), 15, fg, true);
            label.setPadding(dp(18), 0, 0, 0);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(label);
            row.setTag(new int[]{accent, fg});
            row.setOnClickListener(v -> {
                tick(v);
                closeDrawer();
                ui.postDelayed(() -> openLink(url), 180);
            });
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
            rlp.bottomMargin = dp(2);
            list.addView(row, rlp);
            drawerRows.add(row);
            drawerUrls.add(url);
        }
        // Inside the padded root: below the status bar, over the top bar and page.
        root.addView(drawerScrim, matchParent());
        drawerScrim.addView(drawerPanel, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START));
    }

    private void openDrawer() {
        if (drawerScrim == null) return;
        // Highlight the link of the page that is open.
        String u = trimSlash(web.getUrl() == null ? "" : web.getUrl());
        int best = -1;
        int bestLen = -1;
        for (int i = 0; i < drawerUrls.size(); i++) {
            String n = trimSlash(drawerUrls.get(i));
            if (linkMatches(u, n) && n.length() > bestLen) {
                best = i;
                bestLen = n.length();
            }
        }
        for (int i = 0; i < drawerRows.size(); i++) {
            LinearLayout row = (LinearLayout) drawerRows.get(i);
            int[] c = (int[]) row.getTag();
            boolean on = i == best;
            row.setBackground(ripple(rounded(on ? (c[0] & 0x00FFFFFF) | 0x1F000000 : Color.TRANSPARENT, dp(26)), c[1], false));
            ((ImageView) row.getChildAt(0)).setImageTintList(ColorStateList.valueOf(on ? c[0] : (c[1] & 0x00FFFFFF) | 0xB3000000));
            ((TextView) row.getChildAt(1)).setTextColor(on ? c[0] : c[1]);
        }
        drawerScrim.bringToFront();
        drawerScrim.setVisibility(View.VISIBLE);
        drawerScrim.setAlpha(0f);
        drawerScrim.animate().alpha(1f).setDuration(200).start();
        drawerPanel.setTranslationX(-drawerPanel.getLayoutParams().width);
        drawerPanel.animate().translationX(0).setDuration(260).setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    private void closeDrawer() {
        if (drawerScrim == null || drawerScrim.getVisibility() != View.VISIBLE) return;
        drawerPanel.animate().translationX(-drawerPanel.getWidth()).setDuration(200).start();
        drawerScrim.animate().alpha(0f).setDuration(220).withEndAction(() -> drawerScrim.setVisibility(View.GONE)).start();
    }

    // ---- find in page

    private void openFind() {
        int fg = night ? 0xFFE8EAED : 0xFF1B1F24;
        if (findBar == null) {
            findBar = new LinearLayout(this);
            findBar.setGravity(Gravity.CENTER_VERTICAL);
            findBar.setBackgroundColor(night ? 0xFF24272B : Color.WHITE);
            findBar.setElevation(dp(6));
            findBar.setPadding(dp(16), 0, dp(4), 0);
            findBar.setClickable(true);
            findInput = new EditText(this);
            findInput.setHint("Find in page");
            findInput.setSingleLine(true);
            findInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            findInput.setTextColor(fg);
            findInput.setHintTextColor((fg & 0x00FFFFFF) | 0x80000000);
            findInput.setBackground(null);
            findInput.setInputType(InputType.TYPE_CLASS_TEXT);
            findInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            findInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() == 0) {
                        web.clearMatches();
                        findCount.setText("");
                    } else {
                        web.findAllAsync(s.toString());
                    }
                }
            });
            findInput.setOnEditorActionListener((v, id, ev) -> {
                web.findNext(true);
                return true;
            });
            findBar.addView(findInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            findCount = text("", 13, (fg & 0x00FFFFFF) | 0x99000000, false);
            findCount.setPadding(dp(8), 0, dp(4), 0);
            findBar.addView(findCount);
            View up = barButton("arrow_back", fg, v -> web.findNext(false));
            ((ViewGroup) up).getChildAt(0).setRotation(90f);
            up.setContentDescription("Previous");
            View down = barButton("arrow_back", fg, v -> web.findNext(true));
            ((ViewGroup) down).getChildAt(0).setRotation(-90f);
            down.setContentDescription("Next");
            View close = barButton("close", fg, v -> closeFind());
            close.setContentDescription("Close find");
            findBar.addView(up);
            findBar.addView(down);
            findBar.addView(close);
            content.addView(findBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56), Gravity.TOP));
            web.setFindListener((active, count, done) -> {
                if (!done || findInput.getText().length() == 0) return;
                findCount.setText(count == 0 ? "0/0" : (active + 1) + "/" + count);
            });
        }
        findBar.bringToFront();
        findBar.setVisibility(View.VISIBLE);
        findBar.setTranslationY(-dp(56));
        findBar.animate().translationY(0).setDuration(200).start();
        findInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(findInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void closeFind() {
        if (findBar == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(findInput.getWindowToken(), 0);
        web.clearMatches();
        findInput.setText("");
        findBar.animate().translationY(-dp(56)).setDuration(160).withEndAction(() -> findBar.setVisibility(View.GONE)).start();
    }

    // ---- print

    private void printPage() {
        PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        if (pm == null) return;
        String title = web.getTitle();
        String name = title != null && !title.isEmpty() && !title.startsWith("http") ? title : getString(R.string.app_name);
        skipNextLock = true;
        try {
            pm.print(name, web.createPrintDocumentAdapter(name), new PrintAttributes.Builder().build());
        } catch (Exception e) {
            showSnack("Printing is not available");
        }
    }

    // ---- live notice bar

    private static JSONObject fetchJson(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setUseCaches(false);
            if (c.getResponseCode() != 200) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < 65536) out.write(buf, 0, n);
            }
            return new JSONObject(out.toString("UTF-8"));
        } catch (Exception e) {
            return null;
        }
    }

    private void loadNotice(JSONObject o) {
        final String url = o.optString("checkUrl", "");
        if (url.isEmpty()) return;
        new Thread(() -> {
            JSONObject info = fetchJson(url);
            if (info == null) return;
            String message = info.optString("message", "").trim();
            String id = info.optString("id", message);
            String link = info.optString("link", "");
            if (message.isEmpty()) return;
            if (o.optBoolean("dismissible", true) && id.equals(prefs.getString("noticeClosed", null))) return;
            ui.post(() -> showNotice(o, id, message, link));
        }).start();
    }

    private void showNotice(JSONObject o, String id, String message, String link) {
        if (isFinishing() || noticeView != null) return;
        int bg = Config.color(o.optString("color"), theme);
        int fg = Config.onColor(bg);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(ripple(new android.graphics.drawable.ColorDrawable(bg), fg, false));
        bar.setPadding(dp(16), 0, dp(4), 0);
        bar.setMinimumHeight(dp(44));
        TextView t = text(link.isEmpty() ? message : message + "  ›", 14, fg, true);
        t.setPadding(0, dp(10), dp(8), dp(10));
        t.setMaxLines(3);
        t.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!link.isEmpty()) {
            bar.setClickable(true);
            bar.setOnClickListener(v -> {
                tick(v);
                openLink(link);
            });
        }
        if (o.optBoolean("dismissible", true)) {
            View x = barButton("close", fg, v -> {
                prefs.edit().putString("noticeClosed", id).apply();
                bar.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                    ((ViewGroup) bar.getParent()).removeView(bar);
                }).start();
            });
            x.setContentDescription("Close notice");
            x.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
            bar.addView(x);
        }
        noticeView = bar;
        LinearLayout column = (LinearLayout) content.getParent();
        column.addView(bar, column.indexOfChild(adTop), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bar.setAlpha(0f);
        bar.animate().alpha(1f).setDuration(260).start();
    }

    // ---- colours from each page

    private void applyPageTheme() {
        JSONObject o = f("autoTheme");
        if (o == null) return;
        web.evaluateJavascript("(function(){var m=document.querySelectorAll('meta[name=\"theme-color\"]');"
                + "for(var i=0;i<m.length;i++){var q=m[i].getAttribute('media');"
                + "if(!q||matchMedia(q).matches)return m[i].getAttribute('content')||'';}return '';})()", v -> {
            String css = v == null ? "" : v.replace("\"", "").trim();
            int color = parseCssColor(css);
            setPageThemeColor(o, color == 0 ? 0 : (color | 0xFF000000));
        });
    }

    /** #rgb, #rrggbb, rgb(r,g,b); 0 when there is no usable colour. */
    private static int parseCssColor(String s) {
        try {
            if (s.matches("#[0-9a-fA-F]{3}")) {
                s = "#" + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2) + s.charAt(3) + s.charAt(3);
            }
            if (s.matches("#[0-9a-fA-F]{6}")) return Color.parseColor(s) | 0xFF000000;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("rgba?\\(\\s*(\\d+)[ ,]+(\\d+)[ ,]+(\\d+)").matcher(s);
            if (m.find()) {
                return Color.rgb(Math.min(255, Integer.parseInt(m.group(1))), Math.min(255, Integer.parseInt(m.group(2))),
                        Math.min(255, Integer.parseInt(m.group(3))));
            }
        } catch (Exception ignored) {
            // not a colour
        }
        return 0;
    }

    private void setPageThemeColor(JSONObject o, int color) {
        if (color == pageThemeColor) return;
        pageThemeColor = color;
        int bar = color != 0 ? color : topBarBg;
        if (o.optBoolean("topBar", true) && topBarView != null) {
            int from = topBarView.getBackground() instanceof android.graphics.drawable.ColorDrawable
                    ? ((android.graphics.drawable.ColorDrawable) topBarView.getBackground()).getColor() : topBarBg;
            int fg = Config.color(f("topBar").optString("fg"), Config.onColor(bar));
            if (color != 0) fg = Config.onColor(bar);
            ValueAnimator a = ValueAnimator.ofArgb(from, bar);
            a.setDuration(260);
            a.addUpdateListener(an -> topBarView.setBackgroundColor((int) an.getAnimatedValue()));
            a.start();
            ViewGroup g = (ViewGroup) topBarView;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c instanceof TextView) ((TextView) c).setTextColor(fg);
                else if (c instanceof ViewGroup && ((ViewGroup) c).getChildAt(0) instanceof ImageView) {
                    ((ImageView) ((ViewGroup) c).getChildAt(0)).setImageTintList(ColorStateList.valueOf(fg));
                }
            }
        }
        if (o.optBoolean("statusBar", true)) {
            int status = color != 0 ? color : statusColor;
            bars.topColor = status;
            bars.invalidateSelf();
            if (Build.VERSION.SDK_INT < 35) getWindow().setStatusBarColor(status);
            setStatusIcons(color != 0 ? Config.isLight(color) : defaultStatusIconsDark());
        }
    }

    // ---- brightness boost

    private void updateBrightness(String url) {
        JSONObject o = f("brightness");
        if (o == null || url == null) return;
        Uri u = Uri.parse(url);
        String path = u.getPath() == null || u.getPath().isEmpty() ? "/" : u.getPath();
        boolean match = false;
        if (isInternal(u)) {
            for (String p : o.optString("paths", "").split(",")) {
                p = p.trim();
                if (!p.isEmpty() && path.startsWith(p)) match = true;
            }
        }
        if (match == brightOn) return;
        brightOn = match;
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = match ? Math.max(0.5f, Math.min(1f, o.optInt("level", 100) / 100f))
                : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
    }

    // ---- offline reading

    private void setOfflineMode(boolean off) {
        if (f("offlineCache") == null || web == null) return;
        offlineMode = off;
        web.getSettings().setCacheMode(off ? WebSettings.LOAD_CACHE_ELSE_NETWORK
                : cfg.optBoolean("cache", true) ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_NO_CACHE);
    }

    // ---- in-app browser

    @SuppressLint("SetJavaScriptEnabled")
    private void buildSheet() {
        JSONObject o = f("inAppBrowser");
        int bg = Config.color(o != null ? o.optString("color") : "", night ? 0xFF1E1F22 : 0xFFF6F7F9);
        int fg = Config.onColor(bg);
        sheet = new FrameLayout(this);
        sheet.setBackgroundColor(bg);
        sheet.setClickable(true);
        sheet.setVisibility(View.GONE);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sheet.addView(col, matchParent());

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), 0, dp(4), 0);
        bar.setBackgroundColor(bg);
        View close = barButton("close", fg, v -> closeSheet());
        close.setContentDescription("Close browser");
        bar.addView(close);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(8), 0, dp(8), 0);
        sheetTitle = text("", 15, fg, true);
        sheetTitle.setSingleLine(true);
        sheetTitle.setEllipsize(TextUtils.TruncateAt.END);
        sheetHost = text("", 12, (fg & 0x00FFFFFF) | 0x99000000, false);
        sheetHost.setSingleLine(true);
        sheetHost.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titles.addView(sheetTitle);
        titles.addView(sheetHost);
        bar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        View share = barButton("share", fg, v -> {
            String u = sheetWeb.getUrl();
            if (u != null) shareText(u, sheetWeb.getTitle());
        });
        share.setContentDescription("Share link");
        bar.addView(share);
        View out = barButton("open_in_new", fg, v -> {
            String u = sheetWeb.getUrl();
            if (u != null) openExternal(Uri.parse(u));
        });
        out.setContentDescription("Open in browser");
        bar.addView(out);
        col.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        FrameLayout body = new FrameLayout(this);
        body.setBackgroundColor(Color.WHITE);
        sheetWeb = new WebView(this);
        WebSettings s = sheetWeb.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(false);
        sheetWeb.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
                if (scheme.equals("http") || scheme.equals("https")) {
                    if (request.isForMainFrame() && isInternal(u)) {
                        // Back to the app's own site: show it in the app.
                        closeSheet();
                        web.loadUrl(u.toString());
                        return true;
                    }
                    return false;
                }
                if (scheme.equals("about") || scheme.equals("data") || scheme.equals("blob")) return false;
                openExternal(u);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Uri u = Uri.parse(url);
                sheetHost.setText(("https".equals(u.getScheme()) ? "🔒 " : "") + (u.getHost() == null ? url : u.getHost()));
                sheetProgress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                sheetProgress.setVisibility(View.GONE);
            }
        });
        sheetWeb.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                sheetProgress.setProgress(p, true);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                sheetTitle.setText(title);
            }
        });
        if (cfg.optBoolean("downloads", true)) sheetWeb.setDownloadListener(this::startDownload);
        body.addView(sheetWeb, matchParent());
        sheetProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        sheetProgress.setMax(100);
        sheetProgress.setProgressTintList(ColorStateList.valueOf(loaderColor()));
        sheetProgress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        body.addView(sheetProgress, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP));
        col.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        stage.addView(sheet, matchParent());
        stage.requestApplyInsets();
    }

    private void openInApp(Uri u) {
        if (sheet == null) buildSheet();
        sheetTitle.setText(u.getHost());
        sheetWeb.loadUrl(u.toString());
        sheet.bringToFront();
        if (sheet.getVisibility() != View.VISIBLE) {
            sheet.setVisibility(View.VISIBLE);
            sheet.setTranslationY(getResources().getDisplayMetrics().heightPixels);
            sheet.animate().translationY(0).setDuration(320).setInterpolator(new DecelerateInterpolator(2f)).start();
            int bg = ((android.graphics.drawable.ColorDrawable) sheet.getBackground()).getColor();
            setStatusIcons(Config.isLight(bg));
        }
    }

    private void closeSheet() {
        if (sheet == null || sheet.getVisibility() != View.VISIBLE) return;
        sheet.animate().translationY(sheet.getHeight()).setDuration(240).withEndAction(() -> {
            sheet.setVisibility(View.GONE);
            sheetWeb.loadUrl("about:blank");
            sheetWeb.clearHistory();
        }).start();
        setStatusIcons(pageThemeColor != 0 && f("autoTheme") != null && f("autoTheme").optBoolean("statusBar", true)
                ? Config.isLight(pageThemeColor) : defaultStatusIconsDark());
    }

    // ---- long-press menu

    private boolean onLongPress() {
        WebView.HitTestResult hit = web.getHitTestResult();
        if (hit == null) return false;
        int type = hit.getType();
        String extra = hit.getExtra();
        if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
            showLinkMenu(extra, null);
            return true;
        }
        if (type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE || type == WebView.HitTestResult.IMAGE_TYPE) {
            if (type == WebView.HitTestResult.IMAGE_TYPE) {
                showLinkMenu(null, extra);
            } else {
                Message msg = new Handler(Looper.getMainLooper(), m -> {
                    showLinkMenu(m.getData().getString("url"), extra);
                    return true;
                }).obtainMessage();
                web.requestFocusNodeHref(msg);
            }
            return true;
        }
        return false;
    }

    private void showLinkMenu(String link, String image) {
        if (link != null && !(link.startsWith("http://") || link.startsWith("https://"))) link = null;
        boolean imageWeb = image != null && (image.startsWith("http://") || image.startsWith("https://"));
        if (link == null && !imageWeb) return;
        web.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        List<String> labels = new ArrayList<>();
        List<Action> actions = new ArrayList<>();
        final String l = link;
        if (l != null) {
            labels.add("Open link");
            actions.add(() -> openLink(l));
            labels.add("Open in browser");
            actions.add(() -> openExternal(Uri.parse(l)));
            labels.add("Copy link");
            actions.add(() -> copyText(l, "Link copied"));
            labels.add("Share link");
            actions.add(() -> shareText(l, null));
        }
        JSONObject o = f("longPress");
        if (imageWeb && o != null && o.optBoolean("saveImages", true)) {
            labels.add("Save image");
            actions.add(() -> startDownload(image, web.getSettings().getUserAgentString(), null, null, 0));
        }
        if (imageWeb) {
            labels.add("Copy image link");
            actions.add(() -> copyText(image, "Image link copied"));
        }
        actionSheet(link != null ? link : image, labels, actions);
    }

    /** A sheet of choices that slides up from the bottom. */
    private void actionSheet(String headText, List<String> labels, List<Action> actions) {
        closeMenu();
        int fg = night ? 0xFFE8EAED : 0xFF1B1F24;
        menuScrim = new FrameLayout(this);
        menuScrim.setBackgroundColor(0x66000000);
        menuScrim.setClickable(true);
        menuScrim.setOnClickListener(v -> closeMenu());
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(night ? 0xFF24272B : Color.WHITE);
        float r = dp(22);
        g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        card.setBackground(g);
        card.setElevation(dp(12));
        card.setClickable(true);
        // The card runs under the navigation bar, its buttons stay above it.
        card.setPadding(dp(8), dp(10), dp(8), dp(12) + bars.bottom);
        View handle = new View(this);
        handle.setBackground(rounded((fg & 0x00FFFFFF) | 0x33000000, dp(2)));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(36), dp(4));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.bottomMargin = dp(8);
        card.addView(handle, hl);
        if (headText != null && !headText.isEmpty()) {
            TextView head = text(headText, 13, (fg & 0x00FFFFFF) | 0x99000000, false);
            head.setSingleLine(true);
            head.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            head.setPadding(dp(16), dp(4), dp(16), dp(8));
            card.addView(head);
        }
        for (int i = 0; i < labels.size(); i++) card.addView(menuRow(labels.get(i), fg, actions.get(i)));
        menuScrim.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        stage.addView(menuScrim, matchParent());
        stage.requestApplyInsets();
        menuScrim.setAlpha(0f);
        menuScrim.animate().alpha(1f).setDuration(160).start();
        card.setTranslationY(dp(300));
        card.animate().translationY(0).setDuration(260).setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    private View menuRow(String label, int fg, Action action) {
        TextView row = text(label, 16, fg, false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setBackground(ripple(rounded(Color.TRANSPARENT, dp(14)), fg, false));
        row.setClickable(true);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        row.setOnClickListener(v -> {
            tick(v);
            closeMenu();
            action.run();
        });
        return row;
    }

    private void closeMenu() {
        if (menuScrim == null) return;
        final View m = menuScrim;
        menuScrim = null;
        m.animate().alpha(0f).setDuration(150).withEndAction(() -> stage.removeView(m)).start();
    }

    private void copyText(String text, String done) {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cb != null) cb.setPrimaryClip(ClipData.newPlainText("link", text));
        if (Build.VERSION.SDK_INT < 33) showSnack(done);
    }

    // ---- shake

    private void startShake() {
        JSONObject o = f("shake");
        if (o == null) return;
        if (sensors == null) sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensors == null) return;
        Sensor acc = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (acc == null) return;
        if (shakeListener == null) {
            shakeListener = new SensorEventListener() {
                private long firstJolt;
                private int jolts;
                private long lastShake;

                @Override
                public void onSensorChanged(SensorEvent e) {
                    float x = e.values[0], y = e.values[1], z = e.values[2];
                    double g = Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;
                    if (g < 2.5) return;
                    long now = SystemClock.uptimeMillis();
                    if (now - firstJolt > 1000) {
                        firstJolt = now;
                        jolts = 0;
                    }
                    if (++jolts >= 2 && now - lastShake > 2000) {
                        lastShake = now;
                        jolts = 0;
                        onShake();
                    }
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }
            };
        }
        sensors.registerListener(shakeListener, acc, SensorManager.SENSOR_DELAY_UI);
    }

    private void stopShake() {
        if (sensors != null && shakeListener != null) sensors.unregisterListener(shakeListener);
    }

    private void onShake() {
        JSONObject o = f("shake");
        if (o == null || locked || gate != null || dialog != null) return;
        tick(web);
        switch (o.optString("action", "reload")) {
            case "home":
                web.loadUrl(homeUrl);
                break;
            case "feedback": {
                String body = "\n\n---\nPage: " + (web.getUrl() == null ? "" : web.getUrl())
                        + "\nApp: " + getString(R.string.app_name) + " " + appVersionName()
                        + "\nAndroid " + Build.VERSION.RELEASE + ", " + Build.MANUFACTURER + " " + Build.MODEL;
                Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
                i.putExtra(Intent.EXTRA_EMAIL, new String[]{o.optString("email", "")});
                i.putExtra(Intent.EXTRA_SUBJECT, "Feedback: " + getString(R.string.app_name));
                i.putExtra(Intent.EXTRA_TEXT, body);
                try {
                    skipNextLock = true;
                    startActivity(i);
                } catch (Exception e) {
                    showSnack("No email app found");
                }
                break;
            }
            default:
                showSnack("Reloading…");
                web.reload();
        }
    }

    // ---- idle reset (kiosk)

    private final Runnable idleCheck = new Runnable() {
        @Override
        public void run() {
            JSONObject o = f("idleReset");
            if (o == null) return;
            long limit = Math.max(1, o.optInt("minutes", 5)) * 60000L;
            if (SystemClock.uptimeMillis() - lastTouch >= limit) {
                lastTouch = SystemClock.uptimeMillis();
                resetIdle(o);
            }
            ui.postDelayed(this, 15000);
        }
    };

    private void resetIdle(JSONObject o) {
        if (locked || gate != null) return;
        closeMenu();
        closeDialog();
        closeSheet();
        closeDrawer();
        if (findBar != null && findBar.getVisibility() == View.VISIBLE) closeFind();
        Runnable home = () -> {
            clearHistoryNext = true;
            web.loadUrl(homeUrl);
        };
        if (o.optBoolean("clear", false)) {
            web.clearCache(true);
            WebStorage.getInstance().deleteAllData();
            prefs.edit().remove("lastUrl").apply();
            CookieManager.getInstance().removeAllCookies(done -> home.run());
        } else if (!trimSlash(homeUrl).equals(trimSlash(web.getUrl() == null ? "" : web.getUrl()))) {
            home.run();
        } else {
            web.evaluateJavascript("window.scrollTo(0,0)", null);
        }
    }

    // ---- start screens: age check, privacy consent, announcement

    private boolean startupGates() {
        if (gate != null) return true;
        JSONObject age = f("ageGate");
        if (age != null && !prefs.getBoolean("ageOk", false)) {
            showAgeGate(age);
            return true;
        }
        JSONObject c = f("consent");
        if (c != null && prefs.getInt("consentVersion", 0) < c.optInt("version", 1)) {
            showConsent(c);
            return true;
        }
        return false;
    }

    /** A full screen in the app's colour with the logo, a title and a message; returns the box for buttons. */
    private LinearLayout gateScreen(String title, String message) {
        int fg = Config.onColor(theme);
        gate = new FrameLayout(this);
        gate.setBackgroundColor(theme);
        gate.setClickable(true);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(32), dp(32), dp(32), dp(32));
        box.addView(roundLogo(84));
        TextView t = text(title, 24, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(26), 0, dp(10));
        box.addView(t);
        TextView m = text(message, 15, (fg & 0x00FFFFFF) | 0xD9000000, false);
        m.setGravity(Gravity.CENTER);
        m.setLineSpacing(0, 1.2f);
        m.setTag("message");
        box.addView(m);
        scroll.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        gate.addView(scroll, matchParent());
        // Above the welcome slides, below the lock and splash screens.
        int at = lockView != null ? stage.indexOfChild(lockView) : splash != null ? stage.indexOfChild(splash) : -1;
        if (at < 0) {
            stage.addView(gate, matchParent());
            gate.setAlpha(0f);
            gate.animate().alpha(1f).setDuration(220).start();
        } else {
            stage.addView(gate, at, matchParent());
        }
        stage.requestApplyInsets();
        return box;
    }

    private void closeGate() {
        if (gate == null) return;
        final View g = gate;
        gate = null;
        g.animate().alpha(0f).setDuration(240).withEndAction(() -> stage.removeView(g)).start();
        maybeAskPermissions();
    }

    private void addGateButton(LinearLayout box, TextView b, int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMargin);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(b, lp);
    }

    private void showAgeGate(JSONObject o) {
        int age = o.optInt("age", 18);
        int fg = Config.onColor(theme);
        LinearLayout box = gateScreen("Are you " + age + " or older?",
                getString(R.string.app_name) + " is for people aged " + age + " and over.");
        TextView yes = pillButton("Yes, I am " + age + "+", fg, theme);
        yes.setMinWidth(dp(200));
        TextView no = text("No", 15, fg, true);
        no.setGravity(Gravity.CENTER);
        no.setPadding(dp(24), dp(12), dp(24), dp(12));
        no.setBackground(ripple(null, fg, true));
        addGateButton(box, yes, 32);
        addGateButton(box, no, 8);
        yes.setOnClickListener(v -> {
            tick(v);
            prefs.edit().putBoolean("ageOk", true).apply();
            closeGate();
        });
        no.setOnClickListener(v -> {
            tick(v);
            ((TextView) box.findViewWithTag("message")).setText(o.optString("blocked",
                    "Sorry, this app is not available for your age."));
            ((TextView) box.getChildAt(1)).setText(getString(R.string.app_name));
            box.removeView(yes);
            box.removeView(no);
            TextView close = pillButton("Close", fg, theme);
            close.setOnClickListener(c -> finishAndRemoveTask());
            addGateButton(box, close, 32);
        });
    }

    private void showConsent(JSONObject o) {
        int fg = Config.onColor(theme);
        LinearLayout box = gateScreen(o.optString("title", "Your privacy"), o.optString("message", ""));
        TextView policy = text("Read the privacy policy", 15, fg, true);
        policy.setPaintFlags(policy.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        policy.setPadding(dp(16), dp(10), dp(16), dp(10));
        policy.setBackground(ripple(null, fg, true));
        policy.setOnClickListener(v -> {
            String url = o.optString("policyUrl", "");
            Uri u = Uri.parse(url);
            // The policy is read on top of this screen; the app itself stays closed until accepted.
            if (f("inAppBrowser") != null || isInternal(u)) openInApp(u);
            else openExternal(u);
        });
        addGateButton(box, policy, 16);
        TextView accept = pillButton(o.optString("accept", "Accept"), fg, theme);
        accept.setMinWidth(dp(200));
        accept.setOnClickListener(v -> {
            tick(v);
            prefs.edit().putInt("consentVersion", o.optInt("version", 1)).apply();
            closeGate();
        });
        addGateButton(box, accept, 24);
    }

    private void maybeShowAnnouncement() {
        JSONObject o = f("announcement");
        if (o == null || isFinishing()) return;
        int version = o.optInt("version", 1);
        if (prefs.getInt("annVersion", 0) >= version) return;
        if (dialog != null || locked || gate != null || splash != null || onboarding != null) {
            ui.postDelayed(this::maybeShowAnnouncement, 1500);
            return;
        }
        prefs.edit().putInt("annVersion", version).apply();
        String link = o.optString("link", "");
        String close = o.optString("button", "Got it");
        if (link.isEmpty()) {
            showDialog(o.optString("title", ""), o.optString("message", ""), close, null, true, null, null);
        } else {
            showDialog(o.optString("title", ""), o.optString("message", ""), o.optString("linkLabel", "Learn more"),
                    close, true, () -> openLink(link), null);
        }
    }

    // ---- local notifications from the page

    private class NotifyBridge {
        @JavascriptInterface
        public int schedule(String title, String text, double seconds, String url) {
            if (!pageTrusted()) return -1;
            JSONObject o = f("notifyBridge");
            int max = o != null ? o.optInt("max", 20) : 20;
            String link = url != null && !url.isEmpty() && isInternal(Uri.parse(url)) ? url : "";
            int id = NotifyReceiver.add(MainActivity.this, title, text, (long) (seconds * 1000), link, max);
            if (id >= 0) ui.post(MainActivity.this::askNotifyPermission);
            return id;
        }

        @JavascriptInterface
        public void cancel(int id) {
            if (pageTrusted()) NotifyReceiver.cancel(MainActivity.this, id);
        }

        @JavascriptInterface
        public void cancelAll() {
            if (pageTrusted()) NotifyReceiver.cancelAll(MainActivity.this);
        }

        @JavascriptInterface
        public int pending() {
            return pageTrusted() ? NotifyReceiver.pendingCount(MainActivity.this) : 0;
        }
    }

    private void askNotifyPermission() {
        String p = Manifest.permission.POST_NOTIFICATIONS;
        if (Build.VERSION.SDK_INT >= 33 && !granted(p)) requestPermissions(new String[]{p}, REQ_NOTIFY);
    }

    // -------------------------------------------------------- start / links

    /** The page to open: a notification link, a deep link, the last page, or home. */
    private String startUrl(Intent intent) {
        String link = linkFromIntent(intent);
        if (link != null) return link;
        if (f("rememberPage") != null) {
            String last = prefs.getString("lastUrl", null);
            if (last != null && isInternal(Uri.parse(last))) return last;
        }
        return homeUrl;
    }

    private String linkFromIntent(Intent intent) {
        if (intent == null) return null;
        String pushUrl = intent.getStringExtra("url");
        JSONObject push = f("push");
        if (pushUrl != null && push != null && push.optBoolean("openUrl", true)
                && (pushUrl.startsWith("https://") || pushUrl.startsWith("http://"))) {
            intent.removeExtra("url");
            return pushUrl;
        }
        Uri data = intent.getData();
        if (data == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return null;
        String scheme = data.getScheme() == null ? "" : data.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("http") || scheme.equals("https")) {
            if (intent.getBooleanExtra("w2a_trusted", false) || isInternal(data)) return data.toString();
            return null;
        }
        JSONObject deep = f("deepLinks");
        if (deep != null && scheme.equals(deep.optString("scheme", "-"))) {
            // myapp://shop/42?x=1  ->  https://site/shop/42?x=1
            Uri home = Uri.parse(homeUrl);
            String rest = data.toString().substring(scheme.length() + 1).replaceFirst("^/+", "");
            return home.getScheme() + "://" + home.getAuthority() + "/" + rest;
        }
        return null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String link = linkFromIntent(intent);
        if (link != null && web != null) web.loadUrl(link);
    }

    // -------------------------------------------------------------- webview

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        // Smoother scrolling: draw the parts just off screen ahead of time, and keep the page's
        // renderer at full priority while the app is in front.
        s.setOffscreenPreRaster(true);
        if (Build.VERSION.SDK_INT >= 26) web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setAllowFileAccess(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setGeolocationEnabled(has("location"));
        boolean zoom = cfg.optBoolean("zoom", false) || f("desktopMode") != null;
        s.setSupportZoom(zoom);
        s.setBuiltInZoomControls(zoom);
        s.setDisplayZoomControls(false);
        if (!cfg.optBoolean("cache", true)) s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        JSONObject textSize = f("textSize");
        if (textSize != null) s.setTextZoom(textSize.optInt("percent", 100));
        if (f("darkMode") != null && Build.VERSION.SDK_INT >= 33) {
            s.setAlgorithmicDarkeningAllowed(f("darkMode").optBoolean("darkenPages", true));
        }

        String ua = cfg.optString("userAgent", "").trim();
        if (!ua.isEmpty()) {
            s.setUserAgentString(ua);
        } else if (f("desktopMode") != null) {
            String desktop = s.getUserAgentString()
                    .replaceAll("\\(Linux; Android [^)]*\\)", "(X11; Linux x86_64)")
                    .replace("; wv", "")
                    .replace(" Mobile", "")
                    .replaceAll("Version/[0-9.]+ ", "");
            s.setUserAgentString(desktop);
        } else {
            String suffix = cfg.optString("userAgentSuffix", "").trim();
            if (!suffix.isEmpty()) s.setUserAgentString(s.getUserAgentString() + " " + suffix);
        }

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        if (cfg.optBoolean("downloads", true)) web.setDownloadListener(this::startDownload);

        JSONObject bridge = f("jsBridge");
        if (bridge != null) web.addJavascriptInterface(new Bridge(), bridge.optString("name", "Web2App"));

        final boolean noSelect = f("noSelect") != null;
        if (noSelect) web.setHapticFeedbackEnabled(false);
        if (noSelect || f("longPress") != null) {
            web.setOnLongClickListener(v -> {
                if (f("longPress") != null && onLongPress()) return true;
                if (!noSelect) return false;
                WebView.HitTestResult hit = web.getHitTestResult();
                return hit == null || hit.getType() != WebView.HitTestResult.EDIT_TEXT_TYPE;
            });
        }
        JSONObject haptics = f("haptics");
        if (haptics != null && haptics.optBoolean("page", false)) web.addJavascriptInterface(new TapBridge(), "__w2aTap");
        if (f("notifyBridge") != null) web.addJavascriptInterface(new NotifyBridge(), "Web2AppNotify");
        if (f("speech") != null) web.addJavascriptInterface(new SpeechBridge(), "Web2AppSpeech");
        if (f("offlineCache") != null) {
            ConnectivityManager cmgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cmgr != null && cmgr.getActiveNetwork() == null) {
                wasOffline = true;
                setOfflineMode(true);
            }
        }
        if (f("scrollTop") != null) {
            final int after = f("scrollTop").optInt("after", 800);
            web.setOnScrollChangeListener((v, x, y, ox, oy) ->
                    showToTop(y / getResources().getDisplayMetrics().density > after));
        }
        if (f("swipeNav") != null) setupSwipeNav();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupSwipeNav() {
        final float[] start = new float[3];
        web.setOnTouchListener((v, e) -> {
            // The outer 15% of each side (at least 28dp): wide enough to start just past the
            // system back-gesture strip on phones with gesture navigation.
            float edge = Math.max(dp(28), v.getWidth() * 0.15f);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = e.getX();
                    start[1] = e.getY();
                    start[2] = (e.getX() < edge) ? -1 : (e.getX() > v.getWidth() - edge ? 1 : 0);
                    break;
                case MotionEvent.ACTION_UP:
                    float dx = e.getX() - start[0];
                    float dy = Math.abs(e.getY() - start[1]);
                    if (start[2] == -1 && dx > dp(90) && dy < dp(70) && web.canGoBack()) {
                        web.goBack();
                        showSwipeHint(false);
                    } else if (start[2] == 1 && -dx > dp(90) && dy < dp(70) && web.canGoForward()) {
                        web.goForward();
                        showSwipeHint(true);
                    }
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** A round arrow that slides in from the edge the user swiped from. */
    private void showSwipeHint(boolean forward) {
        if (swipeHint != null) content.removeView(swipeHint);
        FrameLayout b = new FrameLayout(this);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(night ? 0xFF2A2D31 : Color.WHITE);
        b.setBackground(disc);
        b.setElevation(dp(6));
        ImageView arrow = iconView("arrow_back", Config.isLight(theme) ? 0xFF3C4148 : theme, 24);
        if (forward) arrow.setRotation(180f);
        b.addView(arrow, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(48), dp(48),
                Gravity.CENTER_VERTICAL | (forward ? Gravity.END : Gravity.START));
        lp.setMargins(dp(12), 0, dp(12), 0);
        content.addView(b, lp);
        swipeHint = b;
        float from = forward ? dp(60) : -dp(60);
        b.setTranslationX(from);
        b.setAlpha(0f);
        b.animate().translationX(0).alpha(1f).setDuration(180).setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> b.animate().alpha(0f).translationX(from / 2).setStartDelay(260).setDuration(220)
                        .withEndAction(() -> {
                            content.removeView(b);
                            if (swipeHint == b) swipeHint = null;
                        }).start()).start();
    }

    private boolean has(String permission) {
        JSONArray a = cfg.optJSONArray("permissions");
        if (a == null) return false;
        for (int i = 0; i < a.length(); i++) {
            if (permission.equals(a.optString(i))) return true;
        }
        return false;
    }

    private boolean granted(String androidPermission) {
        return checkSelfPermission(androidPermission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isInternal(Uri u) {
        String host = u.getHost();
        if (host == null) return true;
        host = stripWww(host.toLowerCase(Locale.ROOT));
        for (String h : internalHosts) {
            if (host.equals(h) || host.endsWith("." + h)) return true;
        }
        return false;
    }

    private boolean isBlocked(Uri u) {
        String host = u.getHost();
        if (host == null || blockedHosts.isEmpty()) return false;
        host = stripWww(host.toLowerCase(Locale.ROOT));
        for (String h : blockedHosts) {
            if (host.equals(h) || host.endsWith("." + h)) return true;
        }
        return false;
    }

    private void openExternal(Uri u) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, u);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            skipNextLock = true;
            startActivity(i);
        } catch (Exception e) {
            showSnack("No app can open this link");
        }
    }

    private void inject() {
        web.evaluateJavascript(LOGIN_JS, null);
        if (swipe != null) web.evaluateJavascript(PULL_JS, null);
        if (!pageJs.isEmpty()) web.evaluateJavascript(pageJs, null);
        if (f("desktopMode") != null) {
            web.evaluateJavascript("(function(){var m=document.querySelector('meta[name=viewport]');"
                    + "if(!m){m=document.createElement('meta');m.name='viewport';"
                    + "(document.head||document.documentElement).appendChild(m);}"
                    + "if(m.content!=='width=1200'){m.content='width=1200';}})();", null);
        }
        if (!injectCss.isEmpty()) {
            web.evaluateJavascript("(function(){var s=document.getElementById('w2a-css');"
                    + "if(!s){s=document.createElement('style');s.id='w2a-css';"
                    + "(document.head||document.documentElement).appendChild(s);}"
                    + "s.textContent=" + JSONObject.quote(injectCss) + ";})();", null);
        }
    }

    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            if (scheme.equals("http") || scheme.equals("https")) {
                if (isBlocked(u)) return true;
                JSONObject stay = f("stayInApp");
                if (stay != null && request.isForMainFrame() && !isInternal(u)) {
                    showSnack(stay.optString("message", "This link cannot be opened here."));
                    return true;
                }
                if (cfg.optBoolean("externalLinksInBrowser", true) && request.isForMainFrame() && !isInternal(u)) {
                    if (f("inAppBrowser") != null) openInApp(u);
                    else openExternal(u);
                    return true;
                }
                return false;
            }
            if (scheme.equals("about") || scheme.equals("data") || scheme.equals("blob") || scheme.equals("javascript")) {
                return false;
            }
            JSONObject deep = f("deepLinks");
            if (deep != null && scheme.equals(deep.optString("scheme", "-"))) {
                String link = linkFromIntent(new Intent(Intent.ACTION_VIEW, u));
                if (link != null) view.loadUrl(link);
                return true;
            }
            if (scheme.equals("intent")) {
                try {
                    Intent i = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME);
                    i.addCategory(Intent.CATEGORY_BROWSABLE);
                    i.setComponent(null);
                    i.setSelector(null);
                    try {
                        skipNextLock = true;
                        startActivity(i);
                    } catch (ActivityNotFoundException e) {
                        String fallback = i.getStringExtra("browser_fallback_url");
                        if (fallback != null && (fallback.startsWith("http://") || fallback.startsWith("https://"))) {
                            view.loadUrl(fallback);
                        }
                    }
                } catch (Exception ignored) {
                    // bad intent link
                }
                return true;
            }
            // tel:, mailto:, sms:, whatsapp:, market:, upi: ...
            openExternal(u);
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (LOCAL_HOST.equals(request.getUrl().getHost())) return localFile(request.getUrl());
            if (!blockedHosts.isEmpty() && isBlocked(request.getUrl())) {
                return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked", null,
                        new ByteArrayInputStream(new byte[0]));
            }
            return null;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            loadFailed = false;
            currentUrl = url == null ? "" : url;
            if (httpErrorUrl == null || !httpErrorUrl.equals(url)) {
                httpErrorUrl = null;
                hideErrorPage();
            }
            applyDataSaver();
            if (progress.getParent() != null) progress.setVisibility(View.VISIBLE);
            if (spinner != null && (swipe == null || !swipe.isRefreshing())) spinner.setVisibility(View.VISIBLE);
            updateNavFor(url);
            updateBrightness(url);
            showToTop(false);
            if (findBar != null && findBar.getVisibility() == View.VISIBLE) closeFind();
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            // Drawn, but the site's scripts may still be building it: give them a moment.
            view.postDelayed(MainActivity.this::pageReady, 1200);
            inject();
            JSONObject fade = f("pageFade");
            if (fade != null && firstPageDone) {
                web.animate().cancel();
                web.setAlpha(0f);
                web.animate().alpha(1f).setDuration(fade.optInt("ms", 250)).setInterpolator(new DecelerateInterpolator()).start();
            }
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            // Also runs for pages that change their address without loading (single page apps).
            updateBrightness(url);
            updateNavFor(url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            pageReady();
            currentUrl = url == null ? "" : url;
            if (httpErrorUrl != null && httpErrorUrl.equals(url)) showErrorPage(url);
            else hideErrorPage();
            httpErrorUrl = null;
            progress.setVisibility(View.GONE);
            if (spinner != null) spinner.setVisibility(View.GONE);
            if (swipe != null) swipe.setRefreshing(false);
            if (!loadFailed) offline.setVisibility(View.GONE);
            CookieManager.getInstance().flush();
            inject();
            if (!injectJs.isEmpty()) {
                view.evaluateJavascript("(function(){try{" + injectJs + "\n}catch(e){console.error(e)}})();", null);
            }
            if (topTitle != null && f("topBar") != null && "page".equals(f("topBar").optString("title"))) {
                String t = view.getTitle();
                if (t != null && !t.isEmpty() && !t.startsWith("http")) topTitle.setText(t);
            }
            applyPageTheme();
            JSONObject haptics = f("haptics");
            if (haptics != null && haptics.optBoolean("page", false)) view.evaluateJavascript(TAP_JS, null);
            if (offlineMode && !loadFailed && f("offlineCache") != null) {
                showSnack(f("offlineCache").optString("message", "You are offline. Showing a saved copy."));
            }
            if (clearHistoryNext) {
                clearHistoryNext = false;
                view.clearHistory();
            }
            if (!firstPageDone) {
                firstPageDone = true;
                splashPageReady = true;
                maybeHideSplash();
                maybeAskRating();
            }
            if (!loadFailed) Ads.onPageLoaded(MainActivity.this);
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
            JSONObject o = f("errorPage");
            if (o == null || !request.isForMainFrame()) return;
            int code = response.getStatusCode();
            if (code >= 500 || (code == 404 && o.optBoolean("notFound", false))) httpErrorUrl = request.getUrl().toString();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                loadFailed = true;
                failedUrl = request.getUrl().toString();
                offline.setVisibility(View.VISIBLE);
                offline.setAlpha(0f);
                offline.animate().alpha(1f).setDuration(220).start();
                splashPageReady = true;
                maybeHideSplash();
            }
        }
    }

    private class Chrome extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (newProgress >= 100) pageReady();
            progress.setProgress(newProgress, true);
            if (newProgress < 100) {
                if (progress.getParent() != null) progress.setVisibility(View.VISIBLE);
                if (spinner != null && (swipe == null || !swipe.isRefreshing())) spinner.setVisibility(View.VISIBLE);
            } else {
                progress.setVisibility(View.GONE);
                if (spinner != null) spinner.setVisibility(View.GONE);
            }
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            if (topTitle != null && f("topBar") != null && "page".equals(f("topBar").optString("title"))
                    && title != null && !title.isEmpty() && !title.startsWith("http")) {
                topTitle.setText(title);
            }
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            runOnUiThread(() -> handleWebPermission(request));
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            if (!has("location")) {
                callback.invoke(origin, false, false);
                return;
            }
            if (granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                callback.invoke(origin, true, false);
                return;
            }
            pendingGeoOrigin = origin;
            pendingGeoCallback = callback;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_GEO);
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
            return openFileChooser(callback, params);
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            customViewCallback = callback;
            if (f("videoRotate") != null) {
                orientationBeforeVideo = getRequestedOrientation();
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }
            fullscreenHolder.addView(view, matchParent());
            fullscreenHolder.setVisibility(View.VISIBLE);
            applyImmersive(true);
        }

        @Override
        public void onHideCustomView() {
            exitCustomView();
        }
    }

    private void exitCustomView() {
        if (customView == null) return;
        fullscreenHolder.removeView(customView);
        fullscreenHolder.setVisibility(View.GONE);
        customView = null;
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
        if (!cfg.optBoolean("fullscreen", false)) applyImmersive(false);
        if (orientationBeforeVideo != null) {
            setRequestedOrientation(orientationBeforeVideo);
            orientationBeforeVideo = null;
        }
    }

    // ------------------------------------------------- files inside the app
    private static final String[][] MIME = {
            {"html", "text/html"}, {"htm", "text/html"}, {"css", "text/css"}, {"js", "text/javascript"},
            {"mjs", "text/javascript"}, {"json", "application/json"}, {"svg", "image/svg+xml"}, {"png", "image/png"},
            {"jpg", "image/jpeg"}, {"jpeg", "image/jpeg"}, {"gif", "image/gif"}, {"webp", "image/webp"},
            {"avif", "image/avif"}, {"ico", "image/x-icon"}, {"woff", "font/woff"}, {"woff2", "font/woff2"},
            {"ttf", "font/ttf"}, {"otf", "font/otf"}, {"mp4", "video/mp4"}, {"webm", "video/webm"},
            {"mp3", "audio/mpeg"}, {"wav", "audio/wav"}, {"ogg", "audio/ogg"}, {"txt", "text/plain"},
            {"xml", "application/xml"}, {"pdf", "application/pdf"}, {"wasm", "application/wasm"},
            {"webmanifest", "application/manifest+json"},
    };

    private static String mimeOf(String path) {
        int dot = path.lastIndexOf('.');
        String ext = dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (String[] m : MIME) if (m[0].equals(ext)) return m[1];
        String guess = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return guess != null ? guess : "application/octet-stream";
    }

    /** Serves the app's own site (uploaded HTML or ZIP) from assets/www, like a web server would. */
    private WebResourceResponse localFile(Uri u) {
        String p = u.getPath() == null ? "/" : u.getPath();
        if (p.endsWith("/")) p += "index.html";
        p = p.replaceFirst("^/+", "");
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Cache-Control", "no-cache");
        if (!p.contains("..")) {
            for (String candidate : new String[]{p, p + "/index.html", p + ".html"}) {
                try {
                    InputStream in = getAssets().open("www/" + candidate);
                    String mime = mimeOf(candidate);
                    boolean text = mime.startsWith("text/") || mime.endsWith("json") || mime.endsWith("javascript")
                            || mime.endsWith("xml") || mime.endsWith("svg+xml");
                    return new WebResourceResponse(mime, text ? "utf-8" : null, 200, "OK", headers, in);
                } catch (java.io.IOException ignored) {
                    // try the next form
                }
            }
        }
        return new WebResourceResponse("text/html", "utf-8", 404, "Not Found", headers,
                new ByteArrayInputStream("<h1 style=\"font-family:sans-serif\">Page not found</h1>".getBytes()));
    }

    // ---------------------------------------------------- data saver
    private void applyDataSaver() {
        if (f("dataSaver") == null || web == null) return;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean metered = cm != null && cm.isActiveNetworkMetered();
        web.getSettings().setBlockNetworkImage(metered);
    }

    // ---------------------------------------------------- friendly error page
    private void showErrorPage(final String url) {
        JSONObject o = f("errorPage");
        if (o == null) return;
        if (errorView == null) {
            int fg = night ? 0xFFE8EAED : 0xFF22262B;
            errorView = new LinearLayout(this);
            errorView.setOrientation(LinearLayout.VERTICAL);
            errorView.setGravity(Gravity.CENTER);
            errorView.setBackgroundColor(pageBg);
            errorView.setPadding(dp(32), dp(32), dp(32), dp(32));
            errorView.setClickable(true);
            FrameLayout badge = new FrameLayout(this);
            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor((theme & 0x00FFFFFF) | 0x1F000000);
            badge.setBackground(disc);
            badge.addView(iconView("info", Config.isLight(theme) ? 0xFF3C4148 : theme, 40),
                    new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
            errorView.addView(badge, new LinearLayout.LayoutParams(dp(96), dp(96)));
            TextView t = text(o.optString("title", "Something went wrong"), 20, fg, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(22), 0, dp(8));
            errorView.addView(t);
            TextView m = text(o.optString("message", "We could not load this page. Please try again in a moment."), 15,
                    (fg & 0x00FFFFFF) | 0xB3000000, false);
            m.setGravity(Gravity.CENTER);
            m.setLineSpacing(0, 1.2f);
            errorView.addView(m);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);
            TextView home = pillButton("Home", (theme & 0x00FFFFFF) | 0x1F000000, Config.isLight(theme) ? 0xFF22262B : theme);
            TextView again = pillButton("Try again", theme, Config.onColor(theme));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.setMargins(dp(6), 0, dp(6), 0);
            row.addView(home, bl);
            row.addView(again, bl);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rl.topMargin = dp(26);
            errorView.addView(row, rl);
            home.setOnClickListener(v -> web.loadUrl(homeUrl));
            again.setOnClickListener(v -> {
                Object tag = errorView.getTag();
                web.loadUrl(tag != null ? tag.toString() : homeUrl);
            });
            content.addView(errorView, matchParent());
        }
        errorView.setTag(url);
        errorView.bringToFront();
        errorView.setVisibility(View.VISIBLE);
        errorView.setAlpha(0f);
        errorView.animate().alpha(1f).setDuration(220).start();
    }

    private void hideErrorPage() {
        if (errorView != null) errorView.setVisibility(View.GONE);
    }

    // ---------------------------------------------------------- read aloud
    private void say(String text, String lang) {
        if (text == null || text.trim().isEmpty()) return;
        if (text.length() > 3900) text = text.substring(0, 3900);
        if (tts == null || !ttsReady) {
            pendingSpeech = text;
            pendingLang = lang;
            if (tts == null) {
                tts = new TextToSpeech(this, status -> ui.post(() -> {
                    ttsReady = status == TextToSpeech.SUCCESS;
                    if (ttsReady && pendingSpeech != null) {
                        String t = pendingSpeech;
                        pendingSpeech = null;
                        say(t, pendingLang);
                    }
                }));
            }
            return;
        }
        if (lang != null && !lang.trim().isEmpty()) tts.setLanguage(Locale.forLanguageTag(lang.trim()));
        JSONObject o = f("speech");
        tts.setSpeechRate(o != null ? o.optInt("rate", 100) / 100f : 1f);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "w2a");
    }

    /** window.Web2AppSpeech: the page speaks text with the phone's voice. Only the app's own site may use it. */
    // ------------------------------------------------------- saved passwords
    // Chrome offers to save a password when a login form is sent. Many sites sign in with a script
    // instead of sending the form, so the page tells us when a filled-in password is being sent,
    // and the phone's password manager then offers to save it (and fills it in next time).

    private static final String LOGIN_JS = "(function(){if(window.__w2aLoginHook)return;window.__w2aLoginHook=1;"
            + "var sent='';"
            + "function pw(){var l=document.querySelectorAll('input[type=password]');"
            + "for(var i=0;i<l.length;i++){if(l[i].value)return l[i];}return null;}"
            + "function save(){var p=pw();if(!p||sent===p.value)return;sent=p.value;"
            + "try{window.__w2aLogin.submitted();}catch(e){}}"
            + "function sendy(b){if(!b)return false;var t=((b.type||'')+'').toLowerCase();"
            + "var label=((b.innerText||b.value||'')+' '+(b.getAttribute('aria-label')||'')).toLowerCase();"
            + "if(/show|hide|eye|visib|forgot|cancel/.test(label))return false;"
            + "if(t==='submit')return true;if(b.tagName==='BUTTON'&&!b.getAttribute('type')&&b.form)return true;"
            + "return /log ?in|sign ?in|sign ?up|register|continue|next|submit|enter|create|join|\u09b2\u0997/.test(label);}"
            + "document.addEventListener('submit',save,true);"
            + "document.addEventListener('click',function(e){var b=e.target&&e.target.closest"
            + "&&e.target.closest('button,input[type=submit],input[type=button],[role=button],a');"
            + "if(sendy(b))save();},true);"
            + "document.addEventListener('keydown',function(e){var t=e.target;"
            + "if(e.key==='Enter'&&t&&t.tagName==='INPUT'&&t.form)save();},true);"
            + "window.addEventListener('pagehide',save);})();";

    private class LoginBridge {
        @JavascriptInterface
        public void submitted() {
            ui.post(() -> {
                if (Build.VERSION.SDK_INT < 26) return;
                android.view.autofill.AutofillManager am = getSystemService(android.view.autofill.AutofillManager.class);
                if (am != null && am.isEnabled()) am.commit();
            });
        }
    }

    private class SpeechBridge {
        private boolean trusted() {
            String u = currentUrl;
            return u != null && !u.isEmpty() && isInternal(Uri.parse(u));
        }

        @JavascriptInterface
        public void speak(String text, String lang) {
            if (trusted()) ui.post(() -> say(text, lang));
        }

        @JavascriptInterface
        public void speak(String text) {
            speak(text, null);
        }

        @JavascriptInterface
        public void stop() {
            ui.post(() -> {
                pendingSpeech = null;
                if (tts != null) tts.stop();
            });
        }

        @JavascriptInterface
        public boolean isSpeaking() {
            return tts != null && tts.isSpeaking();
        }
    }

    // --------------------------------------------------- JavaScript bridge

    /** window.Web2App (or the name the owner chose). Only answers pages of the app's own site. */
    private class Bridge {
        private boolean trusted() {
            String u = currentUrl;
            return u != null && !u.isEmpty() && isInternal(Uri.parse(u));
        }

        @JavascriptInterface
        public String platform() {
            return "android";
        }

        @JavascriptInterface
        public String appVersion() {
            return appVersionName();
        }

        @JavascriptInterface
        public String pushToken() {
            return trusted() ? Push.token() : "";
        }

        @JavascriptInterface
        public void share(String text) {
            if (trusted()) ui.post(() -> shareText(text, null));
        }

        @JavascriptInterface
        public void toast(String text) {
            if (trusted()) ui.post(() -> showSnack(text));
        }

        @JavascriptInterface
        public void copy(String text) {
            if (!trusted()) return;
            ui.post(() -> {
                ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cb != null) cb.setPrimaryClip(ClipData.newPlainText("text", text));
            });
        }

        @JavascriptInterface
        @SuppressWarnings("deprecation")
        public void vibrate(int ms) {
            if (!trusted()) return;
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            long d = Math.max(1, Math.min(ms, 2000));
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(d, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(d);
        }

        @JavascriptInterface
        public void print() {
            if (trusted()) ui.post(MainActivity.this::printPage);
        }

        @JavascriptInterface
        public void openExternal(String url) {
            if (!trusted() || url == null) return;
            ui.post(() -> MainActivity.this.openExternal(Uri.parse(url)));
        }
    }

    // ---------------------------------------------------------- permissions

    private void handleWebPermission(PermissionRequest request) {
        List<String> resources = new ArrayList<>();
        List<String> needed = new ArrayList<>();
        for (String res : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res) && has("camera")) {
                resources.add(res);
                if (!granted(Manifest.permission.CAMERA)) needed.add(Manifest.permission.CAMERA);
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res) && has("microphone")) {
                resources.add(res);
                if (!granted(Manifest.permission.RECORD_AUDIO)) needed.add(Manifest.permission.RECORD_AUDIO);
            } else if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(res)) {
                resources.add(res);
            }
        }
        if (resources.isEmpty()) {
            request.deny();
            return;
        }
        if (needed.isEmpty()) {
            request.grant(resources.toArray(new String[0]));
            return;
        }
        if (pendingWebPerm != null) pendingWebPerm.deny();
        pendingWebPerm = request;
        requestPermissions(needed.toArray(new String[0]), REQ_WEB_PERM);
    }

    /**
     * Asks for the app's permissions once nothing covers the page any more (splash, welcome slides, lock),
     * all in one request: Android refuses a second request while one is open.
     */
    private void maybeAskPermissions() {
        if (isFinishing()) return;
        // Age check and privacy consent are built right away, under the splash and lock screens, so the
        // splash fades straight into them. Each calls back here when answered.
        if (startupGates()) return;
        if (splash != null || onboarding != null || locked) return;
        if (!announcementQueued && f("announcement") != null) {
            announcementQueued = true;
            ui.postDelayed(this::maybeShowAnnouncement, 1400);
        }
        if (permsAsked) return;
        permsAsked = true;
        ui.postDelayed(this::requestStartupPermissions, 450);
    }

    private void requestStartupPermissions() {
        if (isFinishing()) return;
        List<String> ask = new ArrayList<>();
        JSONArray list = cfg.optJSONArray("androidRuntimePermissions");
        if (list != null && cfg.optBoolean("askPermissionsOnStart", true) && !prefs.getBoolean("asked", false)) {
            prefs.edit().putBoolean("asked", true).apply();
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("name", "");
                int min = o.optInt("minSdk", 1);
                int max = o.optInt("maxSdk", Integer.MAX_VALUE);
                if (name.isEmpty() || Build.VERSION.SDK_INT < min || Build.VERSION.SDK_INT > max) continue;
                if (!granted(name) && !ask.contains(name)) ask.add(name);
            }
        }
        // Notifications are needed by push and the daily reminder even when other permissions wait.
        String notify = Manifest.permission.POST_NOTIFICATIONS;
        if ((f("push") != null || f("reminder") != null) && Build.VERSION.SDK_INT >= 33
                && !granted(notify) && !prefs.getBoolean("askedNotify", false)) {
            prefs.edit().putBoolean("askedNotify", true).apply();
            if (!ask.contains(notify)) ask.add(notify);
        }
        if (!ask.isEmpty()) requestPermissions(ask.toArray(new String[0]), REQ_STARTUP);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WEB_PERM && pendingWebPerm != null) {
            PermissionRequest request = pendingWebPerm;
            pendingWebPerm = null;
            List<String> ok = new ArrayList<>();
            for (String res : request.getResources()) {
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res) && has("camera")
                        && granted(Manifest.permission.CAMERA)) {
                    ok.add(res);
                } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res) && has("microphone")
                        && granted(Manifest.permission.RECORD_AUDIO)) {
                    ok.add(res);
                } else if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(res)) {
                    ok.add(res);
                }
            }
            if (ok.isEmpty()) request.deny();
            else request.grant(ok.toArray(new String[0]));
        } else if (requestCode == REQ_GEO && pendingGeoCallback != null) {
            boolean ok = granted(Manifest.permission.ACCESS_FINE_LOCATION)
                    || granted(Manifest.permission.ACCESS_COARSE_LOCATION);
            pendingGeoCallback.invoke(pendingGeoOrigin, ok, false);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
        }
    }

    // --------------------------------------------------------- file uploads

    private boolean openFileChooser(ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        fileCallback = callback;

        Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        List<String> mimes = new ArrayList<>();
        String[] accept = params.getAcceptTypes();
        if (accept != null) {
            for (String a : accept) {
                if (a != null && a.contains("/")) mimes.add(a.trim());
            }
        }
        if (mimes.size() == 1) {
            picker.setType(mimes.get(0));
        } else {
            picker.setType("*/*");
            if (mimes.size() > 1) picker.putExtra(Intent.EXTRA_MIME_TYPES, mimes.toArray(new String[0]));
        }
        if (params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }

        Intent chooser = Intent.createChooser(picker, null);

        // Offer the camera as a source when the camera permission is enabled and granted.
        cameraUri = null;
        cameraFile = null;
        boolean wantsImage = mimes.isEmpty();
        for (String m : mimes) {
            if (m.startsWith("image/") || m.equals("*/*")) wantsImage = true;
        }
        if (wantsImage && has("camera") && granted(Manifest.permission.CAMERA)) {
            try {
                File dir = new File(getCacheDir(), "captures");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                cameraFile = new File(dir, "photo_" + System.currentTimeMillis() + ".jpg");
                cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", cameraFile);
                Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                cam.setClipData(ClipData.newRawUri("photo", cameraUri));
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Parcelable[]{cam});
            } catch (Exception e) {
                cameraUri = null;
                cameraFile = null;
            }
        }

        try {
            skipNextLock = true;
            startActivityForResult(chooser, REQ_FILE);
        } catch (ActivityNotFoundException e) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_LOCK) {
            if (resultCode == RESULT_OK) unlock();
            return;
        }
        if (requestCode != REQ_FILE || fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK) {
            if (data != null && data.getClipData() != null && data.getClipData().getItemCount() > 0) {
                ClipData clip = data.getClipData();
                result = new Uri[clip.getItemCount()];
                for (int i = 0; i < clip.getItemCount(); i++) result[i] = clip.getItemAt(i).getUri();
            } else if (data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            } else if (cameraUri != null && cameraFile != null && cameraFile.length() > 0) {
                result = new Uri[]{cameraUri};
            }
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    // ------------------------------------------------------------ downloads

    private void startDownload(String url, String userAgent, String contentDisposition, String mimeType,
                               long contentLength) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            showSnack("This download type is not supported");
            return;
        }
        try {
            String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            if (mimeType != null && !mimeType.isEmpty()) r.setMimeType(mimeType);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) r.addRequestHeader("Cookie", cookies);
            if (userAgent != null) r.addRequestHeader("User-Agent", userAgent);
            r.setTitle(name);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            try {
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            } catch (Exception e) {
                r.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, name);
            }
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            dm.enqueue(r);
            showSnack("Downloading " + name);
        } catch (Exception e) {
            openExternal(Uri.parse(url));
        }
    }

    // ----------------------------------------------------------- navigation

    private void registerBackHandler() {
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
    }

    private void handleBack() {
        if (dialog != null) {
            if (dialogCancelable) closeDialog();
            return;
        }
        if (menuScrim != null) {
            closeMenu();
            return;
        }
        if (sheet != null && sheet.getVisibility() == View.VISIBLE) {
            if (sheetWeb.canGoBack()) sheetWeb.goBack();
            else closeSheet();
            return;
        }
        if (gate != null) {
            finish();
            return;
        }
        if (drawerScrim != null && drawerScrim.getVisibility() == View.VISIBLE) {
            closeDrawer();
            return;
        }
        if (findBar != null && findBar.getVisibility() == View.VISIBLE) {
            closeFind();
            return;
        }
        if (locked) {
            finish();
            return;
        }
        if (customView != null) {
            exitCustomView();
            return;
        }
        if (offline.getVisibility() != View.VISIBLE && web.canGoBack()) {
            web.goBack();
            return;
        }
        JSONObject exit = f("exitConfirm");
        if (exit == null || onboarding != null) {
            finish();
            return;
        }
        String message = exit.optString("message", "Press back again to exit");
        if ("dialog".equals(exit.optString("mode", "double"))) {
            showDialog(getString(R.string.app_name), message, "Exit", "Cancel", true, this::finish, null);
        } else if (SystemClock.uptimeMillis() - lastBack < 2000) {
            finish();
        } else {
            lastBack = SystemClock.uptimeMillis();
            showSnack(message);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        JSONObject o = f("appLock");
        if (o != null && !locked && leftAt > 0 && !skipNextLock) {
            int minutes = Integer.parseInt(o.optString("relock", "1").replaceAll("[^0-9-]", "0"));
            if (minutes >= 0 && SystemClock.elapsedRealtime() - leftAt >= minutes * 60000L) lock();
        }
        skipNextLock = false;
    }

    @Override
    protected void onStop() {
        super.onStop();
        leftAt = SystemClock.elapsedRealtime();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Background audio: the page keeps running (and playing) while the app is not on screen.
        if (f("backgroundAudio") == null) web.onPause();
        stopShake();
        pausedAt = SystemClock.elapsedRealtime();
        ui.removeCallbacks(idleCheck);
        CookieManager.getInstance().flush();
        if (f("rememberPage") != null) {
            String u = web.getUrl();
            if (u != null && (u.startsWith("https://") || u.startsWith("http://")) && isInternal(Uri.parse(u))) {
                prefs.edit().putString("lastUrl", u).apply();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        hidePrivacyCover();
        applyDataSaver();
        JSONObject fresh = f("resumeReload");
        if (fresh != null && pausedAt > 0 && customView == null
                && SystemClock.elapsedRealtime() - pausedAt >= fresh.optInt("minutes", 30) * 60000L) {
            web.reload();
        }
        pausedAt = 0;
        startShake();
        if (f("idleReset") != null) {
            lastTouch = SystemClock.uptimeMillis();
            ui.removeCallbacks(idleCheck);
            ui.postDelayed(idleCheck, 15000);
        }
    }

    /** Leaving with Home or Recents (not for our own dialogs or pickers): cover the page for the app switcher. */
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        showPrivacyCover();
    }

    private View privacyView;

    private void showPrivacyCover() {
        if (f("privacyCover") == null || privacyView != null) return;
        FrameLayout v = new FrameLayout(this);
        v.setBackgroundColor(theme);
        v.addView(roundLogo(88), new FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER));
        stage.addView(v, matchParent());
        privacyView = v;
    }

    private void hidePrivacyCover() {
        if (privacyView == null) return;
        final View v = privacyView;
        privacyView = null;
        v.animate().alpha(0f).setDuration(180).withEndAction(() -> stage.removeView(v)).start();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        lastTouch = SystemClock.uptimeMillis();
        // Until the page answers for this touch, a pull does not start a refresh.
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && swipe != null) pageAtTop = false;
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onDestroy() {
        if (netCallback != null) {
            try {
                ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(netCallback);
            } catch (Exception ignored) {
                // already gone
            }
        }
        ui.removeCallbacksAndMessages(null);
        if (tts != null) tts.shutdown();
        if (sheetWeb != null) sheetWeb.destroy();
        if (web != null) {
            ViewGroup parent = (ViewGroup) web.getParent();
            if (parent != null) parent.removeView(web);
            web.destroy();
        }
        super.onDestroy();
    }
    /** What a screen reader says for the floating button. */
    static String fabLabel(String action) {
        switch (action) {
            case "whatsapp": return "Chat on WhatsApp";
            case "tel": return "Call us";
            case "email": return "Email us";
            case "share": return "Share";
            default: return "Open link";
        }
    }


}
