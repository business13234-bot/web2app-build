// Usage: node scripts/gen-android.mjs <payload.json> <output-dir>
// Turns the Android template plus a build request into a ready-to-build Gradle project.
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { PERMISSIONS } from '../shared/catalog.mjs';
import { pageScript } from '../shared/inject.mjs';
import { readPayload, copyDir, write, writeImage, writeBinary, setEnv, xml, splashMedia, siteFiles } from './lib.mjs';

const [payloadFile, outDir] = process.argv.slice(2);
if (!payloadFile || !outDir) {
  console.error('Usage: gen-android.mjs <payload.json> <output-dir>');
  process.exit(2);
}

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const payload = readPayload(payloadFile);
const cfg = payload.config;
const out = path.resolve(outDir);
const main = path.join(out, 'app', 'src', 'main');

copyDir(path.join(root, 'templates', 'android'), out);

// ---- features ----
const feat = cfg.features || {};
const on = (id) => Boolean(feat[id] && feat[id].enabled);
const javaDir = path.join(main, 'java', 'app', 'web2app', 'shell');
if (on('push')) {
  // Firebase versions replace the empty stand-ins.
  copyDir(path.join(root, 'templates', 'android-extras', 'push'), javaDir);
  write(path.join(out, 'app', 'google-services.json'), feat.push.googleServices);
}
if (on('admob')) copyDir(path.join(root, 'templates', 'android-extras', 'admob'), javaDir);

// ---- permissions ----
const manifestPerms = new Map([
  ['android.permission.INTERNET', {}],
  ['android.permission.ACCESS_NETWORK_STATE', {}],
]);
if (cfg.downloads) manifestPerms.set('android.permission.WRITE_EXTERNAL_STORAGE', { maxSdk: 28 });
const features = new Set();
const runtime = [];
for (const p of PERMISSIONS) {
  if (!cfg.permissions.includes(p.id) || !p.android) continue;
  for (const [name, opts] of p.android.manifest) manifestPerms.set(name, opts || {});
  for (const f of p.android.features || []) features.add(f);
  runtime.push(...p.android.runtime);
}
if (on('push') || on('reminder') || on('notifyBridge')) {
  manifestPerms.set('android.permission.POST_NOTIFICATIONS', {});
  if (!runtime.some((r) => r.name === 'android.permission.POST_NOTIFICATIONS')) {
    runtime.push({ name: 'android.permission.POST_NOTIFICATIONS', minSdk: 33 });
  }
}
if (on('reminder') || on('notifyBridge')) manifestPerms.set('android.permission.RECEIVE_BOOT_COMPLETED', {});
if (on('appLock')) manifestPerms.set('android.permission.USE_BIOMETRIC', {});
if (on('jsBridge') || on('haptics')) manifestPerms.set('android.permission.VIBRATE', {});

const permXml = [...manifestPerms].map(([name, o]) =>
  `    <uses-permission android:name="${xml(name)}"${o.maxSdk ? ` android:maxSdkVersion="${o.maxSdk}"` : ''} />`).join('\n');
const featureXml = [...features].map((f) =>
  `    <uses-feature android:name="${xml(f)}" android:required="false" />`).join('\n');

const cleartext = cfg.url.startsWith('http://');

// Links that open the app (deep links).
const linkFilters = [];
if (on('deepLinks')) {
  if (feat.deepLinks.scheme) {
    linkFilters.push(`            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="${xml(feat.deepLinks.scheme)}" />
            </intent-filter>`);
  }
  for (const host of feat.deepLinks.hosts || []) {
    linkFilters.push(`            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="https" />
                <data android:scheme="http" />
                <data android:host="${xml(host)}" />
            </intent-filter>`);
  }
}

