import fs from 'node:fs';

const required = ['--oc-bg', '--oc-surface-1', '--oc-text', '--oc-red', '--oc-gold', '--oc-focus'];
const files = ['front/src/assets/styles/obsidian-tokens.scss', 'admin/src/assets/css/obsidian-tokens.scss'];
for (const file of files) {
  const source = fs.readFileSync(file, 'utf8');
  for (const token of required) {
    if (!source.includes(token)) throw new Error(`${file} missing ${token}`);
  }
}

const requiredStates = [':focus-visible', '.el-button--primary', '.el-input__inner', '.el-table', '.el-dialog', '@media (prefers-reduced-motion: reduce)'];
for (const file of ['front/src/assets/styles/obsidian-element.scss', 'admin/src/assets/css/obsidian-element.scss']) {
  const source = fs.readFileSync(file, 'utf8');
  for (const state of requiredStates) {
    if (!source.includes(state)) throw new Error(`${file} missing ${state}`);
  }
}

const frontApp = fs.readFileSync('front/src/App.vue', 'utf8');
const forbiddenLegacyElementValues = ['#4169E1', '#f4f4f5', '#0066d4', 'background: #FFF', 'height: 28px'];
for (const value of forbiddenLegacyElementValues) {
  if (frontApp.includes(value)) throw new Error(`front/src/App.vue retains legacy Element UI value ${value}`);
}
const legacyNextButton = /#pagination\.el-pagination\s+\.btn-next\s*\{[^}]*color:\s*#666\b/i;
if (legacyNextButton.test(frontApp)) {
  throw new Error('front/src/App.vue retains legacy #pagination next-button color #666');
}

const frontShell = fs.readFileSync('front/src/pages/index.vue', 'utf8');
const requiredShellClasses = ['oc-header', 'oc-main', 'oc-hero', 'oc-service-paths', 'oc-footer'];
for (const className of requiredShellClasses) {
  if (!frontShell.includes(className)) throw new Error(`front/src/pages/index.vue missing ${className}`);
}

const frontLayout = fs.readFileSync('front/src/assets/styles/obsidian-layout.scss', 'utf8');
if (!/@media\s*\([^)]*max-width[^)]*\)/.test(frontLayout)) {
  throw new Error('front layout missing mobile media query');
}
if (!/@media\s*\([^)]*min-width[^)]*\)/.test(frontLayout)) {
  throw new Error('front layout missing desktop media query');
}

const requiredShellBehaviors = [
  'carouselImage',
  'aria-haspopup="menu"',
  'oc-swiper-toggle',
  'swiperInstance',
  'loading="lazy"',
  'oc-chat-attachment',
  'oc-chat-emoji'
];
for (const behavior of requiredShellBehaviors) {
  if (!frontShell.includes(behavior)) throw new Error(`front shell missing ${behavior}`);
}
if (!frontShell.includes('prefers-reduced-motion: reduce')) {
  throw new Error('front shell does not disable Swiper autoplay for reduced motion');
}
if (/\.back_box\s+\.backBtn\s*\{[^}]*height:\s*(?:3[0-9]|4[0-3])px/i.test(frontApp)) {
  throw new Error('front App back button is below the 44px target minimum');
}

const frontPages = fs.readFileSync('front/src/assets/styles/obsidian-pages.scss', 'utf8');
const requiredPageFamilies = ['oc-auth', 'oc-list-toolbar', 'oc-content-list', 'oc-detail', 'oc-form-section', 'oc-empty'];
for (const className of requiredPageFamilies) {
  if (!frontPages.includes(`.${className}`)) {
    throw new Error(`front page family stylesheet missing ${className}`);
  }
}

