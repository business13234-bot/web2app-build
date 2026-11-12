// node tests/make-payload.mjs <a|b|c|d|e> <out.json>
// d = features added in 1.4 against the test website, e = an app made from a ZIP of a small site.
// A validated build request with (almost) every feature on, pointing at the test website.
//   SITE=http://10.0.2.2:8090/  (Android emulator)   SITE=http://localhost:8090/  (iOS simulator)
//   REMINDER=HH:MM   time of the daily reminder
// Variant a and b use different option values, so both sides of each option get tested.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import zlib from 'node:zlib';
import { validateConfig } from '../shared/catalog.mjs';

/** A small ZIP (one folder deep, stored and deflated entries) with a site that uses CSS, JS, fetch and an image. */
function siteZip() {
  const files = {
    'MySite/Index.HTML': '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
      + '<link rel="stylesheet" href="css/style.css"><title>Zip Site</title></head><body><h1>Offline ZIP site</h1>'
      + '<img src="img/logo.svg" width="120" alt="logo"><p id="s">js pending</p><a href="about.html">About page</a> '
      + '<a href="docs/">Docs folder</a><script src="js/app.js"></script></body></html>',
    'MySite/about.html': '<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="css/style.css"></head>'
      + '<body><h1>About (from ZIP)</h1><a href="index.html">Back home</a></body></html>',
    'MySite/docs/index.html': '<!doctype html><html><body><h1>Docs index</h1></body></html>',
    'MySite/css/style.css': 'body{font-family:sans-serif;background:#0F766E;color:#fff;padding:20px}h1{color:#FDE68A}',
    'MySite/js/app.js': "fetch('data/info.json').then(function(r){return r.json();}).then(function(j){"
      + "document.getElementById('s').textContent='zip js '+location.protocol+' '+j.msg;});",
    'MySite/data/info.json': JSON.stringify({ msg: 'json loaded' }),
    'MySite/img/logo.svg': '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><circle cx="5" cy="5" r="5" fill="#FDE68A"/></svg>',
  };
  const table = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
  const crc = (b) => { let c = 0xffffffff; for (const v of b) c = table[(c ^ v) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
  const parts = [], central = []; let off = 0, i = 0;
  for (const [name, text] of Object.entries(files)) {
    const raw = Buffer.from(text); const method = i++ % 2 ? 0 : 8; const data = method ? zlib.deflateRawSync(raw) : raw; const nb = Buffer.from(name);
    const h = Buffer.alloc(30); h.writeUInt32LE(0x04034b50, 0); h.writeUInt16LE(20, 4); h.writeUInt16LE(method, 8);
    h.writeUInt32LE(crc(raw), 14); h.writeUInt32LE(data.length, 18); h.writeUInt32LE(raw.length, 22); h.writeUInt16LE(nb.length, 26);
    parts.push(h, nb, data);
    const c = Buffer.alloc(46); c.writeUInt32LE(0x02014b50, 0); c.writeUInt16LE(20, 4); c.writeUInt16LE(20, 6); c.writeUInt16LE(method, 10);
    c.writeUInt32LE(crc(raw), 16); c.writeUInt32LE(data.length, 20); c.writeUInt32LE(raw.length, 24); c.writeUInt16LE(nb.length, 28); c.writeUInt32LE(off, 42);
    central.push(c, nb); off += 30 + nb.length + data.length;
  }
  const cd = Buffer.concat(central); const end = Buffer.alloc(22); const n = Object.keys(files).length;
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(n, 8); end.writeUInt16LE(n, 10); end.writeUInt32LE(cd.length, 12); end.writeUInt32LE(off, 16);
  return Buffer.concat([...parts, cd, end]);
}

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
let source = null;
if (variant === 'd' || variant === 'e') {
  // Only the features under test, so nothing else gets in the way.
  const keep = { splash: F.splash, appLock: F.appLock };
  for (const id of Object.keys(F)) delete F[id];
  const on = (o = {}) => ({ enabled: true, ...o });
  F.splash = { ...keep.splash, enabled: true, background: 'color', color: '#6D28D9', animation: variant === 'd' ? 'pulse' : 'rise' };
  if (variant === 'd') {
    cfg.url = u('/feat');
    F.appLock = { ...(keep.appLock || {}), enabled: true };
    F.progressBar = on({ color: '#F59E0B', height: 4 });
    F.appFont = on({ font: 'Poppins' });
    F.imageViewer = on({ minSize: 80 });
    F.resumeReload = on({ minutes: 1 });
    F.dataSaver = on();
    F.stayInApp = on({ message: 'Stay-in-app test message' });
    F.errorPage = on({ title: 'Oops test', message: 'Error page test', notFound: true });
    F.speech = on({ rate: 100 });
  } else {
    cfg.source = 'local';
    cfg.url = '';
    F.imageViewer = on({ minSize: 80 });
    source = { type: 'zip', data: siteZip().toString('base64') };
  }
} else if (variant === 'c') {
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
const payload = { id: 'test' + variant, platforms: ['android', 'ios'], config: r.config, icons: base.icons };
if (source) payload.source = source;
// Free-plan watermark: variant a has no plan (the mark shows), the others have one (it stays hidden).
payload.watermark = { text: 'Built with Web2App', api: `${site}api/wm/${(variant === 'a' ? 'F' : 'P').repeat(32)}`, until: 0 };
fs.writeFileSync(out, JSON.stringify(payload));
console.log('ok', Object.keys(r.config.features).join(','));
