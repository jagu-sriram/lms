package com.lms.tracker.dto.response;

/**
 * @param content only included for enrolled students, the owning instructor and admins
 */
public record LessonResponse(
        Long id,
        String title,
        String content,
        int displayOrder,
        int durationMinutes
) {
}
