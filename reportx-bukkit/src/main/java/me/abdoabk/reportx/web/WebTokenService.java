package me.abdoabk.reportx.web;

import me.abdoabk.reportx.repository.AbstractSQLRepository;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.function.Consumer;
import java.util.logging.Level;

public class WebTokenService {

    private static final SecureRandom      SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter SQL_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS web_tokens (
                token      VARCHAR(64)  PRIMARY KEY,
                staff_uuid VARCHAR(36)  NOT NULL,
                username   VARCHAR(32)  NOT NULL,
                role       VARCHAR(20)  NOT NULL,
                expires_at TIMESTAMP    NOT NULL,
                created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                used       TINYINT(1)   NOT NULL DEFAULT 0,
                INDEX idx_expires (expires_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;

    private static final String INSERT_TOKEN =
            "INSERT INTO web_tokens (token, staff_uuid, username, role, expires_at, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, NOW())";

    private static final String DELETE_EXPIRED =
            "DELETE FROM web_tokens WHERE expires_at < NOW()";

    private final AbstractSQLRepository repository;
    private final JavaPlugin            plugin;

    public WebTokenService(AbstractSQLRepository repository, JavaPlugin plugin) {
        this.repository = repository;
        this.plugin     = plugin;
    }

    public void initialize() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = repository.getConnectionPublic();
                 Statement stmt  = conn.createStatement()) {
                stmt.execute(CREATE_TABLE);
                plugin.getLogger().info("[ReportX] web_tokens table ready.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "[ReportX] Failed to create web_tokens table: " + e.getMessage(), e);
            }
        });
    }

    public void generateToken(String staffUuid,
                              String username,
                              String role,
                              Consumer<String>    onSuccess,
                              Consumer<Exception> onFailure) {

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {

            try (Connection conn = repository.getConnectionPublic();
                 PreparedStatement ps = conn.prepareStatement(DELETE_EXPIRED)) {
                ps.executeUpdate();
            } catch (SQLException ignored) {}

            byte[] bytes = new byte[32];
            SECURE_RANDOM.nextBytes(bytes);
            String token = HexFormat.of().formatHex(bytes);

            int    ttl       = plugin.getConfig().getInt("web-dashboard.token-ttl-minutes", 30);
            String expiresAt = LocalDateTime.now().plusMinutes(ttl).format(SQL_FMT);

            try (Connection conn = repository.getConnectionPublic();
                 PreparedStatement ps = conn.prepareStatement(INSERT_TOKEN)) {

                ps.setString(1, token);
                ps.setString(2, staffUuid);
                ps.setString(3, username);
                ps.setString(4, role);
                ps.setString(5, expiresAt);
                ps.executeUpdate();

                plugin.getServer().getScheduler()
                        .runTask(plugin, () -> onSuccess.accept(token));

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "[ReportX] Failed to insert web token for " + username + ": " + e.getMessage(), e);
                plugin.getServer().getScheduler()
                        .runTask(plugin, () -> onFailure.accept(e));
            }
        });
    }
}