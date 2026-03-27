package me.abdoabk.reportx.repository;

import me.abdoabk.reportx.ReportXPlugin;
import me.abdoabk.reportx.util.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Logger;

public class H2ReportRepository extends AbstractSQLRepository {

    private final DatabaseConfig.H2Config config;

    public H2ReportRepository(ReportXPlugin plugin, DatabaseConfig.H2Config config, Logger logger) {
        super(plugin, logger);
        this.config = config;
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            logger.severe("[ReportX] H2 driver not found!");
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:h2:" + config.getPath() +
                        ";AUTO_SERVER=FALSE" +
                        ";MODE=MySQL" +
                        ";DATABASE_TO_LOWER=TRUE",
                config.getUsername(),
                config.getPassword()
        );
    }

    @Override
    protected String getCreateReportsTable() {
        return """
                CREATE TABLE IF NOT EXISTS reports (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    reporter_uuid VARCHAR(36) NOT NULL,
                    reporter_name VARCHAR(32) NOT NULL,
                    accused_uuid VARCHAR(36) NOT NULL,
                    accused_name VARCHAR(32) NOT NULL,
                    reason CLOB NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
                    claimed_by VARCHAR(36),
                    created_at TIMESTAMP NOT NULL,
                    resolved_at TIMESTAMP NULL,
                    verdict VARCHAR(100),
                    world VARCHAR(50),
                    x DOUBLE,
                    y DOUBLE,
                    z DOUBLE,
                    chat_snapshot CLOB,
                    inventory_snapshot CLOB,
                    reporter_ip VARCHAR(50)
                )
                """;
    }

    @Override
    protected String getCreateNotesTable() {
        return """
                CREATE TABLE IF NOT EXISTS notes (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    report_id BIGINT NOT NULL,
                    staff_uuid VARCHAR(36) NOT NULL,
                    note CLOB NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """;
    }

    @Override
    protected String getCreateAuditTable() {
        return """
                CREATE TABLE IF NOT EXISTS audit_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    staff_uuid VARCHAR(36) NOT NULL,
                    action CLOB NOT NULL,
                    timestamp TIMESTAMP NOT NULL
                )
                """;
    }

    @Override
    protected String getTodayDateSQL() {
        return "CURRENT_DATE()";
    }
}