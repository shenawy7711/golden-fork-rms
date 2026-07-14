# Handoff — Golden Fork RMS: Authentication + Dashboard (JavaFX 8)

## Overview
This package specifies two screens of the **Golden Fork Restaurant Management System** so they can be built in **JavaFX 8** (FXML views + Java controllers + `app.css`):

1. **Auth** — combined Login / Sign-up card with a role picker.
2. **Dashboard** — role-aware home shell (top bar, side nav, content, status bar).

The target is a desktop JavaFX 8 application. The MySQL schema is already built and is the **source of truth** — build only what the schema supports (see *Schema constraints* below).

## About the design files
The files in `source/` are **design references created in HTML** (interactive prototypes showing the intended look and behavior). They are **not** production code to copy. Your task is to **recreate these designs in JavaFX 8** using standard JavaFX controls and an `app.css` stylesheet — matching the layout, spacing, colors, and typography documented here as closely as JavaFX allows.

Open the HTML files in a browser to see live interaction (tab switching, role picker, role-filtered nav, hover states). Screenshots of the key states are in `screenshots/`.

## Fidelity
**High-fidelity.** Colors, typography, spacing, and interactions are final. Recreate the UI to match. Where a CSS effect has no clean JavaFX 8 equivalent (multi-stop radial gradients, `box-shadow` spread with negative values, backdrop blur), approximate it with the closest `-fx-` equivalent — a note is given per case. Pixel-exact shadow rendering is **not** required; layout, color, type, and states are.

## Files in this package
- `README.md` — this file: overview, design tokens, `app.css` starter, fonts, navigation, schema constraints.
- `01-auth-spec.md` — Auth screen: full control-by-control spec + FXML structure sketch + controller notes.
- `02-dashboard-spec.md` — Dashboard screen: same.
- `app.css` — ready-to-use JavaFX stylesheet implementing the design tokens and the styled classes both screens reference. Wire it with `scene.getStylesheets().add(getClass().getResource("app.css").toExternalForm());`.
- `screenshots/` — reference renders (`auth-login.png`, `auth-signup.png`, `dashboard-admin.png`, `dashboard-cashier.png`).
- `source/` — the original HTML prototypes (`Golden Fork Auth.dc.html`, `Golden Fork Dashboard.dc.html`).

---

## Design tokens

### Color palette
| Token | Hex | Use |
|---|---|---|
| `navy-900` | `#152238` | Top bar, brand panel base, darkest surfaces |
| `navy-800` | `#1F3050` | Primary buttons, active nav item, brand gradient top |
| `navy-700` | `#26385C` | Primary button hover |
| `ink` | `#1B2A41` | Primary text / headings |
| `slate-600` | `#42526B` | Field labels, nav item text |
| `slate-500` | `#6B7280` | Secondary/body text |
| `slate-400` | `#8A93A2` | Hint text |
| `slate-350` | `#9AA2AE` | Muted meta text |
| `gold-400` | `#C8A24A` | Primary gold accent (labels, dots) |
| `gold-500` | `#B8863B` | Gold gradient bottom / logo |
| `gold-300` | `#D8B45C` | Gold gradient top |
| `gold-link` | `#9A742B` | Link text |
| `gold-link-hover` | `#7E5E1F` | Link hover |
| `cream-100` | `#F5F1E9` | Light text on navy, panel tints |
| `cream-200` | `#EFEBE3` | App background |
| `cream-300` | `#F1EDE4` | Segmented-toggle track, nav chip bg |
| `field-bg` | `#FBFAF7` | Input background (resting) |
| `field-border` | `#DCD8CE` | Input border (resting) |
| `card-bg` | `#FFFFFF` | Cards, side nav, form panel |
| `card-border` | `#EAE3D6` | Card borders |
| `divider` | `#F0EBE0` | Hairline dividers inside panels |

### Status colors (from schema — `dining_table.status`)
| Status | Hex |
|---|---|
| Free | `#2E9E6B` |
| Occupied | `#D0574E` |
| Reserved | `#C8912E` |
| Needs Cleaning | `#6B7C93` |

