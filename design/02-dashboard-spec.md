# 02 — Dashboard screen

Reference: `screenshots/dashboard-admin.png`, `screenshots/dashboard-cashier.png` · source: `source/Golden Fork Dashboard.dc.html`

## Purpose
Role-aware home for the app after login. A classic desktop shell: **top bar** + **side nav** + **scrolling content** + **status bar**. The nav and the content adapt to the signed-in role (Cashier / Manager / Administrator). The "Viewing as" switcher in the top bar is a **demo affordance only** — in the real app the role comes from the authenticated session and this switcher should be **removed** (or hidden behind a dev flag).

## Overall layout — `BorderPane`
- **top:** top bar (`.topbar`), fixed height **62px**, navy `#152238`.
- **left:** side nav (`.sidenav`), fixed width **250px**, white, right border `#E4DED2`, vertically scrollable (`ScrollPane`, fit-to-width).
- **center:** content (`.content`), scrollable (`ScrollPane`), padding 28/34/36px, app bg `#EFEBE3`.
- **bottom:** status bar (`.statusbar`), fixed height **32px**, bg `#E7E1D5`, top border `#D8D0C0`.

## Top bar (`.topbar`) — `HBox`, alignment CENTER_LEFT, spacing 18, padding 0 22
Left to right:
1. **Hamburger** — a `Button` drawn as three 20×2 cream bars (`VBox` of 3 `Region`s, gap 4). Toggles the side nav collapsed/expanded (optional; wire to hide the `.sidenav`).
2. **Brand lockup** — 36×36 gold-gradient logo tile (radius 10) with serif "GF" 18px navy; beside it two lines: **"RMS · Golden Fork"** (14.5px/700 cream) and **"DOWNTOWN · CAIRO"** (10.5px, 0.16em, uppercase, gold `#C8A24A`).
3. **Spacer** (`Region`, `HBox.hgrow=ALWAYS`).
4. **"Viewing as" role switcher** — *demo only, remove in production.* Eyebrow "VIEWING AS" + a pill group (bg `rgba(255,255,255,0.08)`, radius 11, padding 4) of three buttons Cashier/Manager/Admin; selected = cream fill `#F5F1E9` + navy text, others transparent + `rgba(245,241,233,0.7)`.
5. **Notifications** — 38×38 rounded button (`rgba(255,255,255,0.08)`, radius 11) with a bell glyph and a red unread dot (`#D0574E`). Use an icon font/SVG or a simple glyph.
6. **User chip** — separated by a left hairline `rgba(245,241,233,0.14)`: 36px round avatar (gold gradient) with the user's **initials** (navy, 13px/700); two lines **name** (13px/600 cream) + **role label** (11px/600 gold); a small down-caret. Bind to the session user.
7. **Log out** — outlined button (1px `rgba(245,241,233,0.22)`, transparent, cream text, 13px/600, radius 10, 36px). → returns to the Login screen.

## Side nav (`.sidenav`) — `VBox`, spacing 22, padding 20/14
Grouped, **role-filtered** sections. Each section = an eyebrow header (10.5px/700, 0.15em, uppercase, `#A9A090`) + a `VBox` of nav buttons (gap 3).

Nav button (`.nav-item`): full width, `HBox` [chip + label], padding 9/10, radius 10, left-aligned, 13.5px. Chip = 26×26 rounded square (radius 8) with the label's first letter.
- **Active** item: bg navy `#1F3050`, cream text, weight 600; chip bg gold `rgba(200,162,74,0.92)` + navy letter.
- **Inactive:** transparent bg, `#42526B` text, weight 500; chip bg `#F1EDE4` + `#9A8656` letter.

**Sections & role visibility (RBAC — from TDD §5.2):**
| Section | Items | Cashier | Manager | Admin |
|---|---|:-:|:-:|:-:|
| **Operations** | Dashboard, POS / Orders, Tables, Reservations | ✓ | ✓ | ✓ |
| **Management** | Menu & Prices, Inventory, Suppliers, Purchasing, Staff, Reports | — | ✓ | ✓ |
| **Administration** | User Accounts, System Config | — | — | ✓ |

