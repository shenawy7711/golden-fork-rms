package domain;

import domain.enums.RoleName;

/** An RBAC role (table: role). */
public class Role {
    private int roleId;
    private RoleName roleName;

    public int getRoleId() { return roleId; }
    public void setRoleId(int roleId) { this.roleId = roleId; }

    public RoleName getRoleName() { return roleName; }
    public void setRoleName(RoleName roleName) { this.roleName = roleName; }
}
