package me.abdoabk.reportx.util;

import me.abdoabk.reportx.ReportXPlugin;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MessageUtil {

    private final ReportXPlugin plugin;

    public MessageUtil(ReportXPlugin plugin) {
        this.plugin = plugin;
    }

    public String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String get(String path) {
        FileConfiguration msgs = plugin.getMessagesConfig();
        String msg = msgs.getString(path, "&cMessage not found: " + path);
        return color(msg);
    }

    public String get(String path, Map<String, String> placeholders) {
        String msg = get(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
        }
        return msg;
    }

    public List<String> getList(String path) {
        FileConfiguration msgs = plugin.getMessagesConfig();
        return msgs.getStringList(path).stream()
                .map(this::color)
                .collect(Collectors.toList());
    }

    public List<String> getList(String path, Map<String, String> placeholders) {
        return getList(path).stream().map(line -> {
            String result = line;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
            }
            return result;
        }).collect(Collectors.toList());
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(get(path));
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(get(path, placeholders));
    }

    public void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }

    public void broadcast(String path, Map<String, String> placeholders, String permission) {
        String msg = get(path, placeholders);
        plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> p.hasPermission(permission))
                .forEach(p -> p.sendMessage(msg));
    }

    public static String sanitize(String input) {
        if (input == null) return "";
        // Remove SQL injection attempts and dangerous characters
        return input.replaceAll("['\";\\\\]", "").trim();
    }
}
