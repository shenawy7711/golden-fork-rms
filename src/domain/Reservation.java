package domain;

import domain.enums.ReservationStatus;

import java.time.LocalDateTime;

/**
 * A table booking (table: reservation). No two active (Booked/Seated) reservations may overlap
 * on the same table (BR-27); at least one contact is required by the service layer (FR-24).
 */
public class Reservation {
    private int reservationId;
    private int tableId;
    private String customerName;
    private String contactPhone;   // nullable individually; one contact required (service rule)
    private String contactEmail;   // nullable individually; one contact required (service rule)
    private LocalDateTime reservationDatetime;
    private int durationMinutes;
    private int partySize;
    private ReservationStatus status;
    private int createdBy;

    public int getReservationId() { return reservationId; }
    public void setReservationId(int reservationId) { this.reservationId = reservationId; }

    public int getTableId() { return tableId; }
    public void setTableId(int tableId) { this.tableId = tableId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public LocalDateTime getReservationDatetime() { return reservationDatetime; }
    public void setReservationDatetime(LocalDateTime reservationDatetime) { this.reservationDatetime = reservationDatetime; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public int getPartySize() { return partySize; }
    public void setPartySize(int partySize) { this.partySize = partySize; }

    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }
}
