package me.abdoabk.reportx.command;

import me.abdoabk.reportx.ReportXPlugin;
import me.abdoabk.reportx.model.Report;
import me.abdoabk.reportx.model.ReportStatus;
import me.abdoabk.reportx.util.TimeUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
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

/**
 * /reportadmin [subcommand] [args]
 *
 * Staff-only management command.  Subcommands:
 *   claim <id>            – claim a report
 *   close <id> <verdict>  – resolve with verdict
 *   reject <id>           – reject
 *   escalate <id>         – escalate
 *   note <id> <msg>       – add a staff note
 *   notes <id>            – list notes
 *   view <id>             – open evidence GUI
 *   status <id> <STATUS>  – force-set status
 *   tp <id>               – teleport to report location
 *   unvanish              – reveal yourself
 *   web                   – generate dashboard link
 *   stats                 – view statistics (admin)
 *   audit [limit]         – view audit log
 *   reload                – reload config (admin)
 *   (no args)             – open staff report panel
 */
public class ReportAdminCommand implements CommandExecutor, TabCompleter {

    private final ReportXPlugin plugin;
    private final Map<UUID, BukkitTask> pendingUnvanishTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long>       webCooldowns         = new ConcurrentHashMap<>();

    private static final List<String> STAFF_SUBS = List.of(
            "claim", "close", "reject", "escalate", "note", "notes",
            "view", "status", "tp", "unvanish", "web");
    private static final List<String> ADMIN_SUBS  = List.of("stats", "reload");
    private static final List<String> AUDIT_SUBS  = List.of("audit");
    private static final List<String> VERDICTS    = List.of(
            "Valid", "Invalid", "Warned", "Banned", "Muted", "Insufficient_Evidence");
    private static final List<String> STATUS_VALUES = Arrays.stream(ReportStatus.values())
            .map(ReportStatus::name).collect(Collectors.toList());

    public ReportAdminCommand(ReportXPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Dispatch ──────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!requireStaff(sender)) return true;

        if (args.length == 0) {
            openStaffPanel(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "claim"    -> handleClaim(sender, args);
            case "close"    -> handleClose(sender, args);
            case "reject"   -> handleReject(sender, args);
            case "escalate" -> handleEscalate(sender, args);
            case "note"     -> handleNote(sender, args);
            case "notes"    -> handleNotes(sender, args);
            case "view"     -> handleView(sender, args);
            case "status"   -> handleStatus(sender, args);
            case "tp"       -> handleTeleport(sender, args);
            case "unvanish" -> handleUnvanish(sender);
            case "web"      -> handleWeb(sender);
            case "stats"    -> handleStats(sender);
            case "audit"    -> handleAudit(sender, args);
            case "reload"   -> handleReload(sender);
            default         -> sendHelp(sender);
        }
        return true;
    }

    // ── No-args: open GUI panel ───────────────────────────────────────

