// Usage: node scripts/encrypt-out.mjs <dir>
// Builds run in a public repository, where anyone signed in to GitHub can download the build
// files. Each finished file is encrypted with the key the Worker made for this one build
// (W2A_FILE_KEY), so only the Worker can open it again.
// File format: "W2AE1" | 12-byte IV | AES-256-GCM ciphertext | 16-byte tag.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const dir = process.argv[2];
const key = Buffer.from(process.env.W2A_FILE_KEY || '', 'base64');
if (!dir) {
  console.error('Usage: encrypt-out.mjs <dir>');
  process.exit(2);
}
if (key.length !== 32) {
  console.error('::error::No file key for this build: refusing to upload unencrypted files');
  process.exit(1);
}

let count = 0;
for (const name of fs.readdirSync(dir)) {
  const file = path.join(dir, name);
  if (!fs.statSync(file).isFile()) continue;
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const body = Buffer.concat([cipher.update(fs.readFileSync(file)), cipher.final()]);
  fs.writeFileSync(file, Buffer.concat([Buffer.from('W2AE1'), iv, body, cipher.getAuthTag()]));
  count++;
}
console.log(`Encrypted ${count} file(s)`);
