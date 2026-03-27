package me.abdoabk.reportx.web;

import me.abdoabk.reportx.ReportXPlugin;
import me.abdoabk.reportx.model.Report;
import me.abdoabk.reportx.model.ReportStatus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Polls /api/internal/poll-notifications every N seconds.
 * When the dashboard resolves/rejects/claims a report the API sets notify_minecraft=true;
 * this poller reads those rows, fires in-game notifications, and the API clears the flag.
 */
public class DashboardNotificationPoller {

    private final ReportXPlugin plugin;
    private BukkitTask task;

    public DashboardNotificationPoller(ReportXPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        int intervalSec = plugin.getConfig().getInt("web-dashboard.poll-interval-seconds", 10);
        long ticks = intervalSec * 20L;
        task = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, this::poll, ticks, ticks);
        plugin.getLogger().info("[ReportX] Dashboard notification poller started (every " + intervalSec + "s).");
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    // ── HTTP poll ─────────────────────────────────────────────────────

    private void poll() {
        String apiUrl  = plugin.getConfig().getString("web-dashboard.api-url", "http://localhost:8080");
        String secret  = plugin.getConfig().getString("web-dashboard.plugin-secret", "CHANGE_THIS_PLUGIN_SECRET");
        String fullUrl = apiUrl + "/api/internal/poll-notifications";

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(fullUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Plugin-Secret", secret);
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);

            if (conn.getResponseCode() != 200) {
                plugin.getLogger().warning("[ReportX] Poll returned HTTP " + conn.getResponseCode());
                return;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            String json = sb.toString().trim();
            if (json.equals("[]") || json.isEmpty()) return;

            List<Map<String, Object>> notifications = SimpleJsonParser.parseArray(json);
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> notifications.forEach(this::handleNotification));

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[ReportX] Dashboard poll failed: " + e.getMessage());
        }
    }

    // ── Dispatch notification ─────────────────────────────────────────

    private void handleNotification(Map<String, Object> n) {
        long   id           = toLong(n.get("id"));
        String statusStr    = (String) n.get("status");
        String reporterUuid = (String) n.getOrDefault("reporterUuid", null);
        String accusedName  = (String) n.getOrDefault("accusedName",  "unknown");
        String verdict      = (String) n.getOrDefault("verdict",      null);
        String claimedBy    = (String) n.getOrDefault("claimedByName", "Dashboard");
        String staffName    = (claimedBy != null && !claimedBy.isBlank()) ? claimedBy : "Dashboard";

        ReportStatus newStatus;
        try { newStatus = ReportStatus.valueOf(statusStr); }
        catch (Exception e) { return; }

        // Minimal Report object — enough for NotificationService
        Report r = new Report();
        r.setId(id);
        r.setStatus(newStatus);
        r.setVerdict(verdict);
        r.setAccusedName(accusedName);
        if (reporterUuid != null) {
            try { r.setReporterUuid(UUID.fromString(reporterUuid)); } catch (Exception ignored) {}
        }

        switch (newStatus) {
            case RESOLVED  -> plugin.getNotificationService().notifyResolved(r, staffName);
            case REJECTED  -> notifyRejected(r, staffName);
            case CLAIMED   -> plugin.getNotificationService().notifyClaimed(r, staffName);
            case ESCALATED -> plugin.getNotificationService().notifyEscalated(r, staffName);
            default        -> {} // OPEN or unknown — no notification needed
        }
    }

    private void notifyRejected(Report r, String staffName) {
        String staffMsg = plugin.getMessageUtil().color(
                "&8[&cReport&4X&8] &7Report &e#" + r.getId()
                + " &7was &crejected &7by &e" + staffName + " &7via dashboard.");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("reportx.staff")) p.sendMessage(staffMsg);
        }
        if (r.getReporterUuid() != null) {
            Player reporter = Bukkit.getPlayer(r.getReporterUuid());
            if (reporter != null) {
                reporter.sendMessage(plugin.getMessageUtil().color(
                        "&8[&cReport&4X&8] &7Your report &e#" + r.getId() + " &7was &crejected&7."));
            }
        }
    }

    private long toLong(Object o) {
        if (o instanceof Number num) return num.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return 0; }
    }
}
