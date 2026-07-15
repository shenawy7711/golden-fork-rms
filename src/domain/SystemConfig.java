package domain;

import java.time.LocalDateTime;

/** A reference/system-data key-value entry (table: system_config); administered via FR-31. */
public class SystemConfig {
    private int configId;
    private String configKey;
    private String configValue;
    private Integer updatedBy;   // nullable audit link to user_account
    private LocalDateTime updatedAt;

    public int getConfigId() { return configId; }
    public void setConfigId(int configId) { this.configId = configId; }

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }

    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }

    public Integer getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Integer updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
