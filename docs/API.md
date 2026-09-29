# API Reference

Base URL: `http://localhost:8080`
All request and response bodies are JSON. Every endpoint except register and login needs an
`Authorization: Bearer <accessToken>` header.

Error responses always look like this:

```json
{
  "timestamp": "2026-09-28T10:15:30.123Z",
  "status": 403,
  "error": "Forbidden",
  "message": "You are not authorized to modify this progress record",
  "path": "/api/progress/25"
}
```

Validation errors add a `fieldErrors` object:

```json
{
  "timestamp": "2026-09-28T10:15:30.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/progress/7",
  "fieldErrors": { "completionPercentage": "must be between 0 and 100" }
}
```

| Status | Meaning in this API |
|---|---|
| 200 / 201 / 204 | Success (201 = created, 204 = no body) |
| 400 | Invalid input: validation, bad id format, unknown JSON field, malformed JSON, business rule |
| 401 | Missing, invalid, expired or revoked token; wrong login credentials |
| 403 | Authenticated, but not allowed to do this / access this object |
| 404 | The resource does not exist |
| 409 | Conflict: duplicate email/enrollment, wrong workflow state, concurrent update |
| 429 | Too many failed logins |

---

## Endpoint summary

| Method | Path | Who | Description |
|---|---|---|---|
| POST | `/api/auth/register` | public | Create a STUDENT account |
| POST | `/api/auth/login` | public | Get a bearer token |
| POST | `/api/auth/logout` | any authenticated | Revoke the current token |
| GET | `/api/auth/me` | any authenticated | Current user |
| **GET** | **`/api/progress/me`** | STUDENT | Own progress in all enrolled courses |
| **GET** | **`/api/progress/course/{courseId}`** | STUDENT (enrolled, own row only), INSTRUCTOR (own course), ADMIN | Progress for one course |
| **PUT** | **`/api/progress/{progressId}`** | STUDENT (own record, while enrolled), ADMIN | Update a lesson progress record |
| GET | `/api/courses` | any authenticated | Course catalog |
| GET | `/api/courses/{courseId}` | any authenticated | Course details (lesson content only for enrolled / owner / admin) |
| POST | `/api/courses` | INSTRUCTOR, ADMIN | Create a course |
| POST | `/api/courses/{courseId}/lessons` | owning INSTRUCTOR, ADMIN | Add a lesson |
| POST | `/api/courses/{courseId}/assessments` | owning INSTRUCTOR, ADMIN | Add an assessment |
| POST | `/api/courses/{courseId}/enrollment` | STUDENT | Enroll yourself |
| DELETE | `/api/courses/{courseId}/enrollment` | STUDENT | Leave a course |
| GET | `/api/enrollments/me` | STUDENT | Own active enrollments |
| GET | `/api/assessments/me` | STUDENT | Own assessment statuses |
| POST | `/api/assessments/{assessmentId}/attempts` | STUDENT (enrolled) | Submit an attempt |
| PUT | `/api/assessments/results/{resultId}/grade` | owning INSTRUCTOR, ADMIN | Grade a submitted attempt |
| GET | `/api/dashboard/student` | STUDENT | Student dashboard |
| GET | `/api/dashboard/instructor` | INSTRUCTOR | Instructor dashboard |
| GET | `/api/dashboard/admin` | ADMIN | System statistics |
| GET | `/api/admin/users` | ADMIN | All users |
| POST | `/api/admin/users` | ADMIN | Create a user with any role |
| PATCH | `/api/admin/users/{userId}/status` | ADMIN | Enable / disable a user |
| GET | `/api/admin/enrollments` | ADMIN | All enrollments |

---

## Authentication

### POST /api/auth/register → 201

```json
{ "fullName": "Meera Nair", "email": "meera@example.com", "password": "Str0ng!Pass" }
```

```json
{
  "id": 7,
  "fullName": "Meera Nair",
  "email": "meera@example.com",
  "role": "STUDENT",
  "enabled": true,
  "createdAt": "2026-09-28T10:00:00.000Z"
}
```

Rules: name 2-100 letters; valid email; password 8-72 chars with upper, lower, digit and special character.
Sending any other field (for example `"role": "ADMIN"`) returns **400**. Existing email returns **409**.

### POST /api/auth/login → 200

```json
{ "email": "student1@lms.local", "password": "Student@123" }
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiI...",
  "tokenType": "Bearer",
  "expiresAt": "2026-09-28T12:00:00Z",
  "user": { "id": 4, "fullName": "Ravi Kumar", "email": "student1@lms.local", "role": "STUDENT", "enabled": true, "createdAt": "..." }
}
```

Wrong email or password → **401** `"Invalid email or password"` (same message for both).
Five failures for the same email from the same IP → **429** for 15 minutes.

