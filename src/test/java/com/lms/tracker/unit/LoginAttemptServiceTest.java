package com.lms.tracker.unit;

import com.lms.tracker.security.LoginAttemptService;
import com.lms.tracker.security.SecurityProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private final LoginAttemptService service = new LoginAttemptService(new SecurityProperties(
            new SecurityProperties.Jwt("unused", Duration.ofHours(1), "issuer"),
            4,
            new SecurityProperties.Login(3, Duration.ofMinutes(15))));

    @Test
    void blocksAfterMaxFailures() {
        String key = LoginAttemptService.key("User@Test.local", "10.0.0.1");
        service.recordFailure(key);
        service.recordFailure(key);
        assertThat(service.isBlocked(key)).isFalse();
        service.recordFailure(key);
        assertThat(service.isBlocked(key)).isTrue();
    }

    @Test
    void lockIsPerEmailAndIp() {
        String attacker = LoginAttemptService.key("victim@test.local", "10.0.0.66");
        for (int i = 0; i < 3; i++) {
            service.recordFailure(attacker);
        }
        assertThat(service.isBlocked(attacker)).isTrue();
        assertThat(service.isBlocked(LoginAttemptService.key("victim@test.local", "10.0.0.7"))).isFalse();
    }

    @Test
    void successResetsCounter() {
        String key = LoginAttemptService.key("user2@test.local", "10.0.0.2");
        service.recordFailure(key);
        service.recordFailure(key);
        service.recordSuccess(key);
        service.recordFailure(key);
        assertThat(service.isBlocked(key)).isFalse();
    }

    @Test
    void keyIsCaseInsensitiveForEmail() {
        assertThat(LoginAttemptService.key(" USER@Test.local ", "ip"))
                .isEqualTo(LoginAttemptService.key("user@test.local", "ip"));
    }
}
