// Single source of truth for permissions and config validation.
import { validateFeatures } from './features.mjs';
export * from './features.mjs';

// Used by the Worker (validation), the build scripts (code generation) and the website (toggles).

// android.manifest: [name, {maxSdk?, minSdk?}]   android.runtime: asked at run time
// ios.plist: Info.plist purpose-string keys      web: true = usable directly by the website
export const PERMISSIONS = [
  {
    id: 'camera', web: true,
    android: { manifest: [['android.permission.CAMERA']], runtime: [{ name: 'android.permission.CAMERA' }],
      features: ['android.hardware.camera'] },
    ios: { plist: ['NSCameraUsageDescription'] },
  },
  {
    id: 'microphone', web: true,
    android: { manifest: [['android.permission.RECORD_AUDIO'], ['android.permission.MODIFY_AUDIO_SETTINGS']],
      runtime: [{ name: 'android.permission.RECORD_AUDIO' }], features: ['android.hardware.microphone'] },
    ios: { plist: ['NSMicrophoneUsageDescription'] },
  },
  {
    id: 'location', web: true,
    android: { manifest: [['android.permission.ACCESS_FINE_LOCATION'], ['android.permission.ACCESS_COARSE_LOCATION']],
      runtime: [{ name: 'android.permission.ACCESS_FINE_LOCATION' }, { name: 'android.permission.ACCESS_COARSE_LOCATION' }],
      features: ['android.hardware.location'] },
    ios: { plist: ['NSLocationWhenInUseUsageDescription'] },
  },
  {
    id: 'notifications',
    android: { manifest: [['android.permission.POST_NOTIFICATIONS']],
      runtime: [{ name: 'android.permission.POST_NOTIFICATIONS', minSdk: 33 }] },
    ios: { plist: [] },
  },
  {
    id: 'photos',
    android: { manifest: [['android.permission.READ_MEDIA_IMAGES'], ['android.permission.READ_MEDIA_VIDEO'],
      ['android.permission.READ_EXTERNAL_STORAGE', { maxSdk: 32 }]],
      runtime: [{ name: 'android.permission.READ_MEDIA_IMAGES', minSdk: 33 },
        { name: 'android.permission.READ_MEDIA_VIDEO', minSdk: 33 },
        { name: 'android.permission.READ_EXTERNAL_STORAGE', maxSdk: 32 }] },
    ios: { plist: ['NSPhotoLibraryUsageDescription', 'NSPhotoLibraryAddUsageDescription'] },
  },
  {
    id: 'contacts',
    android: { manifest: [['android.permission.READ_CONTACTS']], runtime: [{ name: 'android.permission.READ_CONTACTS' }] },
    ios: { plist: ['NSContactsUsageDescription'] },
  },
  {
    id: 'calendar',
    android: { manifest: [['android.permission.READ_CALENDAR'], ['android.permission.WRITE_CALENDAR']],
      runtime: [{ name: 'android.permission.READ_CALENDAR' }, { name: 'android.permission.WRITE_CALENDAR' }] },
    ios: { plist: ['NSCalendarsUsageDescription', 'NSCalendarsFullAccessUsageDescription'] },
  },
  {
    id: 'bluetooth',
    android: { manifest: [['android.permission.BLUETOOTH', { maxSdk: 30 }], ['android.permission.BLUETOOTH_ADMIN', { maxSdk: 30 }],
      ['android.permission.BLUETOOTH_CONNECT'], ['android.permission.BLUETOOTH_SCAN']],
      runtime: [{ name: 'android.permission.BLUETOOTH_CONNECT', minSdk: 31 }, { name: 'android.permission.BLUETOOTH_SCAN', minSdk: 31 }],
      features: ['android.hardware.bluetooth'] },
    ios: { plist: ['NSBluetoothAlwaysUsageDescription'] },
  },
  {
    id: 'phone',
    android: { manifest: [['android.permission.CALL_PHONE']], runtime: [{ name: 'android.permission.CALL_PHONE' }],
      features: ['android.hardware.telephony'] },
    ios: null,
  },
  {
    id: 'motion',
    android: { manifest: [['android.permission.ACTIVITY_RECOGNITION']],
      runtime: [{ name: 'android.permission.ACTIVITY_RECOGNITION', minSdk: 29 }] },
    ios: { plist: ['NSMotionUsageDescription'] },
  },
  {
    id: 'biometric',
    android: { manifest: [['android.permission.USE_BIOMETRIC']], runtime: [] },
    ios: { plist: ['NSFaceIDUsageDescription'] },
  },
  {
    id: 'nfc',
    android: { manifest: [['android.permission.NFC']], runtime: [], features: ['android.hardware.nfc'] },
    ios: null,
  },
  {
    id: 'vibrate',
    android: { manifest: [['android.permission.VIBRATE']], runtime: [] },
    ios: null,
  },
  {
    id: 'wakelock',
    android: { manifest: [['android.permission.WAKE_LOCK']], runtime: [] },
    ios: null,
  },
  {
    id: 'tracking',
    android: { manifest: [['com.google.android.gms.permission.AD_ID']], runtime: [] },
    ios: { plist: ['NSUserTrackingUsageDescription'] },
  },
];

