# 01 — Auth screen (Login / Sign-up)

Reference: `screenshots/auth-login.png`, `screenshots/auth-signup.png` · source: `source/Golden Fork Auth.dc.html`

## Purpose
A single centered card lets a user **Login** or **Sign up**. A segmented toggle switches modes. Sign-up adds an Email field and a role picker (Cashier / Manager / Administrator); picking Manager or Administrator shows an amber note that the account will be created **Inactive** pending admin activation (schema: `user_account.status`).

## Overall layout
- **Scene:** whole window is the auth surface. Background: a warm cream radial gradient (`radial-gradient(120% 120% at 15% 0%, #F5F1E9, #EAE4D8 60%, #E3DBCC)`). In JavaFX, a `StackPane` root with `-fx-background-color: radial-gradient(focus-distance 0% , center 15% 0%, radius 120%, #F5F1E9, #EAE4D8 60%, #E3DBCC);` (tune to taste) or a simple `#EAE4D8` fill if the gradient is fussy.
- **Card:** centered, `max-width 980px`, white, radius **22px**, big soft drop shadow. Two columns side by side (`HBox`), each flex ~50%, min-height 560px.
  - JavaFX: `HBox` inside a centered container; left = brand `VBox`, right = form `VBox`. Give the card a `.auth-card` style class (white bg, radius 22, shadow). On narrow windows the HTML wraps to stacked — for a fixed desktop app you can keep it side-by-side and set a sensible min window size (e.g. 980×640).

### Left — brand panel (`.brand-panel`)
- Background: linear gradient `160deg, #1F3050 → #152238`. Text color cream `#F5F1E9`. Padding 48/44px. `VBox` with `spaceBetween` distribution (top group, middle group, bottom group) → use `VBox` + spacer `Region`s with `VBox.vgrow=ALWAYS`.
- Two faint decorative rings (thin gold-tinted circle outlines, top-right and bottom-left). Optional — reproduce with `Circle` (no fill, stroke `rgba(200,162,74,0.15)`, 1px) positioned in a `Pane`/`StackPane` behind content, or omit. Not load-bearing.
- **Top:** logo tile (52×52, radius 14, gold gradient `150deg #D8B45C → #B8863B`) with serif "GF" 26px in navy `#152238`; beside it the eyebrow `RESTAURANT MANAGEMENT SYSTEM` (11px, 0.22em tracking, uppercase, gold `#C8A24A`, two lines).
- **Middle:** wordmark **Golden Fork** — serif, 52px, 600, cream. Below: paragraph 15.5px, `rgba(245,241,233,0.72)`, max-width 300px: "Front of house to back of house — orders, tables, menu and stock, all in one place."
- **Bottom:** a green dot (`#4CAF7D`, 7px, with soft ring) + "Secure sign-in" + "·" + "v1.0" — 12px, `rgba(245,241,233,0.5)`.

### Right — form panel (`.form-panel`)
Padding 48/48/40px. `VBox`, spacing per field.

1. **Segmented toggle** — track `HBox`, bg `#F1EDE4`, radius 12, padding 5px, two equal buttons.
   - Use a `ToggleGroup` with two `ToggleButton`s ("Login", "Sign Up"), styled so the **selected** one is white with a soft shadow and ink text (`#1B2A41`), the unselected is transparent with muted text (`#8A93A2`). 40px tall, radius 9, weight 600, 14px.
2. **Heading (h1)** — serif, 34px, 600, ink. Text: **"Welcome back"** (login) / **"Create your account"** (sign-up).
3. **Subheading** — 14.5px, `#6B7280`. "Sign in to manage your restaurant." / "Set up your access to Golden Fork."
4. **Form fields** (`VBox`, 18px gap):
   - **Username** — always visible. Label "Username" (12.5px/600, `#42526B`) above a `TextField`, placeholder `e.g. a.ali`.
   - **Email** — **sign-up only** (hidden in login; `setVisible(false)` + `setManaged(false)`). Label "Email", `TextField`, placeholder `you@goldenfork.eg`.
   - **Role picker** — **sign-up only**. Label "Sign up as", then an `HBox` of three equal `ToggleButton`s in a `ToggleGroup`: **Cashier / Manager / Administrator**. Selected = navy `#1F3050` fill, cream text, 1.5px navy border; unselected = `#FBFAF7` fill, `#42526B` text, 1.5px `#DCD8CE` border. 40px tall, radius 10, 13px/600.
     - Below it, an **approval note** shown only when role = Manager or Administrator: an amber panel (`HBox`, bg `#FBF3DF`, 1px border `#EAD9A6`, radius 10, padding 11/13) with a round `!` badge (18px, bg `#C8912E`, white) + text 12.5px `#7A5E1C`:
       - Manager: "New manager accounts are created Inactive and must be activated by an administrator before first sign-in."
       - Administrator: "New administrator accounts are created Inactive and must be activated by an existing administrator before first sign-in."
   - **Password** — always visible. Label "Password". A `TextField`/`PasswordField` pair (see behavior) with a trailing **Show/Hide** text button (gold `#9A742B`, 12.5px/600) overlaid at the right — use a `StackPane` with `StackPane.alignment=CENTER_RIGHT` for the button, or an `HBox` field with a small button. Placeholder `••••••••`.
     - Sign-up only: hint below "Use at least 8 characters." (12px, `#8A93A2`).
5. **Options row** — **login only**: left a "Remember me" `CheckBox` (accent navy), right a "Forgot password?" link (13px/600 gold). Space-between `HBox`.
6. **Primary button** — full width, 52px tall, radius 12, bg navy `#1F3050`, cream text 15px/600, soft shadow. Label **"Login"** (login) / **"Sign Up"** (cashier sign-up) / **"Create account"** (manager/admin sign-up). Hover → `#26385C` + larger shadow; press → nudge down 1px.

