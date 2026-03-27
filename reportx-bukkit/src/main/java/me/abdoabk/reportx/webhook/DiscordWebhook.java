package me.abdoabk.reportx.webhook;

import me.abdoabk.reportx.ReportXPlugin;
import me.abdoabk.reportx.model.Report;
import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class DiscordWebhook {

    private final ReportXPlugin plugin;
    private final Logger logger;

    public DiscordWebhook(ReportXPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    private String getWebhookUrl() {
        return plugin.getConfig().getString("discord.webhook-url", "");
    }

    private String getServerName() {
        return plugin.getConfig().getString("discord.server-name", "Minecraft Server");
    }

    public void sendNewReport(Report report, String reporterName, String accusedName) {
        String payload = buildPayload(
                "🚨 New Report #" + report.getId(),
                "A new report has been submitted.",
                new String[][]{
                    {"Reporter", reporterName, "true"},
                    {"Accused", accusedName, "true"},
                    {"Reason", report.getReason(), "false"},
                    {"Status", report.getStatus().getDisplayName(), "true"},
                    {"Server", getServerName(), "true"}
                },
                16744272 // Orange color
        );
        sendAsync(payload);
    }

    public void sendResolved(Report report, String staffName) {
        String payload = buildPayload(
                "✅ Report #" + report.getId() + " Resolved",
                "A report has been resolved.",
                new String[][]{
                    {"Staff", staffName, "true"},
                    {"Verdict", report.getVerdict() != null ? report.getVerdict() : "N/A", "true"},
                    {"Server", getServerName(), "true"}
                },
                3066993 // Green color
        );
        sendAsync(payload);
    }

    public void sendEscalated(Report report, String staffName) {
        String payload = buildPayload(
                "⚠️ Report #" + report.getId() + " ESCALATED",
                "A report has been escalated and requires urgent attention!",
                new String[][]{
                    {"Escalated by", staffName, "true"},
                    {"Server", getServerName(), "true"}
                },
                15158332 // Red color
        );
        sendAsync(payload);
    }

    private String buildPayload(String title, String description, String[][] fields, int color) {
        StringBuilder fieldsJson = new StringBuilder("[");
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) fieldsJson.append(",");
            fieldsJson.append("{\"name\":\"").append(escapeJson(fields[i][0]))
                      .append("\",\"value\":\"").append(escapeJson(fields[i][1]))
                      .append("\",\"inline\":").append(fields[i][2]).append("}");
        }
        fieldsJson.append("]");

        return "{\"embeds\":[{\"title\":\"" + escapeJson(title) + "\"," +
               "\"description\":\"" + escapeJson(description) + "\"," +
               "\"color\":" + color + "," +
               "\"fields\":" + fieldsJson + "," +
               "\"footer\":{\"text\":\"ReportX • " + getServerName() + "\"}}]}";
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
    }

    private void sendAsync(String payload) {
        String url = getWebhookUrl();
        if (url == null || url.isEmpty()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                if (responseCode != 200 && responseCode != 204) {
                    logger.warning("[ReportX] Discord webhook returned code: " + responseCode);
                }
                conn.disconnect();
            } catch (Exception e) {
                logger.warning("[ReportX] Failed to send Discord webhook: " + e.getMessage());
            }
        });
    }
}
