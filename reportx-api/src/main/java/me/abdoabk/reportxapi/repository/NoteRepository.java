package me.abdoabk.reportxapi.repository;

import me.abdoabk.reportxapi.entity.NoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NoteRepository extends JpaRepository<NoteEntity, Long> {
    List<NoteEntity> findByReportIdOrderByCreatedAtAsc(Long reportId);
}