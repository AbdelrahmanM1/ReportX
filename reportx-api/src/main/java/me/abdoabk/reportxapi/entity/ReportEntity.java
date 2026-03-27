package me.abdoabk.reportxapi.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "reports")
public class ReportEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_uuid")  private String reporterUuid;
    @Column(name = "reporter_name")  private String reporterName;
    @Column(name = "accused_uuid")   private String accusedUuid;
    @Column(name = "accused_name")   private String accusedName;
    @Column(name = "reason")         private String reason;
    @Column(name = "status")         private String status;

    @Column(name = "claimed_by_uuid") private String claimedByUuid;
    @Column(name = "claimed_by_name") private String claimedByName;

    @Column(name = "created_at")     private LocalDateTime createdAt;
    @Column(name = "resolved_at")    private LocalDateTime resolvedAt;
    @Column(name = "verdict")        private String verdict;
    @Column(name = "world")          private String world;
    @Column(name = "x")              private Double x;
    @Column(name = "y")              private Double y;
    @Column(name = "z")              private Double z;
    @Column(name = "chat_snapshot",      columnDefinition = "TEXT") private String chatSnapshot;
    @Column(name = "inventory_snapshot", columnDefinition = "TEXT") private String inventorySnapshot;
    @Column(name = "reporter_ip")    private String reporterIp;

    /** Set to true after a dashboard status change so the plugin can notify in-game. */
    @Column(name = "notify_minecraft", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean notifyMinecraft = false;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "report_id")
    private List<NoteEntity> notes;

    public Long getId()                          { return id; }
    public String getReporterUuid()              { return reporterUuid; }
    public String getReporterName()              { return reporterName; }
    public String getAccusedUuid()               { return accusedUuid; }
    public String getAccusedName()               { return accusedName; }
    public String getReason()                    { return reason; }
    public String getStatus()                    { return status; }
    public void   setStatus(String s)            { this.status = s; }
    public String getClaimedByUuid()             { return claimedByUuid; }
    public void   setClaimedByUuid(String s)     { this.claimedByUuid = s; }
    public String getClaimedByName()             { return claimedByName; }
    public void   setClaimedByName(String s)     { this.claimedByName = s; }
    public LocalDateTime getCreatedAt()          { return createdAt; }
    public LocalDateTime getResolvedAt()         { return resolvedAt; }
    public void   setResolvedAt(LocalDateTime dt){ this.resolvedAt = dt; }
    public String getVerdict()                   { return verdict; }
    public void   setVerdict(String s)           { this.verdict = s; }
    public String getWorld()                     { return world; }
    public Double getX()                         { return x; }
    public Double getY()                         { return y; }
    public Double getZ()                         { return z; }
    public String getChatSnapshot()              { return chatSnapshot; }
    public String getInventorySnapshot()         { return inventorySnapshot; }
    public String getReporterIp()                { return reporterIp; }
    public List<NoteEntity> getNotes()           { return notes; }
    public boolean isNotifyMinecraft()           { return notifyMinecraft; }
    public void   setNotifyMinecraft(boolean v)  { this.notifyMinecraft = v; }
}
