package me.abdoabk.reportx.listener;

import me.abdoabk.reportx.ReportXPlugin;
import me.abdoabk.reportx.gui.ChatSnapShotGUI;
import me.abdoabk.reportx.gui.EvidencesSnapShotGUI;
import me.abdoabk.reportx.gui.InventorySnapshotGUI;
import me.abdoabk.reportx.gui.PlayerReportGUI;
import me.abdoabk.reportx.gui.StaffReportGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class InventoryListener implements Listener {

    private final ReportXPlugin plugin;

    public InventoryListener(ReportXPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();

        // ───────── STAFF GUI ─────────
        if (title.contains("Report Management")) {
            event.setCancelled(true);
            StaffReportGUI gui = plugin.getStaffGUIManager().getOpenGUI(player.getUniqueId());
            if (gui != null) gui.handleClick(event);
            return;
        }

        // ───────── PLAYER GUI ─────────
        if (title.contains("My Reports")) {
            event.setCancelled(true);
            PlayerReportGUI gui = plugin.getPlayerGUIManager().getOpenGUI(player.getUniqueId());
            if (gui != null) gui.handleClick(event);
            return;
        }

        // ───────── EVIDENCE GUI ─────────
        if (title.contains("Evidence \u2014") || title.contains("Evidence —")) {
            event.setCancelled(true);
            Object tracked = plugin.getEvidenceGUIManager().getOpenGUI(player.getUniqueId());
            if (tracked instanceof EvidencesSnapShotGUI gui) gui.handleClick(event);
            return;
        }

        // ───────── CHAT SNAPSHOT GUI ─────────
        if (title.contains("Chat \u2014") || title.contains("Chat —")) {
            event.setCancelled(true);
            // ChatSnapShotGUI is tracked by the evidence GUI manager
            Object tracked = plugin.getEvidenceGUIManager().getOpenGUI(player.getUniqueId());
            if (tracked instanceof ChatSnapShotGUI gui) gui.handleClick(event);
            return;
        }

        // ───────── INVENTORY SNAPSHOT GUI ─────────
        if (title.contains("Inventory \u2014") || title.contains("Inventory —")) {
            event.setCancelled(true);
            Object tracked = plugin.getEvidenceGUIManager().getOpenGUI(player.getUniqueId());
            if (tracked instanceof InventorySnapshotGUI gui) gui.handleClick(event);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        plugin.getPlayerGUIManager().removeGUI(player.getUniqueId());
        plugin.getStaffGUIManager().removeGUI(player.getUniqueId());
        // Note: evidenceGUIManager is NOT cleared here so sub-GUIs can re-register on open
    }
}