A section renders only if it has ≥1 visible item. So a **Cashier** sees only the Operations group (see `dashboard-cashier.png`); Manager adds Management; Admin adds Administration (see `dashboard-admin.png`). This filtering is the whole point — implement it from the role, not hard-coded per screen.

Nav clicks route to the matching screen (center swap). Targets (schema modules): POS/Orders→Orders, Tables→Tables, Reservations→Reservations, Menu & Prices→Menu, Inventory→Inventory, Suppliers→Suppliers, Purchasing→Purchasing, Staff→Staff, Reports→Reports, User Accounts→Users, System Config→Config. (Only Auth + Dashboard are in this package; the rest are separate screens.)

At the bottom of the nav (pushed down with a spacer), a **"Need help?"** card: bg `#F6F2E9`, radius 12, padding 14, title 12px/700 `#42526B` + text 11.5px `#8A93A2` ("Press F1 for the shift guide, or ask a manager on duty.").

## Content (`.content`) — `VBox`, scrollable
1. **Greeting row** — `HBox` space-between:
   - Left: h1 serif 36px/600 "**Good afternoon, {name}**" + sub 14px `#6B7280` "Here is what is happening at Golden Fork right now."
   - Right (right-aligned): date 13px/600 `#42526B` ("Sunday, 12 July 2026") + "Shift open · 14:32" 12px `#9AA2AE`.
2. **Stat cards** — responsive grid, min column 212px, gap 16. Use a `FlowPane` (hgap/vgap 16, prefWrapLength to the content width) or a `GridPane` that re-columns. Card (`.stat-card`): white, 1px `#EAE3D6`, radius 16, padding 18, soft shadow; contents: label row (12.5px/600 `#6B7280` + a 30px tinted rounded-square icon holding an 11px colored square), big serif number 36px/600 ink, sub 12px `#9AA2AE`.
   - **All roles** see: **Open orders** (`3` — "2 dine-in · 1 takeaway"), **Tables free** (`5 / 12` — "4 occupied · 2 reserved"), **Reservations today** (`7` — "3 still upcoming").
   - **Manager/Admin** additionally see: **Today's sales** (`4,210` — "118 orders · avg 35.68"), **Low stock** (`2` — "items below reorder"), **Staff on shift** (`6` — "2 cashiers · 4 floor").
   - Card accent/tint per card (see tokens); values are placeholders — see *Data sources* below.
3. **Quick actions** — eyebrow "QUICK ACTIONS" + a `FlowPane` of pill buttons (`.qa`), 46px tall, radius 11, 14px/600, each with an 8px colored square dot. First ("New order") is **primary** (navy fill, cream text, shadow); rest are white with 1px `#E0D9CA` border.
   - **All roles:** New order → Orders/POS; Table floor → Tables; New reservation → Reservations.
   - **Manager/Admin** also: Add menu item → Menu (add form); Run report → Reports.
4. **Secondary panels** — an `HBox` (wraps on narrow) of two panels:
   - **Tables at a glance** (grows ~1.5) — `.panel` (white, 1px `#EAE3D6`, radius 18, padding 22, shadow). Header: h2 "Tables at a glance" + link "Open table floor" → Tables. A grid (min col 100px, gap 10) of 12 table tiles; each tile: bg `#FDFCF9`, 1px `#EEE7DA`, radius 12; top row = table id (14px/700) + a status dot; then status label (11.5px/600, in the status color) + a detail line (11px `#9AA2AE`). Below, a legend row (top border `#F0EBE0`) with the four statuses + colored dots.
     - Status → color: Free `#2E9E6B`, Occupied `#D0574E`, Reserved `#C8912E`, Cleaning `#6B7C93`. Details: Free→"Available", Cleaning→"Needs cleaning", Occupied→"Order #NNNN", Reserved→"Booked HH:MM". (12 sample tiles in the source.)
   - **Role side panel** (grows ~1) — content depends on role:
     - **Cashier:** "Upcoming reservations" + link "View all" → Reservations. Rows: avatar badge (hour, tint amber) + name + "Party of N · Table TX" + time tag. (Sample: K. Mahmoud 19:00, L. Sami 19:30, N. Wael 20:00.)
     - **Manager/Admin:** "Low stock alerts" + link "View inventory" → Inventory. Rows: `!` badge (amber tint) + item name + "On hand X · reorder Y" + "Low" tag. (Sample: Tomatoes, Olive oil.)
     - Row layout: `HBox` [badge, VBox(name+detail), tag], 12px padding, bottom hairline `#F0EBE0`.

