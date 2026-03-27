package me.abdoabk.reportx.gui;

import me.abdoabk.reportx.ReportXPlugin;
import me.abdoabk.reportx.model.Report;
import me.abdoabk.reportx.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Main Evidence Hub GUI — entry point for viewing all evidence attached to a report.
 *
 * Layout (54 slots):
 *   [4]  Written book  — report summary (ID, accused, reason, status, filed time)
 *   [20] Player head   — Chat Snapshot entry; click opens ChatSnapShotGUI
 *   [24] Player head   — Inventory Snapshot entry; click opens InventorySnapshotGUI
 *   Border / bottom row = decorative glass panes
 */
public class EvidencesSnapShotGUI {

    // ── Slot constants ─────────────────────────────────────────────────────────

    private static final int SLOT_REPORT_INFO   = 4;
    private static final int SLOT_CHAT_SNAPSHOT = 20;
    private static final int SLOT_INV_SNAPSHOT  = 24;
    private static final int INVENTORY_SIZE      = 54;

    // ── Fields ─────────────────────────────────────────────────────────────────

    private final ReportXPlugin plugin;
    private final Player        viewer;
    private final Report        report;
    private       Inventory     inventory;

    // ── Constructor ────────────────────────────────────────────────────────────

    public EvidencesSnapShotGUI(ReportXPlugin plugin, Player viewer, Report report) {
        this.plugin  = plugin;
        this.viewer  = viewer;
        this.report  = report;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public void open() {
        UUID          accusedUuid = report.getAccusedUuid();
        OfflinePlayer accused     = Bukkit.getOfflinePlayer(accusedUuid);
        String        accusedName = accused.getName() != null ? accused.getName() : "Unknown";

        inventory = Bukkit.createInventory(null, INVENTORY_SIZE,
                plugin.getMessageUtil().color("&8Evidence \u2014 &f" + accusedName));

        fillBorder();
        placeReportInfo(accusedName);
        placeChatEntry(accusedUuid, accusedName);
        placeInventoryEntry(accusedUuid, accusedName);

        plugin.getEvidenceGUIManager().registerGUI(viewer.getUniqueId(), this);
        viewer.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();

        if (slot == SLOT_CHAT_SNAPSHOT) {
            viewer.closeInventory();
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    new ChatSnapShotGUI(plugin, viewer, report).open());
            return;
        }

        if (slot == SLOT_INV_SNAPSHOT) {
            viewer.closeInventory();
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    new InventorySnapshotGUI(plugin, viewer, report).open());
        }
    }

    // ── Layout ─────────────────────────────────────────────────────────────────

    private void fillBorder() {
        ItemStack darkPane  = buildPane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack grayPane  = buildPane(Material.GRAY_STAINED_GLASS_PANE);

        for (int i = 45; i < INVENTORY_SIZE; i++) inventory.setItem(i, darkPane);

        int[] accents = {0, 8, 9, 17, 18, 26, 27, 35, 36, 44};
        for (int s : accents) inventory.setItem(s, grayPane);
    }

    private void placeReportInfo(String accusedName) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta  meta = book.getItemMeta();
        meta.setDisplayName(plugin.getMessageUtil().color("&6&lEvidence Report &8#" + report.getId()));

        List<String> lore = new ArrayList<>();
        lore.add(plugin.getMessageUtil().color("&7Accused: &f" + accusedName));
        lore.add(plugin.getMessageUtil().color("&7Reason:  &f" + truncate(report.getReason(), 40)));
        lore.add(plugin.getMessageUtil().color("&7Filed:   &f" + TimeUtil.formatTimeAgo(report.getCreatedAt())));
        lore.add(plugin.getMessageUtil().color("&7Status:  &f" + report.getStatus().name()));
        lore.add("");
        lore.add(plugin.getMessageUtil().color("&eClick an evidence entry below"));
        meta.setLore(lore);

        book.setItemMeta(meta);
        inventory.setItem(SLOT_REPORT_INFO, book);
    }

    private void placeChatEntry(UUID accusedUuid, String accusedName) {
        boolean hasData = report.getChatSnapshot() != null && !report.getChatSnapshot().isBlank();

        ItemStack skull = buildSkull(accusedUuid);
        ItemMeta  meta  = skull.getItemMeta();
        meta.setDisplayName(plugin.getMessageUtil().color("&b&lChat Snapshot"));

        List<String> lore = new ArrayList<>();
        lore.add(plugin.getMessageUtil().color("&7Recorded chat messages for"));
        lore.add(plugin.getMessageUtil().color("&f" + accusedName));
        lore.add("");
        lore.add(hasData
                ? plugin.getMessageUtil().color("&a\u2714 Data available")
                : plugin.getMessageUtil().color("&c\u2718 No chat data captured"));
        lore.add("");
        lore.add(plugin.getMessageUtil().color("&eLeft-Click &7to view"));
        meta.setLore(lore);

        skull.setItemMeta(meta);
        inventory.setItem(SLOT_CHAT_SNAPSHOT, skull);
    }

    private void placeInventoryEntry(UUID accusedUuid, String accusedName) {
        boolean hasData = report.getInventorySnapshot() != null && !report.getInventorySnapshot().isBlank();

        ItemStack skull = buildSkull(accusedUuid);
        ItemMeta  meta  = skull.getItemMeta();
        meta.setDisplayName(plugin.getMessageUtil().color("&6&lInventory Snapshot"));

        List<String> lore = new ArrayList<>();
        lore.add(plugin.getMessageUtil().color("&7Captured inventory, armour,"));
        lore.add(plugin.getMessageUtil().color("&7health, food & location for"));
        lore.add(plugin.getMessageUtil().color("&f" + accusedName));
        lore.add("");
        lore.add(hasData
                ? plugin.getMessageUtil().color("&a\u2714 Data available")
                : plugin.getMessageUtil().color("&c\u2718 No inventory data captured"));
        lore.add("");
        lore.add(plugin.getMessageUtil().color("&eLeft-Click &7to view"));
        meta.setLore(lore);

        skull.setItemMeta(meta);
        inventory.setItem(SLOT_INV_SNAPSHOT, skull);
    }

    // ── Item builders ──────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private ItemStack buildSkull(UUID uuid) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack buildPane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta  meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }
}
