package me.abdoabk.reportx.command;

import me.abdoabk.reportx.ReportXPlugin;
import me.abdoabk.reportx.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * /report <player> <reason>
 *
 * Normal-player command — submits a report only.
 * Staff management commands have been moved to /reportadmin.
 */
public class ReportCommand implements CommandExecutor, TabCompleter {

    private final ReportXPlugin plugin;

    public ReportCommand(ReportXPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageUtil().color("&cOnly players can submit reports."));
            return true;
        }

        if (!player.hasPermission("reportx.use")) {
            plugin.getMessageUtil().send(player, "general.no-permission");
            return true;
        }

        if (args.length < 2) {
            sendHelp(player);
            return true;
        }

        String targetName = args[0];
        String reason     = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        submitReport(player, targetName, reason);
        return true;
    }

    private void submitReport(Player reporter, String targetName, String reason) {
        plugin.getReportService().submitReport(reporter, targetName, reason)
                .thenAccept(result -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    switch (result) {
                        case SUCCESS        -> plugin.getMessageUtil().sendRaw(reporter,
                                                  "&aYour report has been submitted successfully!");
                        case SELF_REPORT    -> plugin.getMessageUtil().send(reporter, "report.cannot-report-self");
                        case PLAYER_OFFLINE -> plugin.getMessageUtil().send(reporter, "report.cannot-report-offline");
                        case ON_COOLDOWN    -> plugin.getMessageUtil().send(reporter, "report.submit-cooldown",
                                                  Map.of("time", TimeUtil.formatSeconds(
                                                      plugin.getReportService().getCooldownRemaining(reporter.getUniqueId()))));
                        case MAX_DAILY      -> plugin.getMessageUtil().send(reporter, "report.submit-max-reached",
                                                  Map.of("max", String.valueOf(
                                                      plugin.getConfig().getInt("report.max-per-day", 5))));
                        case DUPLICATE      -> plugin.getMessageUtil().send(reporter, "report.duplicate-report",
                                                  Map.of("player", targetName));
                        case REASON_TOO_SHORT -> plugin.getMessageUtil().send(reporter, "report.reason-too-short",
                                                  Map.of("min", String.valueOf(
                                                      plugin.getConfig().getInt("report.min-reason-length", 5))));
                        case REASON_TOO_LONG  -> plugin.getMessageUtil().send(reporter, "report.reason-too-long",
                                                  Map.of("max", String.valueOf(
                                                      plugin.getConfig().getInt("report.max-reason-length", 200))));
                    }
                }));
    }

    private void sendHelp(Player player) {
        String line = "&8&m" + "─".repeat(36);
        plugin.getMessageUtil().sendRaw(player, line);
        plugin.getMessageUtil().sendRaw(player, "  &cReport&4X &7— Report a Player");
        plugin.getMessageUtil().sendRaw(player, line);
        plugin.getMessageUtil().sendRaw(player, "&e/report <player> <reason>  &7Submit a report");
        plugin.getMessageUtil().sendRaw(player, "&e/reports [page]            &7View your submitted reports");
        if (player.hasPermission("reportx.staff"))
            plugin.getMessageUtil().sendRaw(player, "&e/reportadmin               &7Staff management panel");
        plugin.getMessageUtil().sendRaw(player, line);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player))
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
