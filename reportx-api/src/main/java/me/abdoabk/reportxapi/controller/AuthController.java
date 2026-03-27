package me.abdoabk.reportxapi.controller;

import me.abdoabk.reportxapi.entity.WebTokenEntity;
import me.abdoabk.reportxapi.repository.WebTokenRepository;
import me.abdoabk.reportxapi.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final WebTokenRepository tokenRepo;
    private final JwtUtil jwtUtil;

    public AuthController(WebTokenRepository tokenRepo, JwtUtil jwtUtil) {
        this.tokenRepo = tokenRepo;
        this.jwtUtil   = jwtUtil;
    }

    @PostMapping("/exchange")
    public ResponseEntity<?> exchange(@RequestBody Map<String, String> body) {
        String raw = body.get("token");
        if (raw == null || raw.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing token"));
        }

        WebTokenEntity wt = tokenRepo.findById(raw.trim()).orElse(null);

        if (wt == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        }
        if (wt.isUsed()) {
            return ResponseEntity.status(401).body(Map.of("error", "Token already used"));
        }
        if (wt.isExpired()) {
            tokenRepo.delete(wt);
            return ResponseEntity.status(401).body(Map.of("error", "Token expired — run /report web again"));
        }

        wt.markUsed();
        tokenRepo.save(wt);

        String jwt = jwtUtil.issueToken(wt.getStaffUuid(), wt.getUsername(), wt.getRole());

        return ResponseEntity.ok(Map.of(
                "accessToken", jwt,
                "user", Map.of(
                        "uuid",     wt.getStaffUuid(),
                        "username", wt.getUsername(),
                        "role",     wt.getRole()
                )
        ));
    }

    @GetMapping("/me")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> me(@AuthenticationPrincipal Object principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        Map<String, String> p = (Map<String, String>) principal;
        return ResponseEntity.ok(Map.of(
                "uuid",     p.get("uuid"),
                "username", p.get("username"),
                "role",     p.get("role")
        ));
    }
}