package me.abdoabk.reportx.repository;

import me.abdoabk.reportx.util.DatabaseConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

public class MySQLReportRepository extends AbstractSQLRepository {

    private final HikariDataSource dataSource;

    public MySQLReportRepository(DatabaseConfig.MySQLConfig config, Logger logger) {
        super(logger);

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(
                "jdbc:mysql://" + config.getHost() + ":" + config.getPort() + "/" + config.getDatabase() +
                        "?useSSL=" + config.isUseSsl() +
                        "&characterEncoding=utf8mb4" +
                        "&connectionCollation=utf8mb4_unicode_ci" +
                        "&serverTimezone=UTC"
        );
        hikari.setUsername(config.getUsername());
        hikari.setPassword(config.getPassword());
        hikari.setMaximumPoolSize(config.getPoolSize());
        hikari.setMinimumIdle(2);
        hikari.setConnectionTimeout(30000);
        hikari.setIdleTimeout(600000);
        hikari.setMaxLifetime(1800000);
        hikari.setPoolName("ReportX-MySQL");

        this.dataSource = new HikariDataSource(hikari);
    }

    @Override
    protected Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    protected String getCreateReportsTable() {
        return """
                CREATE TABLE IF NOT EXISTS reports (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    reporter_uuid VARCHAR(36) NOT NULL,
                    accused_uuid VARCHAR(36) NOT NULL,
                    reason TEXT NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    claimed_by VARCHAR(36),
                    created_at TIMESTAMP NOT NULL,
                    resolved_at TIMESTAMP,
                    verdict TEXT,
                    world VARCHAR(50),
                    x DOUBLE,
                    y DOUBLE,
                    z DOUBLE,
                    chat_snapshot LONGTEXT,
                    inventory_snapshot LONGTEXT,
                    reporter_ip VARCHAR(50)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;
    }

    @Override
    protected String getCreateNotesTable() {
        return """
                CREATE TABLE IF NOT EXISTS notes (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    report_id BIGINT NOT NULL,
                    staff_uuid VARCHAR(36) NOT NULL,
                    note TEXT NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;
    }

    @Override
    protected String getCreateAuditTable() {
        return """
                CREATE TABLE IF NOT EXISTS audit_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    staff_uuid VARCHAR(36) NOT NULL,
                    action TEXT NOT NULL,
                    timestamp TIMESTAMP NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;
    }

    @Override
    protected String getTodayDateSQL() {
        return "CURDATE()";
    }

    @Override
    public void close() {
        super.close();
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }
}