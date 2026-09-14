# WEB performance verification — 2026-08-18

## Scope

This change is limited to the responsive customer and admin web delivery. It does not start WeChat Mini Program work, resume SC10, or perform a production-domain/channel switch.

## Diagnosis

- The supplied recording opened `https://thing-snowboard-anti-labor.trycloudflare.com/admin/benefits` and showed multi-second waits while moving among benefits, booking, and staff-profile routes.
- The local gateway API responses were observed at roughly 5–24 ms, while the same demo APIs through the public Quick Tunnel took roughly 1.2–1.6 seconds per request.
- A fresh unauthenticated public connection was observed with roughly 4.84 seconds to establish TLS and 5.84 seconds to first byte. The Cloudflare response was served through LAX.
- The admin route guard previously completed account-context requests before Vue could begin loading the destination route chunk, creating avoidable serial work.
- The demo gateway previously sent `Cache-Control: no-store` for hashed Vite assets, forcing browsers to revalidate or download unchanged JavaScript and CSS.

## Changes

- Preload the destination admin shell/page chunk in parallel with the initial account-context refresh.
- Send `Cache-Control: public, max-age=31536000, immutable` for hashed `/admin/assets/` and `/client/assets/`; keep HTML and APIs uncached.
- Add static contracts for route/auth parallelism and immutable asset headers.
- Add browser-verifier overflow diagnostics and constrain the booking page grid so the weekly table scrolls internally instead of widening the document.

## Verification

- Impeccable detector: PASS, no findings.
- Targeted browser matrix run 93: PASS across mobile portrait, tablet portrait, desktop, and mobile landscape; severe event count 0.
- WEB-R repeatable run 1: PASS.
- WEB-R repeatable run 2: PASS.
- Per repeatable run: client tests 11/11, admin tests 8/8, backend tests 225/225, production builds PASS, Flyway migrate/validate PASS with 44 dynamically discovered migrations, SC4–SC8 business regression PASS, full four-viewport browser matrix PASS, restart recovery PASS.
- Current public demo: six services running (five health-checked services healthy plus the tunnel process running).
- Current public response contract: `/admin/` returns `Cache-Control: no-store`; hashed admin entry asset returns `Cache-Control: public, max-age=31536000, immutable`.

## Remaining infrastructure limit

The code-side serial loading and repeat asset downloads are addressed. Public requests can still inherit the latency and variability of the temporary Cloudflare Quick Tunnel. A stable named tunnel/formal domain and an origin path closer to the target users are the next infrastructure step, but that production switch is outside the current gate and was not performed.