const pageFamilyMarkup = [
  ['front/src/pages/login/login.vue', ['oc-auth', 'oc-form-section']],
  ['front/src/pages/register/register.vue', ['oc-auth', 'oc-form-section']],
  ['front/src/pages/center/center.vue', ['oc-form-section', 'oc-empty']],
  ['front/src/pages/fuwuyuyue/list.vue', ['oc-list-toolbar', 'oc-content-list', 'oc-empty']],
  ['front/src/pages/fuwuyuyue/detail.vue', ['oc-detail']],
  ['front/src/pages/fuwuyuyue/add.vue', ['oc-form-section']],
  ['front/src/pages/xinnengyuanqiche/list.vue', ['oc-list-toolbar', 'oc-content-list', 'oc-empty']],
  ['front/src/pages/xinnengyuanqiche/detail.vue', ['oc-detail']]
];
for (const [file, classes] of pageFamilyMarkup) {
  const source = fs.readFileSync(file, 'utf8');
  for (const className of classes) {
    if (!source.includes(className)) throw new Error(`${file} missing ${className}`);
  }
}
if (!/\.oc-app\s+\.center-preview\.center-preview\s*\{[^}]*background:\s*var\(--oc-bg\)\s*!important;/s.test(frontPages)) {
  throw new Error('front center page does not override the legacy white root background');
}
const loginPage = fs.readFileSync('front/src/pages/login/login.vue', 'utf8');
if (!/<button[^>]*class="icon iconfont"[^>]*aria-label=/s.test(loginPage)) {
  throw new Error('front login password visibility control is not a labeled native button');
}
for (const file of ['front/src/pages/fuwuyuyue/list.vue', 'front/src/pages/xinnengyuanqiche/list.vue']) {
  const source = fs.readFileSync(file, 'utf8');
  for (const state of ['listLoading', 'listError', 'oc-state-error']) {
    if (!source.includes(state)) throw new Error(`${file} missing ${state} state coverage`);
  }
}

const adminShell = fs.readFileSync('admin/src/components/index/IndexMain.vue', 'utf8');
for (const className of ['oc-admin-shell', 'oc-admin-header', 'oc-admin-main']) {
  if (!adminShell.includes(className)) throw new Error(`admin shell missing ${className}`);
}
const adminSidebar = fs.readFileSync('admin/src/components/index/IndexAsideStatic.vue', 'utf8');
if (!adminSidebar.includes('oc-admin-sidebar')) throw new Error('admin shell missing oc-admin-sidebar');
const adminLayout = fs.readFileSync('admin/src/assets/css/obsidian-layout.scss', 'utf8');
for (const requirement of ['@media (max-width: 767px)', 'overflow-x: auto', '.oc-admin-login']) {
  if (!adminLayout.includes(requirement)) throw new Error(`admin layout missing ${requirement}`);
}

const adminPages = fs.readFileSync('admin/src/assets/css/obsidian-pages.scss', 'utf8');
const requiredAdminFamilies = [
  '.form-content', '.table-content', '.add-update-preview', '.detail-form-content',
  '.oc-admin-toolbar', '.oc-admin-status', '.oc-admin-empty', '.el-form-item__error',
  'position: sticky', 'overflow-x: auto', '.oc-form-section'
];
for (const selector of requiredAdminFamilies) {
  if (!adminPages.includes(selector)) throw new Error(`admin page family stylesheet missing ${selector}`);
}
for (const requirement of ['img[role="button"]:focus-visible', '.el-pagination button', 'min-height: 44px']) {
  if (!adminPages.includes(requirement)) throw new Error(`admin page family stylesheet missing ${requirement}`);
}
for (const moduleName of ['xinnengyuanqiche', 'fuwuyuyue', 'weixiujilu']) {
  const list = fs.readFileSync(`admin/src/views/modules/${moduleName}/list.vue`, 'utf8');
  for (const className of ['oc-admin-page', 'oc-admin-toolbar', 'oc-admin-filter', 'oc-admin-actions', 'oc-admin-table']) {
    if (!list.includes(className)) throw new Error(`${moduleName}/list.vue missing ${className}`);
  }
  for (const behavior of ['resetSearch()', '@click="resetSearch()"', '@keydown.enter', '@keydown.space', 'tabindex="0"', ':alt=']) {
    if (!list.includes(behavior)) throw new Error(`${moduleName}/list.vue missing ${behavior}`);
  }
  const form = fs.readFileSync(`admin/src/views/modules/${moduleName}/add-or-update.vue`, 'utf8');
  for (const className of ['oc-admin-form', 'oc-form-section']) {
    if (!form.includes(className)) throw new Error(`${moduleName}/add-or-update.vue missing ${className}`);
  }
  if (!form.includes(':alt=')) throw new Error(`${moduleName}/add-or-update.vue missing preview alt text`);
}
for (const moduleName of ['fuwuyuyue', 'weixiujilu']) {
  const list = fs.readFileSync(`admin/src/views/modules/${moduleName}/list.vue`, 'utf8');
  if (!list.includes('oc-admin-status')) throw new Error(`${moduleName}/list.vue missing rendered status mapping`);
}
const adminMain = fs.readFileSync('admin/src/main.js', 'utf8');
if (!adminMain.includes("@/assets/css/obsidian-pages.scss")) {
  throw new Error('admin main missing obsidian page family stylesheet import');
}
console.log('obsidian theme tokens verified');
