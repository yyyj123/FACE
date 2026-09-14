# Obsidian Cockpit final QA — 2026-07-12

## Verdict

BLOCKED. Builds and automated guards pass, and the real admin populated-table failure is closed, but Task 7 form acceptance and the full Task 8 browser matrix are not completely evidenced.

## Fresh automated evidence

- `front: npm run build` — exit 0, `DONE Build complete`; known warnings: unresolved optional `hls.js` from `vue-aplayer`, asset/entrypoint size, webpack performance, npm `sass_binary_site`.
- `admin: npm run build` — exit 0, `DONE Build complete`; known warnings: asset/entrypoint size, webpack performance, stale Browserslist data, npm `sass_binary_site`.
- `node scripts/verify-obsidian-theme.mjs` — `obsidian theme tokens verified`.
- `node scripts/verify-obsidian-media.mjs` — `obsidian media verified`.
- `node scripts/verify-obsidian-task5-regression.mjs` — business method/API/route inventories verified for eight Vue scripts and the router.

## Real-stack browser evidence

Topology: Spring Boot `127.0.0.1:8080`; admin Vue CLI proxy `127.0.0.1:8081`. The required in-app browser control endpoint was not exposed in this session, so an isolated headless Edge/CDP profile drove actual visible controls.

- Admin login used visible username/password inputs, visible Element role selection (`管理员`), and visible Login button. A nonempty token was stored and routing completed to `#/`.
- Vehicle route `/xinnengyuanqiche`: 9 live rows rendered. The first-row checkbox remained checked and enabled the bulk delete action; before selection the bulk action was disabled.
- Appointment route `/fuwuyuyue`: 10 live rows rendered for admin with real status/action content. The admin UI correctly hid appointment Add/Modify actions.
- Technician login used `维修账号1` / `123456`, visible role selection (`维修技师`), and visible Login button. Six assigned appointment rows rendered with View/Start repair/Delete actions; Add was also permission-hidden.
- Evidence artifacts: `C:\project\.task8-admin-evidence.json` and `C:\project\.task7-forms-closure-evidence.json`.

## Outstanding release blockers

- No permission role exposes appointment Add/Edit through the tested real UI, so appointment add/edit validation, upload, cancel, save success, and intercepted save error remain unexecuted.
- A full keyboard-only traversal through list, pagination, row actions, and form controls was not completed.
- The complete fresh Task 8 route/viewport matrix (360×800, 768×1024, 1280×800, 1600×900), screenshot read-back, front login/appointment workflow, console/network inventory, 200% zoom, reduced-motion, and measured contrast matrix was not completed in this run. Earlier task reports remain supporting evidence but are not represented as fresh Task 8 coverage.

## Data and process cleanup

- Fresh authenticated searches found zero `OC_QA_` matches in `fuwuyuyue`, `xinnengyuanqiche`, and `weixiujilu` (limit 1000).
- No save request was reached; no QA row was created.
- The isolated Edge profile was removed. Scoped backend/admin processes were stopped; ports 8080, 8081, and 8082 had no listeners afterward.

## Accepted limitations / known warnings

The production bundle-size warnings, stale Browserslist notice, npm config notice, and front optional `hls.js` resolution warning predate this final pass. They do not fail compilation, but the `hls.js` warning remains a runtime risk if HLS playback is exercised.
