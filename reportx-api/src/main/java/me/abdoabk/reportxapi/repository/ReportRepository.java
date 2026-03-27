package me.abdoabk.reportxapi.repository;

import me.abdoabk.reportxapi.entity.ReportEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReportRepository extends JpaRepository<ReportEntity, Long> {

    Page<ReportEntity> findAllByOrderByCreatedAtDesc(Pageable p);
    Page<ReportEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable p);
    Page<ReportEntity> findByReasonContainingIgnoreCaseOrderByCreatedAtDesc(String reason, Pageable p);
    Page<ReportEntity> findByStatusAndReasonContainingIgnoreCaseOrderByCreatedAtDesc(String status, String reason, Pageable p);

    List<ReportEntity> findByAccusedUuidOrderByCreatedAtDesc(String uuid);
    List<ReportEntity> findByReporterUuidOrderByCreatedAtDesc(String uuid);
    List<ReportEntity> findTop10ByOrderByCreatedAtDesc();

    long countByStatus(String status);

    @Query("SELECT r.accusedUuid, COUNT(r) FROM ReportEntity r GROUP BY r.accusedUuid ORDER BY COUNT(r) DESC")
    List<Object[]> findTopAccused(Pageable p);

    @Query("SELECT AVG(TIMESTAMPDIFF(HOUR, r.createdAt, r.resolvedAt)) FROM ReportEntity r WHERE r.resolvedAt IS NOT NULL")
    Double avgResolutionHours();

    /** Returns all reports the plugin needs to notify about, then clears the flag. */
    List<ReportEntity> findByNotifyMinecraftTrue();
}
