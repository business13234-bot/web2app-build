// Page features that are plain JavaScript and CSS, the same on Android and iOS.
// The build scripts put the result into config.json as "pageJs"; both apps run it on every page.

const GOOGLE_FONTS = {
  Poppins: 'Poppins:wght@300;400;500;600;700',
  Inter: 'Inter:wght@300;400;500;600;700',
  Roboto: 'Roboto:wght@300;400;500;700',
  'Open Sans': 'Open+Sans:wght@300;400;600;700',
  Lato: 'Lato:wght@300;400;700',
  Montserrat: 'Montserrat:wght@300;400;500;600;700',
  Nunito: 'Nunito:wght@300;400;600;700',
  'Noto Sans': 'Noto+Sans:wght@300;400;500;600;700',
  'Hind Siliguri': 'Hind+Siliguri:wght@300;400;500;600;700',
  'Noto Serif': 'Noto+Serif:wght@400;600;700',
};

import { PAGE_ICONS } from './inject-icons.mjs';

const svgM = (d, size) => `<svg viewBox="0 -960 960 960" width="${size}" height="${size}" fill="currentColor" aria-hidden="true"><path d="${d}"/></svg>`;
const svgS = (d, size) => `<svg viewBox="0 0 24 24" width="${size}" height="${size}" fill="currentColor" aria-hidden="true"><path d="${d}"/></svg>`;
/** Black or white text on a colour. */
function inkOn(hex) {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex || '');
  if (!m) return '#fff';
  const n = parseInt(m[1], 16);
  return (0.299 * (n >> 16) + 0.587 * ((n >> 8) & 255) + 0.114 * (n & 255)) / 255 > 0.62 ? '#0f172a' : '#fff';
}
/** A style element with this CSS, added once. */
const css = (id, text) => `(function(){if(document.getElementById(${JSON.stringify(id)}))return;var s=document.createElement('style');s.id=${JSON.stringify(id)};s.textContent=${JSON.stringify(text)};(document.head||document.documentElement).appendChild(s);})();`;

const on = (cfg, id) => {
  const f = cfg.features && cfg.features[id];
  return f && f.enabled ? f : null;
};

