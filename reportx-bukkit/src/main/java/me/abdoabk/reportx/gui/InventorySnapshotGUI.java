package me.abdoabk.reportx.gui;

import me.abdoabk.reportx.ReportXPlugin;
import me.abdoabk.reportx.model.Report;
import me.abdoabk.reportx.util.InventorySerializer;
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
import java.util.logging.Level;

public class InventorySnapshotGUI {

    private static final int SLOT_HELMET      = 0;
    private static final int SLOT_CHESTPLATE  = 1;
    private static final int SLOT_LEGGINGS    = 2;
    private static final int SLOT_BOOTS       = 3;
    private static final int SLOT_OFFHAND     = 4;
    private static final int SLOT_PLAYER_HEAD = 8;

    private static final int HOTBAR_OFFSET = 9;
    private static final int MAIN_OFFSET   = 9;

    private static final int SLOT_BACK      = 45;
    private static final int INVENTORY_SIZE = 54;

    private final ReportXPlugin plugin;
    private final Player viewer;
    private final Report report;
    private Inventory inventory;

    public InventorySnapshotGUI(ReportXPlugin plugin, Player viewer, Report report) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.report = report;
    }

    public void open() {
        UUID accusedUuid = report.getAccusedUuid();
        OfflinePlayer accused = Bukkit.getOfflinePlayer(accusedUuid);
        String accusedName = accused.getName() != null ? accused.getName() : "Unknown";

        inventory = Bukkit.createInventory(null, INVENTORY_SIZE,
                plugin.getMessageUtil().color("&6&lInventory \u2014 &f" + accusedName));

        fillDecoration();
        placeBackButton();

        String snapshot = report.getInventorySnapshot();
        if (snapshot == null || snapshot.isBlank()) {
            placeNoDataItem(accusedName);
        } else {
            populateFromSnapshot(snapshot, accusedUuid, accusedName);
        }

        plugin.getEvidenceGUIManager().registerGUI(viewer.getUniqueId(), this);
        viewer.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        if (event.getRawSlot() == SLOT_BACK) {
            viewer.closeInventory();
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> new EvidencesSnapShotGUI(plugin, viewer, report).open());
        }
    }

    private void populateFromSnapshot(String snapshot, UUID accusedUuid, String accusedName) {

        SnapshotMeta meta = parseSnapshotMeta(snapshot);
        ArmorFlags flags  = parseArmorFlags(snapshot);
        String base64     = extractBase64(snapshot);

        if (base64 == null || base64.isBlank()) {
            // Old snapshot — no binary data stored
            placeNoDataItem(accusedName);
        } else {
            try {
                ItemStack[] contents =
                        InventorySerializer.itemStackArrayFromBase64(base64);

                if (contents == null || contents.length == 0) {
                    placeCorruptedDataItem();
                } else {
                    placeInventoryContents(contents, flags);
                }

            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to deserialize inventory snapshot for report #"
                                + report.getId(), ex);

                placeCorruptedDataItem();
            }
        }

        placePlayerHeadStats(accusedUuid, accusedName, meta);
    }

    private void placeInventoryContents(ItemStack[] contents, ArmorFlags flags) {

        // Hotbar (0–8)
        for (int i = 0; i <= 8 && i < contents.length; i++) {
            if (isPresent(contents[i])) {
                inventory.setItem(HOTBAR_OFFSET + i, contents[i].clone());
            }
        }

        // Main inventory (9–35)
        for (int i = 9; i <= 35 && i < contents.length; i++) {
            if (isPresent(contents[i])) {
                inventory.setItem(i + MAIN_OFFSET, contents[i].clone());
            }
        }

        // Armor
        if (flags.helmet && contents.length > 39 && isPresent(contents[39]))
            inventory.setItem(SLOT_HELMET, contents[39].clone());

        if (flags.chestplate && contents.length > 38 && isPresent(contents[38]))
            inventory.setItem(SLOT_CHESTPLATE, contents[38].clone());

        if (flags.leggings && contents.length > 37 && isPresent(contents[37]))
            inventory.setItem(SLOT_LEGGINGS, contents[37].clone());

        if (flags.boots && contents.length > 36 && isPresent(contents[36]))
            inventory.setItem(SLOT_BOOTS, contents[36].clone());

        if (flags.offhand && contents.length > 40 && isPresent(contents[40]))
            inventory.setItem(SLOT_OFFHAND, contents[40].clone());
    }

    private void placePlayerHeadStats(UUID accusedUuid,
                                      String accusedName,
                                      SnapshotMeta meta) {

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();

        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(accusedUuid));
        skullMeta.setDisplayName(plugin.getMessageUtil().color("&f&l" + accusedName));

        List<String> lore = new ArrayList<>();
        lore.add(plugin.getMessageUtil().color("&8▶ Snapshot Info"));
        lore.add(plugin.getMessageUtil().color("&7GameMode: &f" + meta.gameMode));
        lore.add(plugin.getMessageUtil().color("&c❤ Health: &f" + meta.health));
        lore.add(plugin.getMessageUtil().color("&6🍗 Food: &f" + meta.food));
        lore.add("");
        lore.add(plugin.getMessageUtil().color("&7World: &f" + meta.world));
        lore.add(plugin.getMessageUtil().color("&7Coords: &f" + meta.coords));

        skullMeta.setLore(lore);
        skull.setItemMeta(skullMeta);

        inventory.setItem(SLOT_PLAYER_HEAD, skull);
    }

    private void placeCorruptedDataItem() {
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        meta.setDisplayName(plugin.getMessageUtil().color("&cInventory Snapshot Corrupted"));
        meta.setLore(List.of(
                plugin.getMessageUtil().color("&7Stored BASE64 data is invalid or truncated."),
                plugin.getMessageUtil().color("&7Check database column type (TEXT/LONGTEXT).")
        ));
        barrier.setItemMeta(meta);
        inventory.setItem(22, barrier);
    }

    private void placeNoDataItem(String accusedName) {
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        meta.setDisplayName(plugin.getMessageUtil().color("&cNo Inventory Data"));
        meta.setLore(List.of(
                plugin.getMessageUtil().color("&7No binary snapshot stored"),
                plugin.getMessageUtil().color("&7for &f" + accusedName)
        ));
        barrier.setItemMeta(meta);
        inventory.setItem(22, barrier);
    }

    private void fillDecoration() {
        ItemStack pane = buildPane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 45; i < INVENTORY_SIZE; i++) {
            inventory.setItem(i, pane);
        }

        ItemStack gray = buildPane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 5; i <= 7; i++) {
            inventory.setItem(i, gray);
        }
    }

    private void placeBackButton() {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        meta.setDisplayName(plugin.getMessageUtil().color("&7← Back"));
        arrow.setItemMeta(meta);
        inventory.setItem(SLOT_BACK, arrow);
    }

    private SnapshotMeta parseSnapshotMeta(String snapshot) {
        SnapshotMeta meta = new SnapshotMeta();
        for (String line : snapshot.split("\n")) {
            String t = line.trim();
            if (t.startsWith("Health:"))   meta.health   = after(t, "Health:");
            if (t.startsWith("Food:"))     meta.food     = after(t, "Food:");
            if (t.startsWith("GameMode:")) meta.gameMode = after(t, "GameMode:");
            if (t.startsWith("World:"))    meta.world    = after(t, "World:");
            if (t.startsWith("Coords:"))   meta.coords   = after(t, "Coords:");
        }
        return meta;
    }

    private ArmorFlags parseArmorFlags(String snapshot) {
        for (String line : snapshot.split("\n")) {
            if (line.startsWith("ARMOR_FLAGS:")) {
                String[] parts = line.substring("ARMOR_FLAGS:".length()).split("\\|");
                ArmorFlags f = new ArmorFlags();
                if (parts.length >= 1) f.helmet     = Boolean.parseBoolean(parts[0]);
                if (parts.length >= 2) f.chestplate = Boolean.parseBoolean(parts[1]);
                if (parts.length >= 3) f.leggings   = Boolean.parseBoolean(parts[2]);
                if (parts.length >= 4) f.boots      = Boolean.parseBoolean(parts[3]);
                if (parts.length >= 5) f.offhand    = Boolean.parseBoolean(parts[4]);
                return f;
            }
        }
        return ArmorFlags.allTrue();
    }

    private String extractBase64(String snapshot) {
        for (String line : snapshot.split("\n")) {
            if (line.startsWith("BASE64:")) {
                return line.substring(7).trim();
            }
        }
        return null;
    }

    private String after(String line, String prefix) {
        return line.substring(prefix.length()).trim();
    }

    private boolean isPresent(ItemStack item) {
        return item != null && !item.getType().isAir();
    }

    private ItemStack buildPane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }

    private static class SnapshotMeta {
        String health = "?";
        String food = "?";
        String gameMode = "SURVIVAL";
        String world = "?";
        String coords = "?";
    }

    private static class ArmorFlags {
        boolean helmet, chestplate, leggings, boots, offhand;
        static ArmorFlags allTrue() {
            ArmorFlags f = new ArmorFlags();
            f.helmet = f.chestplate = f.leggings = f.boots = f.offhand = true;
            return f;
        }
    }
}