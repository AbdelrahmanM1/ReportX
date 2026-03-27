package me.abdoabk.reportxapi.config;

import me.abdoabk.reportxapi.repository.WebTokenRepository;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@EnableScheduling
public class TokenCleanupTask {

    private final WebTokenRepository tokenRepo;

    public TokenCleanupTask(WebTokenRepository tokenRepo) {
        this.tokenRepo = tokenRepo;
    }

    @Scheduled(fixedDelayString = "#{${reportx.token.cleanup-interval-minutes:30} * 60000}")
    public void clean() {
        tokenRepo.deleteExpired(LocalDateTime.now());
    }
}