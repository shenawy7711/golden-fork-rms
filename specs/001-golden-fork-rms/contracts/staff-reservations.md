# Contract — Staff & Reservations (FR-23…FR-26)

## StaffService

### `saveStaff(staff) → Staff`
- **Permission**: `MANAGE_STAFF` (Manager/Admin). **FR-23; BR-26.**
- Name 2–100; position required; phone/email format-validated. Staff records are **distinct**
  from `user_account`; a staff record may optionally link to a login (`user_id`).

### `deactivateStaff(staffId) → void`
- **Permission**: `MANAGE_STAFF`. **FR-23; BR-05, BR-26.** A staff record linked to historical
  activity is deactivated (hidden from active lists), not deleted; history is retained.

### `listActiveStaff() → List<Staff>`
- **Permission**: `MANAGE_STAFF`. Roster read model.

## ReservationService

### `create(reservation) → Reservation`
- **Permission**: `MANAGE_RESERVATION` (all roles). **FR-24, FR-26; BR-27, BR-28.**
- Customer name 2–100; **at least one** contact (phone/email) required and format-valid;
  date-time in the **future**; party size ≥ 1; table must exist. Party > table capacity →
  warning/override (`ValidationException` unless override flag). Calls `hasOverlap`; overlap →
  `ConflictException`. On success: status Booked, sets the table Reserved for the window.

### `hasOverlap(tableId, window, excludeReservationId?) → boolean`
- **FR-26; BR-27.** True if any reservation on the same table with status in (Booked, Seated),
  excluding self, whose interval overlaps `window` (TDD §5.3:
  `r.start < req.end AND r.start + r.duration > req.start`). Cancelled/Completed never block.

### `seat(id) / complete(id) / cancel(id) / markNoShow(id) → Reservation`
- **Permission**: `MANAGE_RESERVATION`. **FR-25; BR-29.** State machine
  `Booked→Seated→Completed`, `Booked|Seated→Cancelled`, `Booked→No-Show`. Seat sets the table
  Occupied (an order may then open); Complete/Cancel frees the reserved hold. Illegal transition
  → `ValidationException`.

**Tables**: `staff`, `reservation`, `dining_table`.
