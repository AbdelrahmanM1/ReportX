package me.abdoabk.reportx.gui;

import me.abdoabk.reportx.ReportXPlugin;
import me.abdoabk.reportx.model.Report;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Chat Snapshot GUI — paginates recorded chat messages as paper items.
 *
 * Layout (54 slots):
 *   [0]     Arrow   — back to EvidencesSnapShotGUI
 *   [4]     Player head — click closes GUI and prints all chat to viewer's chat
 *   [10-43] Paper items — one per chat line (timestamp as name, message as lore)
 *   [45]    Prev page arrow (when applicable)
 *   [49]    Page info book
 *   [53]    Next page arrow (when applicable)
 *   Border columns and bottom row — decorative glass
 *
 * Clicking the player head:
 *   - Closes the inventory
 *   - Sends every recorded line into the viewer's chat with preserved timestamps
 */
public class ChatSnapShotGUI {

    // ── Constants ──────────────────────────────────────────────────────────────

    private static final int SLOT_BACK        = 0;
    private static final int SLOT_PLAYER_HEAD = 4;
    private static final int SLOT_PREV        = 45;
    private static final int SLOT_NEXT        = 53;
    private static final int INVENTORY_SIZE   = 54;

    /** Content slots: rows 1–4, columns 1–7 (skipping border columns 0 and 8). */
    private static final int[] CONTENT_SLOTS  = buildContentSlots();

    // ── Fields ─────────────────────────────────────────────────────────────────

    private final ReportXPlugin plugin;
    private final Player        viewer;
    private final Report        report;
    private final List<String>  chatLines;
    private       Inventory     inventory;
    private       int           page = 1;

    // ── Constructor ────────────────────────────────────────────────────────────

    public ChatSnapShotGUI(ReportXPlugin plugin, Player viewer, Report report) {
        this.plugin    = plugin;
        this.viewer    = viewer;
        this.report    = report;
        this.chatLines = parseChatLines(report.getChatSnapshot());
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public void open() {
        UUID          accusedUuid = report.getAccusedUuid();
        OfflinePlayer accused     = Bukkit.getOfflinePlayer(accusedUuid);
        String        accusedName = accused.getName() != null ? accused.getName() : "Unknown";

        inventory = Bukkit.createInventory(null, INVENTORY_SIZE,
                plugin.getMessageUtil().color("&b&lChat \u2014 &f" + accusedName
                        + " &8(Page " + page + ")"));

        fillDecoration();
        placeBackButton();
        placePlayerHead(accusedUuid, accusedName);
        placeChatEntries();
        placeNavigation();

        plugin.getEvidenceGUIManager().registerGUI(viewer.getUniqueId(), this);
        viewer.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();

        if (slot == SLOT_BACK) {
            viewer.closeInventory();
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    new EvidencesSnapShotGUI(plugin, viewer, report).open());
            return;
        }

        if (slot == SLOT_PLAYER_HEAD) {
            viewer.closeInventory();
            plugin.getServer().getScheduler().runTask(plugin, () -> printChatToViewer());
            return;
        }

        if (slot == SLOT_PREV && page > 1) {
            page--;
            open();
            return;
        }

        if (slot == SLOT_NEXT && page < totalPages()) {
            page++;
            open();
        }
    }

    // ── Layout ─────────────────────────────────────────────────────────────────

    private void fillDecoration() {
        ItemStack darkPane = buildPane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack grayPane = buildPane(Material.GRAY_STAINED_GLASS_PANE);

        for (int i = 45; i < INVENTORY_SIZE; i++) inventory.setItem(i, darkPane);

        int[] borders = {0, 8, 9, 17, 18, 26, 27, 35, 36, 44};
        for (int s : borders) inventory.setItem(s, grayPane);
    }

    private void placeBackButton() {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta  meta  = arrow.getItemMeta();
        meta.setDisplayName(plugin.getMessageUtil().color("&7\u2190 Back to Evidence"));
        meta.setLore(List.of(plugin.getMessageUtil().color("&8Click to go back")));
        arrow.setItemMeta(meta);
        inventory.setItem(SLOT_BACK, arrow);
    }