/** The script for this app, or '' when no page feature is on. */
export function pageScript(cfg) {
  const parts = [];
  const theme = cfg.themeColor || '#1565C0';

  const bar = on(cfg, 'progressBar');
  if (bar) {
    parts.push(`(function(){
var b=document.createElement('div');b.id='w2a-progress';
b.style.cssText='position:fixed;left:0;top:0;height:${Number(bar.height) || 3}px;width:0;z-index:2147483646;pointer-events:none;background:${bar.color || theme};transition:width .12s linear';
(document.body||document.documentElement).appendChild(b);
function area(){var s=document.scrollingElement||document.documentElement;return s;}
function upd(){var s=area();var max=s.scrollHeight-window.innerHeight;var p=max>8?Math.min(1,Math.max(0,s.scrollTop/max)):0;b.style.width=(p*100)+'%';}
window.addEventListener('scroll',upd,{passive:true});window.addEventListener('resize',upd);upd();
})();`);
  }

  const font = on(cfg, 'appFont');
  if (font) {
    const name = font.font || 'Poppins';
    const family = name === 'system' ? 'system-ui,-apple-system,Roboto,"Segoe UI",sans-serif' : `"${name}",sans-serif`;
    const href = GOOGLE_FONTS[name] ? `https://fonts.googleapis.com/css2?family=${GOOGLE_FONTS[name]}&display=swap` : '';
    const notIcons = ':not(i):not([class*="icon"]):not([class*="Icon"]):not([class^="fa"]):not([class*=" fa"]):not(.material-icons)'
      + ':not([class*="material-symbols"]):not([class*="glyph"]):not(code):not(pre):not(kbd):not(samp)';
    parts.push(`(function(){
var h=document.head||document.documentElement;
${href ? `var l=document.createElement('link');l.rel='stylesheet';l.href=${JSON.stringify(href)};h.appendChild(l);` : ''}
var s=document.createElement('style');s.id='w2a-font';
s.textContent=${JSON.stringify(`html body,html body *${notIcons}{font-family:${family}!important}`)};
h.appendChild(s);
})();`);
  }

  const viewer = on(cfg, 'imageViewer');
  if (viewer) {
    parts.push(`(function(){
var MIN=${Number(viewer.minSize) || 120};var open=null;
function close(back){if(!open)return;var o=open;open=null;o.style.opacity='0';setTimeout(function(){o.remove();},200);
if(back!==false&&history.state&&history.state.w2aViewer)history.back();}
window.addEventListener('popstate',function(){if(open)close(false);});
function show(src){
var o=document.createElement('div');o.id='w2a-viewer';
o.style.cssText='position:fixed;inset:0;z-index:2147483647;background:rgba(0,0,0,.94);display:flex;align-items:center;justify-content:center;opacity:0;transition:opacity .2s;touch-action:none';
var im=document.createElement('img');im.src=src;im.alt='';
im.style.cssText='max-width:100%;max-height:100%;object-fit:contain;transform-origin:0 0;will-change:transform;user-select:none;-webkit-user-select:none';
var x=document.createElement('button');x.type='button';x.setAttribute('aria-label','Close');x.innerHTML='&times;';
x.style.cssText='position:absolute;top:calc(12px + env(safe-area-inset-top));right:12px;width:44px;height:44px;border-radius:22px;border:0;background:rgba(255,255,255,.16);color:#fff;font:28px/44px system-ui;cursor:pointer';
o.appendChild(im);o.appendChild(x);document.body.appendChild(o);open=o;
history.pushState({w2aViewer:1},'');requestAnimationFrame(function(){o.style.opacity='1';});
var sc=1,tx=0,ty=0,st=null,lastTap=0,moved=false;
function apply(){im.style.transform='translate('+tx+'px,'+ty+'px) scale('+sc+')';}
function base(){var r=im.getBoundingClientRect();return {L:r.left-tx,T:r.top-ty};}
function zoomAt(cx,cy,ns){ns=Math.max(1,Math.min(5,ns));var b=base();var lx=(cx-b.L-tx)/sc,ly=(cy-b.T-ty)/sc;
sc=ns;tx=cx-b.L-lx*sc;ty=cy-b.T-ly*sc;if(sc===1){tx=0;ty=0;}apply();}
x.addEventListener('click',function(e){e.stopPropagation();close();});
o.addEventListener('click',function(e){if(e.target===o&&sc===1&&!moved)close();});
o.addEventListener('touchstart',function(e){moved=false;var b=base();
if(e.touches.length===2){var p=e.touches[0],q=e.touches[1];var cx=(p.clientX+q.clientX)/2,cy=(p.clientY+q.clientY)/2;
st={d:Math.hypot(p.clientX-q.clientX,p.clientY-q.clientY),s:sc,L:b.L,T:b.T,cx:cx,cy:cy,lx:(cx-b.L-tx)/sc,ly:(cy-b.T-ty)/sc};}
else if(e.touches.length===1){st={x:e.touches[0].clientX,y:e.touches[0].clientY,tx:tx,ty:ty};}},{passive:true});
o.addEventListener('touchmove',function(e){if(!st)return;moved=true;
if(e.touches.length===2&&st.d){var p=e.touches[0],q=e.touches[1];var d=Math.hypot(p.clientX-q.clientX,p.clientY-q.clientY);
sc=Math.max(1,Math.min(5,st.s*d/st.d));tx=st.cx-st.L-st.lx*sc;ty=st.cy-st.T-st.ly*sc;if(sc===1){tx=0;ty=0;}apply();}
else if(e.touches.length===1&&sc>1&&st.x!=null){tx=st.tx+e.touches[0].clientX-st.x;ty=st.ty+e.touches[0].clientY-st.y;apply();}},{passive:true});
o.addEventListener('touchend',function(e){if(e.touches.length===0){var now=Date.now();
if(!moved&&now-lastTap<300){if(sc>1){sc=1;tx=0;ty=0;apply();}else{var t=e.changedTouches[0];zoomAt(t.clientX,t.clientY,2.5);}lastTap=0;}
else lastTap=now;st=null;}},{passive:true});
window.__w2aViewerState=function(){return {scale:sc,tx:tx,ty:ty};};
}
document.addEventListener('click',function(e){var t=e.target;if(open||!t||t.tagName!=='IMG')return;
if(t.closest('a,button,[onclick],[role=button],label'))return;
var w=t.naturalWidth||0;if(w<MIN||t.getBoundingClientRect().width<MIN/2)return;
e.preventDefault();e.stopPropagation();show(t.currentSrc||t.src);},true);
window.__w2aCloseViewer=function(){if(open){close();return true;}return false;};
})();`);
  }

  const auto = on(cfg, 'autoRefresh');
  if (auto) {
    const ms = Math.max(1, Number(auto.minutes) || 5) * 60000;
    parts.push(`(function(){
var due=Date.now()+${ms};
function typing(){var a=document.activeElement;return a&&(a.tagName==='INPUT'||a.tagName==='TEXTAREA'||a.tagName==='SELECT'||a.isContentEditable);}
setInterval(function(){if(Date.now()<due)return;if(document.visibilityState!=='visible'||typing()||document.getElementById('w2a-viewer'))return;
location.reload();},15000);
})();`);
  }

  // ------------------------------------------------------------ 1.6: look
  const hide = on(cfg, 'hideElements');
  if (hide) {
    const sel = String(hide.selectors || '').split(/\n|,/).map((x) => x.trim()).filter((x) => x && !/[{}<>]/.test(x));
    // Each selector on its own rule: one the browser does not understand does not stop the others.
    if (sel.length) parts.push(css('w2a-hide', sel.map((x) => `${x}{display:none!important}`).join('\n')));
  }
  if (on(cfg, 'nativeFeel')) {
    parts.push(css('w2a-feel', '*{-webkit-tap-highlight-color:transparent}a,img{-webkit-touch-callout:none}'
      + 'a,button,input,select,textarea,label,[role=button]{touch-action:manipulation}'));
  }
  if (on(cfg, 'hideScrollbars')) {
    parts.push(css('w2a-noscroll', '::-webkit-scrollbar{width:0!important;height:0!important;background:transparent}*{scrollbar-width:none}'));
  }
  if (on(cfg, 'smoothScroll')) parts.push(css('w2a-smooth', 'html{scroll-behavior:smooth}'));

  // ------------------------------------------------------------ 1.6: accessibility
  const calm = on(cfg, 'reduceMotion');
  if (calm) {
    const rule = '*,*::before,*::after{animation-duration:.001ms!important;animation-iteration-count:1!important;'
      + 'transition-duration:.001ms!important;scroll-behavior:auto!important}';
    parts.push(css('w2a-calm', calm.onlyIfAsked === false ? rule : `@media (prefers-reduced-motion: reduce){${rule}}`));
  }
  if (on(cfg, 'highlightLinks')) {
    parts.push(css('w2a-links', `a[href]{text-decoration:underline!important;text-underline-offset:2px}`
      + `:focus-visible{outline:3px solid ${theme}!important;outline-offset:2px!important}`));
  }
  const grey = on(cfg, 'grayscale');
  if (grey) {
    // An overlay that greys what is under it: a filter on the page itself would turn its background
    // black and break fixed bars.
    const g = `grayscale(${Math.max(10, Math.min(100, Number(grey.amount) || 100))}%)`;
    parts.push(`(function(){if(document.getElementById('w2a-grey'))return;var d=document.createElement('div');d.id='w2a-grey';
d.style.cssText='position:fixed;inset:0;z-index:2147483647;pointer-events:none;-webkit-backdrop-filter:${g};backdrop-filter:${g}';
document.documentElement.appendChild(d);})();`);
  }
  const easy = on(cfg, 'readableFont');
  if (easy) {
    const lh = ['1.4', '1.6', '1.8'].includes(String(easy.spacing)) ? easy.spacing : '1.6';
    parts.push(`(function(){var h=document.head||document.documentElement;
var l=document.createElement('link');l.rel='stylesheet';l.href='https://fonts.googleapis.com/css2?family=Lexend:wght@300;400;500;600;700&display=swap';h.appendChild(l);})();`);
    parts.push(css('w2a-easy', `html body,html body p,html body li,html body td,html body span,html body a,html body div{font-family:"Lexend",sans-serif}`
      + `html body p,html body li,html body td,html body dd{line-height:${lh}!important}`));
  }

  // ------------------------------------------------------------ 1.6: business
  // Bars at the top and bottom of the page push the page so they never cover it.
  const PAD = `function w2aPad(side,px){var b=document.body;if(!b)return;var k='w2aPad'+side;var old=Number(b.dataset[k]||0);
var cur=parseFloat(getComputedStyle(b)['padding'+side])||0;b.style['padding'+side]=(cur-old+px)+'px';b.dataset[k]=px;}
function w2aStack(){var t=0,bt=0;['w2a-countdown','w2a-hours'].forEach(function(id){var e=document.getElementById(id);if(e){e.style.top=t+'px';t+=e.offsetHeight;}});
var c=document.getElementById('w2a-callbar');if(c)bt+=c.offsetHeight;
var p=document.getElementById('w2a-promo');if(p){if(p.dataset.pos==='top'){p.style.top=(t+10)+'px';t+=p.offsetHeight+20;}
else{p.style.bottom=(bt+10)+'px';bt+=p.offsetHeight+20;}}w2aPad('Top',t);w2aPad('Bottom',bt);}`;
  let usesPad = false;
  // Sites style links, spans and pictures in every way: our bars reset what matters, by their id.
  const BAR_CSS = (id) => `${id} *{box-sizing:border-box!important;float:none!important;position:static!important;text-transform:none!important;letter-spacing:normal!important;line-height:1.2!important}`
    + `${id} svg{display:block!important;flex:none!important;width:20px!important;height:20px!important;margin:0!important;max-width:none!important}`
    + `${id} span,${id} b{display:inline!important;margin:0!important;padding:0!important;border:0!important;background:none!important;color:inherit!important}`;
  const fabOn = on(cfg, 'fab');
  const fabSide = fabOn ? (fabOn.position === 'left' ? 'left' : 'right') : '';

  const call = on(cfg, 'callBar');
  if (call) {
    const bg = call.color || theme;
    const ink = inkOn(bg);
    const btns = [];
    const digits = (t) => String(t || '').replace(/[^0-9+]/g, '');
    if (digits(call.phone)) btns.push([`tel:${digits(call.phone)}`, svgM(PAGE_ICONS.call, 20), 'Call']);
    if (digits(call.whatsapp)) btns.push([`https://wa.me/${digits(call.whatsapp).replace(/^\+/, '')}`, svgS(PAGE_ICONS.wa, 18), 'WhatsApp']);
    if (String(call.address || '').trim()) btns.push([`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(call.address.trim())}`, svgM(PAGE_ICONS.pin, 20), 'Directions']);
    if (btns.length) {
      usesPad = true;
      const html = btns.map(([href, ic, label]) => `<a href="${href.replace(/"/g, '&quot;')}" style="flex:1;display:flex;align-items:center;justify-content:center;gap:7px;color:${ink};text-decoration:none;font:600 14px/1 system-ui,-apple-system,Roboto,sans-serif;height:100%">${ic}<span>${label}</span></a>`).join(`<i style="display:block;width:1px;align-self:stretch;margin:12px 0;background:${ink};opacity:.25"></i>`);
      parts.push(`(function(){if(document.getElementById('w2a-callbar'))return;
var d=document.createElement('div');d.id='w2a-callbar';
d.style.cssText='position:fixed;left:0;right:0;bottom:0;z-index:2147483640;display:flex;align-items:stretch;height:52px;padding-bottom:env(safe-area-inset-bottom);background:${bg};box-shadow:0 -2px 10px rgba(0,0,0,.15)';
d.innerHTML=${JSON.stringify(html)};document.body.appendChild(d);w2aStack();})();`);
      parts.push(css('w2a-callbar-css', BAR_CSS('#w2a-callbar')
        + '#w2a-callbar a{display:flex!important;flex:1 1 0!important;flex-direction:row!important;align-items:center!important;justify-content:center!important;gap:7px!important;text-decoration:none!important;margin:0!important;padding:0!important;min-width:0!important}'));
    }
  }

  const smart = on(cfg, 'smartLinks');
  if (smart && (smart.phones !== false || smart.emails !== false)) {
    parts.push(`(function(){
var P=${smart.phones !== false},E=${smart.emails !== false};
var RE=/([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})|(\\+?\\d[\\d ().-]{7,18}\\d)/g;
var SKIP={A:1,SCRIPT:1,STYLE:1,TEXTAREA:1,INPUT:1,SELECT:1,OPTION:1,BUTTON:1,CODE:1,PRE:1,NOSCRIPT:1,SVG:1,svg:1,TITLE:1,HEAD:1};
function ok(t){var d=t.replace(/[^0-9]/g,'');return d.length>=9&&d.length<=15&&(/^\\+/.test(t)||/^0/.test(t)||/[ ().-]/.test(t))&&!/^\\d{4}[.-]\\d{2}[.-]\\d{2}$/.test(t.trim());}
function scan(root){var w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,{acceptNode:function(n){for(var p=n.parentNode;p&&p!==document.body;p=p.parentNode){if(SKIP[p.nodeName]||p.isContentEditable)return 2;}
return /[@\\d]/.test(n.nodeValue)?1:2;}});var list=[],n,c=0;while((n=w.nextNode())&&c<3000){list.push(n);c++;}
list.forEach(function(t){var s=t.nodeValue;RE.lastIndex=0;if(!RE.test(s))return;RE.lastIndex=0;var f=document.createDocumentFragment(),last=0,m,hit=false;
while((m=RE.exec(s))){var href=null;if(m[1]&&E)href='mailto:'+m[1];else if(m[2]&&P&&ok(m[2]))href='tel:'+m[2].replace(/[^0-9+]/g,'');
if(!href)continue;hit=true;f.appendChild(document.createTextNode(s.slice(last,m.index)));var a=document.createElement('a');a.href=href;a.textContent=m[0];a.className='w2a-smart';f.appendChild(a);last=m.index+m[0].length;}
if(!hit)return;f.appendChild(document.createTextNode(s.slice(last)));t.parentNode.replaceChild(f,t);});}
scan(document.body);var due=0;new MutationObserver(function(){if(due)return;due=setTimeout(function(){due=0;scan(document.body);},1200);}).observe(document.body,{childList:true,subtree:true});
})();`);
  }

  const hours = on(cfg, 'openingHours');
  if (hours) {
    const DAYS = { 'mon-fri': [1, 2, 3, 4, 5], 'mon-sat': [1, 2, 3, 4, 5, 6], 'sun-thu': [0, 1, 2, 3, 4], 'sat-thu': [6, 0, 1, 2, 3, 4], all: [0, 1, 2, 3, 4, 5, 6] };
    const days = DAYS[hours.days] || DAYS['mon-sat'];
    const bg = hours.color || '#334155';
    usesPad = true;
    parts.push(`(function(){
var DAYS=${JSON.stringify(days)},O=${JSON.stringify(hours.open || '09:00')},C=${JSON.stringify(hours.close || '18:00')},T=${JSON.stringify(hours.text || 'We are closed now. We open again')};
var N=['Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday'];
function mins(t){var p=t.split(':');return Number(p[0])*60+Number(p[1]);}
function isOpen(d){var m=d.getHours()*60+d.getMinutes(),o=mins(O),c=mins(C);var day=d.getDay();
if(c>o)return DAYS.indexOf(day)>=0&&m>=o&&m<c;
/* past midnight */ if(m>=o)return DAYS.indexOf(day)>=0;var y=(day+6)%7;return DAYS.indexOf(y)>=0&&m<c;}
function next(d){for(var i=0;i<8;i++){var x=new Date(d.getFullYear(),d.getMonth(),d.getDate()+i,0,0);var day=x.getDay();if(DAYS.indexOf(day)<0)continue;
var at=new Date(x.getTime()+mins(O)*60000);if(at>d)return i===0?'today at '+O:(i===1?'tomorrow at '+O:N[day]+' at '+O);}return '';}
var bar=null;function upd(){var d=new Date();if(isOpen(d)){if(bar){bar.remove();bar=null;w2aStack();}return;}
if(!bar){bar=document.createElement('div');bar.id='w2a-hours';bar.style.cssText='position:fixed;left:0;right:0;top:0;z-index:2147483641;padding:8px 14px;text-align:center;font:600 13px/1.35 system-ui,-apple-system,Roboto,sans-serif;background:${bg};color:${inkOn(bg)}';document.body.appendChild(bar);}
bar.textContent=T+' '+next(d)+'.';w2aStack();}
upd();setInterval(upd,60000);
})();`);
  }

  const promo = on(cfg, 'promoBanner');
  if (promo && String(promo.text || '').trim()) {
    const bg = promo.color || theme;
    const ink = inkOn(bg);
    const top = promo.position === 'top';
    usesPad = true;
    parts.push(`(function(){
var U=${JSON.stringify(promo.until || '')},K='w2aPromo:'+${JSON.stringify(String(promo.text) + '|' + (promo.until || ''))};
if(U){var p=U.split('-');if(new Date()>new Date(Number(p[0]),Number(p[1])-1,Number(p[2]),23,59,59))return;}
try{if(localStorage.getItem(K))return;}catch(e){}
if(document.getElementById('w2a-promo'))return;
var d=document.createElement('div');d.id='w2a-promo';
d.style.cssText='position:fixed;left:${fabSide === 'left' && !top ? 84 : 10}px;right:${fabSide === 'right' && !top ? 84 : 10}px;${top ? 'top:10px' : 'bottom:10px'};z-index:2147483642;display:flex;align-items:center;gap:10px;padding:10px 10px 10px 14px;border-radius:14px;background:${bg};color:${ink};box-shadow:0 8px 24px rgba(0,0,0,.22);font:600 14px/1.3 system-ui,-apple-system,Roboto,sans-serif';
var t=document.createElement('span');t.style.flex='1';t.textContent=${JSON.stringify(String(promo.text))};d.appendChild(t);
${promo.link ? `var a=document.createElement('a');a.href=${JSON.stringify(promo.link)};a.textContent=${JSON.stringify(promo.button || 'Open')};a.style.cssText='flex:none;padding:8px 12px;border-radius:10px;background:${ink};color:${bg};text-decoration:none;font-weight:700';d.appendChild(a);` : ''}
var x=document.createElement('button');x.type='button';x.setAttribute('aria-label','Close');x.innerHTML=${JSON.stringify(svgM(PAGE_ICONS.close, 20))};
x.style.cssText='flex:none;display:grid;place-items:center;width:32px;height:32px;border:0;border-radius:50%;background:transparent;color:inherit;padding:0';
x.onclick=function(){d.remove();w2aStack();try{localStorage.setItem(K,'1');}catch(e){}};d.appendChild(x);
d.dataset.pos=${JSON.stringify(top ? 'top' : 'bottom')};
document.body.appendChild(d);w2aStack();
})();`);
  }

  if (promo && String(promo.text || '').trim()) {
    parts.push(css('w2a-promo-css', BAR_CSS('#w2a-promo') + '#w2a-promo>span{flex:1 1 auto!important;display:block!important}'
      + '#w2a-promo a{display:inline-block!important;flex:none!important;text-decoration:none!important;margin:0!important}'
      + '#w2a-promo button{display:grid!important;place-items:center!important;flex:none!important;margin:0!important;box-shadow:none!important;min-width:0!important}'));
  }
  const cd = on(cfg, 'countdown');
  if (cd && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(String(cd.end || ''))) {
    const [d, t] = cd.end.split(' ');
    const [y, mo, da] = d.split('-').map(Number);
    const [h, mi] = t.split(':').map(Number);
    const bg = cd.color || '#DC2626';
    usesPad = true;
    parts.push(`(function(){
var END=new Date(${y},${mo - 1},${da},${h},${mi}).getTime();if(Date.now()>=END||document.getElementById('w2a-countdown'))return;
var bar=document.createElement(${cd.link ? "'a'" : "'div'"});bar.id='w2a-countdown';${cd.link ? `bar.href=${JSON.stringify(cd.link)};` : ''}
bar.style.cssText='position:fixed;left:0;right:0;top:0;z-index:2147483641;display:flex;justify-content:center;align-items:center;gap:8px;padding:8px 12px;text-decoration:none;font:600 13px/1.3 system-ui,-apple-system,Roboto,sans-serif;background:${bg};color:${inkOn(bg)}';
var a=document.createElement('span');a.textContent=${JSON.stringify(cd.title || 'Ends in')};var b=document.createElement('b');b.style.cssText='font-variant-numeric:tabular-nums;letter-spacing:.02em';
bar.appendChild(a);bar.appendChild(b);document.body.appendChild(bar);w2aStack();
function two(n){return (n<10?'0':'')+n;}
function upd(){var s=Math.floor((END-Date.now())/1000);if(s<=0){bar.remove();w2aStack();clearInterval(iv);return;}
var dd=Math.floor(s/86400),hh=Math.floor(s%86400/3600),mm=Math.floor(s%3600/60),ss=s%60;b.textContent=(dd?dd+'d ':'')+two(hh)+':'+two(mm)+':'+two(ss);}
var iv=setInterval(upd,1000);upd();
})();`);
  }

  // ------------------------------------------------------------ 1.6: media
  const away = on(cfg, 'pauseHidden');
  if (away) {
    parts.push(`(function(){var A=${away.audio === true};
document.addEventListener('visibilitychange',function(){if(document.visibilityState!=='hidden')return;
var m=document.querySelectorAll(A?'video,audio':'video');for(var i=0;i<m.length;i++){try{if(!m[i].paused)m[i].pause();}catch(e){}}});
})();`);
  }
  if (on(cfg, 'quietAutoplay')) {
    parts.push(`(function(){var last=0;['pointerdown','touchstart','keydown','click'].forEach(function(t){document.addEventListener(t,function(){last=Date.now();},true);});
document.addEventListener('play',function(e){var v=e.target;if(!v||v.tagName!=='VIDEO')return;if(Date.now()-last>1500&&!v.muted){v.muted=true;
var un=function(){v.muted=false;v.removeEventListener('click',un);};v.addEventListener('click',un);}},true);
})();`);
  }

  // ------------------------------------------------------------ 1.6: privacy
  const copy = on(cfg, 'copyProtect');
  if (copy) {
    const forms = copy.allowForms !== false;
    parts.push(css('w2a-copy', `html body{-webkit-user-select:none;user-select:none}img{-webkit-user-drag:none;-webkit-touch-callout:none;pointer-events:auto}`
      + (forms ? 'input,textarea,[contenteditable]{-webkit-user-select:text;user-select:text}' : '')));
    parts.push(`(function(){var F=${forms};function field(t){return F&&t&&(t.tagName==='INPUT'||t.tagName==='TEXTAREA'||t.isContentEditable);}
['copy','cut'].forEach(function(n){document.addEventListener(n,function(e){if(!field(e.target))e.preventDefault();},true);});
document.addEventListener('contextmenu',function(e){if(!field(e.target))e.preventDefault();},true);
document.addEventListener('dragstart',function(e){if(e.target&&e.target.tagName==='IMG')e.preventDefault();},true);
})();`);
  }
  if (on(cfg, 'popupBlock')) {
    parts.push(`(function(){var last=0;['pointerdown','touchend','click','keydown'].forEach(function(t){document.addEventListener(t,function(){last=Date.now();},true);});
var o=window.open;window.open=function(){if(Date.now()-last>1000)return null;return o.apply(window,arguments);};
})();`);
  }

  // ------------------------------------------------------------ 1.6: behaviour
  if (on(cfg, 'keyboardFix')) {
    parts.push(`(function(){document.addEventListener('focusin',function(e){var t=e.target;if(!t||!/^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName)&&!t.isContentEditable)return;
if(/^(checkbox|radio|button|submit|range|color|file)$/.test(t.type||''))return;
setTimeout(function(){if(document.activeElement!==t)return;var r=t.getBoundingClientRect();var h=(window.visualViewport?window.visualViewport.height:window.innerHeight);
if(r.top<60||r.bottom>h-20)t.scrollIntoView({block:'center',behavior:'smooth'});},350);},true);
})();`);
  }
  const drafts = on(cfg, 'formSaver');
  if (drafts) {
    const ms = Math.max(1, Math.min(168, Number(drafts.hours) || 24)) * 3600000;
    parts.push(`(function(){var TTL=${ms},K='w2aDraft:'+location.pathname;
var BAD=/pass|card|cc-|cvv|cvc|iban|otp|pin|secret|token|ssn/i;
function key(el){var f=el.form;var fi=f?Array.prototype.indexOf.call(document.forms,f):-1;return fi+':'+(el.name||el.id||'');}
function okEl(el){if(!el||!el.name&&!el.id)return false;var t=(el.type||'').toLowerCase();
if(/^(password|hidden|file|submit|button|reset|image)$/.test(t))return false;
return !BAD.test((el.name||'')+' '+(el.id||'')+' '+(el.autocomplete||''));}
function load(){try{var d=JSON.parse(localStorage.getItem(K)||'null');if(!d||Date.now()-d.t>TTL){localStorage.removeItem(K);return null;}return d;}catch(e){return null;}}
function save(){var v={};var els=document.querySelectorAll('input,textarea,select');for(var i=0;i<els.length;i++){var el=els[i];if(!okEl(el))continue;
var t=(el.type||'').toLowerCase();v[key(el)]=(t==='checkbox'||t==='radio')?(el.checked?'1':'0'):el.value;}
try{localStorage.setItem(K,JSON.stringify({t:Date.now(),v:v}));}catch(e){}}
var d=load();if(d){var els=document.querySelectorAll('input,textarea,select');for(var i=0;i<els.length;i++){var el=els[i];if(!okEl(el))continue;var k=key(el);if(!(k in d.v))continue;
var t=(el.type||'').toLowerCase();if(t==='checkbox'||t==='radio'){el.checked=d.v[k]==='1';}else if(!el.value){el.value=d.v[k];el.dispatchEvent(new Event('input',{bubbles:true}));}}}
var due=0;document.addEventListener('input',function(e){if(!okEl(e.target))return;clearTimeout(due);due=setTimeout(save,500);},true);
document.addEventListener('change',function(e){if(okEl(e.target))save();},true);
document.addEventListener('submit',function(){try{localStorage.removeItem(K);}catch(e){}},true);
})();`);
  }

  // ------------------------------------------------------------ 1.6: engage
  const fb = on(cfg, 'feedback');
  if (fb && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(fb.email || ''))) {
    const bg = fb.color || theme;
    const left = fb.position === 'left';
    const name = cfg.appName || 'the app';
    parts.push(`(function(){if(document.getElementById('w2a-feedback'))return;
var b=document.createElement('a');b.id='w2a-feedback';b.textContent=${JSON.stringify(fb.label || 'Feedback')};
b.style.cssText='position:fixed;${left ? 'left:0;border-radius:0 0 10px 10px;transform-origin:0 0;transform:rotate(-90deg) translate(-100%,0)' : 'right:0;border-radius:10px 10px 0 0;transform-origin:100% 100%;transform:rotate(-90deg) translate(0,0)'};top:55%;z-index:2147483639;padding:7px 12px;background:${bg};color:${inkOn(bg)};text-decoration:none;font:600 12px/1 system-ui,-apple-system,Roboto,sans-serif;box-shadow:0 2px 8px rgba(0,0,0,.2);opacity:.92';
b.addEventListener('click',function(){var body='\\n\\n---\\nPage: '+location.href+'\\nPhone: '+navigator.userAgent;
b.href='mailto:'+${JSON.stringify(fb.email)}+'?subject='+encodeURIComponent(${JSON.stringify(`Feedback: ${name}`)})+'&body='+encodeURIComponent(body);});
document.body.appendChild(b);
})();`);
  }

  if (usesPad) parts.unshift(PAD);

  if (!parts.length) return '';
  return `(function(){if(window.__w2aPage)return;window.__w2aPage=1;
function run(){${parts.join('\n')}}
if(document.body)run();else document.addEventListener('DOMContentLoaded',run);})();`;
}
