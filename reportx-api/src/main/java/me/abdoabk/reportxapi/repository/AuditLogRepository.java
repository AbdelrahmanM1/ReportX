package me.abdoabk.reportxapi.repository;

import me.abdoabk.reportxapi.entity.AuditLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    List<AuditLogEntity> findAllByOrderByTimestampDesc(Pageable pageable);
}