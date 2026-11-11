// Dev helper: writes a sample build request (payload.json) with plain generated icons.
// Usage: node scripts/dev-sample.mjs <out-file>
import fs from 'node:fs';
import zlib from 'node:zlib';

function crc32(buf) {
  let c, crc = ~0;
  for (let n = 0; n < buf.length; n++) {
    c = (crc ^ buf[n]) & 0xff;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    crc = (crc >>> 8) ^ c;
  }
  return ~crc >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}
function png(size, [r, g, b]) {
  const row = Buffer.alloc(1 + size * 3);
  for (let x = 0; x < size; x++) row.set([r, g, b], 1 + x * 3);
  const raw = Buffer.concat(Array.from({ length: size }, () => row));
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0); ihdr.writeUInt32BE(size, 4); ihdr.set([8, 2, 0, 0, 0], 8);
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
// Smallest valid-looking JPEG header is enough for the local generator test.
const jpeg = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(200)]);

const payload = {
  id: 'a'.repeat(24),
  platforms: ['android', 'ios'],
  config: {
    url: 'https://example.com/', appName: "Rahim's \"Shop\" & <Co>", packageId: 'com.example.shop',
    versionName: '1.0.0', versionCode: 1, themeColor: '#0F766E', splashColor: '#FFFFFF', iconBgColor: '#0F766E',
    orientation: 'auto',
    permissions: ['camera', 'microphone', 'location', 'notifications', 'photos', 'contacts', 'calendar',
      'bluetooth', 'phone', 'motion', 'biometric', 'nfc', 'vibrate', 'wakelock', 'tracking'],
    internalHosts: ['example.org'],
  },
  icons: {
    android: png(192, [15, 118, 110]).toString('base64'),
    androidFg: png(432, [15, 118, 110]).toString('base64'),
    ios: jpeg.toString('base64'),
  },
};
fs.writeFileSync(process.argv[2] || 'payload.json', JSON.stringify(payload));
console.log('sample written');