## Field styling (`.field`)
- `TextField`/`PasswordField`: 48px tall, padding 0 14px, radius 10, border 1.5px `#DCD8CE`, bg `#FBFAF7`, text 15px `#1B2A41`.
- **Focus:** border → `#1F3050`, bg → white, focus ring `rgba(31,48,80,0.10)` (JavaFX: `-fx-effect` inner glow or a `-fx-background-insets`/`-fx-border` swap; a simple border-color change on `:focused` is acceptable).

## Interactions & state
State variables (controller / view-model):
- `mode`: `LOGIN | SIGNUP` (default LOGIN) — driven by the toggle group.
- `role`: `CASHIER | MANAGER | ADMIN` (default CASHIER) — sign-up role picker.
- `showPassword`: boolean (default false) — Show/Hide toggle.

Behavior:
- **Toggle mode** → update heading/subheading/primary label; show/hide Email, role picker, password hint, options row. Toggle both `visible` and `managed` so layout collapses cleanly.
- **Role change** → show the approval note only for Manager/Admin, with the matching text; primary label becomes "Create account" for those roles, else "Sign Up".
- **Show/Hide password** → swap between masked and visible. JavaFX 8 has no "unmask" on `PasswordField`; the standard pattern is to keep a `PasswordField` **and** a `TextField` bound to the same `StringProperty`, and toggle which one is visible/managed. Button label flips "Show" ⇄ "Hide".
- **Submit:**
  - *Login:* validate username + password present → authenticate against `user_account` (verify salted hash; reject `status = Inactive`) → on success route to Dashboard with the user's role. In the prototype it simply navigates to the Dashboard.
  - *Sign-up (Cashier):* create `user_account` with `status = Active` → proceed.
  - *Sign-up (Manager/Admin):* create `user_account` with `status = Inactive` → show a confirmation ("Account created — an administrator must activate it before you can sign in"); do **not** route into the app.
- Basic client validation: required fields; password ≥ 8 chars on sign-up; email format on sign-up. Show inline errors (red border + small message) — not shown in the prototype but expected.

## FXML structure sketch
```xml
<StackPane styleClass="auth-bg" xmlns:fx="..." fx:controller="app.auth.AuthController">
  <HBox styleClass="auth-card" maxWidth="980" alignment="CENTER">
    <!-- LEFT -->
    <VBox styleClass="brand-panel" HBox.hgrow="ALWAYS" minWidth="300">
      <HBox styleClass="brand-top" spacing="14"> ...logo + eyebrow... </HBox>
      <Region VBox.vgrow="ALWAYS"/>
      <VBox styleClass="brand-mid"> <Label styleClass="wordmark,heading" text="Golden Fork"/> <Label .../> </VBox>
      <Region VBox.vgrow="ALWAYS"/>
      <HBox styleClass="brand-foot" spacing="10"> ... </HBox>
    </VBox>
    <!-- RIGHT -->
    <VBox styleClass="form-panel" HBox.hgrow="ALWAYS" minWidth="320" spacing="0">
      <HBox styleClass="seg-track">
        <ToggleButton fx:id="loginTab" toggleGroup="$modeGroup" text="Login" HBox.hgrow="ALWAYS"/>
        <ToggleButton fx:id="signupTab" toggleGroup="$modeGroup" text="Sign Up" HBox.hgrow="ALWAYS"/>
      </HBox>
      <Label fx:id="heading" styleClass="h1,heading"/>
      <Label fx:id="subheading" styleClass="sub"/>
      <VBox spacing="18" styleClass="form-fields">
        <VBox styleClass="field-group"> <Label text="Username"/> <TextField fx:id="username"/> </VBox>
        <VBox fx:id="emailGroup" styleClass="field-group"> <Label text="Email"/> <TextField fx:id="email"/> </VBox>
        <VBox fx:id="roleGroup" styleClass="field-group">
          <Label text="Sign up as"/>
          <HBox spacing="8">
            <ToggleButton fx:id="roleCashier" toggleGroup="$roleGroup" text="Cashier" HBox.hgrow="ALWAYS"/>
            <ToggleButton fx:id="roleManager" toggleGroup="$roleGroup" text="Manager" HBox.hgrow="ALWAYS"/>
            <ToggleButton fx:id="roleAdmin"   toggleGroup="$roleGroup" text="Administrator" HBox.hgrow="ALWAYS"/>
          </HBox>
          <HBox fx:id="approvalNote" styleClass="approval-note"> <Label styleClass="badge" text="!"/> <Label fx:id="approvalText" wrapText="true"/> </HBox>
        </VBox>
        <VBox styleClass="field-group">
          <Label text="Password"/>
          <StackPane>
            <PasswordField fx:id="password"/>
            <TextField fx:id="passwordPlain" managed="false" visible="false"/>
            <Button fx:id="pwToggle" styleClass="pw-toggle" text="Show" StackPane.alignment="CENTER_RIGHT"/>
          </StackPane>
          <Label fx:id="pwHint" styleClass="hint" text="Use at least 8 characters."/>
        </VBox>
        <HBox fx:id="optionsRow" styleClass="options-row">
          <CheckBox fx:id="remember" text="Remember me"/>
          <Region HBox.hgrow="ALWAYS"/>
          <Hyperlink text="Forgot password?"/>
        </HBox>
        <Button fx:id="primaryBtn" styleClass="btn-primary" maxWidth="Infinity"/>
      </VBox>
    </VBox>
  </HBox>
</StackPane>
```
Define `modeGroup` and `roleGroup` `ToggleGroup`s in `<fx:define>`. Controller listens to both groups and updates labels/visibility as described.