### Typography
- **Headings:** Cormorant Garamond (600). JavaFX fallback: **Georgia** (serif). Applied to: app titles, screen headings, stat-card numbers, the "GF" logo mark.
- **Body / UI:** DM Sans (400/500/600/700). JavaFX fallback: **system sans-serif** (`System`). Applied to everything else.
- Per the answer given, this build uses the **fallbacks** (Georgia + System) — do NOT bundle the TTFs. Keep a `.heading` style class on serif text so fonts can be swapped later in one place.

Type scale used:
| Role | Size / weight | Font |
|---|---|---|
| Brand wordmark ("Golden Fork") | 52px / 600 | serif |
| Screen heading (h1) | 34–36px / 600 | serif |
| Stat-card number | 36px / 600 | serif |
| Section heading (h2) | 15px / 700 | sans |
| Primary button | 15px / 600 | sans |
| Field value / input | 15px / 400 | sans |
| Field label | 12.5px / 600 | sans |
| Nav item | 13.5px / 500–600 | sans |
| Eyebrow / section label | 10.5–11px / 700, letter-spacing 0.14–0.16em, UPPERCASE | sans |
| Hint / meta | 11.5–12.5px / 400 | sans |

### Spacing & shape
- Corner radius: inputs/small buttons **10px**, primary buttons/toggle track **12px**, cards **16px**, large panels **18px**, auth card **22px**, logo tile **14px** (auth) / **10px** (dashboard).
- Card padding: 18px (stat cards), 22px (panels), 44–48px (auth panels).
- Standard gaps: 16px between stat cards, 12px between quick-action buttons, 18px between form fields.
- Shadows (approximate in JavaFX with `-fx-effect: dropshadow(gaussian, rgba(...), radius, spread, offx, offy)`):
  - Card: `dropshadow(gaussian, rgba(27,42,65,0.18), 24, 0, 0, 10)`
  - Auth card: `dropshadow(gaussian, rgba(27,42,65,0.35), 60, 0, 0, 24)`
  - Primary button: `dropshadow(gaussian, rgba(31,48,80,0.55), 20, 0, 0, 10)`

---

## Fonts in JavaFX
No font files are bundled. In `app.css`, `.heading` uses `-fx-font-family: "Georgia", serif;` and the root uses `-fx-font-family: "System"`. If you later want the true faces, drop `CormorantGaramond-SemiBold.ttf` and `DMSans-*.ttf` into resources, register them at startup with `Font.loadFont(...)`, and change the two `-fx-font-family` declarations — nothing else changes.

## Navigation model (recommended)
Use a **single `Stage`**. Wrap the app in a root `BorderPane` (the Dashboard shell). Swap the **center** node to move between management screens; for Login vs. the app shell, swap the whole **scene root** (or set a new `Scene`). Suggested: a small `ScreenRouter`/`NavigationService` holding the `Stage`, with methods like `showLogin()`, `showDashboard(role)`, `navigate(screenKey)`.

The HTML prototype uses page links + URL params; the JavaFX equivalent is root/center swapping — do not open multiple windows.

## Schema constraints (must match — from TDD)
Everything on these two screens must stay within the fixed MySQL schema:
- **Roles:** Cashier, Manager, Administrator (`role` table, RBAC). Nav and features filter by role.
- **Sign-up approval:** a **Manager** or **Administrator** sign-up creates a `user_account` with `status = Inactive`; an existing administrator must set `status = Active` before first login. A **Cashier** sign-up is `Active` immediately. There is **no PENDING status** — this is modeled purely with the `Active`/`Inactive` status flag. The Auth screen's amber approval note reflects exactly this.
- **Auth:** password is stored **salted + hashed** (never plain). Login checks username + password hash and rejects `Inactive` accounts.
- No fields beyond what `user_account` / `role` support (full_name, username [unique], password hash, role, status). Do not add email verification, 2FA, SSO, "delivery," or any status the schema lacks.
- Dashboard figures are **illustrative** placeholders; when wiring real data, source them from the schema (open orders from `orders.status = 'Open'`, table counts from `dining_table.status`, reservations from `reservation`, low stock from `stock` vs. reorder level, sales from Paid/Closed `orders`, staff from `staff`).

## Data approach
Build **UI-first with mock/in-memory data** (a small `DashboardViewModel` with hard-coded values matching the screenshots is fine to start). Wire the screens to real JDBC/DAO calls as a follow-up — the per-screen specs note which schema entity each value comes from so the swap is mechanical.