### POST /api/auth/logout → 204

Revokes the token in the `Authorization` header. Using it again returns **401** `"Token has been revoked. Please log in again"`.

---

## Progress APIs

### GET /api/progress/me → 200 (STUDENT)

No parameters: the student is always taken from the token.

```json
{
  "studentId": 4,
  "studentName": "Ravi Kumar",
  "overallCompletionPercentage": 39,
  "enrolledCourses": 2,
  "completedCourses": 0,
  "courses": [
    {
      "courseId": 1,
      "courseTitle": "Java Fundamentals",
      "instructorName": "Alice Johnson",
      "completionPercentage": 70,
      "status": "IN_PROGRESS",
      "lessonsCompleted": 3,
      "totalLessons": 5,
      "assessmentsPassed": 1,
      "totalAssessments": 2,
      "lessons": [
        {
          "progressId": 1,
          "lessonId": 1,
          "lessonTitle": "Introduction to Java and the JVM",
          "lessonOrder": 1,
          "status": "COMPLETED",
          "completionPercentage": 100,
          "lastAccessedAt": "2026-09-28T10:00:00Z",
          "completedAt": "2026-09-28T10:00:00Z"
        }
      ],
      "assessments": [
        {
          "resultId": 1,
          "assessmentId": 1,
          "assessmentTitle": "OOP Quiz",
          "courseId": 1,
          "courseTitle": "Java Fundamentals",
          "status": "PASSED",
          "score": 85,
          "maxScore": 100,
          "passingScore": 60,
          "attempts": 1,
          "maxAttempts": 3,
          "completed": true,
          "lastAttemptAt": "2026-09-28T10:00:00Z",
          "gradedAt": "2026-09-28T10:00:00Z",
          "feedback": "Great understanding of OOP."
        }
      ]
    }
  ]
}
```

INSTRUCTOR / ADMIN → **403**. No token → **401**.

**Course completion rule:** every lesson and every assessment is one item. A lesson counts its
percentage, an assessment counts 100 when PASSED, otherwise 0. `completion = floor(sum / items)`,
so 100% (COMPLETED) is only reached when all lessons are complete and all assessments are passed.

### GET /api/progress/course/{courseId} → 200

| Caller | Result |
|---|---|
| STUDENT, actively enrolled | `scope: "SELF"` - only their own row |
| STUDENT, not enrolled / dropped | **403** `"You are not enrolled in this course"` |
| INSTRUCTOR who teaches the course | `scope: "ALL_STUDENTS"` |
| INSTRUCTOR of another course | **403** `"You are not authorized to view progress for this course"` |
| ADMIN | `scope: "ALL_STUDENTS"` |
| unknown course | **404** |
| `courseId` = 0, negative or not a number | **400** |

```json
{
  "courseId": 1,
  "courseTitle": "Java Fundamentals",
  "instructorId": 2,
  "instructorName": "Alice Johnson",
  "scope": "ALL_STUDENTS",
  "enrolledStudents": 2,
  "averageCompletionPercentage": 44,
  "students": [
    {
      "studentId": 5,
      "studentName": "Priya Sharma",
      "studentEmail": "student2@lms.local",
      "completionPercentage": 18,
      "status": "IN_PROGRESS",
      "lessonsCompleted": 1,
      "totalLessons": 5,
      "assessmentsPassed": 0,
      "totalAssessments": 2,
      "lessons": [ { "progressId": 11, "lessonId": 1, "lessonTitle": "Introduction to Java and the JVM", "lessonOrder": 1, "status": "COMPLETED", "completionPercentage": 100, "lastAccessedAt": "...", "completedAt": "..." } ],
      "assessments": [ { "resultId": 4, "assessmentId": 1, "assessmentTitle": "OOP Quiz", "status": "FAILED", "score": 40, "attempts": 1, "maxAttempts": 3, "...": "..." } ]
    }
  ]
}
```

### PUT /api/progress/{progressId} → 200

```json
{ "completionPercentage": 60 }
```

`status` is optional. If omitted it is derived: `0 → NOT_STARTED`, `1-99 → IN_PROGRESS`, `100 → COMPLETED`.
If sent, it must agree with the percentage.

```json
{
  "id": 4,
  "studentId": 4,
  "courseId": 1,
  "lessonId": 4,
  "lessonTitle": "Collections Framework",
  "status": "IN_PROGRESS",
  "completionPercentage": 60,
  "lastAccessedAt": "2026-09-28T10:20:00Z",
  "completedAt": null,
  "createdAt": "2026-09-28T09:00:00Z",
  "updatedAt": "2026-09-28T10:20:00Z"
}
```

