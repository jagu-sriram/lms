package com.lms.tracker.unit;

import com.lms.tracker.entity.Course;
import com.lms.tracker.entity.Lesson;
import com.lms.tracker.entity.Progress;
import com.lms.tracker.entity.Role;
import com.lms.tracker.entity.RoleName;
import com.lms.tracker.entity.User;
import com.lms.tracker.security.AuthenticatedUser;

/** Builds detached entities with ids for pure unit tests (no database). */
final class TestEntities {

    private TestEntities() {
    }

    static User user(long id, RoleName role) {
        User user = new User("User " + id, "user" + id + "@test.local", "$2a$04$hash", new Role(role));
        user.setId(id);
        return user;
    }

    static AuthenticatedUser principal(User user) {
        return AuthenticatedUser.from(user);
    }

    static Course course(long id, User instructor) {
        Course course = new Course("Course " + id, null, instructor);
        course.setId(id);
        return course;
    }

    static Lesson lesson(long id, Course course, int order) {
        Lesson lesson = new Lesson(course, "Lesson " + id, null, order, 30);
        lesson.setId(id);
        return lesson;
    }

    static Progress progress(long id, User student, Lesson lesson) {
        Progress progress = new Progress(student, lesson);
        progress.setId(id);
        return progress;
    }
}
