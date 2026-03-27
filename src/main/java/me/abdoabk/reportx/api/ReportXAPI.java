package me.abdoabk.reportx.api;

import me.abdoabk.reportx.ReportXPlugin;
import me.abdoabk.reportx.model.AuditLog;
import me.abdoabk.reportx.model.Note;
import me.abdoabk.reportx.model.Report;
import me.abdoabk.reportx.model.ReportStatus;
import me.abdoabk.reportx.service.ReportService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * ╔══════════════════════════════════════════╗
 * ║          ReportX Public API              ║
 * ║  For use by other plugins on this server ║
 * ╚══════════════════════════════════════════╝
 *
 * <h2>Usage</h2>
 * <pre>
 * // Obtain the API instance
 * ReportXAPI api = ReportXAPI.get();
 * if (api == null) {
 *     // ReportX is not loaded — handle gracefully
 *     return;
 * }
 *
 * // Submit a report programmatically
 * api.submitReport(reporter, "SuspectName", "Cheating with flight hacks")
 *    .thenAccept(result -> Bukkit.getLogger().info("Result: " + result));
 *
 * // Fetch all open reports
 * api.getReportsByStatus(ReportStatus.OPEN)
 *    .thenAccept(reports -> reports.forEach(r -> ...));
 * </pre>
 *
 * <h2>Events fired by ReportX (listen via Bukkit's event system)</h2>
 * All events are in the package {@code me.abdoabk.reportx.api.event}.
 * Register them like any Bukkit event.
 */
public class ReportXAPI {

    // ── Singleton ─────────────────────────────────────────────────────

    private static ReportXAPI instance;

    /** Returns the active API instance, or null if ReportX is not enabled. */
    public static ReportXAPI get() {
        if (instance != null) return instance;
        var plugin = Bukkit.getPluginManager().getPlugin("ReportX");
        if (plugin instanceof ReportXPlugin rxp) {
            instance = new ReportXAPI(rxp);
        }
        return instance;
    }

    /** Called internally by ReportXPlugin on enable/disable. */
    public static void init(ReportXPlugin plugin) { instance = new ReportXAPI(plugin); }
    public static void shutdown()                  { instance = null; }

    // ── Core ──────────────────────────────────────────────────────────

    private final ReportXPlugin plugin;

    private ReportXAPI(ReportXPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Report Submission ─────────────────────────────────────────────

    /**
     * Submits a report on behalf of a player.
     * This runs all the same validation as the in-game command:
     * cooldowns, duplicate checks, daily limits, reason length, etc.
     *
     * @param reporter    the player filing the report
     * @param accusedName the name of the player being reported
     * @param reason      the reason for the report
     * @return future with the submission result
     */
    public CompletableFuture<ReportService.SubmitResult> submitReport(Player reporter,
                                                                        String accusedName,
                                                                        String reason) {
        return plugin.getReportService().submitReport(reporter, accusedName, reason);
    }

    // ── Report Retrieval ──────────────────────────────────────────────

    /**
     * Find a report by its unique ID.
     */
    public CompletableFuture<Optional<Report>> getReportById(long id) {
        return plugin.getReportService().getRepository().findById(id);
    }

    /**
     * Get all reports filed by a specific player UUID.
     */
    public CompletableFuture<List<Report>> getReportsByReporter(UUID reporterUuid) {
        return plugin.getReportService().getRepository().findByReporter(reporterUuid);
    }

    /**
     * Get all reports filed against a specific player UUID.
     */
    public CompletableFuture<List<Report>> getReportsByAccused(UUID accusedUuid) {
        return plugin.getReportService().getRepository().findByAccused(accusedUuid);
    }

    /**
     * Get all reports with a specific status.
     */
    public CompletableFuture<List<Report>> getReportsByStatus(ReportStatus status) {
        return plugin.getReportService().getRepository().findByStatus(status);
    }

    /**
     * Get a paginated list of all reports (newest first).
     *
     * @param page     1-based page number
     * @param pageSize number of reports per page
     */
    public CompletableFuture<List<Report>> getAllReports(int page, int pageSize) {
        return plugin.getReportService().getRepository().findAll(page, pageSize);
    }

    // ── Report Actions ────────────────────────────────────────────────

    /**
     * Claim a report as a staff member.
     * Report must be in OPEN status.
     *
     * @return true if successfully claimed
     */
    public CompletableFuture<Boolean> claimReport(long reportId, Player staff) {
        return plugin.getReportService().claimReport(reportId, staff);
    }

    /**
     * Close/resolve a report with a verdict.
     * The staff member must have claimed the report, or have override permission.
     *
     * @param verdict free-text verdict (e.g. "Banned 7d", "Warning issued")
     * @return true if successfully closed
     */
    public CompletableFuture<Boolean> closeReport(long reportId, Player staff, String verdict) {
        return plugin.getReportService().closeReport(reportId, staff, verdict);
    }

    /**
     * Reject a report (mark as invalid without resolution).
     *
     * @return true if successfully rejected
     */
    public CompletableFuture<Boolean> rejectReport(long reportId, Player staff) {
        return plugin.getReportService().rejectReport(reportId, staff);
    }

    /**
     * Escalate a report to require senior staff attention.
     *
     * @return true if successfully escalated
     */
    public CompletableFuture<Boolean> escalateReport(long reportId, Player staff) {
        return plugin.getReportService().escalateReport(reportId, staff);
    }

    /**
     * Directly change the status of a report.
     * Fires audit log entry. Staff permission is required.
     *
     * @param reportId  ID of the report to change
     * @param newStatus the target status
     * @param actor     the staff member performing the action
     * @return a StatusChangeResult indicating success or failure reason
     */
    public CompletableFuture<ReportService.StatusChangeResult> changeStatus(long reportId,
                                                                              ReportStatus newStatus,
                                                                              Player actor) {
        return plugin.getReportService().changeStatus(reportId, newStatus, actor);
    }

    /**
     * Add an internal staff note to a report.
     * Notes are only visible to staff, never to the reporting player.
     *
     * @return true if the note was added
     */
    public CompletableFuture<Boolean> addNote(long reportId, Player staff, String note) {
        return plugin.getReportService().addNote(reportId, staff, note);
    }

    /**
     * Get all notes attached to a report.
     */
    public CompletableFuture<List<Note>> getNotes(long reportId) {
        return plugin.getReportService().getRepository().findNotesByReport(reportId);
    }

    // ── Statistics ────────────────────────────────────────────────────

    /** Total number of reports in the database. */
    public CompletableFuture<Long> getTotalReportCount() {
        return plugin.getReportService().getRepository().countTotal();
    }

    /** Number of reports with a specific status. */
    public CompletableFuture<Long> getReportCountByStatus(ReportStatus status) {
        return plugin.getReportService().getRepository().countByStatus(status);
    }

    /** UUID of the most frequently reported player. */
    public CompletableFuture<UUID> getMostReportedPlayer() {
        return plugin.getReportService().getRepository().findMostReportedPlayer();
    }

    /** UUID of the staff member who has handled the most reports. */
    public CompletableFuture<UUID> getMostActiveStaff() {
        return plugin.getReportService().getRepository().findMostActiveStaff();
    }

    /** Average time from report submission to resolution, in hours. */
    public CompletableFuture<Double> getAverageResolutionTimeHours() {
        return plugin.getReportService().getRepository().getAverageResolutionTimeHours();
    }

    // ── Audit Log ─────────────────────────────────────────────────────

    /**
     * Retrieve the most recent audit log entries.
     *
     * @param limit max number of entries to return
     */
    public CompletableFuture<List<AuditLog>> getAuditLog(int limit) {
        return plugin.getReportService().getRepository().findAuditLogs(limit);
    }

    // ── Evidence ──────────────────────────────────────────────────────

    /**
     * Get the live chat snapshot for a player UUID (last N lines).
     * Returns null if snapshots are disabled or the player has no recorded chat.
     */
    public String getChatSnapshot(UUID playerUuid) {
        return plugin.getEvidenceService().getChatSnapshot(playerUuid);
    }

    /**
     * Get a live inventory snapshot for an online player.
     * MUST be called from the main thread.
     * Returns null if the player is offline or snapshots are disabled.
     */
    public String getInventorySnapshot(UUID playerUuid) {
        return plugin.getEvidenceService().getInventorySnapshot(playerUuid);
    }

    // ── Utility ───────────────────────────────────────────────────────

    /**
     * Returns how many seconds remain on a player's report cooldown.
     * Returns 0 if the player is not on cooldown.
     */
    public long getCooldownRemaining(UUID playerUuid) {
        return plugin.getReportService().getCooldownRemaining(playerUuid);
    }

    /** Returns the version string of the loaded ReportX plugin. */
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** Direct access to the underlying plugin instance for advanced use. */
    public ReportXPlugin getPlugin() {
        return plugin;
    }
}
