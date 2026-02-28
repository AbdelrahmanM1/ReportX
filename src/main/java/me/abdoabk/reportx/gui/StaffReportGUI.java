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

import java.util.List;
import java.util.Map;

public class StaffReportGUI {

    private final ReportXPlugin plugin;
    private final Player staff;
    private List<Report> reports;
    private int page;
    private final int pageSize = 45;
    private Inventory inventory;

    public StaffReportGUI(ReportXPlugin plugin, Player staff, List<Report> reports, int page) {
        this.plugin = plugin;
        this.staff = staff;
        this.reports = reports;
        this.page = page;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 54,
                plugin.getMessageUtil().color("&cReport Management &7(Page " + page + ")"));

        populateItems();

        // Navigation
        if (page > 1) {
            inventory.setItem(45, createNavItem(Material.ARROW, plugin.getMessageUtil().get("gui.prev-page")));
        }
        int totalPages = Math.max(1, (int) Math.ceil((double) reports.size() / pageSize));
        if (page < totalPages) {
            inventory.setItem(53, createNavItem(Material.ARROW, plugin.getMessageUtil().get("gui.next-page")));
        }

        // Info button
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(plugin.getMessageUtil().color("&6Report Panel"));
        infoMeta.setLore(List.of(
            plugin.getMessageUtil().color("&7Left-Click &a→ Claim"),
            plugin.getMessageUtil().color("&7Right-Click &e→ View Details"),
            plugin.getMessageUtil().color("&7Shift+Click &c→ Close/Reject")
        ));
        info.setItemMeta(infoMeta);
        inventory.setItem(49, info);

        // Filler
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = 45; i < 54; i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, filler);
        }

        plugin.getStaffGUIManager().registerGUI(staff.getUniqueId(), this);
        staff.openInventory(inventory);
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

        String accusedName = getPlayerName(report.getAccusedUuid());
        String reporterName = getPlayerName(report.getReporterUuid());

        String name = plugin.getMessageUtil().get("gui.report-item-name", Map.of(
                "id", String.valueOf(report.getId()),
                "accused", accusedName
        ));
        meta.setDisplayName(name);

        List<String> lore = plugin.getMessageUtil().getList("gui.staff-item-lore", Map.of(
                "reporter", reporterName,
                "accused", accusedName,
                "reason", report.getReason().length() > 40 ? report.getReason().substring(0, 40) + "..." : report.getReason(),
                "status", getStatusColored(report.getStatus()),
                "time", TimeUtil.formatTimeAgo(report.getCreatedAt())
        ));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();

        // Navigation
        if (slot == 45 && page > 1) {
            page--;
            open();
            return;
        }
        if (slot == 53) {
            int totalPages = (int) Math.ceil((double) reports.size() / pageSize);
            if (page < totalPages) { page++; open(); }
            return;
        }

        // Report items
        int start = (page - 1) * pageSize;
        int reportIndex = start + slot;
        if (slot >= 45 || reportIndex >= reports.size()) return;

        Report report = reports.get(reportIndex);

        if (event.isShiftClick()) {
            // Close/Reject
            staff.closeInventory();
            if (report.getStatus() == ReportStatus.OPEN || report.getStatus() == ReportStatus.CLAIMED) {
                plugin.getReportService().closeReport(report.getId(), staff, "Closed via GUI")
                        .thenAccept(success -> {
                            if (success) {
                                plugin.getServer().getScheduler().runTask(plugin, () ->
                                        plugin.getMessageUtil().send(staff, "staff.close-success",
                                                Map.of("id", String.valueOf(report.getId()), "verdict", "Closed via GUI")));
                            }
                        });
            }
        } else if (event.isRightClick()) {
            // Open Evidence GUI
            staff.closeInventory();
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    new EvidencesSnapShotGUI(plugin, staff, report).open());
        } else {
            // Left click = claim
            if (report.getStatus() == ReportStatus.OPEN) {
                plugin.getReportService().claimReport(report.getId(), staff)
                        .thenAccept(success -> {
                            if (success) {
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    plugin.getMessageUtil().send(staff, "staff.claim-success",
                                            Map.of("id", String.valueOf(report.getId())));
                                    // Refresh GUI
                                    reports.set(reportIndex - start, report);
                                });
                            }
                        });
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        plugin.getMessageUtil().send(staff, "staff.claim-already",
                                Map.of("id", String.valueOf(report.getId()),
                                       "staff", report.getClaimedBy() != null ?
                                               getPlayerName(report.getClaimedBy()) : "unknown")));
            }
        }
    }


    private String getPlayerName(java.util.UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString().substring(0, 8);
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
}
