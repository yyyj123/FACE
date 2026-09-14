# Obsidian Cockpit Redesign Implementation Plan

> Implement this plan task by task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign both Vue 2 applications into a coherent dark “obsidian precision cockpit,” replace all visible imagery with licensed local assets, and preserve every existing business workflow.

**Architecture:** Add one token-driven theme layer per application, then reshape the two app shells and representative page families so generated sibling pages inherit the system. Keep Element UI and all routes/API methods intact; use scoped structural edits only where global theming cannot express the approved composition.

**Tech Stack:** Vue 2.6.14, Element UI 2.x, SCSS via Dart Sass 1.32.13, Vue CLI 4/Webpack 4, Swiper 5, local responsive raster assets.

## Global Constraints

- Preserve all routes, API fields, permission checks, stores, and business methods.
- Do not add a second component library.
- Use near-black obsidian, graphite surfaces, cool-white text, restrained performance red, and low-saturation champagne gold.
- Meet WCAG 2.1 AA; provide visible focus, 44×44px touch targets, and reduced-motion behavior.
- Do not use protected automaker logos, official-site media, or imagery with unclear licensing.
- Download every selected image locally, optimize it, avoid third-party hotlinks, and provide correct alt behavior.
- Cards use 8–14px radii; no gradient text, decorative glassmorphism, looping glow, or decorative dashboard imagery.
- Production builds for both applications must pass before completion.

---

### Task 1: Establish automated theme and asset guardrails

**Files:**
- Create: `C:\project\scripts\verify-obsidian-theme.mjs`
- Create: `C:\project\front\src\assets\styles\obsidian-tokens.scss`
- Create: `C:\project\admin\src\assets\css\obsidian-tokens.scss`
- Modify: `C:\project\front\src\main.js`
- Modify: `C:\project\admin\src\main.js`

**Interfaces:**
- Produces CSS custom properties with identical semantic names in both apps: `--oc-bg`, `--oc-surface-1`, `--oc-surface-2`, `--oc-text`, `--oc-text-muted`, `--oc-red`, `--oc-gold`, `--oc-border`, `--oc-focus`, and spacing/radius/motion tokens.
- Produces a Node verification command used by every later task.

- [ ] **Step 1: Write the failing guard script**

```js
import fs from 'node:fs';

const required = ['--oc-bg', '--oc-surface-1', '--oc-text', '--oc-red', '--oc-gold', '--oc-focus'];
const files = ['front/src/assets/styles/obsidian-tokens.scss', 'admin/src/assets/css/obsidian-tokens.scss'];
for (const file of files) {
  const source = fs.readFileSync(file, 'utf8');
  for (const token of required) {
    if (!source.includes(token)) throw new Error(`${file} missing ${token}`);
  }
}
console.log('obsidian theme tokens verified');
```

- [ ] **Step 2: Run the guard and verify RED**

Run: `node C:\project\scripts\verify-obsidian-theme.mjs`

Expected: failure because both token files do not exist.

- [ ] **Step 3: Add the minimal shared token contract**

```scss
:root {
  --oc-bg: #090b0e;
  --oc-surface-1: #11151a;
  --oc-surface-2: #181d23;
  --oc-text: #f3f5f7;
  --oc-text-muted: #b5bdc7;
  --oc-red: #e1262f;
  --oc-gold: #b9a477;
  --oc-border: rgba(210, 220, 230, 0.16);
  --oc-focus: #f0c987;
  --oc-radius-sm: 8px;
  --oc-radius-md: 12px;
  --oc-motion-fast: 160ms;
  --oc-motion-base: 220ms;
}
```

Import the relevant file after Element UI styles in each `main.js`, ensuring theme rules win without `!important` unless Element UI specificity requires it.

- [ ] **Step 4: Verify GREEN and builds**

Run: `node C:\project\scripts\verify-obsidian-theme.mjs`

Expected: `obsidian theme tokens verified`.

Run both: `npm run build` from `C:\project\front` and `C:\project\admin`.

Expected: both exit 0.

- [ ] **Step 5: Record checkpoint**

Because `C:\project` is not a Git repository, record changed files and build results in the implementation log instead of committing.

### Task 2: Build the reusable Element UI cockpit skin

