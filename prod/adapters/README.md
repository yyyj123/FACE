# Production adapter installation

External adapter JARs are mounted read-only at `/app/adapters` and loaded by Spring Boot `PropertiesLauncher`. An adapter JAR must provide Spring Boot auto-configuration and register the corresponding FACE port implementation. Never copy provider credentials into the JAR.

Package names are exact and case-sensitive after channel normalization:

- `payment-<CHANNEL>.jar`
- `sms-<CHANNEL>.jar`（内置 `HTTP` 通道除外）
- `logistics-<CHANNEL>.jar`

Each enabled channel requires a properties file in `FACE_CHANNEL_EVIDENCE_DIR`. The file records the channel, adapter SHA-256, UTC `checked-at`, and PASS results. Evidence older than 90 days is rejected.

Payment `payment.properties` must contain:

```properties
channel=WECHAT
adapter.sha256=<sha256-of-payment-WECHAT.jar>
checked-at=2026-08-10T00:00:00Z
sandbox=PASS
small-payment=PASS
refund=PASS
reconciliation=PASS
```

Built-in HTTP SMS uses `sms.properties` with `adapter.sha256=BUILTIN`, plus `sandbox=PASS` and `delivery-receipt=PASS`. Logistics evidence requires `sandbox`, `create-shipment`, `tracking`, and `cancel-shipment` PASS results.

The Go/No-Go gate rejects an enabled channel when its adapter, digest, evidence, endpoint, or credential is missing. Keeping a channel set to `disabled` is valid and results in an explicit unavailable response; it never falls back to DEMO or Sandbox behavior.
