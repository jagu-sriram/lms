#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# End-to-end check of every API against a RUNNING application with demo data.
#   1. Start the app on a fresh database (MySQL or the "demo" profile)
#   2. bash scripts/api-smoke-test.sh            (or BASE_URL=http://host:port bash ...)
# Works in Linux, macOS and Git Bash on Windows. Needs only curl.
# ---------------------------------------------------------------------------
set -u
BASE="${BASE_URL:-http://localhost:8080}"
PASS=0
FAIL=0
BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT

login() { # email password -> prints token
  curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$2\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'
}

# check <description> <expected-status> <method> <path> [token] [json-body]
check() {
  local name="$1" expected="$2" method="$3" path="$4" token="${5:-}" body="${6:-}"
  local args=(-s -o "$BODY_FILE" -w '%{http_code}' -X "$method" "$BASE$path")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")
  local code
  code=$(curl "${args[@]}")
  if [ "$code" = "$expected" ]; then
    PASS=$((PASS + 1)); printf '  \033[32mPASS\033[0m %-3s %-6s %-45s %s\n' "$code" "$method" "$path" "$name"
  else
    FAIL=$((FAIL + 1)); printf '  \033[31mFAIL\033[0m got %s expected %s  %s %s  %s\n' "$code" "$expected" "$method" "$path" "$name"
    head -c 300 "$BODY_FILE"; echo
  fi
}

first_number() { # json-key -> first numeric value of that key in the last response body
  grep -o "\"$1\":[0-9]*" "$BODY_FILE" | head -1 | cut -d: -f2
}

echo "Target: $BASE"
echo "== Authentication"
STUDENT1=$(login student1@lms.local 'Student@123')
STUDENT2=$(login student2@lms.local 'Student@123')
STUDENT3=$(login student3@lms.local 'Student@123')
ALICE=$(login alice.instructor@lms.local 'Instructor@123')
BOB=$(login bob.instructor@lms.local 'Instructor@123')
ADMIN=$(login admin@lms.local 'Admin@123')
if [ -z "$STUDENT1" ] || [ -z "$ADMIN" ]; then
  echo "Could not log in with the demo accounts. Is the app running with demo data on $BASE?"; exit 1
fi
RANDOM_EMAIL="smoke$RANDOM$RANDOM@test.local"
check "register new student"            201 POST /api/auth/register "" "{\"fullName\":\"Smoke Test\",\"email\":\"$RANDOM_EMAIL\",\"password\":\"Str0ng!Pass\"}"
check "register duplicate email"        409 POST /api/auth/register "" "{\"fullName\":\"Smoke Test\",\"email\":\"$RANDOM_EMAIL\",\"password\":\"Str0ng!Pass\"}"
check "register as ADMIN (mass assign)" 400 POST /api/auth/register "" "{\"fullName\":\"Evil\",\"email\":\"x$RANDOM_EMAIL\",\"password\":\"Str0ng!Pass\",\"role\":\"ADMIN\"}"
check "register weak password"          400 POST /api/auth/register "" '{"fullName":"Weak","email":"weak@test.local","password":"123"}'
check "login wrong password"            401 POST /api/auth/login "" '{"email":"student1@lms.local","password":"Wrong!Pass1"}'
check "current user"                    200 GET  /api/auth/me "$STUDENT1"
check "no token"                        401 GET  /api/auth/me
check "garbage token"                   401 GET  /api/auth/me "not-a-jwt"

echo "== Look up ids from the demo data"
check "student1 progress"               200 GET /api/progress/me "$STUDENT1"
S1_PROGRESS=$(first_number progressId)
S1_COURSE=$(first_number courseId)
check "student2 progress"               200 GET /api/progress/me "$STUDENT2"
S2_PROGRESS=$(first_number progressId)
check "course list"                     200 GET /api/courses "$STUDENT1"
REACT=$(grep -o '"id":[0-9]*,"title":"React for Beginners"' "$BODY_FILE" | cut -d: -f2 | cut -d, -f1)
echo "  student1 progressId=$S1_PROGRESS courseId=$S1_COURSE | student2 progressId=$S2_PROGRESS | React courseId=$REACT"

echo "== GET /api/progress/me"
check "student own progress"            200 GET /api/progress/me "$STUDENT1"
check "instructor -> 403"               403 GET /api/progress/me "$ALICE"
check "unauthenticated -> 401"          401 GET /api/progress/me

echo "== GET /api/progress/course/{id}"
check "enrolled student (SELF)"         200 GET "/api/progress/course/$S1_COURSE" "$STUDENT1"
check "not-enrolled student -> 403"     403 GET "/api/progress/course/$REACT" "$STUDENT1"
check "owning instructor"               200 GET "/api/progress/course/$S1_COURSE" "$ALICE"
check "other instructor -> 403"         403 GET "/api/progress/course/$REACT" "$ALICE"
check "bob's own course"                200 GET "/api/progress/course/$REACT" "$BOB"
check "admin any course"                200 GET "/api/progress/course/$REACT" "$ADMIN"
check "unknown course -> 404"           404 GET /api/progress/course/999999 "$ADMIN"
check "invalid id -> 400"               400 GET /api/progress/course/abc "$ADMIN"
check "negative id -> 400"              400 GET /api/progress/course/-1 "$ADMIN"

