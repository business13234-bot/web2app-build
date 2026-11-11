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

  if (!parts.length) return '';
  return `(function(){if(window.__w2aPage)return;window.__w2aPage=1;
function run(){${parts.join('\n')}}
if(document.body)run();else document.addEventListener('DOMContentLoaded',run);})();`;
}