const extraApp = [];
if (on('reminder')) {
  extraApp.push(`        <receiver
            android:name="app.web2app.shell.ReminderReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>`);
}
if (on('notifyBridge')) {
  extraApp.push(`        <receiver
            android:name="app.web2app.shell.NotifyReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>`);
}
if (on('push')) {
  extraApp.push(`        <service
            android:name="app.web2app.shell.PushService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
        <meta-data
            android:name="com.google.firebase.messaging.default_notification_channel_id"
            android:value="w2a_push" />
        <meta-data
            android:name="com.google.firebase.messaging.default_notification_icon"
            android:resource="@drawable/w2a_notifications" />
        <meta-data
            android:name="com.google.firebase.messaging.default_notification_color"
            android:resource="@color/theme" />`);
}
if (on('admob')) {
  extraApp.push(`        <meta-data
            android:name="com.google.android.gms.ads.APPLICATION_ID"
            android:value="${xml(feat.admob.appId)}" />`);
}

write(path.join(main, 'AndroidManifest.xml'), `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

${permXml}
${featureXml}

    <queries>
        <intent>
            <action android:name="android.media.action.IMAGE_CAPTURE" />
        </intent>
        <intent>
            <action android:name="android.intent.action.VIEW" />
            <data android:scheme="https" />
        </intent>
        <intent>
            <action android:name="android.intent.action.TTS_SERVICE" />
        </intent>
    </queries>

    <application
        android:allowBackup="true"
        android:enableOnBackInvokedCallback="true"
        android:hardwareAccelerated="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/AppTheme"
        android:usesCleartextTraffic="${cleartext}">

        <activity
            android:name="app.web2app.shell.MainActivity"
            android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboard|keyboardHidden|uiMode|density"
            android:exported="true"
            android:launchMode="singleTask"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
${linkFilters.join('\n')}
        </activity>
${extraApp.join('\n')}

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="\${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
`);

