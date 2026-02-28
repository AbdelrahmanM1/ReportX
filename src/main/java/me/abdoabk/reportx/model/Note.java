package me.abdoabk.reportx.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class Note {

    private long id;
    private long reportId;
    private UUID staffUuid;
    private String note;
    private LocalDateTime createdAt;

    public Note() {}

    public Note(long reportId, UUID staffUuid, String note) {
        this.reportId = reportId;
        this.staffUuid = staffUuid;
        this.note = note;
        this.createdAt = LocalDateTime.now();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getReportId() { return reportId; }
    public void setReportId(long reportId) { this.reportId = reportId; }

    public UUID getStaffUuid() { return staffUuid; }
    public void setStaffUuid(UUID staffUuid) { this.staffUuid = staffUuid; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
