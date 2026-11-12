// Test website for checking every app feature on a phone or simulator.
// node tests/site/server.mjs [port]      TRACKER_HOST=<host that resolves to this machine>
// Pages show a status line (#status) that the tests read: what the app changed in the page.
import http from 'node:http';
import { execFile } from 'node:child_process';
import zlib from 'node:zlib';

const port = Number(process.argv[2] || 8090);
const tracker = process.env.TRACKER_HOST || '10-0-2-2.nip.io';
const state = { versionCode: 1, hits: [], noticeId: 'n1', noticeMsg: 'Live notice test', down: false };

// A 20 second 440 Hz tone, so background audio can be checked.
function tone() {
  const rate = 8000, secs = 20, n = rate * secs;
  const b = Buffer.alloc(44 + n * 2);
  b.write('RIFF', 0); b.writeUInt32LE(36 + n * 2, 4); b.write('WAVE', 8); b.write('fmt ', 12);
  b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22); b.writeUInt32LE(rate, 24);
  b.writeUInt32LE(rate * 2, 28); b.writeUInt16LE(2, 32); b.writeUInt16LE(16, 34); b.write('data', 36); b.writeUInt32LE(n * 2, 40);
  for (let i = 0; i < n; i++) b.writeInt16LE(Math.round(Math.sin(2 * Math.PI * 440 * i / rate) * 6000), 44 + i * 2);
  return b;
}
const toneWav = tone();

