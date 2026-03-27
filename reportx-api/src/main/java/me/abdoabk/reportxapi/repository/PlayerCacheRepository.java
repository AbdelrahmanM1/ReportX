package me.abdoabk.reportxapi.repository;

import me.abdoabk.reportxapi.entity.PlayerCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PlayerCacheRepository extends JpaRepository<PlayerCacheEntity, String> {
    Optional<PlayerCacheEntity> findByLastKnownNameIgnoreCase(String name);
    List<PlayerCacheEntity> findByLastKnownNameContainingIgnoreCaseOrderByLastSeenDesc(String name);
}