// ---- resources ----
function androidString(s) {
  let v = s.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/"/g, '\\"');
  if (/^[@?]/.test(v)) v = `\\${v}`;
  return v.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

write(path.join(main, 'res', 'values', 'strings.xml'), `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">${androidString(cfg.appName)}</string>
</resources>
`);

write(path.join(main, 'res', 'values', 'colors.xml'), `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="theme">${cfg.themeColor}</color>
    <color name="splash_bg">${cfg.splashColor}</color>
    <color name="icon_bg">${cfg.iconBgColor}</color>
</resources>
`);

const icons = payload.icons || {};
writeImage(path.join(main, 'res', 'mipmap-xxxhdpi', 'ic_launcher.png'), icons.android, 'png');
writeImage(path.join(main, 'res', 'mipmap-xxxhdpi', 'ic_launcher_legacy.png'), icons.android, 'png');
writeImage(path.join(main, 'res', 'drawable-xxxhdpi', 'ic_launcher_fg.png'), icons.androidFg, 'png');
// Splash image or video, found by name in MainActivity (resource shrinking is off).
const media = splashMedia(payload);
if (media && media.kind === 'image') write(path.join(main, 'res', 'drawable-nodpi', `w2a_splash_bg.${media.ext}`), media.buf);
if (media && media.kind === 'video') write(path.join(main, 'res', 'raw', 'w2a_splash.mp4'), media.buf);

// Apps made from uploaded files: the site goes into the app and is served from there (no internet needed).
const site = siteFiles(payload);
if (site) {
  for (const file of site) write(path.join(main, 'assets', 'www', ...file.path.split('/')), file.buf);
  console.log(`Site files: ${site.length}`);
}

// The window behind the first page: the logo on the splash colour only when the splash screen is on.
// Off, the app opens on a plain page colour, like a browser.
const launchBg = on('splash') ? '@drawable/splash' : '@color/splash_bg';
write(path.join(main, 'res', 'values', 'styles.xml'), `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">${launchBg}</item>
        <item name="android:forceDarkAllowed">false</item>
        <item name="android:colorAccent">@color/theme</item>
        <item name="android:statusBarColor">@color/theme</item>
        <item name="android:navigationBarColor">@color/theme</item>
    </style>
</resources>
`);

if (on('darkMode')) {
  // Dark theme when the phone is in dark mode; web pages then see prefers-color-scheme: dark.
  if (!on('splash')) {
    write(path.join(main, 'res', 'values-night', 'colors.xml'), `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="splash_bg">#121212</color>
</resources>
`);
  }
  write(path.join(main, 'res', 'values-night', 'styles.xml'), `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">${launchBg}</item>
        <item name="android:forceDarkAllowed">false</item>
        <item name="android:colorAccent">@color/theme</item>
        <item name="android:statusBarColor">@color/theme</item>
        <item name="android:navigationBarColor">@color/theme</item>
    </style>
</resources>
`);
}

// Android 12+ always shows a system launch screen. It shows only a plain colour (no icon): with our
// splash on, the app goes straight from the colour into it; with it off, straight into the page.
function v31Styles(parent) {
  const blankIcon = `
        <item name="android:windowSplashScreenAnimatedIcon">@drawable/w2a_blank</item>
        <item name="android:windowSplashScreenIconBackgroundColor">@color/splash_bg</item>`;
  return `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="${parent}">
        <item name="android:windowBackground">@color/splash_bg</item>
        <item name="android:windowSplashScreenBackground">@color/splash_bg</item>${blankIcon}
        <item name="android:forceDarkAllowed">false</item>
        <item name="android:colorAccent">@color/theme</item>
        <item name="android:statusBarColor">@color/theme</item>
        <item name="android:navigationBarColor">@color/theme</item>
    </style>
</resources>
`;
}
write(path.join(main, 'res', 'values-v31', 'styles.xml'), v31Styles('android:Theme.Material.Light.NoActionBar'));
if (on('darkMode')) write(path.join(main, 'res', 'values-night-v31', 'styles.xml'), v31Styles('android:Theme.Material.NoActionBar'));

// ---- runtime config read by MainActivity ----
const appConfig = { ...cfg, androidRuntimePermissions: runtime, pageJs: pageScript(cfg) };
delete appConfig.iosPurpose;
if (appConfig.features && appConfig.features.push) {
  // The Firebase file is compiled in by the Google Services plugin; the app does not need a copy.
  appConfig.features = { ...appConfig.features, push: { ...appConfig.features.push, googleServices: undefined } };
}
write(path.join(main, 'assets', 'config.json'), JSON.stringify(appConfig, null, 2));

write(path.join(out, 'app', 'appconfig.properties'),
  `applicationId=${cfg.packageId}\nversionCode=${cfg.versionCode}\nversionName=${cfg.versionName}\n` +
  `push=${on('push')}\nadmob=${on('admob')}\n`);

// ---- signing ----
const signing = payload.android || {};
const ksFile = path.join(out, 'release.keystore');
if (signing.keystore) {
  writeBinary(ksFile, signing.keystore);
  setEnv('W2A_KEYSTORE_GENERATED', '0');
  setEnv('W2A_KEYSTORE_PASS', signing.storePass || '', { secret: true });
  setEnv('W2A_KEY_ALIAS', signing.alias || 'upload');
  setEnv('W2A_KEY_PASS', signing.keyPass || signing.storePass || '', { secret: true });
} else {
  // No keystore supplied: the workflow creates one and ships it with the build.
  const pass = crypto.randomBytes(12).toString('hex');
  setEnv('W2A_KEYSTORE_GENERATED', '1');
  setEnv('W2A_KEYSTORE_PASS', pass, { secret: true });
  setEnv('W2A_KEY_ALIAS', 'upload');
  setEnv('W2A_KEY_PASS', pass, { secret: true });
}
setEnv('W2A_KEYSTORE_FILE', ksFile);
setEnv('W2A_PACKAGE_ID', cfg.packageId);
setEnv('W2A_VERSION', `${cfg.versionName}-${cfg.versionCode}`);

console.log(`Android project ready: v${cfg.versionName} (${cfg.versionCode}), ` +
  `permissions: ${cfg.permissions.join(', ') || 'none'}, ` +
  `features: ${Object.keys(feat).join(', ') || 'none'}`);
