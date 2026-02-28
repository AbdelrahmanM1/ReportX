package me.abdoabk.reportx.listener;

import me.abdoabk.reportx.ReportXPlugin;
import me.abdoabk.reportx.service.EvidenceService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatListener implements Listener {

    private final EvidenceService evidenceService;

    public ChatListener(ReportXPlugin plugin) {
        this.evidenceService = plugin.getEvidenceService();
    }

    /**
     * Capture FINAL formatted chat.
     * Runs async — EvidenceService handles thread safety.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {

        String formatted = String.format(
                event.getFormat(),
                event.getPlayer().getDisplayName(),
                event.getMessage()
        );

        evidenceService.recordChat(event.getPlayer().getUniqueId(), formatted);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Do NOT clear immediately — report may happen after leave
    }
}