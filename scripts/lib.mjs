import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import zlib from 'node:zlib';
import { validateConfig } from '../shared/catalog.mjs';
import { checkZipSite, checkHtmlFile, MAX_SITE_BYTES } from '../shared/site.mjs';

/** Reads the build request and re-validates the config (the scripts never trust raw input). */
export function readPayload(file) {
  const payload = JSON.parse(fs.readFileSync(file, 'utf8'));
  const checked = validateConfig(payload.config);
  if (!checked.ok) throw new Error(`Invalid config: ${checked.error}`);
  payload.config = checked.config;
  hideCustomerData(payload);
  return payload;
}

/**
 * Builds run in a public repository, where logs can be read by anyone: hide what identifies
 * the customer's app, and pass on the key that encrypts the finished files.
 */
function hideCustomerData(payload) {
  if (!process.env.GITHUB_ACTIONS) return;
  const c = payload.config;
  const values = new Set([c.packageId, c.url]);
  if (String(c.appName || '').trim().length >= 4) values.add(String(c.appName).trim());
  try { values.add(new URL(c.url).host); } catch { /* ignore */ }
  for (const v of values) {
    if (v && !/[\r\n]/.test(v)) console.log(`::add-mask::${v}`);
  }
  if (payload.fileKey) setEnv('W2A_FILE_KEY', payload.fileKey, { secret: true });
}

export function copyDir(from, to) {
  fs.mkdirSync(to, { recursive: true });
  fs.cpSync(from, to, { recursive: true });
}

export function write(file, content) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, content);
}

const MAGIC = {
  png: [0x89, 0x50, 0x4e, 0x47],
  jpeg: [0xff, 0xd8, 0xff],
};

/** Writes a base64 image after checking that it really is the expected format. */
export function writeImage(file, base64, kind) {
  const buf = Buffer.from(String(base64 || ''), 'base64');
  const magic = MAGIC[kind];
  if (buf.length < 100 || !magic.every((b, i) => buf[i] === b)) {
    throw new Error(`Icon is not a valid ${kind} image`);
  }
  write(file, buf);
}

export function writeBinary(file, base64) {
  const buf = Buffer.from(String(base64 || ''), 'base64');
  if (!buf.length) throw new Error(`Empty file: ${path.basename(file)}`);
  write(file, buf);
}

/** Exposes a value to later workflow steps, hiding it from the logs when secret. */
export function setEnv(name, value, { secret = false } = {}) {
  const v = String(value);
  if (/[\r\n]/.test(v)) throw new Error(`${name} must be a single line`);
  if (secret && v) console.log(`::add-mask::${v}`);
  const envFile = process.env.GITHUB_ENV;
  if (envFile) {
    const delim = `EOF_${crypto.randomBytes(8).toString('hex')}`;
    fs.appendFileSync(envFile, `${name}<<${delim}\n${v}\n${delim}\n`);
  } else {
    console.log(`[env] ${name}=${secret ? '***' : v}`);
  }
}

export function xml(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/**
 * The splash image or video sent with the build, checked by its first bytes.
 * Returns { kind: 'image' | 'video', ext, buf } or null.
 */
export function splashMedia(payload) {
  const sp = payload.config.features && payload.config.features.splash;
  const media = payload.media || {};
  if (!sp) return null;
  if (sp.image && media.splashImage) {
    const buf = Buffer.from(String(media.splashImage), 'base64');
    const ext = buf[0] === 0x89 && buf[1] === 0x50 ? 'png'
      : buf[0] === 0xff && buf[1] === 0xd8 ? 'jpg'
        : buf.subarray(0, 4).toString('latin1') === 'RIFF' && buf.subarray(8, 12).toString('latin1') === 'WEBP' ? 'webp' : '';
    if (!ext) throw new Error('Splash image is not a PNG, JPG or WebP file');
    return { kind: 'image', ext, buf };
  }
  if (sp.video && media.splashVideo) {
    const buf = Buffer.from(String(media.splashVideo), 'base64');
    if (buf.subarray(4, 8).toString('latin1') !== 'ftyp') throw new Error('Splash video is not an MP4 file');
    return { kind: 'video', ext: 'mp4', buf };
  }
  return null;
}

/**
 * The website files of an app made from an uploaded HTML file or ZIP.
 * Returns [{ path, buf }] (paths relative to the site folder) or null for a normal website app.
 */
export function siteFiles(payload) {
  if (payload.config.source !== 'local') return null;
  const src = payload.source || {};
  const bytes = new Uint8Array(Buffer.from(String(src.data || ''), 'base64'));
  if (src.type === 'html') {
    checkHtmlFile(bytes);
    return [{ path: 'index.html', buf: Buffer.from(bytes) }];
  }
  if (src.type !== 'zip') throw new Error('The site files are missing');
  const { files } = checkZipSite(bytes);
  const dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  return files.map((e) => {
    if (dv.getUint32(e.offset, true) !== 0x04034b50) throw new Error(`Damaged ZIP entry: ${e.name}`);
    const start = e.offset + 30 + dv.getUint16(e.offset + 26, true) + dv.getUint16(e.offset + 28, true);
    const data = Buffer.from(bytes.subarray(start, start + e.compSize));
    const buf = e.method === 8 ? zlib.inflateRawSync(data, { maxOutputLength: MAX_SITE_BYTES }) : data;
    if (buf.length !== e.size) throw new Error(`Damaged ZIP entry: ${e.name}`);
    // The first page is found as index.html whatever its case in the ZIP.
    const p = e.path.toLowerCase() === 'index.html' ? 'index.html' : e.path;
    return { path: p, buf };
  });
}
