package me.abdoabk.reportxapi.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_uuid")  private String staffUuid;
    @Column(name = "staff_name")  private String staffName;
    @Column(name = "action", columnDefinition = "TEXT") private String action;
    @Column(name = "timestamp")   private LocalDateTime timestamp;

    public Long getId()                 { return id; }
    public String getStaffUuid()        { return staffUuid; }
    public String getStaffName()        { return staffName; }
    public String getAction()           { return action; }
    public LocalDateTime getTimestamp() { return timestamp; }

    public void setStaffUuid(String s)  { this.staffUuid = s; }
    public void setStaffName(String s)  { this.staffName = s; }
    public void setAction(String s)     { this.action = s; }
    public void setTimestamp(LocalDateTime dt) { this.timestamp = dt; }
}