package me.abdoabk.reportx.command;

import me.abdoabk.reportx.ReportXPlugin;
import me.abdoabk.reportx.gui.PlayerReportGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class ReportsCommand implements CommandExecutor, TabCompleter {

    private final ReportXPlugin plugin;

    public ReportsCommand(ReportXPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageUtil().color("&cOnly players can use this command."));
            return true;
        }

        if (!player.hasPermission("reportx.use")) {
            plugin.getMessageUtil().send(player, "general.no-permission");
            return true;
        }

        int page = 1;
        if (args.length > 0) {
            try { page = Math.max(1, Integer.parseInt(args[0])); } catch (NumberFormatException ignored) {}
        }

        final int finalPage = page;

        plugin.getReportService().getRepository().findByReporter(player.getUniqueId())
                .thenAccept(reports -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (reports.isEmpty()) {
                            plugin.getMessageUtil().send(player, "report.no-reports");
                            return;
                        }
                        PlayerReportGUI gui = new PlayerReportGUI(plugin, player, reports, finalPage);
                        gui.open();
                    });
                });

        return true;
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("reportx.use")) {
            String prefix = args[0];
            return List.of("1", "2", "3", "4", "5").stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}