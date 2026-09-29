package com.lms.tracker.dto.response;

import java.time.Instant;

/**
 * @param enrolled only set for STUDENT viewers (null otherwise)
 */
public record CourseSummaryResponse(
        Long id,
        String title,
        String description,
        Long instructorId,
        String instructorName,
        long lessonCount,
        long assessmentCount,
        long enrolledStudents,
        Boolean enrolled,
        Instant createdAt
) {
}
