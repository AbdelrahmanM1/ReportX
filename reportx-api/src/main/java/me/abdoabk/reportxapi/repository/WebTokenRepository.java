package me.abdoabk.reportxapi.repository;

import me.abdoabk.reportxapi.entity.WebTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

public interface WebTokenRepository extends JpaRepository<WebTokenEntity, String> {

    @Modifying
    @Transactional
    @Query("DELETE FROM WebTokenEntity t WHERE t.expiresAt < :now")
    void deleteExpired(LocalDateTime now);
}