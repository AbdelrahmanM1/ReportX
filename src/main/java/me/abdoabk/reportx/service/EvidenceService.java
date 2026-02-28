package me.abdoabk.reportx.service;

import me.abdoabk.reportx.ReportXPlugin;
import me.abdoabk.reportx.util.InventorySerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class EvidenceService {

    private final ReportXPlugin plugin;

    private final Map<UUID, Deque<String>> chatHistory = new ConcurrentHashMap<>();
    private static final int MAX_CHAT_BUFFER = 100;

    public EvidenceService(ReportXPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Chat Evidence ──────────────────────────────────────────────────────────

    /** Thread-safe — safe to call from async chat event. */
    public void recordChat(UUID uuid, String formattedMessage) {
        chatHistory.compute(uuid, (id, deque) -> {
            if (deque == null) deque = new ConcurrentLinkedDeque<>();
            if (deque.size() >= MAX_CHAT_BUFFER) deque.pollFirst();
            deque.addLast("[" + LocalTime.now().withNano(0) + "] " + formattedMessage);
            return deque;
        });
    }

    /**
     * Returns the last N recorded chat lines as a single newline-delimited string.
     * Returns null if chat snapshots are disabled in config, empty string if no lines.
     */
    public String getChatSnapshot(UUID playerUuid) {
        if (!plugin.getConfig().getBoolean("evidence.chat-snapshot", true)) return null;

        int lines = plugin.getConfig().getInt("evidence.chat-lines", 20);
        Deque<String> history = chatHistory.get(playerUuid);
        if (history == null || history.isEmpty()) return "";

        List<String> copy = new ArrayList<>(history);
        int start = Math.max(0, copy.size() - lines);
        return String.join("\n", copy.subList(start, copy.size()));
    }

    // ── Inventory Evidence ─────────────────────────────────────────────────────

    /**
     * Captures the full inventory snapshot for an online player.
     * Must be called from the main server thread.
     *
     * The returned string contains:
     *   - Human-readable item listing (text block)
     *   - Context section: Health, Food, GameMode, World, Coords
     *   - ARMOR_FLAGS:<helmet>|<chest>|<legs>|<boots>|<offhand>  (true/false per slot)
     *   - BASE64:<serialized ItemStack[]>  (41 items: [0-35]=main, [36-39]=armour, [40]=offhand)
     *
     * Returns null if snapshots are disabled or player is offline.
     */
    public String getInventorySnapshot(UUID playerUuid) {
        if (!plugin.getConfig().getBoolean("evidence.inventory-snapshot", true)) return null;
        if (playerUuid == null) return null;

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) return null;

        Location loc       = player.getLocation();
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] armor    = player.getInventory().getArmorContents();
        ItemStack   offhand  = player.getInventory().getItemInOffHand();

        StringBuilder sb = new StringBuilder();
        sb.append("=== Inventory of ").append(player.getName())
          .append(" at ").append(LocalTime.now().withNano(0)).append(" ===\n");

        // Main content listing
        appendSection(sb, "Hotbar (0-8)", contents, 0, 9);
        appendSection(sb, "Main  (9-35)", contents, 9, 36);

        // Armor — only list slots that are actually worn
        boolean hasAnyArmor = hasArmor(armor);
        if (hasAnyArmor) {
            sb.append("Armor:\n");
            String[] slotNames = {"Boots", "Leggings", "Chestplate", "Helmet"};
            for (int i = 0; i < armor.length; i++) {
                if (isPresent(armor[i])) {
                    sb.append("  ").append(slotNames[i]).append(": ")
                      .append(formatItem(armor[i])).append("\n");
                }
            }
        } else {
            sb.append("Armor: (none)\n");
        }

        // Off-hand
        if (isPresent(offhand)) {
            sb.append("Offhand: ").append(formatItem(offhand)).append("\n");
        }

        // Context
        sb.append("\nContext:\n");
        sb.append("  Health:   ").append(String.format("%.1f/%.1f", player.getHealth(), player.getMaxHealth())).append("\n");
        sb.append("  Food:     ").append(player.getFoodLevel()).append("/20\n");
        sb.append("  GameMode: ").append(player.getGameMode().name()).append("\n");
        sb.append("  World:    ").append(loc.getWorld() != null ? loc.getWorld().getName() : "unknown").append("\n");
        sb.append("  Coords:   [")
          .append((int) loc.getX()).append(", ")
          .append((int) loc.getY()).append(", ")
          .append((int) loc.getZ()).append("]\n");

        // Armor presence flags — used by InventorySnapshotGUI to skip empty slots
        sb.append("ARMOR_FLAGS:")
          .append(isPresent(armor.length > 3 ? armor[3] : null)).append("|")  // helmet
          .append(isPresent(armor.length > 2 ? armor[2] : null)).append("|")  // chestplate
          .append(isPresent(armor.length > 1 ? armor[1] : null)).append("|")  // leggings
          .append(isPresent(armor.length > 0 ? armor[0] : null)).append("|")  // boots
          .append(isPresent(offhand)).append("\n");

        // Binary serialisation — 41-slot array for GUI reconstruction
        try {
            ItemStack[] full = new ItemStack[41];
            System.arraycopy(contents, 0, full, 0, Math.min(contents.length, 36));
            for (int i = 0; i < Math.min(armor.length, 4); i++) full[36 + i] = armor[i];
            full[40] = offhand;
            sb.append("BASE64:").append(InventorySerializer.itemStackArrayToBase64(full)).append("\n");
        } catch (Exception e) {
            plugin.getLogger().warning("Could not serialise inventory for " + player.getName() + ": " + e.getMessage());
        }

        return sb.toString();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void appendSection(StringBuilder sb, String label, ItemStack[] contents, int from, int to) {
        sb.append(label).append(":\n");
        for (int i = from; i < Math.min(to, contents.length); i++) {
            if (isPresent(contents[i])) {
                sb.append("  Slot ").append(String.format("%2d", i))
                  .append(": ").append(formatItem(contents[i])).append("\n");
            }
        }
    }

    private String formatItem(ItemStack item) {
        StringBuilder s = new StringBuilder(item.getType().name());
        s.append(" x").append(item.getAmount());
        if (item.hasItemMeta()) {
            var meta = item.getItemMeta();
            if (meta.hasDisplayName()) s.append(" [\"").append(meta.getDisplayName()).append("\"]");
            if (meta.hasEnchants()) {
                s.append(" {");
                meta.getEnchants().forEach((e, lvl) -> s.append(e.getKey().getKey()).append(":").append(lvl).append(" "));
                s.append("}");
            }
            if (meta.isUnbreakable()) s.append(" (Unbreakable)");
        }
        return s.toString();
    }

    private boolean hasArmor(ItemStack[] armor) {
        if (armor == null) return false;
        for (ItemStack piece : armor) if (isPresent(piece)) return true;
        return false;
    }

    private boolean isPresent(ItemStack item) {
        return item != null && !item.getType().isAir();
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    public void clearHistory(UUID playerUuid) {
        chatHistory.remove(playerUuid);
    }

    public void clearAll() {
        chatHistory.clear();
    }
}