    private void placePlayerHead(UUID accusedUuid, String accusedName) {
        ItemStack skull = buildSkull(accusedUuid);
        ItemMeta  meta  = skull.getItemMeta();
        meta.setDisplayName(plugin.getMessageUtil().color("&f&l" + accusedName));

        List<String> lore = new ArrayList<>();
        lore.add(plugin.getMessageUtil().color("&7Total messages: &f" + chatLines.size()));
        lore.add("");
        lore.add(plugin.getMessageUtil().color("&eLeft-Click &7to print all chat"));
        lore.add(plugin.getMessageUtil().color("&7into your chat window"));
        meta.setLore(lore);

        skull.setItemMeta(meta);
        inventory.setItem(SLOT_PLAYER_HEAD, skull);
    }

    private void placeChatEntries() {
        int startLine = (page - 1) * CONTENT_SLOTS.length;
        int endLine   = Math.min(startLine + CONTENT_SLOTS.length, chatLines.size());

        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int lineIndex = startLine + i;
            if (lineIndex >= endLine) break;

            String[] parts     = splitTimestampAndMessage(chatLines.get(lineIndex));
            String   timestamp = parts[0];
            String   message   = parts[1];

            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta  meta  = paper.getItemMeta();
            meta.setDisplayName(plugin.getMessageUtil().color("&7" + timestamp));
            meta.setLore(wrapText(message, 40));
            paper.setItemMeta(meta);

            inventory.setItem(CONTENT_SLOTS[i], paper);
        }
    }

    private void placeNavigation() {
        // Fill any remaining bottom-row slots
        ItemStack filler = buildPane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 45; i < INVENTORY_SIZE; i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, filler);
        }

        if (page > 1) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta  meta = prev.getItemMeta();
            meta.setDisplayName(plugin.getMessageUtil().color("&a\u2190 Previous Page"));
            prev.setItemMeta(meta);
            inventory.setItem(SLOT_PREV, prev);
        }

        if (page < totalPages()) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta  meta = next.getItemMeta();
            meta.setDisplayName(plugin.getMessageUtil().color("&aNext Page \u2192"));
            next.setItemMeta(meta);
            inventory.setItem(SLOT_NEXT, next);
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta  meta = info.getItemMeta();
        meta.setDisplayName(plugin.getMessageUtil().color(
                "&fPage &e" + page + " &7/ &e" + totalPages()));
        info.setItemMeta(meta);
        inventory.setItem(49, info);
    }

    // ── Chat print ─────────────────────────────────────────────────────────────

    private void printChatToViewer() {
        String separator = plugin.getMessageUtil().color("&8&m" + "-".repeat(44));
        viewer.sendMessage(separator);
        viewer.sendMessage(plugin.getMessageUtil().color("&b&lChat Snapshot &8\u2014 &f"
                + Bukkit.getOfflinePlayer(report.getAccusedUuid()).getName()));

        if (chatLines.isEmpty()) {
            viewer.sendMessage(plugin.getMessageUtil().color("&cNo messages recorded."));
        } else {
            for (String line : chatLines) {
                String[] parts = splitTimestampAndMessage(line);
                viewer.sendMessage(plugin.getMessageUtil().color("&8" + parts[0] + " &f" + parts[1]));
            }
        }
        viewer.sendMessage(separator);
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private List<String> parseChatLines(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(snapshot.split("\n")));
    }

    /**
     * Splits "[HH:MM:SS] message" into {"[HH:MM:SS]", "message"}.
     * Returns {"", line} if the format is not recognised.
     */
    private String[] splitTimestampAndMessage(String line) {
        if (line != null && line.startsWith("[") && line.contains("]")) {
            int end = line.indexOf(']');
            return new String[]{
                    line.substring(0, end + 1),
                    end + 2 < line.length() ? line.substring(end + 2) : ""
            };
        }
        return new String[]{"", line != null ? line : ""};
    }

    private List<String> wrapText(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() + word.length() + 1 > width && current.length() > 0) {
                lines.add(plugin.getMessageUtil().color("&f" + current.toString().trim()));
                current = new StringBuilder();
            }
            current.append(word).append(' ');
        }
        if (current.length() > 0)
            lines.add(plugin.getMessageUtil().color("&f" + current.toString().trim()));
        return lines;
    }

    private int totalPages() {
        return chatLines.isEmpty() ? 1 : (int) Math.ceil((double) chatLines.size() / CONTENT_SLOTS.length);
    }

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

    private static int[] buildContentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++)
            for (int col = 1; col <= 7; col++)
                slots.add(row * 9 + col);
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }
}
