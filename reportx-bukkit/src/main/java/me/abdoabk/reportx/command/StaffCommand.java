package me.abdoabk.reportx.command;

import me.abdoabk.reportx.ReportXPlugin;
import me.abdoabk.reportx.gui.StaffReportGUI;
import me.abdoabk.reportx.model.ReportStatus;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class StaffCommand implements CommandExecutor, TabCompleter {

    private final ReportXPlugin plugin;

    public StaffCommand(ReportXPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageUtil().color("&cOnly players can use this command."));
            return true;
        }

        if (!player.hasPermission("reportx.staff")) {
            plugin.getMessageUtil().send(player, "general.no-permission");
            return true;
        }

        int page = 1;
        if (args.length > 0) {
            try { page = Math.max(1, Integer.parseInt(args[0])); } catch (NumberFormatException ignored) {}
        }

        final int finalPage = page;

        plugin.getReportService().getRepository().findByStatus(ReportStatus.OPEN)
                .thenCombine(plugin.getReportService().getRepository().findByStatus(ReportStatus.CLAIMED),
                        (open, claimed) -> {
                            open.addAll(claimed);
                            open.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
                            return open;
                        })
                .thenAccept(reports -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (reports.isEmpty()) {
                            plugin.getMessageUtil().send(player, "staff.no-reports");
                            return;
                        }
                        StaffReportGUI gui = new StaffReportGUI(plugin, player, reports, finalPage);
                        gui.open();
                    });
                });

        return true;
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("reportx.staff")) return List.of();

        if (args.length == 1) {
            String prefix = args[0];
            return List.of("1", "2", "3", "4", "5", "6", "7", "8", "9").stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}