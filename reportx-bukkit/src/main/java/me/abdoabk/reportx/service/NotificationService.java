package me.abdoabk.reportx.service;

import me.abdoabk.reportx.ReportXPlugin;
import me.abdoabk.reportx.model.Report;
import me.abdoabk.reportx.util.TimeUtil;
import me.abdoabk.reportx.webhook.DiscordWebhook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

public class NotificationService {

    private final ReportXPlugin plugin;
    private final DiscordWebhook webhook;

    public NotificationService(ReportXPlugin plugin) {
        this.plugin = plugin;
        this.webhook = new DiscordWebhook(plugin);
    }

    public void notifyNewReport(Report report, String reporterName, String accusedName) {
        String msg = plugin.getMessageUtil().get("staff.claim-broadcast", Map.of(
                "id", String.valueOf(report.getId()),
                "staff", reporterName
        ));
        String notification = plugin.getMessageUtil().color(
                "&6New report &e#" + report.getId() + " &6submitted by &e" + reporterName +
                " &6against &e" + accusedName + "&6. Reason: &f" + report.getReason()
        );
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("reportx.staff")) {
                p.sendMessage(notification);
            }
        }

        if (plugin.getConfig().getBoolean("discord.enabled", false) &&
            plugin.getConfig().getBoolean("discord.notify-new-report", true)) {
            webhook.sendNewReport(report, reporterName, accusedName);
        }
    }

    public void notifyClaimed(Report report, String staffName) {
        if (!plugin.getConfig().getBoolean("staff.broadcast-claim", true)) return;
        String msg = plugin.getMessageUtil().get("staff.claim-broadcast", Map.of(
                "id", String.valueOf(report.getId()),
                "staff", staffName
        ));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("reportx.staff")) {
                p.sendMessage(msg);
            }
        }
    }

    public void notifyResolved(Report report, String staffName) {
        if (!plugin.getConfig().getBoolean("staff.broadcast-resolve", true)) return;
        String msg = plugin.getMessageUtil().get("staff.resolve-broadcast", Map.of(
                "id", String.valueOf(report.getId()),
                "staff", staffName,
                "verdict", report.getVerdict() != null ? report.getVerdict() : "N/A"
        ));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("reportx.staff")) {
                p.sendMessage(msg);
            }
        }

        Player reporter = Bukkit.getPlayer(report.getReporterUuid());
        if (reporter != null) {
            reporter.sendMessage(plugin.getMessageUtil().color(
                    "&6Your report &e#" + report.getId() + " &6has been resolved. Verdict: &e" + report.getVerdict()
            ));
        }

        if (plugin.getConfig().getBoolean("discord.enabled", false) &&
            plugin.getConfig().getBoolean("discord.notify-resolved", true)) {
            webhook.sendResolved(report, staffName);
        }
    }

    public void notifyEscalated(Report report, String staffName) {
        String msg = plugin.getMessageUtil().color(
                "&d[ESCALATED] &6Report &e#" + report.getId() + " &6escalated by &e" + staffName
        );
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("reportx.staff")) {
                p.sendMessage(msg);
            }
        }

        if (plugin.getConfig().getBoolean("discord.enabled", false) &&
            plugin.getConfig().getBoolean("discord.notify-escalated", true)) {
            webhook.sendEscalated(report, staffName);
        }
    }
}
