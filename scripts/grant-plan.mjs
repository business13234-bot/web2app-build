// Usage: node scripts/grant-plan.mjs <email> <days>      give (or extend) a plan: no watermark
//        node scripts/grant-plan.mjs <email> 0           end the plan now: the watermark comes back
//        node scripts/grant-plan.mjs <email>             show when the plan ends
// Works on the live Web2App data (Cloudflare KV) through wrangler, signed in as the owner.
// The apps of that account notice the change the next time they start.
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const [email, daysText] = process.argv.slice(2);
if (!email || !/^[^\s@]+@[^\s@]+$/.test(email) || (daysText !== undefined && !/^\d{1,4}$/.test(daysText))) {
  console.error('Usage: node scripts/grant-plan.mjs <email> [days]');
  process.exit(2);
}
const worker = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'worker');
const npx = process.platform === 'win32' ? 'npx.cmd' : 'npx';
const kv = (...args) => execFileSync(npx, ['wrangler', 'kv', 'key', ...args, '--binding', 'KV', '--remote'], {
  cwd: worker, encoding: 'utf8', shell: process.platform === 'win32', stdio: ['ignore', 'pipe', 'pipe'],
}).trim();
const get = (key) => {
  try {
    return kv('get', key, '--text');
  } catch {
    return '';
  }
};

const uid = get(`email:${email.trim().toLowerCase()}`);
if (!/^[A-Za-z0-9_-]{6,64}$/.test(uid)) {
  console.error('No Web2App account with that email.');
  process.exit(1);
}
const now = Date.now();
const current = Math.max(0, Number(get(`plan:${uid}`)) || 0);
const show = (t) => (t > now ? `plan until ${new Date(t).toISOString().slice(0, 16).replace('T', ' ')} UTC (no watermark)` : 'no plan (watermark shown)');
if (daysText === undefined) {
  console.log(show(current));
  process.exit(0);
}
const days = Number(daysText);
const until = days === 0 ? now : Math.max(now, current) + days * 86400000;
kv('put', `plan:${uid}`, String(until));
console.log(`Before: ${show(current)}\nNow:    ${show(until)}`);
