package com.lms.tracker.security;

import com.lms.tracker.entity.RevokedToken;
import com.lms.tracker.repository.RevokedTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Server-side logout for stateless JWTs: the token id (jti) is stored in a deny-list
 * until the token would have expired anyway.
 */
@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);

    private final RevokedTokenRepository repository;

    public TokenRevocationService(RevokedTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void revoke(String tokenId, Long userId, Instant expiresAt) {
        if (!repository.existsById(tokenId)) {
            repository.save(new RevokedToken(tokenId, userId, expiresAt, Instant.now()));
        }
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String tokenId) {
        return repository.existsById(tokenId);
    }

    /** Housekeeping: expired tokens are rejected by signature/expiry checks anyway. */
    @Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT1H")
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteExpired(Instant.now());
        if (removed > 0) {
            log.info("Purged {} expired revoked-token entries", removed);
        }
    }
}
