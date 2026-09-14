import fs from 'node:fs';
import crypto from 'node:crypto';

const manifest = fs.readFileSync('docs/media/obsidian-media-manifest.md', 'utf8');

for (const field of ['Source URL', 'License', 'Local path', 'Alt policy']) {
  if (!manifest.includes(field)) throw new Error(`media manifest missing ${field}`);
}

const touchedPages = [
  'front/src/pages/index.vue',
  'front/src/pages/login/login.vue',
  'front/src/pages/register/register.vue',
  'front/src/pages/center/center.vue',
  'front/src/pages/xinnengyuanqiche/detail.vue',
  'front/src/pages/xinnengyuanqiche/list.vue',
  'front/src/pages/shouhoufuwu/detail.vue',
  'front/src/pages/shouhoufuwu/list.vue',
  'front/src/pages/weixiuziliao/detail.vue',
  'front/src/pages/weixiuziliao/list.vue',
];

for (const page of touchedPages) {
  const source = fs.readFileSync(page, 'utf8');
  if (/https?:\/\//.test(source)) throw new Error(`${page} contains remote URL`);
}

const assets = [
  ['hero-ev-charge-desktop.webp', 350_000],
  ['hero-ev-charge-mobile.webp', 350_000],
  ['auth-workshop.webp', 350_000],
  ['service-workshop-card.webp', 140_000],
  ['ev-charging-card.webp', 140_000],
  ['engine-detail-card.webp', 140_000],
];

for (const [name, budget] of assets) {
  const path = `front/src/assets/images/obsidian/${name}`;
  if (!fs.existsSync(path)) throw new Error(`missing media asset ${path}`);
  if (fs.statSync(path).size > budget) throw new Error(`${path} exceeds ${budget} byte budget`);
  if (!manifest.includes(path)) throw new Error(`media manifest missing ${path}`);
  const sha256 = crypto.createHash('sha256').update(fs.readFileSync(path)).digest('hex').toUpperCase();
  if (!manifest.includes(sha256)) throw new Error(`media manifest missing SHA-256 for ${path}`);
}

console.log('obsidian media verified');
