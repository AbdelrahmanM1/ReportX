package me.abdoabk.reportx;

import me.abdoabk.reportx.api.ReportXAPI;
import me.abdoabk.reportx.command.ReportCommand;
import me.abdoabk.reportx.command.ReportsCommand;
import me.abdoabk.reportx.command.StaffCommand;
import me.abdoabk.reportx.gui.GUIManager;
import me.abdoabk.reportx.gui.PlayerReportGUI;
import me.abdoabk.reportx.gui.StaffReportGUI;
import me.abdoabk.reportx.listener.ChatListener;
import me.abdoabk.reportx.listener.InventoryListener;
import me.abdoabk.reportx.repository.H2ReportRepository;
import me.abdoabk.reportx.repository.MySQLReportRepository;
import me.abdoabk.reportx.repository.ReportRepository;
import me.abdoabk.reportx.service.EvidenceService;
import me.abdoabk.reportx.service.NotificationService;
import me.abdoabk.reportx.service.ReportService;
import me.abdoabk.reportx.util.DatabaseConfig;
import me.abdoabk.reportx.util.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ReportXPlugin extends JavaPlugin {

    private ReportRepository repository;
    private ReportService reportService;
    private EvidenceService evidenceService;
    private NotificationService notificationService;
    private MessageUtil messageUtil;

    private GUIManager<PlayerReportGUI> playerGUIManager;
    private GUIManager<StaffReportGUI>  staffGUIManager;
    private GUIManager<Object>          evidenceGUIManager;

    private FileConfiguration messagesConfig;
    private File messagesFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultMessages();
        loadMessagesConfig();

        messageUtil     = new MessageUtil(this);
        evidenceService = new EvidenceService(this);

        initDatabase();

        notificationService = new NotificationService(this);
        reportService       = new ReportService(this, repository, evidenceService, notificationService);

        playerGUIManager   = new GUIManager<>();
        staffGUIManager    = new GUIManager<>();
        evidenceGUIManager = new GUIManager<>();

        // Initialise public API
        ReportXAPI.init(this);

        // Commands
        ReportCommand reportCmd = new ReportCommand(this);
        getCommand("report").setExecutor(reportCmd);
        getCommand("report").setTabCompleter(reportCmd);

        ReportsCommand reportsCmd = new ReportsCommand(this);
        getCommand("reports").setExecutor(reportsCmd);
        getCommand("reports").setTabCompleter(reportsCmd);

        StaffCommand staffCmd = new StaffCommand(this);
        getCommand("rstaff").setExecutor(staffCmd);
        getCommand("rstaff").setTabCompleter(staffCmd);

        // Listeners
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);

        getLogger().info("╔═══════════════════════╗");
        getLogger().info("║    ReportX v1.0.0     ║");
        getLogger().info("║   by abdoabk          ║");
        getLogger().info("╚═══════════════════════╝");
        getLogger().info("ReportX enabled successfully!");
    }

    @Override
    public void onDisable() {
        ReportXAPI.shutdown();
        if (evidenceService != null) evidenceService.clearAll();
        if (repository      != null) repository.close();
        getLogger().info("ReportX disabled.");
    }

    // ── Database init ────────────────────────────────────────────────

    private void initDatabase() {
        String type = getConfig().getString("database.type", "H2").toUpperCase();
        DatabaseConfig dbConfig = new DatabaseConfig();

        if ("MYSQL".equals(type)) {
            dbConfig.setType(DatabaseConfig.Type.MYSQL);
            DatabaseConfig.MySQLConfig mysql = new DatabaseConfig.MySQLConfig();
            mysql.setHost(getConfig().getString("database.mysql.host", "localhost"));
            mysql.setPort(getConfig().getInt("database.mysql.port", 3306));
            mysql.setDatabase(getConfig().getString("database.mysql.database", "reportx"));
            mysql.setUsername(getConfig().getString("database.mysql.username", "root"));
            mysql.setPassword(getConfig().getString("database.mysql.password", ""));
            mysql.setPoolSize(getConfig().getInt("database.mysql.pool-size", 10));
            mysql.setUseSsl(getConfig().getBoolean("database.mysql.use-ssl", false));
            dbConfig.setMysql(mysql);

            try {
                repository = new MySQLReportRepository(dbConfig.getMysql(), getLogger());
                getLogger().info("[ReportX] Using MySQL at " + mysql.getHost() + ":" + mysql.getPort());
            } catch (Exception e) {
                getLogger().severe("[ReportX] MySQL failed, falling back to H2: " + e.getMessage());
                initH2(dbConfig);
            }
        } else {
            initH2(dbConfig);
        }

        repository.initialize().join();
    }

    private void initH2(DatabaseConfig dbConfig) {
        DatabaseConfig.H2Config h2 = new DatabaseConfig.H2Config();
        h2.setPath(getDataFolder().getAbsolutePath() + "/reportx-data");
        h2.setUsername(getConfig().getString("database.h2.username", "ReportX"));
        h2.setPassword(getConfig().getString("database.h2.password", "ReportX"));
        dbConfig.setH2(h2);

        repository = new H2ReportRepository(h2, getLogger());
        getLogger().info("[ReportX] Using H2 embedded database with user " + h2.getUsername());
    }

    // ── Config helpers ───────────────────────────────────────────────

    private void saveDefaultMessages() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) saveResource("messages.yml", false);
    }

    public void loadMessagesConfig() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) saveResource("messages.yml", false);
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        InputStream def = getResource("messages.yml");
        if (def != null) {
            messagesConfig.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(def, StandardCharsets.UTF_8)));
        }
    }

    public void reloadConfigurations() {
        reloadConfig();
        loadMessagesConfig();
    }

    // ── Getters ──────────────────────────────────────────────────────

    public FileConfiguration            getMessagesConfig()     { return messagesConfig;     }
    public ReportService                getReportService()      { return reportService;      }
    public EvidenceService              getEvidenceService()    { return evidenceService;    }
    public MessageUtil                  getMessageUtil()        { return messageUtil;         }
    public GUIManager<PlayerReportGUI>  getPlayerGUIManager()  { return playerGUIManager;   }
    public GUIManager<StaffReportGUI>   getStaffGUIManager()   { return staffGUIManager;    }
    public GUIManager<Object>           getEvidenceGUIManager(){ return evidenceGUIManager; }
}