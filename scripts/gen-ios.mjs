// Usage: node scripts/gen-ios.mjs <payload.json> <output-dir>
// Turns the iOS template plus a build request into an XcodeGen project (project.json).
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { PERMISSIONS } from '../shared/catalog.mjs';
import { readPayload, copyDir, write, writeImage, writeBinary, setEnv } from './lib.mjs';

const [payloadFile, outDir] = process.argv.slice(2);
if (!payloadFile || !outDir) {
  console.error('Usage: gen-ios.mjs <payload.json> <output-dir>');
  process.exit(2);
}

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const payload = readPayload(payloadFile);
const cfg = payload.config;
const out = path.resolve(outDir);

copyDir(path.join(root, 'templates', 'ios'), out);

// ---- features ----
const feat = cfg.features || {};
const on = (id) => Boolean(feat[id] && feat[id].enabled);

// ---- runtime config read by AppConfig.swift ----
const appConfig = { ...cfg };
if (appConfig.features && appConfig.features.push) {
  // Push is Android only; its Firebase file is not needed here.
  appConfig.features = { ...appConfig.features, push: { ...appConfig.features.push, googleServices: undefined } };
}
write(path.join(out, 'Sources', 'config.json'), JSON.stringify(appConfig, null, 2));

// ---- assets ----
const assets = path.join(out, 'Sources', 'Assets.xcassets');
write(path.join(assets, 'Contents.json'), JSON.stringify({ info: { author: 'xcode', version: 1 } }, null, 2));
write(path.join(assets, 'AppIcon.appiconset', 'Contents.json'), JSON.stringify({
  images: [{ filename: 'icon.png', idiom: 'universal', platform: 'ios', size: '1024x1024' }],
  info: { author: 'xcode', version: 1 },
}, null, 2));
// The workflow converts this JPEG to an opaque PNG (App Store icons may not have transparency).
writeImage(path.join(out, 'icon-source.jpg'), (payload.icons || {}).ios, 'jpeg');
// The same artwork for the splash screen and welcome slides (the app icon itself cannot be loaded by name).
writeImage(path.join(assets, 'SplashLogo.imageset', 'logo.jpg'), (payload.icons || {}).ios, 'jpeg');
write(path.join(assets, 'SplashLogo.imageset', 'Contents.json'), JSON.stringify({
  images: [{ filename: 'logo.jpg', idiom: 'universal' }],
  info: { author: 'xcode', version: 1 },
}, null, 2));

const hex = (i) => `0x${cfg.splashColor.slice(i, i + 2).toUpperCase()}`;
write(path.join(assets, 'LaunchBg.colorset', 'Contents.json'), JSON.stringify({
  colors: [{
    idiom: 'universal',
    color: { 'color-space': 'srgb', components: { red: hex(1), green: hex(3), blue: hex(5), alpha: '1.000' } },
  }],
  info: { author: 'xcode', version: 1 },
}, null, 2));

// ---- Info.plist ----
const portrait = ['UIInterfaceOrientationPortrait'];
const landscape = ['UIInterfaceOrientationLandscapeLeft', 'UIInterfaceOrientationLandscapeRight'];
const orientations = cfg.orientation === 'portrait' ? portrait
  : cfg.orientation === 'landscape' ? landscape : [...portrait, ...landscape];

const info = {
  CFBundleDisplayName: cfg.appName,
  CFBundleShortVersionString: '$(MARKETING_VERSION)',
  CFBundleVersion: '$(CURRENT_PROJECT_VERSION)',
  LSRequiresIPhoneOS: true,
  ITSAppUsesNonExemptEncryption: false,
  UILaunchScreen: { UIColorName: 'LaunchBg' },
  UIApplicationSceneManifest: { UIApplicationSupportsMultipleScenes: false },
  UISupportedInterfaceOrientations: orientations,
  NSAppTransportSecurity: { NSAllowsArbitraryLoadsInWebContent: true, NSAllowsLocalNetworking: true },
};
if (cfg.ipad) {
  // iPad apps must support every orientation unless they opt out of multitasking.
  info['UISupportedInterfaceOrientations~ipad'] = [...portrait, 'UIInterfaceOrientationPortraitUpsideDown', ...landscape];
  if (cfg.orientation !== 'auto') info.UIRequiresFullScreen = true;
}
for (const p of PERMISSIONS) {
  if (!cfg.permissions.includes(p.id) || !p.ios) continue;
  const text = cfg.iosPurpose[p.id] || `${cfg.appName} needs this access for features you use in the app.`;
  for (const key of p.ios.plist) info[key] = text;
}
// Without the dark mode feature the app (and the pages in it) always stay light, as on Android.
if (!on('darkMode')) info.UIUserInterfaceStyle = 'Light';
if (on('appLock') && !info.NSFaceIDUsageDescription) {
  info.NSFaceIDUsageDescription = `${cfg.appName} uses Face ID to unlock the app.`;
}
// Music and radio keep playing with the app closed or the screen off.
if (on('backgroundAudio')) info.UIBackgroundModes = ['audio'];
// Saving a long-pressed image to Photos.
if (on('longPress') && feat.longPress.saveImages !== false && !info.NSPhotoLibraryAddUsageDescription) {
  info.NSPhotoLibraryAddUsageDescription = `${cfg.appName} saves pictures you choose to your photo library.`;
}
if (on('deepLinks') && feat.deepLinks.scheme) {
  info.CFBundleURLTypes = [{ CFBundleURLName: cfg.packageId, CFBundleURLSchemes: [feat.deepLinks.scheme] }];
}

// ---- XcodeGen spec (JSON is valid input for xcodegen) ----
const spec = {
  name: 'App',
  options: { deploymentTarget: { iOS: '15.0' }, createIntermediateGroups: true },
  targets: {
    App: {
      type: 'application',
      platform: 'iOS',
      sources: ['Sources'],
      info: { path: 'Info.plist', properties: info },
      settings: {
        base: {
          PRODUCT_NAME: 'App',
          PRODUCT_BUNDLE_IDENTIFIER: cfg.packageId,
          MARKETING_VERSION: cfg.versionName,
          CURRENT_PROJECT_VERSION: String(cfg.versionCode),
          TARGETED_DEVICE_FAMILY: cfg.ipad ? '1,2' : '1',
          ASSETCATALOG_COMPILER_APPICON_NAME: 'AppIcon',
          SWIFT_VERSION: '5.0',
          ENABLE_BITCODE: 'NO',
        },
      },
      scheme: {},
    },
  },
};
write(path.join(out, 'project.json'), JSON.stringify(spec, null, 2));

// ---- signing ----
const signing = payload.ios || {};
if (signing.p12 && signing.profile) {
  writeBinary(path.join(out, 'signing', 'cert.p12'), signing.p12);
  writeBinary(path.join(out, 'signing', 'profile.mobileprovision'), signing.profile);
  setEnv('W2A_IOS_SIGNED', '1');
  setEnv('W2A_P12_PASS', signing.p12Pass || '', { secret: true });
} else {
  setEnv('W2A_IOS_SIGNED', '0');
}
setEnv('W2A_BUNDLE_ID', cfg.packageId);
setEnv('W2A_VERSION', `${cfg.versionName}-${cfg.versionCode}`);

console.log(`iOS project ready: v${cfg.versionName} (${cfg.versionCode}), ` +
  `signed: ${signing.p12 && signing.profile ? 'yes' : 'no'}, ` +
  `features: ${Object.keys(feat).filter(on).join(', ') || 'none'}`);
