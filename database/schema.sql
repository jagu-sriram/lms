-- =====================================================================
--  LMS Progress Tracker - complete MySQL 8 setup script
--
--  Usage (as a MySQL admin user, e.g. root):
--      mysql -u root -p < database/schema.sql
--
--  This creates the database, an application user and all tables.
--  The application itself also runs the table part of this script
--  (src/main/resources/db/schema-mysql.sql) on every start-up, so
--  running this file manually is optional if the DB user may create tables.
--
--  Roles and demo data (users, courses, lessons, assessments, enrollments)
--  are inserted by the application on first start, so passwords are
--  BCrypt-hashed by the same code path as real registrations.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS lms_progress
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- Application user with only the privileges it needs on this schema.
-- CHANGE THE PASSWORD for anything other than local development.
CREATE USER IF NOT EXISTS 'lms_user'@'localhost' IDENTIFIED BY 'lms_password';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
    ON lms_progress.* TO 'lms_user'@'localhost';
FLUSH PRIVILEGES;

USE lms_progress;


CREATE TABLE IF NOT EXISTS roles (
    id    BIGINT      NOT NULL AUTO_INCREMENT,
    name  VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name),
    CONSTRAINT chk_roles_name CHECK (name IN ('STUDENT', 'INSTRUCTOR', 'ADMIN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS users (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    full_name      VARCHAR(100) NOT NULL,
    email          VARCHAR(150) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    role_id        BIGINT       NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id),
    INDEX idx_users_role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS courses (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    title          VARCHAR(150)  NOT NULL,
    description    VARCHAR(2000) NULL,
    instructor_id  BIGINT        NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_courses_instructor FOREIGN KEY (instructor_id) REFERENCES users (id),
    INDEX idx_courses_instructor (instructor_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lessons (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    course_id         BIGINT       NOT NULL,
    title             VARCHAR(150) NOT NULL,
    content           TEXT         NULL,
    display_order     INT          NOT NULL,
    duration_minutes  INT          NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lessons_course_order UNIQUE (course_id, display_order),
    CONSTRAINT fk_lessons_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT chk_lessons_order CHECK (display_order >= 1),
    CONSTRAINT chk_lessons_duration CHECK (duration_minutes BETWEEN 1 AND 600)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS enrollments (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    student_id  BIGINT      NOT NULL,
    course_id   BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_enrollments_student_course UNIQUE (student_id, course_id),
    CONSTRAINT fk_enrollments_student FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_enrollments_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT chk_enrollments_status CHECK (status IN ('ACTIVE', 'DROPPED')),
    INDEX idx_enrollments_course_status (course_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS progress (
    id                     BIGINT      NOT NULL AUTO_INCREMENT,
    student_id             BIGINT      NOT NULL,
    course_id              BIGINT      NOT NULL,
    lesson_id              BIGINT      NOT NULL,
    status                 VARCHAR(20) NOT NULL,
    completion_percentage  INT         NOT NULL DEFAULT 0,
    last_accessed_at       DATETIME(6) NULL,
    completed_at           DATETIME(6) NULL,
    version                BIGINT      NOT NULL DEFAULT 0,
    created_at             DATETIME(6) NOT NULL,
    updated_at             DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_progress_student_lesson UNIQUE (student_id, lesson_id),
    CONSTRAINT fk_progress_student FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_progress_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT fk_progress_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE CASCADE,
    CONSTRAINT chk_progress_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT chk_progress_percentage CHECK (completion_percentage BETWEEN 0 AND 100),
    INDEX idx_progress_student_course (student_id, course_id),
    INDEX idx_progress_course (course_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS assessments (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    course_id      BIGINT        NOT NULL,
    title          VARCHAR(150)  NOT NULL,
    description    VARCHAR(2000) NULL,
    max_score      INT           NOT NULL,
    passing_score  INT           NOT NULL,
    max_attempts   INT           NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_assessments_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT chk_assessments_max_score CHECK (max_score BETWEEN 1 AND 1000),
    CONSTRAINT chk_assessments_passing CHECK (passing_score >= 0 AND passing_score <= max_score),
    CONSTRAINT chk_assessments_attempts CHECK (max_attempts BETWEEN 1 AND 10),
    INDEX idx_assessments_course (course_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS assessment_results (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    assessment_id    BIGINT       NOT NULL,
    student_id       BIGINT       NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    score            INT          NULL,
    attempts         INT          NOT NULL DEFAULT 0,
    completed        BOOLEAN      NOT NULL DEFAULT FALSE,
    last_attempt_at  DATETIME(6)  NULL,
    graded_at        DATETIME(6)  NULL,
    graded_by        BIGINT       NULL,
    feedback         VARCHAR(500) NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_results_assessment_student UNIQUE (assessment_id, student_id),
    CONSTRAINT fk_results_assessment FOREIGN KEY (assessment_id) REFERENCES assessments (id) ON DELETE CASCADE,
    CONSTRAINT fk_results_student FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_results_graded_by FOREIGN KEY (graded_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_results_status CHECK (status IN ('NOT_ATTEMPTED', 'SUBMITTED', 'PASSED', 'FAILED')),
    CONSTRAINT chk_results_score CHECK (score IS NULL OR score >= 0),
    CONSTRAINT chk_results_attempts CHECK (attempts >= 0),
    INDEX idx_results_student (student_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS revoked_tokens (
    jti         VARCHAR(64) NOT NULL,
    user_id     BIGINT      NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    revoked_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (jti),
    CONSTRAINT fk_revoked_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_revoked_tokens_expires (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
