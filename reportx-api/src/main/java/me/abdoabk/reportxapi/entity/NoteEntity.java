package me.abdoabk.reportxapi.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notes")
public class NoteEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id")   private Long reportId;
    @Column(name = "staff_uuid")  private String staffUuid;
    @Column(name = "staff_name")  private String staffName;
    @Column(name = "note", columnDefinition = "TEXT") private String note;
    @Column(name = "created_at")  private LocalDateTime createdAt;

    public NoteEntity() {}

    public NoteEntity(Long reportId, String staffUuid, String staffName, String note) {
        this.reportId   = reportId;
        this.staffUuid  = staffUuid;
        this.staffName  = staffName;
        this.note       = note;
        this.createdAt  = LocalDateTime.now();
    }

    public Long getId()                 { return id; }
    public Long getReportId()           { return reportId; }
    public String getStaffUuid()        { return staffUuid; }
    public String getStaffName()        { return staffName; }
    public String getNote()             { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}