import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const baselineRoot = process.env.OC_TASK5_BASELINE || 'D:/\.AAApersonal/biye/bishe/daima/car/front/src';
const currentRoot = 'C:/project/front/src';
const pageFiles = [
  'pages/login/login.vue',
  'pages/register/register.vue',
  'pages/center/center.vue',
  'pages/fuwuyuyue/list.vue',
  'pages/fuwuyuyue/detail.vue',
  'pages/fuwuyuyue/add.vue',
  'pages/xinnengyuanqiche/list.vue',
  'pages/xinnengyuanqiche/detail.vue'
];

function read(root, relative) {
  const file = path.join(root, relative);
  if (!fs.existsSync(file)) throw new Error(`Task 5 regression baseline missing ${file}`);
  return fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n');
}

function scriptBlock(source, file) {
  const match = source.match(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/);
  if (!match) throw new Error(`${file} has no Vue script block`);
  return match[1];
}

function digest(source) {
  return crypto.createHash('sha256').update(source).digest('hex');
}

function inventory(source) {
  const methodNames = [...source.matchAll(/^\s{3,}([A-Za-z_$][\w$]*)\s*\([^)]*\)\s*\{/gm)].map(match => match[1]).sort();
  const httpCalls = [...source.matchAll(/\$http\.(get|post)\(\s*([`'"][^,\n]+)/g)].map(match => `${match[1]}:${match[2]}`).sort();
  const routeCalls = [...source.matchAll(/\$router\.(push|replace|go)\(\s*([^;\n]+)/g)].map(match => `${match[1]}:${match[2].trim()}`).sort();
  return { methodNames, httpCalls, routeCalls };
}

for (const file of pageFiles) {
  const current = scriptBlock(read(currentRoot, file), file);
  const baseline = scriptBlock(read(baselineRoot, file), file);
  const currentInventory = inventory(current);
  const baselineInventory = inventory(baseline);
  if (JSON.stringify(currentInventory) !== JSON.stringify(baselineInventory)) {
    throw new Error(`${file} business inventory changed: current ${digest(JSON.stringify(currentInventory))}, baseline ${digest(JSON.stringify(baselineInventory))}`);
  }
}

const routeFile = 'router/router.js';
const currentRoutes = read(currentRoot, routeFile);
const baselineRoutes = read(baselineRoot, routeFile);
if (currentRoutes !== baselineRoutes) {
  throw new Error(`${routeFile} changed: current ${digest(currentRoutes)}, baseline ${digest(baselineRoutes)}`);
}

console.log(`obsidian Task 5 business regression guard verified method/API/route inventories for ${pageFiles.length} Vue scripts and exact router/router.js`);