| Case | Status |
|---|---|
| Student updates own record while enrolled | 200 |
| Student updates another student's record (IDOR) | **403** `"You are not authorized to modify this progress record"` |
| Student updates own record after dropping the course | **403** |
| Instructor (any record, even in own course) | **403** - lesson progress is read-only for instructors |
| Admin | 200 (audited in the log) |
| Record does not exist | **404** |
| id `0`, `-1`, `abc`, `1.5` | **400** |
| `completionPercentage` missing / null / <0 / >100 / 50.5 / "text" | **400** |
| `status` not one of `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED` | **400** |
| `status` inconsistent with percentage | **400** |
| Extra fields such as `studentId`, `lessonId` | **400** |
| Record changed by a concurrent request | **409** |

---

## Courses and enrollment

### POST /api/courses → 201 (INSTRUCTOR / ADMIN)

```json
{ "title": "Docker Basics", "description": "Containers 101" }
```

An instructor always becomes the owner (`instructorId` of someone else → **403**).
An admin must pass `"instructorId"` of an INSTRUCTOR account (otherwise **400**).

### POST /api/courses/{courseId}/lessons → 201

```json
{ "title": "Images and Containers", "content": "...", "durationMinutes": 30 }
```

### POST /api/courses/{courseId}/assessments → 201

```json
{ "title": "Docker Quiz", "description": "...", "maxScore": 100, "passingScore": 60, "maxAttempts": 3 }
```

`passingScore > maxScore` → **400**. Not the owner → **403**.
Adding a lesson/assessment automatically creates progress/result rows for already enrolled students.

### POST /api/courses/{courseId}/enrollment → 201 (STUDENT)

No body. Creates progress rows for every lesson and a result row for every assessment.

```json
{ "id": 6, "studentId": 4, "studentName": "Ravi Kumar", "courseId": 3, "courseTitle": "React for Beginners", "status": "ACTIVE", "enrolledAt": "...", "updatedAt": "..." }
```

Already enrolled → **409**. Re-enrolling after leaving keeps earlier progress.

### DELETE /api/courses/{courseId}/enrollment → 204

---

## Assessments

### POST /api/assessments/{assessmentId}/attempts → 200 (STUDENT)

No body. Status becomes `SUBMITTED`. Not enrolled → **403**; already passed, awaiting grading, or no attempts left → **409**.

### PUT /api/assessments/results/{resultId}/grade → 200 (owning INSTRUCTOR / ADMIN)

```json
{ "score": 75, "feedback": "Well done" }
```

```json
{ "resultId": 2, "assessmentId": 2, "assessmentTitle": "Final Exam", "courseId": 1, "courseTitle": "Java Fundamentals",
  "status": "PASSED", "score": 75, "maxScore": 100, "passingScore": 70, "attempts": 1, "maxAttempts": 2,
  "completed": true, "lastAttemptAt": "...", "gradedAt": "...", "feedback": "Well done" }
```

Student → **403**. Instructor of another course → **403**. Score > maxScore → **400**. Not in SUBMITTED state → **409**.

---

## Dashboards

`GET /api/dashboard/student`

```json
{ "studentName": "Ravi Kumar", "enrolledCourses": 2, "completedCourses": 0, "overallCompletionPercentage": 39,
  "lessonsCompleted": 3, "totalLessons": 9, "pendingAssessments": 2, "passedAssessments": 1, "courses": [ "...CourseProgress..." ] }
```

`GET /api/dashboard/instructor`

```json
{ "instructorName": "Alice Johnson", "coursesTaught": 2, "totalEnrolledStudents": 3, "submissionsAwaitingGrading": 1,
  "courses": [ { "courseId": 1, "title": "Java Fundamentals", "lessonCount": 5, "assessmentCount": 2, "enrolledStudents": 2,
                 "averageCompletionPercentage": 44, "studentsCompleted": 0, "submissionsAwaitingGrading": 1 } ] }
```

`GET /api/dashboard/admin`

```json
{ "totalUsers": 6, "students": 3, "instructors": 2, "admins": 1, "totalCourses": 3, "totalLessons": 13,
  "totalAssessments": 4, "activeEnrollments": 5, "droppedEnrollments": 0, "lessonsCompleted": 8,
  "progressRecords": 22, "assessmentsPassed": 2, "submissionsAwaitingGrading": 1 }
```

---

## Admin

`POST /api/admin/users` → 201

```json
{ "fullName": "Kiran Rao", "email": "kiran@lms.local", "password": "Teach!ng123", "role": "INSTRUCTOR" }
```

`PATCH /api/admin/users/{userId}/status` → 200

```json
{ "enabled": false }
```

A disabled user cannot log in, and their existing tokens are rejected immediately. An admin cannot disable their own account (**400**).
