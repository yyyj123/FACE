# Obsidian Media Manifest

All photography below was downloaded from Pexels on 2026-07-11 and is used under the [Pexels License](https://www.pexels.com/license/). Files are stored locally; runtime pages do not hotlink these sources. Final crops were visually inspected for automaker badges, model/technology wordmarks, watermarks, and other protected branding.

| Local path | Source URL | Author/provider | License | Download date | Crop role | Dimensions | Content SHA-256 | Alt policy |
|---|---|---|---|---|---|---|---|---|
| `front/src/assets/images/obsidian/hero-ev-charge-desktop.webp` | https://www.pexels.com/photo/electric-connector-van-18555543/ | Timothy Huliselan / Pexels | Pexels License: https://www.pexels.com/license/ | 2026-07-11 | Desktop EV precision hero; darkened wide crop centered on charging connector | 1920×820 | `ADC2CC5F8B2DF0E9A5E0C957C8B6C8E9E155D153B7CF4DBA6E21752C8C135E77` | Meaningful use: `电动汽车充电接口与红色充电线`; empty alt only for CSS atmosphere use |
| `front/src/assets/images/obsidian/hero-ev-charge-mobile.webp` | https://www.pexels.com/photo/electric-connector-van-18555543/ | Timothy Huliselan / Pexels | Pexels License: https://www.pexels.com/license/ | 2026-07-11 | Mobile EV precision hero; darkened portrait crop centered on charging connector | 828×1104 | `20D2709D1B65ACFF98B784C6536A79F91D068B4296FE84B0505E72AA0F1B2B75` | Same factual description when meaningful; empty alt only for CSS atmosphere use |
| `front/src/assets/images/obsidian/auth-workshop.webp` | https://www.pexels.com/photo/mechanics-working-with-cars-4116231/ | Jose Ricardo Barraza Morachis / Pexels | Pexels License: https://www.pexels.com/license/ | 2026-07-11 | Login/register workshop atmosphere background | 1600×1200 | `7E71424A998604763D604EE82DC852CA121A7385FDF2D499EC153B22900F8917` | Empty alt because it is a CSS atmosphere background and form content supplies meaning |
| `admin/src/assets/images/obsidian/auth-workshop.webp` | https://www.pexels.com/photo/mechanics-working-with-cars-4116231/ | Jose Ricardo Barraza Morachis / Pexels | Pexels License: https://www.pexels.com/license/ | 2026-07-12 | Admin login atmosphere background; byte-identical local copy | 1600×1200 | `7E71424A998604763D604EE82DC852CA121A7385FDF2D499EC153B22900F8917` | Empty alt because it is a CSS atmosphere background and form content supplies meaning |
| `front/src/assets/images/obsidian/service-workshop-card.webp` | https://www.pexels.com/photo/mechanics-working-with-cars-4116231/ | Jose Ricardo Barraza Morachis / Pexels | Pexels License: https://www.pexels.com/license/ | 2026-07-11 | Service card and safe fallback for remote service media | 720×480 | `A86F7FB8B6A2C6AC693115A4CAC8697268D99F59441EEC5F670B7A472315435B` | Descriptive Chinese alt: `专业汽车维修服务` or page-specific equivalent |
| `front/src/assets/images/obsidian/ev-charging-card.webp` | https://www.pexels.com/photo/electric-connector-van-18555543/ | Timothy Huliselan / Pexels | Pexels License: https://www.pexels.com/license/ | 2026-07-11 | EV charging card and safe fallback for remote vehicle media | 720×480 | `DA3E638C5AF63945A880DCBDB5A78015A771434CBD6D9BD171057F089FD0FBA2` | Descriptive Chinese alt: `电动汽车充电接口与红色充电线` or page-specific equivalent |
| `front/src/assets/images/obsidian/engine-detail-card.webp` | https://www.pexels.com/photo/close-up-of-car-engine-13972229/ | Aliaksei Semirski / Pexels | Pexels License: https://www.pexels.com/license/ | 2026-07-11 | Precision mechanical detail card and maintenance-document fallback | 720×480 | `B84E1B99C0D2ADB2CE3DB76257528C8D47AB3CDA87B3205F7103B2813648D0C9` | Descriptive Chinese alt: `汽车发动机精密部件` or page-specific equivalent |

## Processing notes

- Originals were downloaded directly from `images.pexels.com`, then resized/cropped with Pillow using Lanczos resampling and encoded as WebP.
- Hero outputs are below 350 KB; card outputs are below 140 KB. No third-party URL is used at runtime.
- Both replacement hero crops were visually inspected at full output resolution on 2026-07-11. They show a real EV charging connector, red charging cable, plain vehicle body, and wheel; no automaker logo, badge, model name, technology wordmark, watermark, or implausible generated detail is visible.
- CSS background assets are intentionally non-semantic. Content images use concise Chinese descriptions; avatars and chat decoration retain empty alt where adjacent text identifies the person.
- `admin/src/assets/images/obsidian/` is intentionally empty: Task 3 found no admin-only brand/business image reference that required replacement, so duplicating unused media would add weight without benefit.

## Task 3B additional licensed portrait

| Local path | Source URL | Author/provider | License | Download date | Crop role | Dimensions | Content SHA-256 | Alt policy |
|---|---|---|---|---|---|---|---|---|
| `car/docs/media/optimized/obsidian-mechanic-portrait.webp` | https://www.pexels.com/photo/industrial-worker-in-a-mechanic-workshop-setting-37364516/ | Mehmet Turgut Kirkgoz / Pexels | Pexels License: https://www.pexels.com/license/ | 2026-07-11 | Seed/demo owner, technician, administrator, friend, and chat avatar replacement | 640x640 | `EAA1E9714420AE45FA268DD667566CAE159D5A5A7FBE73E97173F9886436D673` | Empty alt when an adjacent name identifies the person; otherwise describe as a workshop technician portrait |

The portrait was downloaded from the Pexels image CDN, center-cropped with Pillow/Lanczos, and encoded as WebP. The original and output were visually inspected at full resolution on 2026-07-11: no logo, badge, wordmark, watermark, or other protected branding is visible.
