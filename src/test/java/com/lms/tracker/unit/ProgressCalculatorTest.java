package com.lms.tracker.unit;

import com.lms.tracker.entity.CourseCompletionStatus;
import com.lms.tracker.entity.ProgressStatus;
import com.lms.tracker.exception.BadRequestException;
import com.lms.tracker.util.ProgressCalculator;
import com.lms.tracker.util.ProgressMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgressCalculatorTest {

    @ParameterizedTest
    @CsvSource({"0,NOT_STARTED", "1,IN_PROGRESS", "50,IN_PROGRESS", "99,IN_PROGRESS", "100,COMPLETED"})
    void derivesLessonStatusFromPercentage(int percentage, ProgressStatus expected) {
        assertThat(ProgressCalculator.resolveLessonStatus(percentage, null)).isEqualTo(expected);
        assertThat(ProgressCalculator.resolveLessonStatus(percentage, expected)).isEqualTo(expected);
    }

    @Test
    void rejectsInconsistentStatus() {
        assertThatThrownBy(() -> ProgressCalculator.resolveLessonStatus(50, ProgressStatus.COMPLETED))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("inconsistent");
        assertThatThrownBy(() -> ProgressCalculator.resolveLessonStatus(100, ProgressStatus.IN_PROGRESS))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsOutOfRangePercentage() {
        assertThatThrownBy(() -> ProgressCalculator.resolveLessonStatus(101, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ProgressCalculator.resolveLessonStatus(-1, null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void emptyCourseIsNotStarted() {
        ProgressMetrics m = ProgressCalculator.calculate(List.of(), List.of());
        assertThat(m.completionPercentage()).isZero();
        assertThat(m.status()).isEqualTo(CourseCompletionStatus.NOT_STARTED);
    }

    @Test
    void completionIsFlooredSoHundredMeansEverythingDone() {
        // 100 + 100 + 99 = 299 / 3 = 99.67 -> 99, still IN_PROGRESS
        ProgressMetrics m = ProgressCalculator.calculateFromValues(List.of(100, 100, 99), 2, 0, 0, false);
        assertThat(m.completionPercentage()).isEqualTo(99);
        assertThat(m.status()).isEqualTo(CourseCompletionStatus.IN_PROGRESS);
    }

    @Test
    void assessmentsCountAsItems() {
        // two lessons done, one of two assessments passed -> (100+100+100+0)/4 = 75
        ProgressMetrics m = ProgressCalculator.calculateFromValues(List.of(100, 100), 2, 1, 2, true);
        assertThat(m.completionPercentage()).isEqualTo(75);
        assertThat(m.status()).isEqualTo(CourseCompletionStatus.IN_PROGRESS);

        ProgressMetrics done = ProgressCalculator.calculateFromValues(List.of(100, 100), 2, 2, 2, true);
        assertThat(done.completionPercentage()).isEqualTo(100);
        assertThat(done.status()).isEqualTo(CourseCompletionStatus.COMPLETED);
    }

    @Test
    void averageRounds() {
        assertThat(ProgressCalculator.average(List.of())).isZero();
        assertThat(ProgressCalculator.average(List.of(50, 51))).isEqualTo(51);
    }
}
