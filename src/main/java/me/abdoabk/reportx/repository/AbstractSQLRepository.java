package me.abdoabk.reportx.repository;

import me.abdoabk.reportx.model.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public abstract class AbstractSQLRepository implements ReportRepository {

    protected final Logger logger;
    protected final ExecutorService executor = Executors.newFixedThreadPool(4);

    protected AbstractSQLRepository(Logger logger) {
        this.logger = logger;
    }

    protected abstract Connection getConnection() throws SQLException;

    protected abstract String getCreateReportsTable();
    protected abstract String getCreateNotesTable();
    protected abstract String getCreateAuditTable();

    /** Returns the SQL expression for "today" for your database */
    protected abstract String getTodayDateSQL();

    // ------------------- Initialization -------------------
    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {

                stmt.execute(getCreateReportsTable());
                stmt.execute(getCreateNotesTable());
                stmt.execute(getCreateAuditTable());

                runMigrations(conn);
                logger.info("[ReportX] Database tables initialized.");

            } catch (SQLException e) {
                logger.severe("[ReportX] Failed to initialize database: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private void runMigrations(Connection conn) {
        addColumnIfMissing(conn, "reports", "chat_snapshot", "CLOB");
        addColumnIfMissing(conn, "reports", "inventory_snapshot", "CLOB");
        addColumnIfMissing(conn, "reports", "reporter_ip", "VARCHAR(50)");
    }

    private void addColumnIfMissing(Connection conn, String table, String column, String type) {
        try (ResultSet rs1 = conn.getMetaData().getColumns(null, null, table.toUpperCase(), column.toUpperCase())) {
            if (rs1.next()) return;
        } catch (SQLException ignored) {}
        try (ResultSet rs2 = conn.getMetaData().getColumns(null, null, table, column)) {
            if (rs2.next()) return;
        } catch (SQLException ignored) {}

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            logger.info("[ReportX] Migration applied: added column '" + column + "' to table '" + table + "'.");
        } catch (SQLException e) {
            logger.warning("[ReportX] Migration note for '" + column + "': " + e.getMessage());
        }
    }

    // ------------------- Report CRUD -------------------
    @Override
    public CompletableFuture<Long> saveReport(Report report) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO reports " +
                    "(reporter_uuid, accused_uuid, reason, status, world, x, y, z, created_at, chat_snapshot, inventory_snapshot, reporter_ip) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, report.getReporterUuid().toString());
                ps.setString(2, report.getAccusedUuid().toString());
                ps.setString(3, report.getReason());
                ps.setString(4, report.getStatus().name());
                ps.setString(5, report.getWorld());
                ps.setDouble(6, report.getX());
                ps.setDouble(7, report.getY());
                ps.setDouble(8, report.getZ());
                ps.setTimestamp(9, Timestamp.valueOf(report.getCreatedAt()));
                ps.setString(10, report.getChatSnapshot());
                ps.setString(11, report.getInventorySnapshot());
                ps.setString(12, report.getReporterIp());

                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : -1L;
                }
            } catch (SQLException e) {
                logger.severe("[ReportX] Failed to save report: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<Report>> findById(long id) {
        return querySingle("SELECT * FROM reports WHERE id = ?", id);
    }

    @Override
    public CompletableFuture<List<Report>> findByReporter(UUID reporterUuid) {
        return queryList("SELECT * FROM reports WHERE reporter_uuid = ? ORDER BY created_at DESC", reporterUuid.toString());
    }

    @Override
    public CompletableFuture<List<Report>> findByAccused(UUID accusedUuid) {
        return queryList("SELECT * FROM reports WHERE accused_uuid = ? ORDER BY created_at DESC", accusedUuid.toString());
    }

    @Override
    public CompletableFuture<List<Report>> findByStatus(ReportStatus status) {
        return queryList("SELECT * FROM reports WHERE status = ? ORDER BY created_at DESC", status.name());
    }

    @Override
    public CompletableFuture<List<Report>> findAll(int page, int pageSize) {
        return CompletableFuture.supplyAsync(() -> {
            List<Report> reports = new ArrayList<>();
            int offset = (page - 1) * pageSize;
            String sql = "SELECT * FROM reports ORDER BY created_at DESC LIMIT ? OFFSET ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, pageSize);
                ps.setInt(2, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) reports.add(mapReport(rs));
                }
            } catch (SQLException e) {
                logger.severe("[ReportX] Failed to list reports: " + e.getMessage());
            }
            return reports;
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> updateReport(Report report) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE reports SET status = ?, claimed_by = ?, resolved_at = ?, verdict = ? WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, report.getStatus().name());
                ps.setString(2, report.getClaimedBy() != null ? report.getClaimedBy().toString() : null);
                ps.setTimestamp(3, report.getResolvedAt() != null ? Timestamp.valueOf(report.getResolvedAt()) : null);
                ps.setString(4, report.getVerdict());
                ps.setLong(5, report.getId());

                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                logger.severe("[ReportX] Failed to update report: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    // ------------------- Counts & Stats -------------------
    @Override
    public CompletableFuture<Integer> countByReporterToday(UUID reporterUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM reports WHERE reporter_uuid = ? AND DATE(created_at) = " + getTodayDateSQL();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, reporterUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) {
                logger.severe("[ReportX] Failed to count reports by reporter today: " + e.getMessage());
            }
            return 0;
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> hasDuplicateOpenReport(UUID reporterUuid, UUID accusedUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM reports WHERE reporter_uuid = ? AND accused_uuid = ? AND status = 'OPEN'";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, reporterUuid.toString());
                ps.setString(2, accusedUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            } catch (SQLException e) {
                logger.severe("[ReportX] Failed to check duplicate open report: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Long> countTotal() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM reports";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            } catch (SQLException e) {
                logger.severe("[ReportX] Failed to count total reports: " + e.getMessage());
            }
            return 0L;
        }, executor);
    }

    @Override
    public CompletableFuture<Long> countByStatus(ReportStatus status) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM reports WHERE status = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, status.name());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getLong(1);
                }
            } catch (SQLException e) {
                logger.severe("[ReportX] Failed to count reports by status: " + e.getMessage());
            }
            return 0L;
        }, executor);
    }

    @Override
    public CompletableFuture<UUID> findMostReportedPlayer() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT accused_uuid, COUNT(*) as cnt FROM reports GROUP BY accused_uuid ORDER BY cnt DESC LIMIT 1";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return UUID.fromString(rs.getString("accused_uuid"));
            } catch (SQLException e) {
                logger.severe("[ReportX] Failed to find most reported player: " + e.getMessage());
            }
            return null;
        }, executor);
    }

    @Override
    public CompletableFuture<UUID> findMostActiveStaff() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT claimed_by, COUNT(*) as cnt FROM reports WHERE claimed_by IS NOT NULL GROUP BY claimed_by ORDER BY cnt DESC LIMIT 1";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return UUID.fromString(rs.getString("claimed_by"));
            } catch (SQLException e) {
                logger.severe("[ReportX] Failed to find most active staff: " + e.getMessage());
            }
            return null;
        }, executor);
    }

    @Override
    public CompletableFuture<Double> getAverageResolutionTimeHours() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT AVG(" + getHourDiffSQL("created_at", "resolved_at") + ") FROM reports WHERE resolved_at IS NOT NULL";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            } catch (SQLException e) {
                logger.severe("[ReportX] Failed to calculate average resolution time: " + e.getMessage());
            }
            return 0.0;
        }, executor);
    }

    /**
     * Returns the SQL expression to calculate the difference in hours between two timestamp columns.
     * Override in subclasses if the database requires a different syntax.
     */
    protected String getHourDiffSQL(String fromCol, String toCol) {
        // Works for MySQL; H2 in MySQL mode also supports TIMESTAMPDIFF
        return "TIMESTAMPDIFF(HOUR, " + fromCol + ", " + toCol + ")";
    }

    // ------------------- Notes -------------------
    @Override
    public CompletableFuture<Long> saveNote(Note note) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO notes (report_id, staff_uuid, note, created_at) VALUES (?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, note.getReportId());
                ps.setString(2, note.getStaffUuid().toString());
                ps.setString(3, note.getNote());
                ps.setTimestamp(4, Timestamp.valueOf(note.getCreatedAt()));

                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : -1L;
                }
            } catch (SQLException e) {
                logger.severe("[ReportX] Failed to save note: " + e.getMessage());
                return -1L;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<Note>> findNotesByReport(long reportId) {
        return CompletableFuture.supplyAsync(() -> {
            List<Note> notes = new ArrayList<>();
            String sql = "SELECT * FROM notes WHERE report_id = ? ORDER BY created_at ASC";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, reportId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Note note = new Note();
                        note.setId(rs.getLong("id"));
                        note.setReportId(rs.getLong("report_id"));
                        note.setStaffUuid(UUID.fromString(rs.getString("staff_uuid")));
                        note.setNote(rs.getString("note"));
                        note.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                        notes.add(note);
                    }
                }
            } catch (SQLException e) {
                logger.severe("[ReportX] Failed to find notes: " + e.getMessage());
            }
            return notes;
        }, executor);
    }

    // ------------------- Audit Logs -------------------
    @Override
    public CompletableFuture<Void> saveAuditLog(AuditLog auditLog) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO audit_log (staff_uuid, action, timestamp) VALUES (?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, auditLog.getStaffUuid().toString());
                ps.setString(2, auditLog.getAction());
                ps.setTimestamp(3, Timestamp.valueOf(auditLog.getTimestamp()));
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.severe("[ReportX] Failed to save audit log: " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<AuditLog>> findAuditLogs(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<AuditLog> logs = new ArrayList<>();
            String sql = "SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        AuditLog log = new AuditLog();
                        log.setId(rs.getLong("id"));
                        log.setStaffUuid(UUID.fromString(rs.getString("staff_uuid")));
                        log.setAction(rs.getString("action"));
                        log.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
                        logs.add(log);
                    }
                }
            } catch (SQLException e) {
                logger.severe("[ReportX] Failed to find audit logs: " + e.getMessage());
            }
            return logs;
        }, executor);
    }

    // ------------------- Mapping -------------------
    protected Report mapReport(ResultSet rs) throws SQLException {
        Report report = new Report();
        report.setId(rs.getLong("id"));
        report.setReporterUuid(UUID.fromString(rs.getString("reporter_uuid")));
        report.setAccusedUuid(UUID.fromString(rs.getString("accused_uuid")));
        report.setReason(rs.getString("reason"));
        report.setStatus(ReportStatus.fromString(rs.getString("status")));
        String claimedBy = rs.getString("claimed_by");
        if (claimedBy != null) report.setClaimedBy(UUID.fromString(claimedBy));
        report.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        if (resolvedAt != null) report.setResolvedAt(resolvedAt.toLocalDateTime());
        report.setWorld(rs.getString("world"));
        report.setX(rs.getDouble("x"));
        report.setY(rs.getDouble("y"));
        report.setZ(rs.getDouble("z"));
        report.setVerdict(rs.getString("verdict"));
        report.setChatSnapshot(rs.getString("chat_snapshot"));
        report.setInventorySnapshot(rs.getString("inventory_snapshot"));
        report.setReporterIp(rs.getString("reporter_ip"));
        return report;
    }

    private CompletableFuture<Optional<Report>> querySingle(String sql, Object param) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                if (param instanceof Long) ps.setLong(1, (Long) param);
                else ps.setString(1, param.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapReport(rs)) : Optional.empty();
                }
            } catch (SQLException e) {
                logger.severe("[ReportX] Query failed: " + e.getMessage());
                return Optional.empty();
            }
        }, executor);
    }

    private CompletableFuture<List<Report>> queryList(String sql, String param) {
        return CompletableFuture.supplyAsync(() -> {
            List<Report> reports = new ArrayList<>();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, param);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) reports.add(mapReport(rs));
                }
            } catch (SQLException e) {
                logger.severe("[ReportX] Query failed: " + e.getMessage());
            }
            return reports;
        }, executor);
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}