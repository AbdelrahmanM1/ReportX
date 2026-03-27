package me.abdoabk.reportx.repository;

import me.abdoabk.reportx.model.AuditLog;
import me.abdoabk.reportx.model.Note;
import me.abdoabk.reportx.model.Report;
import me.abdoabk.reportx.model.ReportStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ReportRepository {

    CompletableFuture<Void> initialize();

    CompletableFuture<Long> saveReport(Report report);

    CompletableFuture<Optional<Report>> findById(long id);

    CompletableFuture<List<Report>> findByReporter(UUID reporterUuid);

    CompletableFuture<List<Report>> findByAccused(UUID accusedUuid);

    CompletableFuture<List<Report>> findByStatus(ReportStatus status);

    CompletableFuture<List<Report>> findAll(int page, int pageSize);

    CompletableFuture<Boolean> updateReport(Report report);

    CompletableFuture<Integer> countByReporterToday(UUID reporterUuid);

    CompletableFuture<Boolean> hasDuplicateOpenReport(UUID reporterUuid, UUID accusedUuid);

    CompletableFuture<Long> saveNote(Note note);

    CompletableFuture<List<Note>> findNotesByReport(long reportId);

    CompletableFuture<Void> saveAuditLog(AuditLog auditLog);

    CompletableFuture<List<AuditLog>> findAuditLogs(int limit);

    CompletableFuture<Long> countTotal();

    CompletableFuture<Long> countByStatus(ReportStatus status);

    CompletableFuture<UUID> findMostReportedPlayer();

    CompletableFuture<UUID> findMostActiveStaff();

    CompletableFuture<Double> getAverageResolutionTimeHours();

    void close();
}
