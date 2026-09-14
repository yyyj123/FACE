# WEB-R staff booking and pagination plan

## Scope

- Correct the demo data contract so the active `DEMO-ST-*` technicians are associated with active services and can appear in the booking technician selector.
- Paginate the customer home-page staff gallery at five technicians per page.
- Preserve the current responsive visual language and booking qualification rules.
- Do not start WeChat Mini Program or SC10 work, and do not perform a production-domain or real-channel switch.

## Implementation

1. Add an expand-only Flyway migration that idempotently inserts missing `staff_service` rows for active `DEMO-ST-*` technicians and active services in their assigned shop.
2. Add a migration contract test so the demo-technician mapping cannot regress.
3. Add client-side home staff pagination with a fixed page size of five, previous/next controls, numbered pages, and accessible current/disabled states.
4. Add a frontend contract test for the five-person page size and rendered paginated subset.
5. Keep card dimensions stable between full and partial pages by using fixed responsive column tracks: five on desktop, three on tablet, and two on mobile.

## Verification

- Run frontend unit tests and production build.
- Run the backend migration contract test and full backend suite as required by WEB-R verification.
- Run the SC8 static demo contract.
- Run Impeccable mechanical detection over the changed UI.
- Deploy the current demo in place, run Flyway migrate/validate, and confirm the selected nail service exposes multiple qualified technicians.
- Run WEB-R repeatable acceptance twice and retain the generated evidence.
