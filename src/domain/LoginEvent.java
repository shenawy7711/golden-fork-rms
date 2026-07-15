package domain;

import domain.enums.LoginEventType;

import java.time.LocalDateTime;

/** A session audit entry (table: login_event); source for the staff-activity report (FR-29). */
public class LoginEvent {
    private long eventId;
    private int userId;
    private LoginEventType eventType;
    private LocalDateTime eventTime;

    public long getEventId() { return eventId; }
    public void setEventId(long eventId) { this.eventId = eventId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public LoginEventType getEventType() { return eventType; }
    public void setEventType(LoginEventType eventType) { this.eventType = eventType; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
}
