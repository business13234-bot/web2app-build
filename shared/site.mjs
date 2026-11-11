// Apps made from uploaded files: one HTML file, or a ZIP of a whole site (HTML, CSS, JS, images…).
// Used by the Worker (check before a build) and the build scripts (unpack into the app).

export const MAX_HTML_BYTES = 2 * 1024 * 1024;
export const MAX_ZIP_BYTES = 10 * 1024 * 1024;
export const MAX_SITE_FILES = 3000;
export const MAX_SITE_BYTES = 60 * 1024 * 1024; // unpacked

/** Lists the entries of a zip: [{ name, method, compSize, size, offset, dir }]. Throws on a broken file. */
export function listZip(bytes) {
  const dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const min = Math.max(0, bytes.length - 65557);
  let eocd = -1;
  for (let i = bytes.length - 22; i >= min; i--) {
    if (dv.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('This is not a ZIP file.');
  const count = dv.getUint16(eocd + 10, true);
  let p = dv.getUint32(eocd + 16, true);
  if (count === 0xffff || p === 0xffffffff) throw new Error('ZIP64 files are not supported. Make a normal ZIP.');
  const dec = new TextDecoder();
  const out = [];
  for (let i = 0; i < count; i++) {
    if (p + 46 > bytes.length || dv.getUint32(p, true) !== 0x02014b50) throw new Error('The ZIP file is damaged.');
    const flags = dv.getUint16(p + 8, true);
    const nameLen = dv.getUint16(p + 28, true);
    const extraLen = dv.getUint16(p + 30, true);
    const commentLen = dv.getUint16(p + 32, true);
    const name = dec.decode(bytes.subarray(p + 46, p + 46 + nameLen)).replace(/\\/g, '/');
    if (flags & 1) throw new Error('The ZIP file is password protected. Make a ZIP without a password.');
    out.push({
      name,
      method: dv.getUint16(p + 10, true),
      compSize: dv.getUint32(p + 20, true),
      size: dv.getUint32(p + 24, true),
      offset: dv.getUint32(p + 42, true),
      dir: name.endsWith('/'),
    });
    p += 46 + nameLen + extraLen + commentLen;
  }
  return out;
}

/** True for a path that stays inside the site folder. */
export function safePath(name) {
  if (!name || name.length > 300 || name.startsWith('/') || /^[a-zA-Z]:/.test(name)) return false;
  return name.split('/').every((part) => part !== '..' && part !== '.' && !/[\u0000-\u001f]/.test(part));
}

const JUNK = /(^|\/)(__MACOSX\/|\.DS_Store$|Thumbs\.db$|\.git\/)/;

/**
 * Checks a ZIP of a website. Returns { files: [entry & { path }], root } or throws with a message
 * for the user. The site may sit in one folder inside the zip; index.html must be at its top.
 */
export function checkZipSite(bytes) {
  if (bytes.length > MAX_ZIP_BYTES) throw new Error('The ZIP file is larger than 10 MB.');
  const entries = listZip(bytes).filter((e) => !e.dir && !JUNK.test(e.name));
  if (!entries.length) throw new Error('The ZIP file is empty.');
  if (entries.length > MAX_SITE_FILES) throw new Error(`The ZIP has more than ${MAX_SITE_FILES} files.`);
  let total = 0;
  for (const e of entries) {
    if (!safePath(e.name)) throw new Error(`The ZIP has a file with an unsafe name: ${e.name.slice(0, 60)}`);
    if (e.method !== 0 && e.method !== 8) throw new Error('The ZIP uses an unusual compression. Make it again with your computer\'s normal "Compress" option.');
    total += e.size;
  }
  if (total > MAX_SITE_BYTES) throw new Error('The unpacked site is larger than 60 MB.');
  const names = entries.map((e) => e.name);
  let root = '';
  if (!names.some((n) => n.toLowerCase() === 'index.html')) {
    // One folder holding everything, e.g. "my-site/index.html".
    const tops = new Set(names.map((n) => n.split('/')[0]));
    const top = tops.size === 1 ? [...tops][0] : null;
    if (top && names.some((n) => n.toLowerCase() === `${top}/index.html`.toLowerCase())) root = `${top}/`;
    else throw new Error('The ZIP needs an index.html file at its top (the first page of your site).');
  }
  const files = entries.map((e) => ({ ...e, path: e.name.slice(root.length) }));
  return { files, root };
}

/** Checks a single HTML file. Throws with a message for the user. */
export function checkHtmlFile(bytes) {
  if (bytes.length > MAX_HTML_BYTES) throw new Error('The HTML file is larger than 2 MB. Use a ZIP for bigger sites.');
  const head = new TextDecoder().decode(bytes.subarray(0, 4096)).toLowerCase();
  if (!/<(html|head|body|div|script|p|h1|!doctype)/.test(head)) throw new Error('This does not look like an HTML file.');
  return true;
}

export function b64ToBytes(b64) {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}