**Files:**
- Create: `C:\project\front\src\assets\styles\obsidian-element.scss`
- Create: `C:\project\admin\src\assets\css\obsidian-element.scss`
- Modify: `C:\project\front\src\main.js`
- Modify: `C:\project\admin\src\main.js`
- Modify: `C:\project\front\src\App.vue`
- Modify: `C:\project\admin\src\App.vue`
- Modify: `C:\project\scripts\verify-obsidian-theme.mjs`

**Interfaces:**
- Consumes Task 1 tokens.
- Produces consistent button, field, select, table, dialog, pagination, tab, badge, loading, empty, focus, disabled, and error states.

- [ ] **Step 1: Extend the guard with required component states**

```js
const requiredStates = [':focus-visible', '.el-button--primary', '.el-input__inner', '.el-table', '.el-dialog', '@media (prefers-reduced-motion: reduce)'];
for (const file of ['front/src/assets/styles/obsidian-element.scss', 'admin/src/assets/css/obsidian-element.scss']) {
  const source = fs.readFileSync(file, 'utf8');
  for (const state of requiredStates) if (!source.includes(state)) throw new Error(`${file} missing ${state}`);
}
```

- [ ] **Step 2: Run the guard and verify RED**

Expected: failure on the first missing component stylesheet.

- [ ] **Step 3: Implement the skin**

Use tokenized selectors. Primary controls use red with a restrained edge highlight; selected navigation/data states use gold; neutral surfaces stay graphite. Include default, hover, focus-visible, active, disabled, loading, error, success, and empty states. Adapt Uiverse-style micro-interactions as local CSS only: a one-pass edge sweep on primary hover, precise inset field focus, and a low-motion loader.

```scss
.el-button--primary {
  border: 1px solid color-mix(in srgb, var(--oc-red) 72%, white);
  border-radius: var(--oc-radius-sm);
  background: var(--oc-red);
  color: var(--oc-text);
  transition: transform var(--oc-motion-fast) ease-out, filter var(--oc-motion-fast) ease-out;
}
.el-button--primary:active { transform: translateY(1px); }
:where(button, a, input, select, textarea, [tabindex]):focus-visible {
  outline: 2px solid var(--oc-focus);
  outline-offset: 3px;
}
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { scroll-behavior: auto !important; transition-duration: 0.01ms !important; animation-duration: 0.01ms !important; }
}
```

- [ ] **Step 4: Import styles and verify**

Run the guard and both production builds. Expected: all exit 0.

- [ ] **Step 5: Browser smoke check**

Open one form, list, detail, modal, and pagination state in each app. Verify keyboard focus, disabled controls, error contrast, and no light Element UI surface leaks.

### Task 3: Source, license, optimize, and map all imagery

