package me.abdoabk.reportxapi.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "web_tokens")
public class WebTokenEntity {

    @Id
    @Column(name = "token", length = 64)
    private String token;

    @Column(name = "staff_uuid", nullable = false, length = 36)
    private String staffUuid;

    @Column(name = "username", nullable = false, length = 32)
    private String username;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "used", nullable = false)
    private boolean used = false;

    public String getToken()            { return token; }
    public String getStaffUuid()        { return staffUuid; }
    public String getUsername()         { return username; }
    public String getRole()             { return role; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isUsed()             { return used; }
    public void markUsed()              { this.used = true; }
    public boolean isExpired()          { return LocalDateTime.now().isAfter(expiresAt); }
}