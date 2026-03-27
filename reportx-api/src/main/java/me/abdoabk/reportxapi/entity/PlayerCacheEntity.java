package me.abdoabk.reportxapi.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "player_cache")
public class PlayerCacheEntity {

    @Id
    @Column(name = "uuid", length = 36)
    private String uuid;

    @Column(name = "last_known_name", length = 16)
    private String lastKnownName;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    public String getUuid()             { return uuid; }
    public String getLastKnownName()    { return lastKnownName; }
    public LocalDateTime getLastSeen()  { return lastSeen; }
}