**Files:**
- Create: `C:\project\docs\media\obsidian-media-manifest.md`
- Create: `C:\project\front\src\assets\images\obsidian\` (optimized WebP/JPEG assets)
- Create: `C:\project\admin\src\assets\images\obsidian\` (only required brand/business assets)
- Create: `C:\project\scripts\verify-obsidian-media.mjs`
- Modify: image references in `C:\project\front\src\pages\index.vue`, `C:\project\front\src\pages\login\login.vue`, `C:\project\front\src\pages\register\register.vue`, `C:\project\front\src\pages\center\center.vue`, and representative `detail.vue`/`list.vue` modules.

**Interfaces:**
- Produces a manifest row per asset: local path, source URL, author/provider, license, download date, crop role, dimensions, and alt text policy.
- Produces stable local imports consumed by Tasks 4–6.

- [ ] **Step 1: Write the failing asset verifier**

```js
import fs from 'node:fs';
const manifest = fs.readFileSync('docs/media/obsidian-media-manifest.md', 'utf8');
for (const field of ['Source URL', 'License', 'Local path', 'Alt policy']) {
  if (!manifest.includes(field)) throw new Error(`media manifest missing ${field}`);
}
if (/https?:\/\//.test(fs.readFileSync('front/src/pages/index.vue', 'utf8'))) {
  throw new Error('homepage contains remote image URL');
}
console.log('obsidian media verified');
```

- [ ] **Step 2: Run verifier and verify RED**

Expected: missing manifest.

- [ ] **Step 3: Search and select assets**

Use image search for licensed high-end automotive silhouettes, precision mechanical details, dark workshop/service environments, EV charging, maintenance documentation, and neutral owner portraits. Reject official automaker media, visible protected logos, watermarks, implausible vehicle details, and ambiguous licenses.

- [ ] **Step 4: Download and optimize locally**

Create desktop and mobile crops for hero assets, preserve safe subject areas, and compress below practical budgets: hero ≤ 350KB, cards ≤ 140KB, thumbnails ≤ 80KB where visual quality permits. Use empty alt for atmosphere-only media and descriptive Chinese alt for meaningful vehicle/service content.

- [ ] **Step 5: Replace image references and verify GREEN**

Run: `node C:\project\scripts\verify-obsidian-media.mjs`

Expected: `obsidian media verified`.

Build both apps and use browser network inspection to confirm no broken image requests or third-party image hotlinks.

### Task 3B: Back up and migrate database-backed imagery

**Files:**
- Create: `D:\.AAApersonal\biye\bishe\daima\car\scripts\migrate-obsidian-media.ps1`
- Create: `D:\.AAApersonal\biye\bishe\daima\car\docs\media\obsidian-db-media-map.csv`
- Create at execution time: `D:\.AAApersonal\biye\bishe\daima\car\backups\obsidian-media-<timestamp>\car-image-fields.sql`
- Create at execution time: `D:\.AAApersonal\biye\bishe\daima\car\backups\obsidian-media-<timestamp>\upload\`
- Modify: `D:\.AAApersonal\biye\bishe\daima\car\db\car.sql`
- Modify: `D:\.AAApersonal\biye\bishe\daima\car\src\main\resources\static\upload\` image files

**Interfaces:**
- Consumes the licensed asset manifest and optimized media from Task 3.
- Produces a CSV mapping with `table,column,row_id,old_value,new_value,source_asset,sha256` and a PowerShell command supporting `-Mode DryRun`, `-Mode Apply`, `-Mode Verify`, and `-Mode Rollback -BackupPath <path>`.

- [ ] **Step 1: Write migration dry-run assertions**

The script must refuse `Apply` without a successful MySQL connection and a newly created backup, enumerate every affected row before mutation, verify every new local file exists, and print row/file counts without exposing the database password.

```powershell
param(
  [ValidateSet('DryRun','Apply','Verify','Rollback')][string]$Mode = 'DryRun',
  [string]$BackupPath
)
$Database = 'car'
$UploadRoot = 'D:\.AAApersonal\biye\bishe\daima\car\src\main\resources\static\upload'
$MapPath = 'D:\.AAApersonal\biye\bishe\daima\car\docs\media\obsidian-db-media-map.csv'
```

- [ ] **Step 2: Run dry-run and verify RED**

Run: `powershell -NoProfile -File .\scripts\migrate-obsidian-media.ps1 -Mode DryRun`

Expected before the mapping exists: non-zero exit with a clear missing-map message and no database/file mutations.

- [ ] **Step 3: Build the complete mapping**

Cover image references in `config`, `xinnengyuanqiche`, `fuwuyuyue`, `shouhoufuwu`, `guzhangpaicha`, `peijianxinxi`, `pingjiafankui`, `weixiujilu`, `weixiuziliao`, `chezhu`, `weixiujishi`, `users`, `friend`, `storeup`, and initial demonstration rows in `chat`. Preserve real post-seed chat attachments unless explicitly mapped as demo data.

- [ ] **Step 4: Create backup and apply migration**

Copy all 98 existing upload files into the timestamped backup, export affected columns/rows to SQL, then copy new optimized assets into the upload directory and update MySQL values inside a transaction. Update `db\car.sql` with the same paths so a fresh database import matches the live database.

- [ ] **Step 5: Verify references and rollback contract**

Run `-Mode Verify`; expected: every `upload/...` path referenced by the affected database columns exists, all mapped files match recorded SHA-256 values, and no mapped old path remains. Exercise rollback against a disposable copy or transaction fixture and verify old values/files are restored.

- [ ] **Step 6: Backend regression check**

Run `mvn test` and `mvn package -DskipTests` in the backend project. Start the backend and request representative images through `/car/upload/<filename>` plus vehicle/service list endpoints; expect HTTP 200 and valid image content types.

### Task 4: Recompose the user-facing application shell and homepage

**Files:**
- Modify: `C:\project\front\src\pages\index.vue`
- Modify: `C:\project\front\src\App.vue`
- Modify: `C:\project\front\src\components\Breadcrumb.vue`
- Create: `C:\project\front\src\assets\styles\obsidian-layout.scss`
- Modify: `C:\project\front\src\main.js`
- Modify: `C:\project\scripts\verify-obsidian-theme.mjs`

**Interfaces:**
- Consumes Tasks 1–3 tokens, controls, and media.
- Produces the responsive top navigation, hero, service pathways, content rhythm, footer, and global page container inherited by user routes.

- [ ] **Step 1: Add shell assertions to the guard**

Verify `index.vue` contains semantic landmarks/classes `oc-header`, `oc-main`, `oc-hero`, `oc-service-paths`, and `oc-footer`, and that the layout stylesheet contains mobile and desktop media queries.

- [ ] **Step 2: Run guard and verify RED**

Expected: missing `oc-header`.

- [ ] **Step 3: Recompose without changing data methods**

Keep existing menu arrays, login state, routes, Swiper data, and click handlers. Restructure markup into a slim floating header, image-led hero with one primary action, asymmetric vehicle/service sections, and compact footer. Avoid decorative metrics and repeated equal cards.

- [ ] **Step 4: Implement responsive layout**

At 360px use a collapsible keyboard-operable menu and stacked hero; at 768px use a two-column service rhythm; at 1280px use the full cockpit grid. Ensure long Chinese labels wrap safely and no heading exceeds its container.

- [ ] **Step 5: Verify**

Run guard and front build. Inspect 360×800, 768×1024, 1280×800, and 1600×900 screenshots. Check hover, keyboard navigation, reduced motion, Swiper controls, login state, and route transitions.

### Task 5: Apply the user page-family system

**Files:**
- Modify: `C:\project\front\src\pages\login\login.vue`
- Modify: `C:\project\front\src\pages\register\register.vue`
- Modify: `C:\project\front\src\pages\center\center.vue`
- Modify: `C:\project\front\src\pages\fuwuyuyue\list.vue`
- Modify: `C:\project\front\src\pages\fuwuyuyue\detail.vue`
- Modify: `C:\project\front\src\pages\fuwuyuyue\add.vue`
- Modify: `C:\project\front\src\pages\xinnengyuanqiche\list.vue`
- Modify: `C:\project\front\src\pages\xinnengyuanqiche\detail.vue`
- Create: `C:\project\front\src\assets\styles\obsidian-pages.scss`

**Interfaces:**
- Produces shared page-family classes that generated sibling modules inherit: `oc-auth`, `oc-list-toolbar`, `oc-content-list`, `oc-detail`, `oc-form-section`, and `oc-empty`.

- [ ] **Step 1: Extend guard for page families and verify RED**

Require all six class names in `obsidian-pages.scss` and representative page markup.

- [ ] **Step 2: Implement auth and center composition**

Use a two-zone auth layout with a local atmospheric image and clear form. Reorganize center content around vehicle, appointments, repairs, and after-sales records while retaining existing tabs, data calls, and actions.

- [ ] **Step 3: Implement list/detail/form composition**

Use the two representative business modules to establish toolbar, status, media, metadata, action, empty, loading, and error patterns. Keep all existing bindings and handler names.

- [ ] **Step 4: Propagate through global family selectors**

Map existing generated classes (`list-preview`, `detail-preview`, `add-update-preview`, `list-form-pv`) to the new tokens so remaining modules inherit without risky template rewrites.

- [ ] **Step 5: Verify**

Build front; test login, register, search, clear, pagination, detail navigation, appointment submit validation, back navigation, and center tabs at desktop and mobile widths.

### Task 6: Recompose the admin shell and dense work surfaces

**Files:**
- Modify: `C:\project\admin\src\views\index.vue`
- Modify: `C:\project\admin\src\views\login.vue`
- Modify: `C:\project\admin\src\components\index\IndexAside.vue`
- Modify: `C:\project\admin\src\components\index\IndexAsideStatic.vue`
- Modify: `C:\project\admin\src\components\index\IndexHeader.vue`
- Modify: `C:\project\admin\src\components\index\IndexMain.vue`
- Modify: `C:\project\admin\src\assets\css\style.scss`
- Create: `C:\project\admin\src\assets\css\obsidian-layout.scss`
- Modify: `C:\project\admin\src\main.js`

**Interfaces:**
- Consumes shared semantic tokens and Element UI skin.
- Produces a stable sidebar/header/content shell and dense table/form conventions used by every admin module.

- [ ] **Step 1: Add admin shell assertions and verify RED**

Require `oc-admin-shell`, `oc-admin-sidebar`, `oc-admin-header`, `oc-admin-main`, and narrow-screen sidebar rules.

- [ ] **Step 2: Recompose the admin shell**

Preserve menu data, route actions, collapse behavior, account controls, tags view, and permissions. Use a graphite sidebar, slim header, gold selected marker, and red only for primary/destructive signals.

- [ ] **Step 3: Redesign admin login**

Use one licensed local atmosphere image and a compact high-contrast form. Preserve captcha/auth behavior and validation.

- [ ] **Step 4: Implement dense responsive behavior**

Collapse sidebar below the existing breakpoint, keep high-frequency actions visible, allow table horizontal scroll, and preserve readable fixed rem typography at 200% zoom.

- [ ] **Step 5: Verify**

Build admin; test login, menu collapse, route selection, tags navigation, account menu, logout, and 360/768/1280/1600 viewport layouts.

### Task 7: Apply the admin table, filter, form, and detail families

**Files:**
- Modify: `C:\project\admin\src\views\modules\xinnengyuanqiche\list.vue`
- Modify: `C:\project\admin\src\views\modules\xinnengyuanqiche\add-or-update.vue`
- Modify: `C:\project\admin\src\views\modules\fuwuyuyue\list.vue`
- Modify: `C:\project\admin\src\views\modules\fuwuyuyue\add-or-update.vue`
- Modify: `C:\project\admin\src\views\modules\weixiujilu\list.vue`
- Modify: `C:\project\admin\src\views\modules\weixiujilu\add-or-update.vue`
- Create: `C:\project\admin\src\assets\css\obsidian-pages.scss`
- Modify: `C:\project\admin\src\main.js`

**Interfaces:**
- Produces global admin family selectors for `.form-content`, `.table-content`, `.add-update-preview`, `.detail-form-content`, and Element UI tables/forms.

- [ ] **Step 1: Add page-family guard and verify RED**

Require table toolbar, status, empty, field error, sticky header, horizontal overflow, and form-section selectors in `obsidian-pages.scss`.

- [ ] **Step 2: Implement representative vehicle, appointment, and repair modules**

Retain every API call, permission directive, column binding, upload, editor, validation rule, and route method. Only reorganize toolbar grouping and add semantic classes where needed.

- [ ] **Step 3: Generalize generated module styling**

Use existing shared class names so all other module lists/forms inherit the cockpit treatment without editing business logic.

- [ ] **Step 4: Verify workflow states**

Test empty tables, populated tables, long cells, pagination, search reset, selection, add/edit validation, upload, cancel, save success, save error, disabled fields, and permission-hidden actions.

- [ ] **Step 5: Build admin**

Expected: exit 0 with no new Sass or Vue compilation warnings attributable to the redesign.

### Task 8: Full visual, accessibility, and regression acceptance

**Files:**
- Modify as defects require: only files listed in Tasks 1–7.
- Create: `C:\project\docs\qa\2026-07-11-obsidian-cockpit-qa.md`

**Interfaces:**
- Produces the final evidence log: commands, viewports, routes, screenshots, keyboard checks, image requests, known warnings, and accepted limitations.

- [ ] **Step 1: Run complete builds**

Run `npm run build` in both apps. Expected: exit 0 for each.

- [ ] **Step 2: Run automated guards**

Run theme and media verification scripts. Expected: both print their success messages.

- [ ] **Step 3: Browser-check representative routes**

Inspect user home/auth/list/detail/form/center and admin login/shell/table/form routes at 360×800, 768×1024, 1280×800, and 1600×900. Capture screenshots and read each screenshot back before accepting it.

- [ ] **Step 4: Accessibility checks**

Keyboard through navigation, menus, forms, dialogs, pagination, and table actions; verify focus visibility, logical order, Esc behavior, labels, AA contrast, 200% zoom, and reduced motion.

- [ ] **Step 5: Regression checks**

Exercise both login flows, route navigation, search/reset, pagination, list/detail transitions, appointment creation, admin create/edit flows, upload, logout, and error states. Check console and network panels for new errors, broken assets, and hotlinks.

- [ ] **Step 6: Fix material defects and rerun evidence**

For each defect, record route/viewpoint, patch only the owning component or theme file, then rerun the affected workflow plus both final builds.

- [ ] **Step 7: Complete QA report**

Record final build exit codes, guard outputs, checked viewports/routes, image manifest location, resolved defects, pre-existing warnings, and any remaining risk without placeholders.
