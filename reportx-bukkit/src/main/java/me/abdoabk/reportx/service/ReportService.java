package me.abdoabk.reportx.service;

import me.abdoabk.reportx.ReportXPlugin;
import me.abdoabk.reportx.model.AuditLog;
import me.abdoabk.reportx.model.Note;
import me.abdoabk.reportx.model.Report;
import me.abdoabk.reportx.model.ReportStatus;
import me.abdoabk.reportx.repository.ReportRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ReportService {

    private final ReportXPlugin plugin;
    private final ReportRepository repository;
    private final EvidenceService evidenceService;
    private final NotificationService notificationService;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public ReportService(ReportXPlugin plugin, ReportRepository repository,
                         EvidenceService evidenceService,
                         NotificationService notificationService) {
        this.plugin = plugin;
        this.repository = repository;
        this.evidenceService = evidenceService;
        this.notificationService = notificationService;
    }

    public enum SubmitResult {
        SUCCESS, SELF_REPORT, PLAYER_OFFLINE, ON_COOLDOWN,
        MAX_DAILY, DUPLICATE, REASON_TOO_SHORT, REASON_TOO_LONG
    }

    public enum StatusChangeResult {
        SUCCESS, REPORT_NOT_FOUND, SAME_STATUS, NO_PERMISSION
    }

    public CompletableFuture<SubmitResult> submitReport(Player reporter, String accusedName, String reason) {
        if (reporter.getName().equalsIgnoreCase(accusedName))
            return CompletableFuture.completedFuture(SubmitResult.SELF_REPORT);

        int minLen = plugin.getConfig().getInt("report.min-reason-length", 5);
        int maxLen = plugin.getConfig().getInt("report.max-reason-length", 200);
        if (reason.length() < minLen) return CompletableFuture.completedFuture(SubmitResult.REASON_TOO_SHORT);
        if (reason.length() > maxLen) return CompletableFuture.completedFuture(SubmitResult.REASON_TOO_LONG);

        if (!reporter.hasPermission("reportx.bypass.cooldown")) {
            int cooldownSec = plugin.getConfig().getInt("report.cooldown-seconds", 120);
            Long last = cooldowns.get(reporter.getUniqueId());
            if (last != null && (System.currentTimeMillis() - last) / 1000 < cooldownSec)
                return CompletableFuture.completedFuture(SubmitResult.ON_COOLDOWN);
        }

        final String sanitizedReason = plugin.getConfig().getBoolean("security.sanitize-input", true)
                ? reason.replaceAll("['\";\\\\ <>]", "").trim()
                : reason;

        boolean allowOffline = plugin.getConfig().getBoolean("report.allow-offline", false);
        Player accusedOnline = Bukkit.getPlayer(accusedName);
        OfflinePlayer accused;

        if (accusedOnline != null) {
            accused = accusedOnline;
        } else {
            if (!allowOffline) return CompletableFuture.completedFuture(SubmitResult.PLAYER_OFFLINE);
            OfflinePlayer op = Bukkit.getOfflinePlayer(accusedName);
            if (!op.hasPlayedBefore()) return CompletableFuture.completedFuture(SubmitResult.PLAYER_OFFLINE);
            accused = op;
        }

        final OfflinePlayer finalAccused = accused;
        Location loc = reporter.getLocation();
        final String capturedWorld     = loc.getWorld() != null ? loc.getWorld().getName() : "unknown";
        final double rx = loc.getX(), ry = loc.getY(), rz = loc.getZ();
        final String chatSnapshot      = evidenceService.getChatSnapshot(finalAccused.getUniqueId());
        final String inventorySnapshot = evidenceService.getInventorySnapshot(finalAccused.getUniqueId());
        final String reporterIp = (!plugin.getConfig().getBoolean("security.gdpr-mode", false)
                && reporter.getAddress() != null)
                ? reporter.getAddress().getAddress().getHostAddress() : null;
        final String reporterName = reporter.getName(); // capture on main thread

        return repository.countByReporterToday(reporter.getUniqueId()).thenCompose(todayCount -> {
            int maxPerDay = plugin.getConfig().getInt("report.max-per-day", 5);
            if (todayCount >= maxPerDay)
                return CompletableFuture.completedFuture(SubmitResult.MAX_DAILY);

            return repository.hasDuplicateOpenReport(reporter.getUniqueId(), finalAccused.getUniqueId())
                    .thenCompose(dup -> {
                        if (dup) return CompletableFuture.completedFuture(SubmitResult.DUPLICATE);

                        Report report = new Report(
                                reporter.getUniqueId(), reporterName,
                                finalAccused.getUniqueId(), finalAccused.getName(),
                                sanitizedReason, capturedWorld, rx, ry, rz
                        );
                        report.setChatSnapshot(chatSnapshot);
                        report.setInventorySnapshot(inventorySnapshot);
                        report.setReporterIp(reporterIp);

                        return repository.saveReport(report).thenApply(id -> {
                            report.setId(id);
                            cooldowns.put(reporter.getUniqueId(), System.currentTimeMillis());
                            // Pass reporterName so audit_log.staff_name is saved correctly
                            repository.saveAuditLog(new AuditLog(
                                    reporter.getUniqueId(), reporterName,
                                    "SUBMITTED_REPORT #" + id + " against " + finalAccused.getName()
                            ));
                            String accusedDisplay = finalAccused.getName() != null
                                    ? finalAccused.getName() : accusedName;
                            notificationService.notifyNewReport(report, reporterName, accusedDisplay);
                            return SubmitResult.SUCCESS;
                        });
                    });
        });
    }

    public long getCooldownRemaining(UUID playerUuid) {
        int cooldownSec = plugin.getConfig().getInt("report.cooldown-seconds", 120);
        Long last = cooldowns.get(playerUuid);
        if (last == null) return 0;
        long elapsed = (System.currentTimeMillis() - last) / 1000;
        return Math.max(0, cooldownSec - elapsed);
    }

    public CompletableFuture<Boolean> claimReport(long reportId, Player staff) {
        return repository.findById(reportId).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(false);
            Report r = opt.get();
            if (r.getStatus() != ReportStatus.OPEN) return CompletableFuture.completedFuture(false);
            r.setStatus(ReportStatus.CLAIMED);
            r.setClaimedBy(staff.getUniqueId());
            r.setClaimedByName(staff.getName());
            return repository.updateReport(r).thenApply(ok -> {
                if (ok) {
                    repository.saveAuditLog(new AuditLog(staff.getUniqueId(), staff.getName(), "CLAIMED_REPORT #" + reportId));
                    notificationService.notifyClaimed(r, staff.getName());
                }
                return ok;
            });
        });
    }

    public CompletableFuture<Boolean> closeReport(long reportId, Player staff, String verdict) {
        return repository.findById(reportId).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(false);
            Report r = opt.get();
            boolean requireClaim = plugin.getConfig().getBoolean("staff.require-claim-to-close", true);
            boolean canOverride  = staff.hasPermission("reportx.override.claim");
            if (requireClaim && r.getStatus() == ReportStatus.OPEN && !canOverride)
                return CompletableFuture.completedFuture(false);
            if (r.getClaimedBy() != null && !r.getClaimedBy().equals(staff.getUniqueId()) && !canOverride)
                return CompletableFuture.completedFuture(false);
            r.setStatus(ReportStatus.RESOLVED);
            r.setVerdict(verdict);
            r.setResolvedAt(LocalDateTime.now());
            return repository.updateReport(r).thenApply(ok -> {
                if (ok) {
                    repository.saveAuditLog(new AuditLog(staff.getUniqueId(), staff.getName(),
                            "CLOSED_REPORT #" + reportId + " verdict=" + verdict));
                    notificationService.notifyResolved(r, staff.getName());
                }
                return ok;
            });
        });
    }

    public CompletableFuture<Boolean> rejectReport(long reportId, Player staff) {
        return repository.findById(reportId).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(false);
            Report r = opt.get();
            r.setStatus(ReportStatus.REJECTED);
            r.setResolvedAt(LocalDateTime.now());
            return repository.updateReport(r).thenApply(ok -> {
                if (ok) repository.saveAuditLog(new AuditLog(staff.getUniqueId(), staff.getName(), "REJECTED_REPORT #" + reportId));
                return ok;
            });
        });
    }

    public CompletableFuture<Boolean> escalateReport(long reportId, Player staff) {
        return repository.findById(reportId).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(false);
            Report r = opt.get();
            r.setStatus(ReportStatus.ESCALATED);
            return repository.updateReport(r).thenApply(ok -> {
                if (ok) {
                    repository.saveAuditLog(new AuditLog(staff.getUniqueId(), staff.getName(), "ESCALATED_REPORT #" + reportId));
                    notificationService.notifyEscalated(r, staff.getName());
                }
                return ok;
            });
        });
    }

    public CompletableFuture<StatusChangeResult> changeStatus(long reportId, ReportStatus newStatus, Player actor) {
        if (!actor.hasPermission("reportx.staff"))
            return CompletableFuture.completedFuture(StatusChangeResult.NO_PERMISSION);

        return repository.findById(reportId).thenCompose(opt -> {
            if (opt.isEmpty())
                return CompletableFuture.completedFuture(StatusChangeResult.REPORT_NOT_FOUND);
            Report r = opt.get();
            if (r.getStatus() == newStatus)
                return CompletableFuture.completedFuture(StatusChangeResult.SAME_STATUS);

            r.setStatus(newStatus);
            if (newStatus == ReportStatus.CLAIMED) {
                r.setClaimedBy(actor.getUniqueId());
                r.setClaimedByName(actor.getName());
            }
            if (newStatus == ReportStatus.RESOLVED || newStatus == ReportStatus.REJECTED)
                r.setResolvedAt(LocalDateTime.now());

            return repository.updateReport(r).thenApply(ok -> {
                if (ok) repository.saveAuditLog(new AuditLog(actor.getUniqueId(), actor.getName(),
                        "STATUS_CHANGE #" + reportId + " -> " + newStatus.name()));
                return ok ? StatusChangeResult.SUCCESS : StatusChangeResult.REPORT_NOT_FOUND;
            });
        });
    }

    public CompletableFuture<Boolean> addNote(long reportId, Player staff, String noteText) {
        return repository.findById(reportId).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(false);
            Note note = new Note(reportId, staff.getUniqueId(), staff.getName(), noteText);
            return repository.saveNote(note).thenApply(id -> {
                if (id > 0) {
                    repository.saveAuditLog(new AuditLog(staff.getUniqueId(), staff.getName(), "ADDED_NOTE to #" + reportId));
                    return true;
                }
                return false;
            });
        });
    }

    public ReportRepository getRepository()      { return repository; }
    public EvidenceService getEvidenceService()  { return evidenceService; }
}