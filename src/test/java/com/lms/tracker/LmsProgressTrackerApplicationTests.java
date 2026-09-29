package com.lms.tracker;

import com.lms.tracker.entity.RoleName;
import com.lms.tracker.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Smoke test: the application context starts and the roles are initialised. */
class LmsProgressTrackerApplicationTests extends IntegrationTestSupport {

    @Test
    void contextLoadsAndRolesAreInitialised() {
        for (RoleName role : RoleName.values()) {
            assertThat(roleRepository.findByName(role)).isPresent();
        }
    }
}
