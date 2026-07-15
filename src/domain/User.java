package domain;

import domain.enums.RoleName;
import domain.enums.Status;

import java.time.LocalDateTime;

/** A login identity (table: user_account). Password is only ever a salted hash (BR-02). */
public class User {
    private int userId;
    private String username;
    private String passwordHash;
    private String fullName;
    private int roleId;
    private RoleName role;      // resolved from role_id for RBAC convenience
    private Status status;
    private LocalDateTime createdAt;

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public int getRoleId() { return roleId; }
    public void setRoleId(int roleId) { this.roleId = roleId; }

    public RoleName getRole() { return role; }
    public void setRole(RoleName role) { this.role = role; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
