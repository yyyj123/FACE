# WEB-R staff booking and pagination verification — 2026-08-18

## Cause

- The customer home page lists every active staff member assigned to the shop.
- The booking selector intentionally lists only active staff with an enabled `staff_service` mapping for the selected service.
- Demo service `法式裸粉美甲` (service 11) had only one such mapping, to staff 2 (`安然`), so the selector correctly returned one person even though the home page displayed seven staff records.

## Changes

- Flyway `V2026081802__demo_staff_service_coverage.sql` idempotently fills missing active-service mappings for active `DEMO-ST-*` technicians. It does not include the store manager and does not relax the production qualification query.
- The home staff gallery now renders five cards per page with previous, next, numbered-page, current-page, disabled, keyboard-focus, and live page-status states.
- Partial pages keep the same card dimensions as full pages through fixed responsive tracks: five columns on desktop, three on tablet, and two on mobile.
- The WEB-R browser verifier now clicks through the staff pages and asserts exactly five cards on page 1 and no more than five on later pages.

## Current demo result

- `法式裸粉美甲` has six qualified technician records: staff 2–7 (`安然`, `安然`, `可欣`, `若琳`, `书雅`, `清禾`).
- Staff 1 (`店长`) remains visible in the public team gallery but is not treated as a qualified technician for booking.
- The seven public staff cards render as five on page 1 and two on page 2.
- The existing Quick Tunnel container and public URL were preserved during deployment.

## Verification

- Impeccable layout detector: PASS, no findings.
- Targeted four-viewport browser run 95: PASS; page 1 = 5, page 2 = 2 on mobile portrait, tablet portrait, desktop, and mobile landscape; severe event count 0.
- Post-correction page 1/page 2 card widths are identical at every tested breakpoint: mobile portrait 175/175 px, tablet portrait 228/228 px, desktop 226/226 px, and mobile landscape 253/253 px.
- WEB-R repeatable run 1: PASS.
- WEB-R repeatable run 2: PASS.
- Per repeatable run: client tests 12/12, admin tests 8/8, backend tests 226/226, production builds PASS, Flyway migrate/validate PASS with 45 dynamically discovered migration files and latest version `2026081802`, SC4–SC8 business regression PASS, four-viewport browser matrix PASS, restart recovery PASS.