## Status bar (`.statusbar`) — `HBox`, spacing 14, padding 0 22, 12px `#6B7280`
Left: green dot `#2E9E6B` + "Ready" · "·" · "Logged in 14:32" · "·" · "DB: connected". Spacer. Right: "v1.0".

## Interactions & state
- `role`: `CASHIER | MANAGER | ADMIN` — in production comes from the session (passed into the controller / view-model). The demo switcher sets it and rebuilds nav + cards + quick actions + side panel. **Remove the switcher for production.**
- `activeNav`: which nav item is highlighted (default Dashboard/home). Clicking a nav item either highlights (Dashboard) or routes to another screen (center swap).
- Hover states: nav items and cards get a subtle lift/tint on hover (`:hover` in `app.css`). Buttons darken slightly.
- Log out → Login screen.

## Data sources (when wiring real data — all illustrative now)
- Open orders → count `orders WHERE status='Open'`, split by `order_type` (Dine-in/Takeaway).
- Tables free / occupied / reserved → group `dining_table` by `status`.
- Reservations today → `reservation` for today's date; "upcoming" = `status='Booked'` with future time.
- Today's sales / order count / avg → Paid/Closed `orders` for today (sum totals; avg = total/count).
- Low stock → `stock` rows where `quantity_on_hand` ≤ reorder level; also feeds the Manager/Admin side panel.
- Staff on shift → `staff` (active) — role split is illustrative unless a shift/attendance concept exists in schema; if not, show total active staff only.
- Tables at a glance → `dining_table` (id + status), detail from linked open `orders` / `reservation`.

## FXML structure sketch
```xml
<BorderPane xmlns:fx="..." fx:controller="app.dashboard.DashboardController" styleClass="app-root">
  <top>
    <HBox styleClass="topbar" alignment="CENTER_LEFT" spacing="18">
      <Button styleClass="hamburger"> ... </Button>
      <HBox styleClass="brand-lockup" spacing="12"> ...GF tile + two labels... </HBox>
      <Region HBox.hgrow="ALWAYS"/>
      <HBox fx:id="roleSwitcher" styleClass="role-switcher"> ...demo only... </HBox>
      <Button styleClass="icon-btn" fx:id="notifBtn"/>
      <HBox styleClass="user-chip"> ...avatar + name/role + caret... </HBox>
      <Button styleClass="btn-outline" text="Log out" onAction="#onLogout"/>
    </HBox>
  </top>
  <left>
    <ScrollPane fitToWidth="true" styleClass="sidenav-scroll">
      <VBox fx:id="navContainer" styleClass="sidenav" spacing="22"/> <!-- sections built in controller -->
    </ScrollPane>
  </left>
  <center>
    <ScrollPane fitToWidth="true" styleClass="content-scroll">
      <VBox styleClass="content" spacing="0">
        <HBox styleClass="greeting"> ... </HBox>
        <FlowPane fx:id="statCards" styleClass="stat-grid" hgap="16" vgap="16"/>
        <VBox styleClass="qa-block"> <Label styleClass="eyebrow" text="Quick actions"/> <FlowPane fx:id="quickActions" hgap="12" vgap="12"/> </VBox>
        <HBox styleClass="panels" spacing="20">
          <VBox fx:id="tablesPanel" styleClass="panel" HBox.hgrow="ALWAYS"> ... </VBox>
          <VBox fx:id="sidePanel" styleClass="panel" HBox.hgrow="ALWAYS"> ... </VBox>
        </HBox>
      </VBox>
    </ScrollPane>
  </center>
  <bottom>
    <HBox styleClass="statusbar" alignment="CENTER_LEFT" spacing="14"> ... </HBox>
  </bottom>
</BorderPane>
```
Build the nav sections, stat cards, quick actions, and side panel **programmatically in the controller** from the role (they're data-driven lists) rather than hard-coding every node in FXML — mirrors the prototype and keeps role filtering in one place.
