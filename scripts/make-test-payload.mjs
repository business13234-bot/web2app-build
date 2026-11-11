// Usage: node scripts/make-test-payload.mjs <in.json> <out.json> [platforms]
// Validates a build request exactly like the Worker does and gives it a new build id.
// For testing the build workflows directly (gh workflow run), without the website.
import fs from 'node:fs';
import { validateConfig } from '../shared/catalog.mjs';

const [input, output, platforms = 'android,ios'] = process.argv.slice(2);
const p = JSON.parse(fs.readFileSync(input, 'utf8'));
const r = validateConfig(p.config);
if (r.error) {
  console.error('Invalid config:', r.error);
  process.exit(1);
}
const id = [...crypto.getRandomValues(new Uint8Array(12))].map((b) => b.toString(16).padStart(2, '0')).join('');
fs.writeFileSync(output, JSON.stringify({ id, platforms: platforms.split(','), config: r.config, icons: p.icons }));
console.log(id);
