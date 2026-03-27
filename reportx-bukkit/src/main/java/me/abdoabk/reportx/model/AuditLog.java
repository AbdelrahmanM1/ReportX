package me.abdoabk.reportx.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class AuditLog {

    private long id;
    private UUID staffUuid;
    private String staffName;
    private String action;
    private LocalDateTime timestamp;

    public AuditLog() {}

    public AuditLog(UUID staffUuid, String staffName, String action) {
        this.staffUuid  = staffUuid;
        this.staffName  = staffName;
        this.action     = action;
        this.timestamp  = LocalDateTime.now();
    }

    public long getId()                           { return id; }
    public void setId(long id)                    { this.id = id; }
    public UUID getStaffUuid()                    { return staffUuid; }
    public void setStaffUuid(UUID staffUuid)      { this.staffUuid = staffUuid; }
    public String getStaffName()                  { return staffName; }
    public void setStaffName(String staffName)    { this.staffName = staffName; }
    public String getAction()                     { return action; }
    public void setAction(String action)          { this.action = action; }
    public LocalDateTime getTimestamp()           { return timestamp; }
    public void setTimestamp(LocalDateTime v)     { this.timestamp = v; }
}