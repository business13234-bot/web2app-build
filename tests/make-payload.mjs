// node tests/make-payload.mjs <a|b> <out.json>
// A validated build request with (almost) every feature on, pointing at the test website.
//   SITE=http://10.0.2.2:8090/  (Android emulator)   SITE=http://localhost:8090/  (iOS simulator)
//   REMINDER=HH:MM   time of the daily reminder
// Variant a and b use different option values, so both sides of each option get tested.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { validateConfig } from '../shared/catalog.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const [variant = 'a', out] = process.argv.slice(2);
const base = JSON.parse(fs.readFileSync(path.join(here, 'fixture-base.json'), 'utf8'));
const site = process.env.SITE || 'http://10.0.2.2:8090/';
const cfg = structuredClone(base.config);
cfg.url = site;
cfg.packageId = 'com.example.w2atest';
cfg.appName = 'W2A Test';
const F = cfg.features;
const u = (p) => new URL(p, site).toString();
F.bottomNav.items = [
  { label: 'Home', url: u('/'), icon: 'home' },
  { label: 'Search', url: u('/search'), icon: 'search' },
  { label: 'Cart', url: u('/cart'), icon: 'shopping_cart' },
  { label: 'Me', url: u('/me'), icon: 'person' },
];
F.shortcuts.items = [{ label: 'Cart', url: u('/cart'), icon: 'shopping_cart' }, { label: 'Search', url: u('/search'), icon: 'search' }];
F.updatePrompt = { enabled: true, checkUrl: u('/version.json'), force: false, message: 'Default update text' };
F.deepLinks = { enabled: true, scheme: 'w2atest', hosts: [] };
F.blocker = { enabled: true, hosts: ['nip.io'] };
F.customCss = { enabled: true, css: '.site-footer{display:none!important}' };
F.customJs = { enabled: true, js: 'window.w2aJsRan=(window.w2aJsRan||0)+1;document.documentElement.dataset.js="ran";' };
F.jsBridge = { enabled: true, name: 'Web2App' };
F.textSize = { enabled: true, percent: 150 };
F.rateApp = { enabled: true, launches: 2, title: 'Rate test title', message: 'Rate test message' };
F.reminder = { enabled: true, time: process.env.REMINDER || '19:00', title: 'Reminder test', text: 'Reminder body' };
delete F.secureScreen; // would block the test screenshots; checked on its own
delete F.push; // needs a real Firebase project
if (variant === 'c') {
  // The features added in 1.2, together with the older ones they touch.
  for (const id of ['rateApp', 'updatePrompt', 'onboarding', 'admob', 'desktopMode', 'clearOnStart', 'rememberPage']) delete F[id];
  F.topBar = { ...F.topBar, title: 'page', back: true, refresh: true };
  F.exitConfirm = { enabled: true, mode: 'dialog', message: 'Exit test dialog?' };
  F.blocker = { enabled: true, hosts: ['tracker.invalid'] };
  F.textSize = { enabled: true, percent: 100 };
  const on = (o = {}) => ({ enabled: true, ...o });
  F.announcement = on({ title: 'Test news', message: 'Announcement body', button: 'Got it', link: u('/page2'), linkLabel: 'Open page', version: 1 });
  F.noticeBar = on({ checkUrl: u('/notice.json'), color: '#6200EA', dismissible: true });
  F.drawer = on({ items: [{ label: 'Home', url: u('/'), icon: 'home' }, { label: 'Cart', url: u('/cart'), icon: 'shopping_cart' },
    { label: 'Ticket', url: u('/ticket/42'), icon: 'bookmark' }], header: true, headerColor: '' });
  F.scrollTop = on({ after: 600, position: 'right' });
  F.pageFade = on({ ms: 300 });
  F.autoTheme = on({ topBar: true, statusBar: true });
  F.notifyBridge = on({ channelName: 'Test reminders', max: 5 });
  F.privacyCover = on();
  F.consent = on({ title: 'Your privacy', message: 'Consent test message', policyUrl: u('/privacy'), accept: 'Accept', version: 1 });
  F.ageGate = on({ age: 18, blocked: 'Age blocked test' });
  F.haptics = on({ page: true });
  F.shake = on({ action: 'reload', email: '' });
  F.idleReset = on({ minutes: 1, clear: false });
  F.findInPage = on();
  F.printPage = on();
  F.offlineCache = on({ message: 'Offline copy test' });
  F.inAppBrowser = on({ color: '' });
  F.longPress = on({ saveImages: true });
  F.brightness = on({ paths: '/ticket', level: 100 });
  F.backgroundAudio = on();
} else if (variant === 'b') {
  F.loader = { enabled: true, style: 'spinner', color: '#E91E63' };
  F.topBar = { ...F.topBar, title: 'page', home: true, share: true, back: true, refresh: true, bg: '#004D40', fg: '#FFFFFF' };
  F.exitConfirm = { enabled: true, mode: 'dialog', message: 'Exit test dialog?' };
  F.fab = { ...F.fab, action: 'url', value: u('/page2'), position: 'left', icon: 'star' };
  F.splash = { ...F.splash, style: 'name' };
  F.desktopMode = { enabled: true };
  F.clearOnStart = { enabled: true, what: 'all' };
  delete F.rememberPage;
  delete F.onboarding;
  delete F.admob;
} else {
  F.exitConfirm = { enabled: true, mode: 'double', message: 'Press back again to exit' };
  delete F.desktopMode;
  delete F.clearOnStart;
}
const r = validateConfig(cfg);
if (r.error) {
  console.error('invalid:', r.error);
  process.exit(1);
}
fs.writeFileSync(out, JSON.stringify({ id: 'test' + variant, platforms: ['android', 'ios'], config: r.config, icons: base.icons }));
console.log('ok', Object.keys(r.config.features).join(','));
