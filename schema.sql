-- ReportX SQL Schema
-- Compatible with MySQL 5.7+ and MariaDB 10.3+
-- For H2 embedded DB, tables are auto-created by the plugin

-- Create database (run manually if using MySQL)
-- CREATE DATABASE IF NOT EXISTS reportx CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE reportx;

-- =====================
--   REPORTS TABLE
-- =====================
CREATE TABLE IF NOT EXISTS reports (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_uuid VARCHAR(36)  NOT NULL COMMENT 'UUID of the reporting player',
    accused_uuid  VARCHAR(36)  NOT NULL COMMENT 'UUID of the accused player',
    reason        TEXT         NOT NULL COMMENT 'Report reason',
    status        VARCHAR(20)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN | CLAIMED | RESOLVED | REJECTED | ESCALATED',
    claimed_by    VARCHAR(36)  NULL COMMENT 'UUID of staff who claimed the report',
    created_at    TIMESTAMP    NOT NULL COMMENT 'When the report was submitted',
    resolved_at   TIMESTAMP    NULL DEFAULT NULL COMMENT 'When the report was closed',
    verdict       VARCHAR(100) NULL COMMENT 'Staff verdict on resolution',
    world         VARCHAR(50)  NULL COMMENT 'World name where report was submitted',
    x             DOUBLE       NULL COMMENT 'X coordinate of accused at report time',
    y             DOUBLE       NULL COMMENT 'Y coordinate',
    z             DOUBLE       NULL COMMENT 'Z coordinate',
    chat_snapshot TEXT         NULL COMMENT 'Last N chat messages from accused',
    reporter_ip   VARCHAR(50)  NULL COMMENT 'IP of reporter (nullable for GDPR mode)',
    INDEX idx_reporter (reporter_uuid),
    INDEX idx_accused  (accused_uuid),
    INDEX idx_status   (status),
    INDEX idx_created  (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Main reports table';

-- =====================
--   NOTES TABLE
-- =====================
CREATE TABLE IF NOT EXISTS notes (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id   BIGINT      NOT NULL COMMENT 'FK to reports.id',
    staff_uuid  VARCHAR(36) NOT NULL COMMENT 'UUID of staff who added the note',
    note        TEXT        NOT NULL COMMENT 'Internal note content',
    created_at  TIMESTAMP   NOT NULL COMMENT 'When the note was added',
    INDEX idx_report_id (report_id),
    CONSTRAINT fk_notes_report FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Internal staff notes on reports';

-- =====================
--   AUDIT LOG TABLE
-- =====================
CREATE TABLE IF NOT EXISTS audit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    staff_uuid  VARCHAR(36) NOT NULL COMMENT 'UUID of staff who performed the action',
    action      TEXT        NOT NULL COMMENT 'Description of the action performed',
    timestamp   TIMESTAMP   NOT NULL COMMENT 'When the action occurred',
    INDEX idx_staff     (staff_uuid),
    INDEX idx_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Staff action audit log';

-- =====================
--   EXAMPLE QUERIES
-- =====================

-- Get all open reports ordered by newest first:
-- SELECT * FROM reports WHERE status = 'OPEN' ORDER BY created_at DESC;

-- Get report stats:
-- SELECT status, COUNT(*) as count FROM reports GROUP BY status;

-- Get most reported players:
-- SELECT accused_uuid, COUNT(*) as reports FROM reports GROUP BY accused_uuid ORDER BY reports DESC LIMIT 10;

-- Get most active staff:
-- SELECT claimed_by, COUNT(*) as handled FROM reports WHERE claimed_by IS NOT NULL GROUP BY claimed_by ORDER BY handled DESC;

-- Get average resolution time in hours:
-- SELECT AVG(TIMESTAMPDIFF(HOUR, created_at, resolved_at)) as avg_hours FROM reports WHERE resolved_at IS NOT NULL;

-- Get reports for a specific player (accused):
-- SELECT * FROM reports WHERE accused_uuid = 'your-uuid-here' ORDER BY created_at DESC;

-- Get all notes for a report:
-- SELECT n.*, r.reason FROM notes n JOIN reports r ON n.report_id = r.id WHERE n.report_id = 1;

-- Recent audit log entries:
-- SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT 50;
