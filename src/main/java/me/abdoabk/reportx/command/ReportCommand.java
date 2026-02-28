package me.abdoabk.reportx.command;

import me.abdoabk.reportx.ReportXPlugin;
import me.abdoabk.reportx.model.Report;
import me.abdoabk.reportx.model.ReportStatus;
import me.abdoabk.reportx.service.ReportService;
import me.abdoabk.reportx.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ReportCommand implements CommandExecutor, TabCompleter {

    private final ReportXPlugin plugin;

    // Tracks scheduled auto-unvanish tasks per player so we can cancel them
    // if the player manually unvanishes before the timer fires
    private final Map<UUID, BukkitTask> pendingUnvanishTasks = new ConcurrentHashMap<>();

    private static final List<String> STAFF_SUBS = List.of(
            "claim", "close", "reject", "escalate", "note", "view", "status", "unvanish");
    private static final List<String> VERDICTS = List.of(
            "Valid", "Invalid", "Warned", "Banned", "Muted", "Insufficient_Evidence");
    private static final List<String> STATUS_VALUES = Arrays.stream(ReportStatus.values())
            .map(ReportStatus::name)
            .collect(Collectors.toList());

    public ReportCommand(ReportXPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Command dispatch ──────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "claim"    -> handleClaim(sender, args);
            case "close"    -> handleClose(sender, args);
            case "reject"   -> handleReject(sender, args);
            case "escalate" -> handleEscalate(sender, args);
            case "note"     -> handleNote(sender, args);
            case "view"     -> handleView(sender, args);
            case "status"   -> handleStatus(sender, args);
            case "tp"       -> handleTeleport(sender, args);
            case "unvanish" -> handleUnvanish(sender);
            case "stats"    -> handleStats(sender);
            case "audit"    -> handleAudit(sender, args);
            case "reload"   -> handleReload(sender);
            default -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getMessageUtil().color("&cOnly players can submit reports."));
                    return true;
                }
                if (!sender.hasPermission("reportx.use")) {
                    plugin.getMessageUtil().send(sender, "general.no-permission");
                    return true;
                }
                if (args.length < 2) { sendHelp(sender); return true; }
                String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                submitReport(player, args[0], reason);
            }
        }
        return true;
    }

    // ── Handlers ──────────────────────────────────────────────────────

    private void submitReport(Player reporter, String targetName, String reason) {
        plugin.getReportService().submitReport(reporter, targetName, reason)
                .thenAccept(result -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    switch (result) {
                        case SUCCESS -> plugin.getMessageUtil().sendRaw(reporter,
                                "&aYour report has been submitted successfully!");
                        case SELF_REPORT    -> plugin.getMessageUtil().send(reporter, "report.cannot-report-self");
                        case PLAYER_OFFLINE -> plugin.getMessageUtil().send(reporter, "report.cannot-report-offline");
                        case ON_COOLDOWN    -> plugin.getMessageUtil().send(reporter, "report.submit-cooldown",
                                Map.of("time", TimeUtil.formatSeconds(
                                        plugin.getReportService().getCooldownRemaining(reporter.getUniqueId()))));
                        case MAX_DAILY      -> plugin.getMessageUtil().send(reporter, "report.submit-max-reached",
                                Map.of("max", String.valueOf(plugin.getConfig().getInt("report.max-per-day", 5))));
                        case DUPLICATE      -> plugin.getMessageUtil().send(reporter, "report.duplicate-report",
                                Map.of("player", targetName));
                        case REASON_TOO_SHORT -> plugin.getMessageUtil().send(reporter, "report.reason-too-short",
                                Map.of("min", String.valueOf(plugin.getConfig().getInt("report.min-reason-length", 5))));
                        case REASON_TOO_LONG  -> plugin.getMessageUtil().send(reporter, "report.reason-too-long",
                                Map.of("max", String.valueOf(plugin.getConfig().getInt("report.max-reason-length", 200))));
                    }
                }));
    }

    private void handleClaim(CommandSender sender, String[] args) {
        if (!requireStaff(sender) || !requirePlayer(sender)) return;
        if (args.length < 2) { usage(sender, "/report claim <id>"); return; }
        long id = parseLong(sender, args[1]); if (id < 0) return;
        Player staff = (Player) sender;
        plugin.getReportService().claimReport(id, staff)
                .thenAccept(ok -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (ok) plugin.getMessageUtil().send(sender, "staff.claim-success", Map.of("id", String.valueOf(id)));
                    else    plugin.getMessageUtil().sendRaw(sender, "&cFailed to claim report #" + id + ". It may not exist or is already claimed.");
                }));
    }

    private void handleClose(CommandSender sender, String[] args) {
        if (!requireStaff(sender) || !requirePlayer(sender)) return;
        if (args.length < 3) { usage(sender, "/report close <id> <verdict>"); return; }
        long id = parseLong(sender, args[1]); if (id < 0) return;
        String verdict = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        Player staff = (Player) sender;
        plugin.getReportService().closeReport(id, staff, verdict)
                .thenAccept(ok -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (ok) plugin.getMessageUtil().send(sender, "staff.close-success", Map.of("id", String.valueOf(id), "verdict", verdict));
                    else    plugin.getMessageUtil().sendRaw(sender, "&cFailed to close report #" + id + ". Check claim/permission status.");
                }));
    }

    private void handleReject(CommandSender sender, String[] args) {
        if (!requireStaff(sender) || !requirePlayer(sender)) return;
        if (args.length < 2) { usage(sender, "/report reject <id>"); return; }
        long id = parseLong(sender, args[1]); if (id < 0) return;
        Player staff = (Player) sender;
        plugin.getReportService().rejectReport(id, staff)
                .thenAccept(ok -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (ok) plugin.getMessageUtil().sendRaw(sender, "&aReport &e#" + id + " &arejected.");
                    else    plugin.getMessageUtil().sendRaw(sender, "&cFailed to reject report #" + id + ".");
                }));
    }

    private void handleEscalate(CommandSender sender, String[] args) {
        if (!requireStaff(sender) || !requirePlayer(sender)) return;
        if (args.length < 2) { usage(sender, "/report escalate <id>"); return; }
        long id = parseLong(sender, args[1]); if (id < 0) return;
        Player staff = (Player) sender;
        plugin.getReportService().escalateReport(id, staff)
                .thenAccept(ok -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (ok) plugin.getMessageUtil().send(sender, "staff.escalate-success", Map.of("id", String.valueOf(id)));
                    else    plugin.getMessageUtil().sendRaw(sender, "&cFailed to escalate report #" + id + ".");
                }));
    }

    private void handleNote(CommandSender sender, String[] args) {
        if (!requireStaff(sender) || !requirePlayer(sender)) return;
        if (args.length < 3) { usage(sender, "/report note <id> <message>"); return; }
        long id = parseLong(sender, args[1]); if (id < 0) return;
        String note = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        Player staff = (Player) sender;
        plugin.getReportService().addNote(id, staff, note)
                .thenAccept(ok -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (ok) plugin.getMessageUtil().send(sender, "staff.note-added", Map.of("id", String.valueOf(id)));
                    else    plugin.getMessageUtil().sendRaw(sender, "&cFailed to add note. Report #" + id + " may not exist.");
                }));
    }

    /** /report status <id> <STATUS> — directly change a report's status */
    private void handleStatus(CommandSender sender, String[] args) {
        if (!requireStaff(sender) || !requirePlayer(sender)) return;
        if (args.length < 3) { usage(sender, "/report status <id> <OPEN|CLAIMED|RESOLVED|REJECTED|ESCALATED>"); return; }
        long id = parseLong(sender, args[1]); if (id < 0) return;

        ReportStatus newStatus;
        try {
            newStatus = ReportStatus.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getMessageUtil().sendRaw(sender, "&cUnknown status '&e" + args[2] + "&c'. Valid: OPEN, CLAIMED, RESOLVED, REJECTED, ESCALATED");
            return;
        }

        Player staff = (Player) sender;
        plugin.getReportService().changeStatus(id, newStatus, staff)
                .thenAccept(result -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    switch (result) {
                        case SUCCESS          -> plugin.getMessageUtil().sendRaw(sender,
                                "&aReport &e#" + id + " &astatus changed to &e" + newStatus.name() + "&a.");
                        case REPORT_NOT_FOUND -> plugin.getMessageUtil().send(sender, "report.not-found",
                                Map.of("id", String.valueOf(id)));
                        case SAME_STATUS      -> plugin.getMessageUtil().sendRaw(sender,
                                "&eReport &f#" + id + " &eis already &f" + newStatus.name() + "&e.");
                        case NO_PERMISSION    -> plugin.getMessageUtil().send(sender, "general.no-permission");
                    }
                }));
    }

    /** /report view <id> — opens the Evidence GUI for this report */
    private void handleView(CommandSender sender, String[] args) {
        if (!requireStaff(sender) || !requirePlayer(sender)) return;
        if (args.length < 2) { usage(sender, "/report view <id>"); return; }
        long id = parseLong(sender, args[1]); if (id < 0) return;

        Player staff = (Player) sender;

        plugin.getReportService().getRepository().findById(id)
                .thenAccept(opt -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (opt.isEmpty()) {
                        plugin.getMessageUtil().send(sender, "report.not-found", Map.of("id", String.valueOf(id)));
                        return;
                    }
                    new me.abdoabk.reportx.gui.EvidencesSnapShotGUI(plugin, staff, opt.get()).open();
                }));
    }


    /**
     * /report tp <id>
     * Vanishes staff on arrival; auto-unvanishes after configured delay.
     * Pending auto-unvanish task is tracked so /report unvanish can cancel it.
     */
    private void handleTeleport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("reportx.teleport")) { plugin.getMessageUtil().send(sender, "general.no-permission"); return; }
        if (!requirePlayer(sender)) return;
        if (args.length < 2) { usage(sender, "/report tp <id>"); return; }
        long id = parseLong(sender, args[1]); if (id < 0) return;

        Player staff = (Player) sender;

        plugin.getReportService().getRepository().findById(id)
                .thenAccept(opt -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (opt.isEmpty()) {
                        plugin.getMessageUtil().send(sender, "report.not-found", Map.of("id", String.valueOf(id)));
                        return;
                    }
                    Report r = opt.get();
                    if (r.getWorld() == null) { plugin.getMessageUtil().send(sender, "staff.teleport-no-location"); return; }

                    var world = Bukkit.getWorld(r.getWorld());
                    if (world == null) { plugin.getMessageUtil().sendRaw(sender, "&cWorld '" + r.getWorld() + "' not found."); return; }

                    boolean autoVanish     = plugin.getConfig().getBoolean("staff.auto-vanish-on-tp", true);
                    int     unvanishAfterSec = plugin.getConfig().getInt("staff.unvanish-after-seconds", 30);

                    if (autoVanish) vanish(staff);

                    staff.teleport(new Location(world, r.getX(), r.getY(), r.getZ()));
                    plugin.getMessageUtil().send(sender, "staff.teleport-success",
                            Map.of("player", name(r.getAccusedUuid())));

                    if (autoVanish) {
                        plugin.getMessageUtil().sendRaw(staff,
                                "&7You are now &cvanished&7. Auto-visible in &e" + unvanishAfterSec
                                + "s &7or run &e/report unvanish &7to reveal yourself now.");

                        // Cancel any existing pending task for this player first
                        cancelPendingUnvanish(staff.getUniqueId());

                        // Schedule the auto-unvanish and store the task
                        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            pendingUnvanishTasks.remove(staff.getUniqueId());
                            if (staff.isOnline()) {
                                unvanish(staff);
                                plugin.getMessageUtil().sendRaw(staff, "&7You are now &avisible&7 again &8(auto)&7.");
                            }
                        }, unvanishAfterSec * 20L);

                        pendingUnvanishTasks.put(staff.getUniqueId(), task);
                    }
                }));
    }

    /**
     * /report unvanish — manually reveal yourself immediately.
     * Cancels the pending auto-unvanish timer so it doesn't fire again.
     */
    private void handleUnvanish(CommandSender sender) {
        if (!requireStaff(sender) || !requirePlayer(sender)) return;
        Player staff = (Player) sender;

        // Check they are actually vanished (by our metadata)
        if (!staff.hasMetadata("vanished")) {
            plugin.getMessageUtil().sendRaw(sender, "&eYou are not currently vanished by ReportX.");
            return;
        }

        // Cancel the scheduled auto-unvanish so it doesn't fire redundantly
        cancelPendingUnvanish(staff.getUniqueId());

        unvanish(staff);
        plugin.getMessageUtil().sendRaw(staff, "&aYou are now &2visible&a. Auto-unvanish timer cancelled.");
    }

    // ── Vanish helpers ────────────────────────────────────────────────

    /** Hide the player from all other online players and set vanish metadata. */
    private void vanish(Player player) {
        Bukkit.getOnlinePlayers().forEach(p -> {
            if (!p.equals(player)) p.hidePlayer(plugin, player);
        });
        player.setMetadata("vanished", new FixedMetadataValue(plugin, true));
    }

    /** Reveal the player to all other online players and remove vanish metadata. */
    private void unvanish(Player player) {
        Bukkit.getOnlinePlayers().forEach(p -> {
            if (!p.equals(player)) p.showPlayer(plugin, player);
        });
        player.removeMetadata("vanished", plugin);
    }

    /** Cancel and remove any scheduled auto-unvanish task for the given UUID. */
    private void cancelPendingUnvanish(UUID uuid) {
        BukkitTask existing = pendingUnvanishTasks.remove(uuid);
        if (existing != null) existing.cancel();
    }

    // ── Stats / Audit / Reload ────────────────────────────────────────

    private void handleStats(CommandSender sender) {
        if (!sender.hasPermission("reportx.admin")) { plugin.getMessageUtil().send(sender, "general.no-permission"); return; }
        var repo = plugin.getReportService().getRepository();

        repo.countTotal().thenCombine(repo.countByStatus(ReportStatus.OPEN), (total, open) ->
        repo.countByStatus(ReportStatus.RESOLVED).thenCombine(repo.countByStatus(ReportStatus.REJECTED), (resolved, rejected) ->
        repo.findMostReportedPlayer().thenCombine(repo.findMostActiveStaff(), (mostReported, mostStaff) ->
        repo.getAverageResolutionTimeHours().thenAccept(avgTime ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getMessageUtil().send(sender, "stats.header");
                    plugin.getMessageUtil().send(sender, "stats.total",          Map.of("total",    String.valueOf(total)));
                    plugin.getMessageUtil().send(sender, "stats.open",           Map.of("open",     String.valueOf(open)));
                    plugin.getMessageUtil().send(sender, "stats.closed",         Map.of("resolved", String.valueOf(resolved)));
                    plugin.getMessageUtil().send(sender, "stats.rejected",       Map.of("rejected", String.valueOf(rejected)));
                    if (mostReported != null)
                        plugin.getMessageUtil().send(sender, "stats.most-reported",     Map.of("player", name(mostReported), "count", "?"));
                    if (mostStaff != null)
                        plugin.getMessageUtil().send(sender, "stats.most-active-staff", Map.of("staff",  name(mostStaff),    "count", "?"));
                    plugin.getMessageUtil().send(sender, "stats.avg-resolution", Map.of("time", TimeUtil.formatHours(avgTime)));
                    plugin.getMessageUtil().send(sender, "stats.footer");
                }))))).join();
    }

    private void handleAudit(CommandSender sender, String[] args) {
        if (!sender.hasPermission("reportx.audit")) { plugin.getMessageUtil().send(sender, "general.no-permission"); return; }
        int limit = 10;
        if (args.length > 1) {
            try { limit = Math.min(50, Integer.parseInt(args[1])); } catch (NumberFormatException ignored) {}
        }
        final int finalLimit = limit;
        plugin.getReportService().getRepository().findAuditLogs(finalLimit)
                .thenAccept(logs -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getMessageUtil().sendRaw(sender, "&8&m===== &cAudit Log &7(last " + finalLimit + ") &8&m=====");
                    if (logs.isEmpty()) { plugin.getMessageUtil().sendRaw(sender, "&7No entries found."); return; }
                    logs.forEach(log -> plugin.getMessageUtil().sendRaw(sender,
                            "&8[" + TimeUtil.formatTimeAgo(log.getTimestamp()) + "] &e"
                                    + name(log.getStaffUuid()) + " &f" + log.getAction()));
                }));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("reportx.admin")) { plugin.getMessageUtil().send(sender, "general.no-permission"); return; }
        plugin.reloadConfigurations();
        plugin.getMessageUtil().send(sender, "general.reload-success");
    }

    // ── Tab Completion ────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        String current = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            if (sender.hasPermission("reportx.staff"))    result.addAll(STAFF_SUBS);
            if (sender.hasPermission("reportx.teleport")) result.add("tp");
            if (sender.hasPermission("reportx.admin"))    result.addAll(List.of("stats", "reload"));
            if (sender.hasPermission("reportx.audit"))    result.add("audit");
            if (sender.hasPermission("reportx.use"))
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !(sender instanceof Player sp) || !p.equals(sp))
                        .map(Player::getName)
                        .forEach(result::add);
            return filter(result, current);
        }

        String sub = args[0].toLowerCase();

        if (args.length == 2) {
            return switch (sub) {
                case "claim", "reject", "escalate", "view", "note", "tp" -> filter(fetchLiveIds(sub), current);
                case "close"    -> filter(fetchLiveIds("close"),  current);
                case "status"   -> filter(fetchLiveIds("status"), current);
                case "audit"    -> filter(List.of("10", "25", "50"), current);
                default         -> List.of();
            };
        }

        if (args.length == 3) {
            return switch (sub) {
                case "close"  -> filter(VERDICTS,      current);
                case "status" -> filter(STATUS_VALUES, current);
                default       -> List.of();
            };
        }

        return List.of();
    }

    private List<String> fetchLiveIds(String sub) {
        List<ReportStatus> relevant = switch (sub) {
            case "claim"           -> List.of(ReportStatus.OPEN);
            case "reject", "close" -> List.of(ReportStatus.OPEN, ReportStatus.CLAIMED);
            case "escalate"        -> List.of(ReportStatus.CLAIMED);
            default                -> List.of(ReportStatus.OPEN, ReportStatus.CLAIMED, ReportStatus.ESCALATED);
        };
        List<String> ids = new ArrayList<>();
        for (ReportStatus s : relevant) {
            try {
                plugin.getReportService().getRepository().findByStatus(s)
                        .thenAccept(list -> list.forEach(r -> ids.add(String.valueOf(r.getId()))))
                        .get(50, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {}
        }
        return ids.isEmpty() ? List.of("<id>") : ids;
    }

    private List<String> filter(List<String> list, String prefix) {
        if (prefix.isEmpty()) return list;
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
    }


    private void sendHelp(CommandSender sender) {
        String line = "&8&m" + "─".repeat(36);
        plugin.getMessageUtil().sendRaw(sender, line);
        plugin.getMessageUtil().sendRaw(sender, "  &cReport&4X &7— Command Reference");
        plugin.getMessageUtil().sendRaw(sender, line);
        plugin.getMessageUtil().sendRaw(sender, "&e/report <player> <reason>  &7Submit a report");
        plugin.getMessageUtil().sendRaw(sender, "&e/reports [page]            &7View your submitted reports");
        if (sender.hasPermission("reportx.staff")) {
            plugin.getMessageUtil().sendRaw(sender, "&e/rstaff [page]             &7Staff report panel");
            plugin.getMessageUtil().sendRaw(sender, "&e/report claim <id>         &7Claim a report");
            plugin.getMessageUtil().sendRaw(sender, "&e/report close <id> <v>     &7Close with verdict");
            plugin.getMessageUtil().sendRaw(sender, "&e/report reject <id>        &7Reject a report");
            plugin.getMessageUtil().sendRaw(sender, "&e/report escalate <id>      &7Escalate a report");
            plugin.getMessageUtil().sendRaw(sender, "&e/report note <id> <msg>    &7Add staff note");
            plugin.getMessageUtil().sendRaw(sender, "&e/report view <id>          &7View full details");
            plugin.getMessageUtil().sendRaw(sender, "&e/report status <id> <s>    &7Set report status directly");
            plugin.getMessageUtil().sendRaw(sender, "&e/report unvanish           &7Reveal yourself immediately");
        }
        if (sender.hasPermission("reportx.teleport"))
            plugin.getMessageUtil().sendRaw(sender, "&e/report tp <id>            &7Teleport to report location");
        if (sender.hasPermission("reportx.admin")) {
            plugin.getMessageUtil().sendRaw(sender, "&e/report stats              &7View statistics");
            plugin.getMessageUtil().sendRaw(sender, "&e/report reload             &7Reload configuration");
        }
        if (sender.hasPermission("reportx.audit"))
            plugin.getMessageUtil().sendRaw(sender, "&e/report audit [limit]      &7View audit log");
        plugin.getMessageUtil().sendRaw(sender, line);
    }


    private boolean requireStaff(CommandSender sender) {
        if (!sender.hasPermission("reportx.staff")) {
            plugin.getMessageUtil().send(sender, "general.no-permission");
            return false;
        }
        return true;
    }

    private boolean requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageUtil().color("&cThis command requires a player."));
            return false;
        }
        return true;
    }

    private long parseLong(CommandSender sender, String s) {
        try { return Long.parseLong(s); }
        catch (NumberFormatException e) {
            plugin.getMessageUtil().sendRaw(sender, "&cInvalid ID: &e" + s);
            return -1;
        }
    }

    private void usage(CommandSender sender, String usage) {
        plugin.getMessageUtil().send(sender, "general.invalid-usage", Map.of("usage", usage));
    }

    private String name(UUID uuid) {
        String n = Bukkit.getOfflinePlayer(uuid).getName();
        return n != null ? n : uuid.toString().substring(0, 8);
    }

    private String statusColored(ReportStatus status) {
        return plugin.getMessageUtil().get("status." + status.name().toLowerCase());
    }
}