export const PERMISSION_IDS = PERMISSIONS.map((p) => p.id);

const HEX = /^#[0-9a-fA-F]{6}$/;
const PACKAGE = /^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$/;
const BUNDLE = /^[a-zA-Z][a-zA-Z0-9-]*(\.[a-zA-Z0-9][a-zA-Z0-9-]*)+$/;
const VERSION = /^\d{1,4}(\.\d{1,4}){0,2}$/;
const HOST = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$/;
const JAVA_KEYWORDS = new Set(('abstract assert boolean break byte case catch char class const continue default do double ' +
  'else enum extends final finally float for goto if implements import instanceof int interface long native new package ' +
  'private protected public return short static strictfp super switch synchronized this throw throws transient try void ' +
  'volatile while true false null').split(' '));

function str(v, max) {
  // Drop control characters, keep everything else (any language).
  return String(v ?? '').replace(/[\x00-\x1f\x7f\u{2028}\u{2029}]/gu, ' ').trim().slice(0, max);
}

/**
 * Validates and normalises a user supplied app config.
 * Returns { ok: true, config } or { ok: false, error }.
 */
export function validateConfig(input) {
  const c = input && typeof input === 'object' ? input : {};
  const fail = (error) => ({ ok: false, error });

  let url;
  try {
    url = new URL(str(c.url, 2000));
  } catch {
    return fail('url: valid website address needed');
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:') return fail('url: must start with https:// or http://');
  if (!url.hostname.includes('.')) return fail('url: must be a public website');

  const appName = str(c.appName, 30);
  if (!appName) return fail('appName: required');

  const packageId = str(c.packageId, 100);
  if (!PACKAGE.test(packageId) || !BUNDLE.test(packageId)) {
    return fail('packageId: use a form like com.company.app (letters, digits, dots)');
  }
  if (packageId.split('.').some((part) => JAVA_KEYWORDS.has(part))) {
    return fail('packageId: a part of the id is a reserved word');
  }

  const versionName = str(c.versionName || '1.0', 20);
  if (!VERSION.test(versionName)) return fail('versionName: use a form like 1.0 or 1.2.3');
  const versionCode = Number.parseInt(c.versionCode ?? 1, 10);
  if (!Number.isInteger(versionCode) || versionCode < 1 || versionCode > 2000000000) {
    return fail('versionCode: must be a number from 1');
  }

  const themeColor = str(c.themeColor || '#1565C0', 7);
  const splashColor = str(c.splashColor || '#FFFFFF', 7);
  const iconBgColor = str(c.iconBgColor || '#FFFFFF', 7);
  if (!HEX.test(themeColor) || !HEX.test(splashColor) || !HEX.test(iconBgColor)) {
    return fail('colors: use #RRGGBB');
  }

  const orientation = ['auto', 'portrait', 'landscape'].includes(c.orientation) ? c.orientation : 'auto';

  const permissions = [...new Set(Array.isArray(c.permissions) ? c.permissions : [])]
    .filter((id) => PERMISSION_IDS.includes(id));

  const internalHosts = (Array.isArray(c.internalHosts) ? c.internalHosts : [])
    .map((h) => str(h, 200).toLowerCase())
    .filter((h) => HOST.test(h))
    .slice(0, 20);

  const purposeIn = c.iosPurpose && typeof c.iosPurpose === 'object' ? c.iosPurpose : {};
  const iosPurpose = {};
  for (const id of permissions) {
    const text = str(purposeIn[id], 200);
    if (text) iosPurpose[id] = text;
  }

  const bool = (v, d) => (typeof v === 'boolean' ? v : d);

  const feat = validateFeatures(c.features, packageId);
  if (!feat.ok) return fail(feat.error);

  return {
    ok: true,
    config: {
      url: url.toString(),
      appName,
      packageId,
      versionName,
      versionCode,
      themeColor,
      splashColor,
      iconBgColor,
      orientation,
      permissions,
      internalHosts,
      iosPurpose,
      askPermissionsOnStart: bool(c.askPermissionsOnStart, true),
      pullToRefresh: bool(c.pullToRefresh, true),
      externalLinksInBrowser: bool(c.externalLinksInBrowser, true),
      zoom: bool(c.zoom, false),
      fullscreen: bool(c.fullscreen, false),
      keepScreenOn: bool(c.keepScreenOn, false),
      downloads: bool(c.downloads, true),
      cache: bool(c.cache, true),
      ipad: bool(c.ipad, false),
      userAgent: str(c.userAgent, 300),
      userAgentSuffix: str(c.userAgentSuffix, 100),
      offlineMessage: str(c.offlineMessage, 120) || 'No internet connection',
      retryLabel: str(c.retryLabel, 30) || 'Retry',
      features: feat.features,
    },
  };
}
