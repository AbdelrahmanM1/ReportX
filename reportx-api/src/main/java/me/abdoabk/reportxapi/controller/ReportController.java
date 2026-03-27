package me.abdoabk.reportxapi.controller;

import jakarta.validation.Valid;
import me.abdoabk.reportxapi.dto.AddNoteRequest;
import me.abdoabk.reportxapi.dto.UpdateStatusRequest;
import me.abdoabk.reportxapi.entity.*;
import me.abdoabk.reportxapi.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ReportController {

    private static final Set<String> VALID_STATUSES =
            Set.of("OPEN", "CLAIMED", "RESOLVED", "REJECTED", "ESCALATED");

    private final ReportRepository      reportRepo;
    private final NoteRepository        noteRepo;
    private final AuditLogRepository    auditRepo;
    private final PlayerCacheRepository playerRepo;

    public ReportController(ReportRepository reportRepo, NoteRepository noteRepo,
                            AuditLogRepository auditRepo, PlayerCacheRepository playerRepo) {
        this.reportRepo = reportRepo;
        this.noteRepo   = noteRepo;
        this.auditRepo  = auditRepo;
        this.playerRepo = playerRepo;
    }

    @GetMapping("/reports")
    public ResponseEntity<?> getReports(
            @RequestParam(defaultValue = "1")  int    page,
            @RequestParam(defaultValue = "20") int    size,
            @RequestParam(required = false)    String status,
            @RequestParam(required = false)    String search) {

        PageRequest pageable = PageRequest.of(
                Math.max(0, page - 1), size, Sort.by("createdAt").descending());

        Page<ReportEntity> result;

        boolean hasStatus = status != null && !status.isBlank() && VALID_STATUSES.contains(status);
        boolean hasSearch = search != null && !search.isBlank();

        if (hasStatus && hasSearch) {
            result = reportRepo.findByStatusAndReasonContainingIgnoreCaseOrderByCreatedAtDesc(status, search, pageable);
        } else if (hasStatus) {
            result = reportRepo.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else if (hasSearch) {
            result = reportRepo.findByReasonContainingIgnoreCaseOrderByCreatedAtDesc(search, pageable);
        } else {
            result = reportRepo.findAllByOrderByCreatedAtDesc(pageable);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content",       result.getContent().stream().map(this::toLight).collect(Collectors.toList()));
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages",    result.getTotalPages());
        response.put("page",          page);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reports/stats")
    public ResponseEntity<?> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", reportRepo.count());

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String s : List.of("OPEN", "CLAIMED", "RESOLVED", "REJECTED", "ESCALATED")) {
            byStatus.put(s, reportRepo.countByStatus(s));
        }
        stats.put("byStatus", byStatus);
        stats.put("avgResolutionHours", reportRepo.avgResolutionHours());

        List<Map<String, Object>> topAccused = reportRepo
                .findTopAccused(PageRequest.of(0, 5))
                .stream().map(row -> {
                    String uuid = (String) row[0];
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("uuid",  uuid);
                    e.put("count", row[1]);
                    playerRepo.findById(uuid).ifPresent(p -> e.put("name", p.getLastKnownName()));
                    return e;
                }).collect(Collectors.toList());
        stats.put("topAccused", topAccused);

        stats.put("recentReports", reportRepo.findTop10ByOrderByCreatedAtDesc()
                .stream().map(this::toLight).collect(Collectors.toList()));

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/reports/{id}")
    public ResponseEntity<?> getReport(@PathVariable Long id) {
        return reportRepo.findById(id).map(r -> {
            Map<String, Object> map = toFull(r);
            map.put("notes", noteRepo.findByReportIdOrderByCreatedAtAsc(id)
                    .stream().map(this::noteToMap).collect(Collectors.toList()));
            return ResponseEntity.ok(map);
        }).orElse(ResponseEntity.notFound().build());
    }

@PatchMapping("/reports/{id}/status")
@SuppressWarnings("unchecked")
public ResponseEntity<?> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateStatusRequest req,
        @AuthenticationPrincipal Object principal) {

    if (!VALID_STATUSES.contains(req.status())) {
        return ResponseEntity.badRequest().body(
            Map.of("error", "Invalid status: " + req.status())
        );
    }

    return reportRepo.findById(id).map(r -> {

        String oldStatus = r.getStatus();

        if ("CLAIMED".equals(oldStatus) && principal != null) {

            Map<String, String> p = (Map<String, String>) principal;

            String actorRole = p.getOrDefault("role", "STAFF");
            String actorUuid = p.get("uuid");

            boolean isElevated =
                    "ADMIN".equals(actorRole) ||
                    "SENIOR_STAFF".equals(actorRole);

            boolean isClaimer =
                    actorUuid != null &&
                    actorUuid.equals(r.getClaimedByUuid());

            if (!isElevated && !isClaimer) {
                return ResponseEntity.status(403).body(
                    Map.of("error",
                        "Only the staff member who claimed this report (or an admin) can change its status.")
                );
            }
        }

        r.setStatus(req.status());

        // timestamps
        if ("RESOLVED".equals(req.status()) || "REJECTED".equals(req.status())) {
            r.setResolvedAt(LocalDateTime.now());
        }

        // claiming logic
        if ("CLAIMED".equals(req.status()) && principal != null) {
            Map<String, String> p = (Map<String, String>) principal;
            r.setClaimedByUuid(p.get("uuid"));
            r.setClaimedByName(p.get("username"));
        }

        // verdict
        if (req.verdict() != null && !req.verdict().isBlank()) {
            r.setVerdict(req.verdict());
        }

        // notify plugin if changed
        if (!req.status().equals(oldStatus)) {
            r.setNotifyMinecraft(true);
        }

        reportRepo.save(r);
        return ResponseEntity.ok(toFull(r));

    }).orElse(ResponseEntity.notFound().build());
}

    @PostMapping("/reports/{id}/notes")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> addNote(
            @PathVariable Long id,
            @Valid @RequestBody AddNoteRequest req,
            @AuthenticationPrincipal Object principal) {

        if (!reportRepo.existsById(id)) return ResponseEntity.notFound().build();

        String staffUuid = "dashboard";
        String staffName = "Dashboard";
        if (principal != null) {
            Map<String, String> p = (Map<String, String>) principal;
            staffUuid = p.get("uuid");
            staffName = p.get("username");
        }

        NoteEntity note = new NoteEntity(id, staffUuid, staffName, req.note());
        noteRepo.save(note);
        return ResponseEntity.ok(noteToMap(note));
    }

    // ── Plugin poll endpoint ──────────────────────────────────────────
    /**
     * The Minecraft plugin calls this endpoint every N seconds.
     * Returns all reports that need an in-game notification, then clears the flag.
     * Secured by a shared secret header (X-Plugin-Secret) checked in SecurityConfig.
     */
    @GetMapping("/internal/poll-notifications")
    public ResponseEntity<?> pollNotifications() {
        List<ReportEntity> pending = reportRepo.findByNotifyMinecraftTrue();

        if (pending.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        // Build payload and clear the flag atomically
        List<Map<String, Object>> payload = pending.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",           r.getId());
            m.put("status",       r.getStatus());
            m.put("reporterUuid", r.getReporterUuid());
            m.put("reporterName", r.getReporterName());
            m.put("accusedName",  r.getAccusedName());
            m.put("verdict",      r.getVerdict());
            m.put("claimedByName", r.getClaimedByName());
            return m;
        }).collect(Collectors.toList());

        pending.forEach(r -> r.setNotifyMinecraft(false));
        reportRepo.saveAll(pending);

        return ResponseEntity.ok(payload);
    }

    @GetMapping("/audit-log")
    public ResponseEntity<?> getAuditLog(@RequestParam(defaultValue = "50") int limit) {
        int safe = Math.min(limit, 500);
        return ResponseEntity.ok(
                auditRepo.findAllByOrderByTimestampDesc(PageRequest.of(0, safe))
                        .stream().map(l -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("id",        l.getId());
                            m.put("staffUuid", l.getStaffUuid());
                            m.put("staffName", l.getStaffName() != null ? l.getStaffName() : "Unknown");
                            m.put("action",    l.getAction());
                            m.put("timestamp", l.getTimestamp());
                            return m;
                        }).collect(Collectors.toList()));
    }

    @GetMapping("/players/search")
    public ResponseEntity<?> searchPlayer(@RequestParam String name,
                                          @RequestParam(defaultValue = "false") boolean suggestions) {
        if (suggestions) {
            // Return list of matching players (for autocomplete/head preview)
            List<Map<String, Object>> results = playerRepo
                    .findByLastKnownNameContainingIgnoreCaseOrderByLastSeenDesc(name)
                    .stream()
                    .limit(10)
                    .map(p -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("uuid",          p.getUuid());
                        m.put("lastKnownName", p.getLastKnownName());
                        m.put("lastSeen",      p.getLastSeen());
                        return m;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(results);
        }
        // Exact search — try exact match first, then partial
        Optional<PlayerCacheEntity> exact = playerRepo.findByLastKnownNameIgnoreCase(name);
        if (exact.isPresent()) {
            return buildPlayerResponse(exact.get().getUuid(), exact);
        }
        // Fall back to first partial match
        return playerRepo
                .findByLastKnownNameContainingIgnoreCaseOrderByLastSeenDesc(name)
                .stream()
                .findFirst()
                .map(p -> buildPlayerResponse(p.getUuid(), Optional.of(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/players/{uuid}")
    public ResponseEntity<?> getPlayer(@PathVariable String uuid) {
        return buildPlayerResponse(uuid, playerRepo.findById(uuid));
    }

    private ResponseEntity<?> buildPlayerResponse(String uuid, Optional<PlayerCacheEntity> pc) {
        List<ReportEntity> against = reportRepo.findByAccusedUuidOrderByCreatedAtDesc(uuid);
        List<ReportEntity> filed   = reportRepo.findByReporterUuidOrderByCreatedAtDesc(uuid);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("uuid", uuid);
        pc.ifPresent(p -> {
            res.put("lastKnownName", p.getLastKnownName());
            res.put("lastSeen",      p.getLastSeen());
        });
        res.put("reportedCount",   against.size());
        res.put("reporterCount",   filed.size());
        res.put("openAgainst",     against.stream().filter(r -> "OPEN".equals(r.getStatus())).count());
        res.put("resolvedAgainst", against.stream().filter(r -> "RESOLVED".equals(r.getStatus())).count());
        res.put("reportsAgainst",  against.stream().limit(50).map(this::toLight).collect(Collectors.toList()));
        return ResponseEntity.ok(res);
    }

    private Map<String, Object> toFull(ReportEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",                r.getId());
        m.put("reporterUuid",      r.getReporterUuid());
        m.put("reporterName",      r.getReporterName());
        m.put("accusedUuid",       r.getAccusedUuid());
        m.put("accusedName",       r.getAccusedName());
        m.put("reason",            r.getReason());
        m.put("status",            r.getStatus());
        m.put("claimedByUuid",     r.getClaimedByUuid());
        m.put("claimedByName",     r.getClaimedByName());
        m.put("createdAt",         r.getCreatedAt());
        m.put("resolvedAt",        r.getResolvedAt());
        m.put("verdict",           r.getVerdict());
        m.put("world",             r.getWorld());
        m.put("x",                 r.getX());
        m.put("y",                 r.getY());
        m.put("z",                 r.getZ());
        m.put("chatSnapshot",      r.getChatSnapshot());
        m.put("inventorySnapshot", r.getInventorySnapshot());
        m.put("reporterIp",        r.getReporterIp());
        return m;
    }

    private Map<String, Object> toLight(ReportEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",           r.getId());
        m.put("status",       r.getStatus());
        m.put("reason",       r.getReason());
        m.put("reporterUuid", r.getReporterUuid());
        m.put("reporterName", r.getReporterName());
        m.put("accusedUuid",  r.getAccusedUuid());
        m.put("accusedName",  r.getAccusedName());
        m.put("world",        r.getWorld());
        m.put("createdAt",    r.getCreatedAt());
        return m;
    }

    private Map<String, Object> noteToMap(NoteEntity n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",        n.getId());
        m.put("reportId",  n.getReportId());
        m.put("staffUuid", n.getStaffUuid());
        m.put("staffName", n.getStaffName() != null ? n.getStaffName() : "Unknown");
        m.put("note",      n.getNote());
        m.put("createdAt", n.getCreatedAt());
        return m;
    }
}