echo "== PUT /api/progress/{id}"
check "student updates own"             200 PUT "/api/progress/$S1_PROGRESS" "$STUDENT1" '{"completionPercentage":100}'
check "IDOR: other student's record"    403 PUT "/api/progress/$S2_PROGRESS" "$STUDENT1" '{"completionPercentage":100}'
check "instructor cannot modify"        403 PUT "/api/progress/$S1_PROGRESS" "$ALICE" '{"completionPercentage":10}'
check "admin correction"                200 PUT "/api/progress/$S1_PROGRESS" "$ADMIN" '{"completionPercentage":100,"status":"COMPLETED"}'
check "percentage 150 -> 400"           400 PUT "/api/progress/$S1_PROGRESS" "$STUDENT1" '{"completionPercentage":150}'
check "bad status -> 400"               400 PUT "/api/progress/$S1_PROGRESS" "$STUDENT1" '{"completionPercentage":50,"status":"DONE"}'
check "inconsistent status -> 400"      400 PUT "/api/progress/$S1_PROGRESS" "$STUDENT1" '{"completionPercentage":50,"status":"COMPLETED"}'
check "extra studentId field -> 400"    400 PUT "/api/progress/$S1_PROGRESS" "$STUDENT1" '{"completionPercentage":50,"studentId":1}'
check "unknown record -> 404"           404 PUT /api/progress/999999 "$STUDENT1" '{"completionPercentage":50}'
check "id abc -> 400"                   400 PUT /api/progress/abc "$STUDENT1" '{"completionPercentage":50}'
check "no token -> 401"                 401 PUT "/api/progress/$S1_PROGRESS" "" '{"completionPercentage":50}'

echo "== Courses / enrollment"
check "student cannot create course"    403 POST /api/courses "$STUDENT1" '{"title":"Hacked"}'
check "instructor creates course"       201 POST /api/courses "$ALICE" '{"title":"Smoke Test Course","description":"created by smoke test"}'
NEW_COURSE=$(first_number id)
check "owner adds lesson"               201 POST "/api/courses/$NEW_COURSE/lessons" "$ALICE" '{"title":"Smoke Lesson","durationMinutes":15}'
check "other instructor adds lesson"    403 POST "/api/courses/$NEW_COURSE/lessons" "$BOB" '{"title":"Not mine","durationMinutes":15}'
check "owner adds assessment"           201 POST "/api/courses/$NEW_COURSE/assessments" "$ALICE" '{"title":"Smoke Quiz","maxScore":10,"passingScore":5,"maxAttempts":2}'
NEW_ASSESSMENT=$(first_number id)
check "course details"                  200 GET "/api/courses/$NEW_COURSE" "$STUDENT3"
check "student3 enrolls"                201 POST "/api/courses/$NEW_COURSE/enrollment" "$STUDENT3"
check "duplicate enrollment -> 409"     409 POST "/api/courses/$NEW_COURSE/enrollment" "$STUDENT3"
check "instructor cannot enroll"        403 POST "/api/courses/$NEW_COURSE/enrollment" "$BOB"
check "my enrollments"                  200 GET /api/enrollments/me "$STUDENT3"

echo "== Assessments"
check "student3 submits attempt"        200 POST "/api/assessments/$NEW_ASSESSMENT/attempts" "$STUDENT3"
RESULT_ID=$(first_number resultId)
check "not enrolled -> 403"             403 POST "/api/assessments/$NEW_ASSESSMENT/attempts" "$STUDENT1"
check "student cannot grade"            403 PUT "/api/assessments/results/$RESULT_ID/grade" "$STUDENT3" '{"score":10}'
check "other instructor cannot grade"   403 PUT "/api/assessments/results/$RESULT_ID/grade" "$BOB" '{"score":10}'
check "score above max -> 400"          400 PUT "/api/assessments/results/$RESULT_ID/grade" "$ALICE" '{"score":11}'
check "owner grades"                    200 PUT "/api/assessments/results/$RESULT_ID/grade" "$ALICE" '{"score":8,"feedback":"Nice"}'
check "my assessments"                  200 GET /api/assessments/me "$STUDENT3"
check "leave course"                    204 DELETE "/api/courses/$NEW_COURSE/enrollment" "$STUDENT3"

echo "== Dashboards / admin"
check "student dashboard"               200 GET /api/dashboard/student "$STUDENT1"
check "instructor dashboard"            200 GET /api/dashboard/instructor "$ALICE"
check "admin dashboard"                 200 GET /api/dashboard/admin "$ADMIN"
check "student -> admin dashboard 403"  403 GET /api/dashboard/admin "$STUDENT1"
check "admin users"                     200 GET /api/admin/users "$ADMIN"
check "student -> admin users 403"      403 GET /api/admin/users "$STUDENT1"
check "instructor -> admin users 403"   403 GET /api/admin/users "$ALICE"
check "admin enrollments"               200 GET /api/admin/enrollments "$ADMIN"

echo "== Logout"
TEMP=$(login student3@lms.local 'Student@123')
check "logout"                          204 POST /api/auth/logout "$TEMP"
check "revoked token -> 401"            401 GET /api/auth/me "$TEMP"

echo
echo "Passed: $PASS   Failed: $FAIL"
[ "$FAIL" -eq 0 ]
