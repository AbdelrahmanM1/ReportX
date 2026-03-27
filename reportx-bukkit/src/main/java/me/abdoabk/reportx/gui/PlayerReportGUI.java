package me.abdoabk.reportx.gui;

import me.abdoabk.reportx.ReportXPlugin;
import me.abdoabk.reportx.model.Report;
import me.abdoabk.reportx.model.ReportStatus;
import me.abdoabk.reportx.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PlayerReportGUI {

    private final ReportXPlugin plugin;
    private final Player player;
    private final List<Report> reports;
    private int page;
    private final int pageSize = 45;
    private Inventory inventory;

    public PlayerReportGUI(ReportXPlugin plugin, Player player, List<Report> reports, int page) {
        this.plugin = plugin;
        this.player = player;
        this.reports = reports;
        this.page = page;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 54,
                plugin.getMessageUtil().color("&cMy Reports &7(Page " + page + ")"));

        populateItems();

        // Navigation buttons
        if (page > 1) {
            inventory.setItem(45, createNavItem(Material.ARROW, plugin.getMessageUtil().get("gui.prev-page")));
        }

        int totalPages = (int) Math.ceil((double) reports.size() / pageSize);
        if (page < totalPages) {
            inventory.setItem(53, createNavItem(Material.ARROW, plugin.getMessageUtil().get("gui.next-page")));
        }

        // Fill empty with glass
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName(" ");
        filler.setItemMeta(meta);
        for (int i = 45; i < 54; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }

        plugin.getPlayerGUIManager().registerGUI(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    private void populateItems() {
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, reports.size());

        for (int i = start; i < end; i++) {
            Report report = reports.get(i);
            inventory.setItem(i - start, createReportItem(report));
        }

        if (reports.isEmpty()) {
            ItemStack noReports = new ItemStack(Material.BARRIER);
            ItemMeta meta = noReports.getItemMeta();
            meta.setDisplayName(plugin.getMessageUtil().get("gui.no-reports-item"));
            noReports.setItemMeta(meta);
            inventory.setItem(22, noReports);
        }
    }

    private ItemStack createReportItem(Report report) {
        Material mat = getMaterialForStatus(report.getStatus());
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        String accusedName = Bukkit.getOfflinePlayer(report.getAccusedUuid()).getName();
        if (accusedName == null) accusedName = report.getAccusedUuid().toString().substring(0, 8);

        String name = plugin.getMessageUtil().get("gui.report-item-name", Map.of(
                "id", String.valueOf(report.getId()),
                "accused", accusedName
        ));
        meta.setDisplayName(name);

        List<String> lore = plugin.getMessageUtil().getList("gui.report-item-lore", Map.of(
                "reason", report.getReason(),
                "status", getStatusColored(report.getStatus()),
                "time", TimeUtil.formatTimeAgo(report.getCreatedAt())
        ));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Material getMaterialForStatus(ReportStatus status) {
        return switch (status) {
            case OPEN -> Material.YELLOW_WOOL;
            case CLAIMED -> Material.BLUE_WOOL;
            case RESOLVED -> Material.GREEN_WOOL;
            case REJECTED -> Material.RED_WOOL;
            case ESCALATED -> Material.PURPLE_WOOL;
        };
    }

    private String getStatusColored(ReportStatus status) {
        return switch (status) {
            case OPEN -> plugin.getMessageUtil().get("status.open");
            case CLAIMED -> plugin.getMessageUtil().get("status.claimed");
            case RESOLVED -> plugin.getMessageUtil().get("status.resolved");
            case REJECTED -> plugin.getMessageUtil().get("status.rejected");
            case ESCALATED -> plugin.getMessageUtil().get("status.escalated");
        };
    }

    private ItemStack createNavItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == 45 && page > 1) {
            page--;
            open();
        } else if (slot == 53) {
            int totalPages = (int) Math.ceil((double) reports.size() / pageSize);
            if (page < totalPages) {
                page++;
                open();
            }
        }
    }
}