    private void openStaffPanel(CommandSender sender) {
        if (!requirePlayer(sender)) return;
        Player staff = (Player) sender;

        plugin.getReportService().getRepository().findByStatus(ReportStatus.OPEN)
                .thenCombine(
                    plugin.getReportService().getRepository().findByStatus(ReportStatus.CLAIMED),
                    (open, claimed) -> {
                        open.addAll(claimed);
                        open.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
                        return open;
                    })
                .thenAccept(reports -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (reports.isEmpty()) {
                        plugin.getMessageUtil().send(staff, "staff.no-reports");
                        return;
                    }
                    new me.abdoabk.reportx.gui.StaffReportGUI(plugin, staff, reports, 1).open();
                }));
    }

    // ── Handlers ──────────────────────────────────────────────────────

    private void handleClaim(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return;
        if (args.length < 2) { usage(sender, "/reportadmin claim <id>"); return; }
        long id = parseLong(sender, args[1]); if (id < 0) return;
        Player staff = (Player) sender;
        plugin.getReportService().claimReport(id, staff)
                .thenAccept(ok -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (ok) plugin.getMessageUtil().send(sender, "staff.claim-success", Map.of("id", String.valueOf(id)));
                    else    plugin.getMessageUtil().sendRaw(sender, "&cFailed to claim report #" + id + ". It may not exist or is already claimed.");
                }));
    }

    private void handleClose(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return;
        if (args.length < 3) { usage(sender, "/reportadmin close <id> <verdict>"); return; }
        long id = parseLong(sender, args[1]); if (id < 0) return;
        String verdict = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        Player staff = (Player) sender;
        plugin.getReportService().closeReport(id, staff, verdict)
                .thenAccept(ok -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (ok) plugin.getMessageUtil().send(sender, "staff.close-success",
                                Map.of("id", String.valueOf(id), "verdict", verdict));
                    else    plugin.getMessageUtil().sendRaw(sender, "&cFailed to close report #" + id + ". Check claim/permission status.");
                }));
    }

    private void handleReject(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return;
        if (args.length < 2) { usage(sender, "/reportadmin reject <id>"); return; }
        long id = parseLong(sender, args[1]); if (id < 0) return;
        Player staff = (Player) sender;
        plugin.getReportService().rejectReport(id, staff)
                .thenAccept(ok -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (ok) plugin.getMessageUtil().sendRaw(sender, "&aReport &e#" + id + " &arejected.");
                    else    plugin.getMessageUtil().sendRaw(sender, "&cFailed to reject report #" + id + ".");
                }));
    }

    private void handleEscalate(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return;
        if (args.length < 2) { usage(sender, "/reportadmin escalate <id>"); return; }
        long id = parseLong(sender, args[1]); if (id < 0) return;
        Player staff = (Player) sender;
        plugin.getReportService().escalateReport(id, staff)
                .thenAccept(ok -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (ok) plugin.getMessageUtil().send(sender, "staff.escalate-success", Map.of("id", String.valueOf(id)));
                    else    plugin.getMessageUtil().sendRaw(sender, "&cFailed to escalate report #" + id + ".");
                }));
    }

    private void handleNote(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return;
        if (args.length < 3) { usage(sender, "/reportadmin note <id> <message>"); return; }
        long id = parseLong(sender, args[1]); if (id < 0) return;
        String note = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        Player staff = (Player) sender;
        plugin.getReportService().addNote(id, staff, note)
                .thenAccept(ok -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (ok) plugin.getMessageUtil().send(sender, "staff.note-added", Map.of("id", String.valueOf(id)));
                    else    plugin.getMessageUtil().sendRaw(sender, "&cFailed to add note. Report #" + id + " may not exist.");
                }));
    }

    private void handleNotes(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return;
        if (args.length < 2) { usage(sender, "/reportadmin notes <id>"); return; }
        long id = parseLong(sender, args[1]); if (id < 0) return;
        plugin.getReportService().getRepository().findNotesByReport(id)
                .thenAccept(notes -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (notes.isEmpty()) {
                        plugin.getMessageUtil().sendRaw(sender, "&eNo notes found for report #" + id + ".");
                        return;
                    }
                    plugin.getMessageUtil().sendRaw(sender, "&6Notes for report #" + id + ":");
                    notes.forEach(note -> plugin.getMessageUtil().sendRaw(sender,
                            "&7[" + (note.getCreatedAt() != null ? note.getCreatedAt() : "") + "] " +
                            "&b" + (note.getStaffUuid() != null ? displayName(note.getStaffUuid()) : "unknown") +
                            "&7: &f" + note.getNote()));
                }));
    }

    private void handleView(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return;
        if (args.length < 2) { usage(sender, "/reportadmin view <id>"); return; }
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

    private void handleStatus(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return;
        if (args.length < 3) {
            usage(sender, "/reportadmin status <id> <OPEN|CLAIMED|RESOLVED|REJECTED|ESCALATED>");
            return;
        }
        long id = parseLong(sender, args[1]); if (id < 0) return;
        ReportStatus newStatus;
        try { newStatus = ReportStatus.valueOf(args[2].toUpperCase()); }
        catch (IllegalArgumentException e) {
            plugin.getMessageUtil().sendRaw(sender, "&cUnknown status '&e" + args[2] + "&c'.");
            return;
        }
        Player staff = (Player) sender;
        plugin.getReportService().changeStatus(id, newStatus, staff)
                .thenAccept(result -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    switch (result) {
                        case SUCCESS          -> plugin.getMessageUtil().sendRaw(sender,
                                "&aReport &e#" + id + " &astatus set to &e" + newStatus.name() + "&a.");
                        case REPORT_NOT_FOUND -> plugin.getMessageUtil().send(sender, "report.not-found",
                                Map.of("id", String.valueOf(id)));
                        case SAME_STATUS      -> plugin.getMessageUtil().sendRaw(sender,
                                "&eReport &f#" + id + " &eis already &f" + newStatus.name() + "&e.");
                        case NO_PERMISSION    -> plugin.getMessageUtil().send(sender, "general.no-permission");
                    }
                }));
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("reportx.teleport")) {
            plugin.getMessageUtil().send(sender, "general.no-permission"); return;
        }
        if (!requirePlayer(sender)) return;
        if (args.length < 2) { usage(sender, "/reportadmin tp <id>"); return; }
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

                    boolean autoVanish       = plugin.getConfig().getBoolean("staff.auto-vanish-on-tp", true);
                    int     unvanishAfterSec = plugin.getConfig().getInt("staff.unvanish-after-seconds", 30);

                    if (autoVanish) vanish(staff);
                    staff.teleport(new Location(world, r.getX(), r.getY(), r.getZ()));
                    plugin.getMessageUtil().send(sender, "staff.teleport-success",
                            Map.of("player", name(r.getAccusedUuid())));

                    if (autoVanish) {
                        plugin.getMessageUtil().sendRaw(staff,
                                "&7You are now &cvanished&7. Auto-visible in &e" + unvanishAfterSec
                                + "s &7or run &e/reportadmin unvanish&7.");
                        cancelPendingUnvanish(staff.getUniqueId());
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

    private void handleUnvanish(CommandSender sender) {
        if (!requirePlayer(sender)) return;
        Player staff = (Player) sender;
        if (!staff.hasMetadata("vanished")) {
            plugin.getMessageUtil().sendRaw(sender, "&eYou are not currently vanished by ReportX.");
            return;
        }
        cancelPendingUnvanish(staff.getUniqueId());
        unvanish(staff);
        plugin.getMessageUtil().sendRaw(staff, "&aYou are now &2visible&a. Auto-unvanish timer cancelled.");
    }

    private void handleWeb(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageUtil().color("&cOnly players can use /reportadmin web."));
            return;
        }

        int  cooldownSeconds = plugin.getConfig().getInt("web-dashboard.cooldown-seconds", 300);
        long now = System.currentTimeMillis();
        Long lastUse = webCooldowns.get(player.getUniqueId());
        if (lastUse != null && now - lastUse < cooldownSeconds * 1000L) {
            long remaining = (cooldownSeconds * 1000L - (now - lastUse)) / 1000;
            plugin.getMessageUtil().sendRaw(player, "&8[&cReport&4X&8] &c⏳ Cooldown active! Try again in &e" + remaining + " &cseconds.");
            return;
        }

        String role = player.hasPermission("reportx.admin")   ? "ADMIN"
                    : player.hasPermission("reportx.audit")   ? "SENIOR_STAFF"
                    : player.hasPermission("reportx.staff")   ? "STAFF"
                    : null;

        if (role == null) { plugin.getMessageUtil().send(player, "general.no-permission"); return; }

        String dbType = plugin.getConfig().getString("database.type", "H2").toUpperCase();
        if (!"MYSQL".equals(dbType)) {
            plugin.getMessageUtil().sendRaw(player, "&8[&cReport&4X&8] &c❌ Dashboard requires MySQL database.");
            plugin.getMessageUtil().sendRaw(player, "&8[&cReport&4X&8] &7Set &edatabase.type: MYSQL &7in config.yml.");
            return;
        }
        if (plugin.getWebTokenService() == null) {
            plugin.getMessageUtil().sendRaw(player, "&8[&cReport&4X&8] &c⚠️ Web token service unavailable. Check console.");
            return;
        }

        plugin.getMessageUtil().sendRaw(player, "&8[&cReport&4X&8] &e🔗 &7Generating your secure dashboard link...");

        final String finalRole = role;
        plugin.getWebTokenService().generateToken(
                player.getUniqueId().toString(), player.getName(), finalRole,
                token -> {
                    String baseUrl = plugin.getConfig().getString("web-dashboard.url", "http://localhost:5173");
                    int    ttl     = plugin.getConfig().getInt("web-dashboard.token-ttl-minutes", 30);
                    String link    = baseUrl + "/auth?token=" + token;

                    plugin.getMessageUtil().sendRaw(player, "&8[&cReport&4X&8] &a✅ &7Your dashboard link (click to open):");

                    TextComponent msg = new TextComponent(link);
                    msg.setColor(ChatColor.AQUA);
                    msg.setUnderlined(true);
                    msg.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, link));
                    msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new TextComponent[]{ new TextComponent("§eClick to open dashboard") }));
                    player.spigot().sendMessage(msg);

                    plugin.getMessageUtil().sendRaw(player,
                            "&8[&cReport&4X&8] &7⏰ Expires in &c" + ttl + " min &8• &cSingle use &8• &cDo not share");
                    plugin.getMessageUtil().sendRaw(player, "&8[&cReport&4X&8] &7👤 Role: &b" + finalRole);
                    webCooldowns.put(player.getUniqueId(), now);
                },
                err -> plugin.getMessageUtil().sendRaw(player, "&8[&cReport&4X&8] &c❌ Failed to generate link. Check console.")
        );
    }

    private void handleStats(CommandSender sender) {
        if (!sender.hasPermission("reportx.admin")) { plugin.getMessageUtil().send(sender, "general.no-permission"); return; }
        var repo = plugin.getReportService().getRepository();
        repo.countTotal().thenCombine(repo.countByStatus(ReportStatus.OPEN), (total, open) ->
                repo.countByStatus(ReportStatus.RESOLVED).thenCombine(repo.countByStatus(ReportStatus.REJECTED), (resolved, rejected) ->
                        repo.findMostReportedPlayer().thenCombine(repo.findMostActiveStaff(), (mostReported, mostStaff) ->
                                repo.getAverageResolutionTimeHours().thenAccept(avgTime ->
                                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                                            plugin.getMessageUtil().send(sender, "stats.header");
                                            plugin.getMessageUtil().send(sender, "stats.total",      Map.of("total",    String.valueOf(total)));
                                            plugin.getMessageUtil().send(sender, "stats.open",       Map.of("open",     String.valueOf(open)));
                                            plugin.getMessageUtil().send(sender, "stats.closed",     Map.of("resolved", String.valueOf(resolved)));
                                            plugin.getMessageUtil().send(sender, "stats.rejected",   Map.of("rejected", String.valueOf(rejected)));
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
        if (args.length > 1) { try { limit = Math.min(50, Integer.parseInt(args[1])); } catch (NumberFormatException ignored) {} }
        final int finalLimit = limit;
        plugin.getReportService().getRepository().findAuditLogs(finalLimit)
                .thenAccept(logs -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getMessageUtil().sendRaw(sender, "&8&m===== &cAudit Log &7(last " + finalLimit + ") &8&m=====");
                    if (logs.isEmpty()) { plugin.getMessageUtil().sendRaw(sender, "&7No entries found."); return; }
                    logs.forEach(log -> plugin.getMessageUtil().sendRaw(sender,
                            "&8[" + TimeUtil.formatTimeAgo(log.getTimestamp()) + "] &e"
                            + displayName(log.getStaffUuid()) + " &f" + log.getAction()));
                }));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("reportx.admin")) { plugin.getMessageUtil().send(sender, "general.no-permission"); return; }
        plugin.reloadConfigurations();
        plugin.getMessageUtil().send(sender, "general.reload-success");
    }

    // ── Help ──────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        String line = "&8&m" + "─".repeat(38);
        plugin.getMessageUtil().sendRaw(sender, line);
        plugin.getMessageUtil().sendRaw(sender, "  &cReport&4X &7— Staff Commands (/reportadmin)");
        plugin.getMessageUtil().sendRaw(sender, line);
        plugin.getMessageUtil().sendRaw(sender, "&e/reportadmin                 &7Open report panel");
        plugin.getMessageUtil().sendRaw(sender, "&e/reportadmin claim <id>      &7Claim a report");
        plugin.getMessageUtil().sendRaw(sender, "&e/reportadmin close <id> <v>  &7Close with verdict");
        plugin.getMessageUtil().sendRaw(sender, "&e/reportadmin reject <id>     &7Reject a report");
        plugin.getMessageUtil().sendRaw(sender, "&e/reportadmin escalate <id>   &7Escalate a report");
        plugin.getMessageUtil().sendRaw(sender, "&e/reportadmin note <id> <msg> &7Add staff note");
        plugin.getMessageUtil().sendRaw(sender, "&e/reportadmin notes <id>      &7List notes");
        plugin.getMessageUtil().sendRaw(sender, "&e/reportadmin view <id>       &7View evidence GUI");
        plugin.getMessageUtil().sendRaw(sender, "&e/reportadmin status <id> <s> &7Set status directly");
        plugin.getMessageUtil().sendRaw(sender, "&e/reportadmin web             &7Generate dashboard link");
        if (sender.hasPermission("reportx.teleport"))
            plugin.getMessageUtil().sendRaw(sender, "&e/reportadmin tp <id>         &7Teleport to location");
        if (sender.hasPermission("reportx.admin")) {
            plugin.getMessageUtil().sendRaw(sender, "&e/reportadmin stats           &7View statistics");
            plugin.getMessageUtil().sendRaw(sender, "&e/reportadmin reload          &7Reload configuration");
        }
        if (sender.hasPermission("reportx.audit"))
            plugin.getMessageUtil().sendRaw(sender, "&e/reportadmin audit [limit]   &7View audit log");
        plugin.getMessageUtil().sendRaw(sender, line);
    }

    // ── Tab Completion ────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("reportx.staff")) return List.of();
        List<String> result = new ArrayList<>();
        String current = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            result.addAll(STAFF_SUBS);
            if (sender.hasPermission("reportx.teleport")) result.add("tp");
            if (sender.hasPermission("reportx.admin"))    result.addAll(ADMIN_SUBS);
            if (sender.hasPermission("reportx.audit"))    result.addAll(AUDIT_SUBS);
            return filter(result, current);
        }

        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            return switch (sub) {
                case "claim", "reject", "escalate", "view", "note", "notes", "tp"
                    -> filter(fetchLiveIds(sub), current);
                case "close", "status"
                    -> filter(fetchLiveIds("close"), current);
                case "audit"
                    -> filter(List.of("10", "25", "50"), current);
                default -> List.of();
            };
        }

        if (args.length == 3) {
            return switch (sub) {
                case "close"  -> filter(VERDICTS, current);
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
        return list.stream().filter(s -> s.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
    }

    // ── Vanish helpers ────────────────────────────────────────────────

    private void vanish(Player player) {
        Bukkit.getOnlinePlayers().forEach(p -> { if (!p.equals(player)) p.hidePlayer(plugin, player); });
        player.setMetadata("vanished", new FixedMetadataValue(plugin, true));
    }

    private void unvanish(Player player) {
        Bukkit.getOnlinePlayers().forEach(p -> { if (!p.equals(player)) p.showPlayer(plugin, player); });
        player.removeMetadata("vanished", plugin);
    }

    private void cancelPendingUnvanish(UUID uuid) {
        BukkitTask existing = pendingUnvanishTasks.remove(uuid);
        if (existing != null) existing.cancel();
    }

    // ── Utilities ─────────────────────────────────────────────────────

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
        catch (NumberFormatException e) { plugin.getMessageUtil().sendRaw(sender, "&cInvalid ID: &e" + s); return -1; }
    }

    private void usage(CommandSender sender, String usage) {
        plugin.getMessageUtil().send(sender, "general.invalid-usage", Map.of("usage", usage));
    }

    private String name(UUID uuid) {
        String n = Bukkit.getOfflinePlayer(uuid).getName();
        return n != null ? n : uuid.toString().substring(0, 8);
    }

    private String displayName(UUID uuid) {
        String n = Bukkit.getOfflinePlayer(uuid).getName();
        return n != null ? n + " (" + uuid.toString().substring(0, 8) + ")" : uuid.toString().substring(0, 8);
    }
}
