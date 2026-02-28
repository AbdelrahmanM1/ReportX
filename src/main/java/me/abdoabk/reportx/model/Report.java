package me.abdoabk.reportx.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class Report {

    private long id;
    private UUID reporterUuid;
    private UUID accusedUuid;
    private String reason;
    private ReportStatus status;
    private UUID claimedBy;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private String world;
    private double x, y, z;
    private String verdict;
    private String chatSnapshot;
    private String inventorySnapshot;   // fixed: was "InventorySnapshot" (inconsistent casing)
    private String reporterIp;

    public Report() {}

    public Report(UUID reporterUuid, UUID accusedUuid, String reason,
                  String world, double x, double y, double z) {
        this.reporterUuid = reporterUuid;
        this.accusedUuid  = accusedUuid;
        this.reason       = reason;
        this.status       = ReportStatus.OPEN;
        this.world        = world;
        this.x = x; this.y = y; this.z = z;
        this.createdAt    = LocalDateTime.now();
    }

    // ── Getters / Setters ────────────────────────────────────────────

    public long getId()                          { return id; }
    public void setId(long id)                   { this.id = id; }

    public UUID getReporterUuid()                { return reporterUuid; }
    public void setReporterUuid(UUID v)          { this.reporterUuid = v; }

    public UUID getAccusedUuid()                 { return accusedUuid; }
    public void setAccusedUuid(UUID v)           { this.accusedUuid = v; }

    public String getReason()                    { return reason; }
    public void setReason(String v)              { this.reason = v; }

    public ReportStatus getStatus()              { return status; }
    public void setStatus(ReportStatus v)        { this.status = v; }

    public UUID getClaimedBy()                   { return claimedBy; }
    public void setClaimedBy(UUID v)             { this.claimedBy = v; }

    public LocalDateTime getCreatedAt()          { return createdAt; }
    public void setCreatedAt(LocalDateTime v)    { this.createdAt = v; }

    public LocalDateTime getResolvedAt()         { return resolvedAt; }
    public void setResolvedAt(LocalDateTime v)   { this.resolvedAt = v; }

    public String getWorld()                     { return world; }
    public void setWorld(String v)               { this.world = v; }

    public double getX()                         { return x; }
    public void setX(double v)                   { this.x = v; }

    public double getY()                         { return y; }
    public void setY(double v)                   { this.y = v; }

    public double getZ()                         { return z; }
    public void setZ(double v)                   { this.z = v; }

    public String getVerdict()                   { return verdict; }
    public void setVerdict(String v)             { this.verdict = v; }

    public String getChatSnapshot()              { return chatSnapshot; }
    public void setChatSnapshot(String v)        { this.chatSnapshot = v; }

    /** Fixed: was assigning field to itself (this.InventorySnapshot = InventorySnapshot). */
    public String getInventorySnapshot()         { return inventorySnapshot; }
    public void setInventorySnapshot(String v)   { this.inventorySnapshot = v; }

    public String getReporterIp()                { return reporterIp; }
    public void setReporterIp(String v)          { this.reporterIp = v; }

    @Override
    public String toString() {
        return "Report{id=" + id + ", accused=" + accusedUuid + ", status=" + status + "}";
    }
}
