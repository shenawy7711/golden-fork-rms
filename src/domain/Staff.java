package domain;

import domain.enums.Status;

/** An employee record (table: staff). Distinct from a login account; user_id is optional (BR-26). */
public class Staff {
    private int staffId;
    private String fullName;
    private String position;
    private String phone;
    private String email;
    private Status status;
    private Integer userId;   // nullable link to a user_account

    public int getStaffId() { return staffId; }
    public void setStaffId(int staffId) { this.staffId = staffId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
}
