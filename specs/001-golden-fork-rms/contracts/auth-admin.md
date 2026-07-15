# Contract — Authentication & Administration (FR-01…FR-04, FR-31)

## AuthService

### `login(username, password) → Session`
- **Permission**: — (pre-auth). **FR-01; BR-01, BR-02.**
- **Validates**: username present (3–50); password present. Compares against stored salted hash
  via `PasswordHasher.verify` — never logs/echoes plain text.
- **Rules**: refuse `status='Inactive'` accounts even with correct password; after
  `login_max_attempts` (default 5) consecutive failures for a username, temporarily throttle; on
  success bind a `Session` to the user + role and write a `LOGIN` `login_event`.
- **Returns / errors**: valid+active → `Session`. Any bad credential/inactive →
  `AuthorizationException` with generic *"Invalid username or password"* (no enumeration, no
  session). DB unreachable → `PersistenceException` → *"Unable to sign in — service
  unavailable"*.

### `logout(session) → void`
- **Permission**: — (own session). **FR-04; BR-07.**
- **Rules**: invalidate session, write `LOGOUT` event. If the session holds an unsaved **Open**
  order → `ConflictException` requiring finalise/park/discard first. Optional idle timeout
  (default 15 min) triggers auto-logout.

### `RbacGuard.require(session, permission) → void`
- **FR-02; BR-03.** Business-layer check performed before every protected operation. Throws
  `AuthorizationException` (and records the attempt) if the session's role lacks the permission.
  Called internally by all services below — bypassing the UI cannot bypass it.

## UserService  *(all methods: Permission = `MANAGE_USERS`, Administrator only)*

### `create(user, initialPassword) → User`
- **FR-03; BR-02, BR-04.** Unique username (else `ConflictException` *"Username already
  exists."*); full name 2–100; role required; password meets policy and is stored hashed;
  default `status='Active'`. New active account can log in immediately.

### `update(user) → User`
- **FR-03.** Edit full name/role/status; role change takes effect next login.

### `deactivate(userId) → void` / `delete(userId) → void`
- **FR-03; BR-05, BR-06.** `deactivate` blocks login but preserves history. `delete` allowed
  **only** when no dependent historical rows exist, else must deactivate. Removing/deactivating
  the **last active Administrator** → `ValidationException` (BR-06 — never zero admins).

**Tables**: `user_account`, `role`, `login_event`.

## SystemConfigService  *(all methods: Permission = `CONFIGURE_SYSTEM`, Administrator only)*

### `getAll() → Map<String,String>` / `get(key) → String`
- **FR-31; BR-03.** Reads reference/system data (`tax_rate`, payment methods,
  `idle_timeout_min`, `login_max_attempts`, `reservation_slot_minutes`,
  `discount_approval_threshold`). `RbacGuard.require(CONFIGURE_SYSTEM)` first — a
  non-Administrator caller → `AuthorizationException`, even if the UI were bypassed.

### `update(key, value, session) → void`
- **FR-31; BR-31, BR-03, BR-09, BR-18.** Validates per Appendix A (tax rate 0 ≤ r ≤ 1;
  timeouts/attempts/slot as bounded integers; threshold ≥ 0) → `ValidationException` on breach.
  Persists the new value and stamps `updated_by = session.user` and `updated_at = now` for audit.
  The tax rate takes effect on **future** finalisations only; each order snapshots the rate in
  force (BR-18), so a change never alters a finalised bill (BR-09).

### `listPaymentMethods() → List<PaymentMethod>` / `setPaymentMethodActive(id, active) → void`
- **FR-31; BR-03.** Maintains the payment-method reference list; a method referenced by an
  existing payment is deactivated, never hard-deleted. At least one active method must remain for
  finalisation (FR-15 dependency).

**Dependency**: a `tax_rate` and ≥ 1 active payment method must be configured before any order
can be finalised (FR-14/FR-15).

**Tables**: `system_config`, `payment_method`.