// A 600x400 PNG with a colour gradient and a grid, so the image viewer has something real to open and zoom.
function bigPng() {
  const w = 600, h = 400;
  const raw = Buffer.alloc((w * 3 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 3 + 1)] = 0;
    for (let x = 0; x < w; x++) {
      const o = y * (w * 3 + 1) + 1 + x * 3;
      const grid = x % 50 === 0 || y % 50 === 0;
      raw[o] = grid ? 255 : Math.round(40 + 200 * x / w);
      raw[o + 1] = grid ? 255 : Math.round(60 + 120 * y / h);
      raw[o + 2] = grid ? 255 : 200;
    }
  }
  const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
  const crc = (b) => { let c = 0xffffffff; for (const v of b) c = crcTable[(c ^ v) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
  const chunk = (type, data) => { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([len, td, c]); };
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 2;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
const big = bigPng();

const probeScript = `<script>
setTimeout(function(){
  var f=document.getElementById('footer'), p=document.getElementById('probe');
  var b=window.Web2App;
  var s=['js='+(document.documentElement.dataset.js||'none'),
    'css='+(getComputedStyle(f).display==='none'?'applied':'missing'),
    'select='+(getComputedStyle(document.body).webkitUserSelect||getComputedStyle(document.body).userSelect),
    'bridge='+(b?b.platform():'none'),
    'tracker='+(window.trackerLoaded?'loaded':'blocked'),
    'width='+innerWidth,
    'dark='+matchMedia('(prefers-color-scheme: dark)').matches,
    'text='+Math.round(p.getBoundingClientRect().height)];
  document.getElementById('status').textContent=s.join(' ');
},1500);
</script>`;

const page = (title, body, head = '') => `<!doctype html><html><head><meta charset="utf-8">${head}
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title}</title>
<style>
body{font-family:-apple-system,sans-serif;margin:0;padding:16px;font-size:16px;color:#222;background:#fff}
@media (prefers-color-scheme: dark){body.darkaware{background:#111;color:#eee}}
a,button{display:block;padding:10px 0;font-size:18px;margin:4px 0}
#probe{font-size:16px;line-height:1;display:inline-block}
#status{font:12px monospace;white-space:pre-wrap;color:#555}
.box{height:700px;background:linear-gradient(#fff,#ddd)}
</style></head><body class="${title === 'Home' ? '' : 'darkaware'}">
<h1 id="h">${title}</h1>
<span id="probe">Probe text</span>
<div id="status">status pending</div>
<div class="site-footer" id="footer">Site footer</div>
${body}
<img id="tracker" alt="" width="1" height="1" src="http://${tracker}:${port}/pixel.png?t=${Date.now()}" onerror="window.trackerFailed=true" onload="window.trackerLoaded=true">
${probeScript}
<div class="box"></div>
</body></html>`;

const pages = {
  // Login pages for saved passwords: a classic form, and a script sign-in that never sends the form.
  '/login': ['Login form', `<form method="post" action="/welcome"><input id="lu" name="email" type="email" autocomplete="username" placeholder="Email">
    <input id="lp" name="password" type="password" autocomplete="current-password" placeholder="Password"><button id="lb">Sign in</button></form>`],
  '/spa-login': ['Script login', `<div id="box"><input id="su" type="text" placeholder="Email or phone">
    <input id="sp2" type="password" placeholder="Password"><div id="sb" role="button" tabindex="0">Log in</div></div>
    <script>document.getElementById('sb').onclick=function(){setTimeout(function(){document.getElementById('box').innerHTML='<p id="done">Signed in</p>';},300);};</script>`],
  '/welcome': ['Welcome', '<p id="done">Signed in</p>'],
  '/': ['Home', `<a id="l-search" href="/search">Search</a><a id="l-cart" href="/cart">Cart</a>
    <button id="b-toast" onclick="window.Web2App&&Web2App.toast('Bridge toast works')">Show toast</button>
    <a id="l-ext" href="https://example.org/">External site</a>
    <a id="l-dl" href="/file.txt" download>Download file</a>
    <a id="l-tel" href="tel:+8801712345678">Call</a>
    <a id="l-deep" href="w2atest://cart">Deep link inside page</a>
    <input id="in" placeholder="Type here">
    <a id="l-long" href="/long">Long page</a>
    <a id="l-themed" href="/themed">Themed page</a>
    <a id="l-ticket" href="/ticket/42">Ticket</a>
    <a id="l-img" href="/img">Image page</a>
    <a id="l-audio" href="/audio">Audio page</a>
    <a id="l-notify" href="/notify">Notify page</a>
    <a id="l-feat" href="/feat">Feature page</a>
    <p id="findme">Needle one, needle two, needle three.</p>`],
  '/privacy': ['Privacy policy', '<p id="policy">This is the test privacy policy.</p>'],
  '/themed': ['Themed page', '<p>Pink theme colour</p>', '<meta name="theme-color" content="#C2185B">'],
  '/long': ['Long page', '<div style="height:5000px;background:linear-gradient(#fff,#9cf)">Scroll down</div><p id="bottom">Bottom</p>'],
  '/img': ['Image page', '<a id="imglink" href="/page2"><img id="pic" src="/photo.png" width="240" height="240" alt="photo"></a><br><img id="pic2" src="/photo.png" width="240" height="240" alt="photo two">'],
  '/audio': ['Audio page', `<audio id="au" src="/tone.wav" loop></audio><button id="play" onclick="document.getElementById('au').play()">Play</button><script>setInterval(function(){var a=document.getElementById('au');document.title='t='+Math.round(a.currentTime*10)/10+' p='+a.paused;},500)</script>`],
  '/notify': ['Notify page', `<button id="b-notify" onclick="var n=document.getElementById('nid');if(!window.Web2AppNotify){n.textContent='id=none';return;}Promise.resolve(Web2AppNotify.schedule('Local test','Local body',5,location.origin+'/cart')).then(function(id){n.textContent='id='+id;})">Schedule in 5 s</button><div id="nid">id=?</div>`],
  '/search': ['Search page', '<a href="/">Home</a><a href="/page2">Page 2</a>'],
  '/cart': ['Cart page', '<a href="/">Home</a>'],
  '/me': ['Me page', '<a href="/">Home</a>'],
  '/page2': ['Page two', '<a href="/">Home</a>'],
  '/feat': ['Feature page', `<button id="b-speak" onclick="window.Web2AppSpeech&&Web2AppSpeech.speak('Hello from the app','en-US')">Speak</button>
    <button id="b-fs" onclick="document.getElementById('fsbox').requestFullscreen()">Fullscreen</button>
    <div id="fsbox" style="background:#333;color:#fff;height:120px">Fullscreen box</div>
    <a id="l-500" href="/err500">Server error</a><a id="l-404" href="/missing-page">Missing page</a>
    <a id="l-ext2" href="https://example.org/">External site</a>
    <img id="bigpic" src="/big.png" style="width:100%;height:auto" alt="big photo">
    <div id="fstatus">feat pending</div>
    <div id="sp">speaking=false</div>
    <script>setTimeout(function(){var p=document.getElementById('probe');
    document.getElementById('fstatus').textContent='font='+getComputedStyle(p).fontFamily.split(',')[0]+' bar='+(!!document.getElementById('w2a-progress'))+' speech='+(typeof window.Web2AppSpeech)+' loaded='+performance.timeOrigin;},2500);</script>
    <script>setInterval(function(){document.getElementById('sp').textContent='speaking='+!!(window.Web2AppSpeech&&Web2AppSpeech.isSpeaking());},300);</script>`],
  '/ticket/42': ['Ticket 42', '<p>Show this at the door</p>', '<meta name="theme-color" content="#1B5E20">'],
};

http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  state.hits.push(`${req.headers.host} ${url.pathname}`);
  if (state.hits.length > 400) state.hits.shift();
  // /set-down?on=1: the site stops answering (to test offline copies); /set-* still works.
  if (url.pathname === '/set-down') {
    state.down = url.searchParams.get('on') === '1';
    return res.end('down ' + state.down);
  }
  if (state.down && !url.pathname.startsWith('/set-') && url.pathname !== '/sim') return req.socket.destroy();
  // /sim?do=shake|open: shakes the iOS simulator or opens a link on it, this site runs next to (SIM_UDID).
  if (url.pathname === '/sim') {
    const udid = process.env.SIM_UDID || (process.platform === 'darwin' ? 'booted' : '');
    const what = url.searchParams.get('do');
    if (!udid) return res.end('no sim');
    // do=open&url=...: opens a link on the simulator as if tapped in another app (deep link test).
    const args = what === 'shake' ? ['spawn', udid, 'notifyutil', '-p', 'com.apple.UIKit.SimulatorShake']
      : what === 'open' && url.searchParams.get('url') ? ['openurl', udid, url.searchParams.get('url')] : null;
    if (!args) return res.end('unknown');
    return execFile('xcrun', ['simctl', ...args], (err) => res.end(err ? 'error ' + err.message : 'done'));
  }
  // Watermark check of the test apps: key FFFF... has no plan, any other key one for 30 days.
  if (url.pathname.startsWith('/api/wm/')) {
    const free = url.pathname.endsWith('F'.repeat(32));
    res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' });
    return res.end(JSON.stringify({ show: free, until: free ? 0 : Date.now() + 30 * 86400000, text: 'Built with Web2App', now: Date.now() }));
  }
  if (url.pathname === '/version.json') {
    res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' });
    return res.end(JSON.stringify({ versionCode: state.versionCode, message: 'Version test message' }));
  }
  if (url.pathname === '/notice.json') {
    res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' });
    return res.end(JSON.stringify({ id: state.noticeId, message: state.noticeMsg, link: `http://${req.headers.host}/page2` }));
  }
  if (url.pathname === '/set-notice') {
    state.noticeId = url.searchParams.get('id') || 'n1';
    state.noticeMsg = url.searchParams.get('msg') ?? '';
    return res.end('ok');
  }
  if (url.pathname === '/tone.wav') {
    res.writeHead(200, { 'content-type': 'audio/wav', 'content-length': toneWav.length });
    return res.end(toneWav);
  }
  if (url.pathname === '/big.png') {
    res.writeHead(200, { 'content-type': 'image/png', 'cache-control': 'max-age=3600' });
    return res.end(big);
  }
  if (url.pathname === '/photo.png') {
    res.writeHead(200, { 'content-type': 'image/gif', 'cache-control': 'max-age=3600' });
    return res.end(Buffer.from('R0lGODlhAQABAIAAAP8AAAAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw==', 'base64'));
  }
  if (url.pathname === '/set-version') {
    state.versionCode = Number(url.searchParams.get('v') || 1);
    return res.end('ok ' + state.versionCode);
  }
  if (url.pathname === '/hits') {
    res.writeHead(200, { 'content-type': 'application/json' });
    return res.end(JSON.stringify(state.hits));
  }
  if (url.pathname === '/file.txt') {
    res.writeHead(200, { 'content-type': 'text/plain', 'content-disposition': 'attachment; filename="w2a-test.txt"' });
    return res.end('downloaded ok\n');
  }
  if (url.pathname === '/pixel.png') {
    res.writeHead(200, { 'content-type': 'image/gif' });
    return res.end(Buffer.from('R0lGODlhAQABAIAAAP///wAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw==', 'base64'));
  }
  if (url.pathname === '/slow') {
    return setTimeout(() => { res.writeHead(200, { 'content-type': 'text/html' }); res.end(page('Slow page', '')); }, 4000);
  }
  const p = pages[url.pathname];
  if (url.pathname === '/err500') { res.writeHead(500, { 'content-type': 'text/html' }); return res.end('<h1>500 raw server error</h1>'); }
  if (!p) { res.writeHead(404); return res.end('not found'); }
  res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-cache' });
  res.end(page(p[0], p[1], p[2]));
}).listen(port, '0.0.0.0', () => console.log('test site on', port));
