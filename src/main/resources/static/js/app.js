// LMS Progress Tracker - single-page frontend (vanilla JS, no build step).
// Security notes:
//  * All user-controlled data is inserted with textContent (see h()), never innerHTML -> no XSS sinks.
//  * The CSP only allows scripts/styles from this origin, so no inline handlers are used.
//  * The UI hides buttons a user may not use, but every rule is enforced by the API anyway.

import { session, get, post, put, patch, del, ApiError } from './api.js';

const app = document.getElementById('app');
const nav = document.getElementById('nav');
const userBox = document.getElementById('user-box');
const toastEl = document.getElementById('toast');

// ------------------------------------------------------------------ DOM helpers

function h(tag, attrs = {}, ...children) {
    const el = document.createElement(tag);
    for (const [key, value] of Object.entries(attrs || {})) {
        if (value === null || value === undefined || value === false) continue;
        if (key === 'class') el.className = value;
        else if (key === 'text') el.textContent = value;
        else if (key.startsWith('on') && typeof value === 'function') el.addEventListener(key.slice(2).toLowerCase(), value);
        else if (key === 'value') el.value = value;
        else el.setAttribute(key, value === true ? '' : String(value));
    }
    for (const child of children.flat()) {
        if (child === null || child === undefined || child === false) continue;
        el.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return el;
}

function mount(...nodes) {
    app.replaceChildren(...nodes);
    app.focus({ preventScroll: true });
}

let toastTimer;
function toast(message, type = 'success') {
    toastEl.textContent = message;
    toastEl.className = `toast show ${type}`;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { toastEl.className = 'toast'; }, 3500);
}

function errorBox(err) {
    const box = h('div', { class: 'error-box', role: 'alert' }, err.message || 'Something went wrong');
    const fields = err.body && err.body.fieldErrors;
    if (fields) {
        box.append(h('ul', {}, Object.entries(fields).map(([f, m]) => h('li', {}, `${f}: ${m}`))));
    }
    return box;
}

function showError(err) {
    if (err instanceof ApiError && err.status === 401) return; // handled globally
    toast(err.message || 'Request failed', 'error');
}

function progressBar(percent) {
    const fill = h('div', { class: `bar-fill${percent >= 100 ? ' done' : ''}` });
    fill.style.width = `${Math.max(0, Math.min(100, percent))}%`; // CSSOM - allowed by the CSP
    return h('div', { class: 'bar', title: `${percent}%` },
        h('div', { class: 'bar-track' }, fill),
        h('span', { class: 'bar-label' }, `${percent}%`));
}

const STATUS_STYLE = {
    COMPLETED: 'ok', PASSED: 'ok', ACTIVE: 'ok',
    IN_PROGRESS: 'info', SUBMITTED: 'warn',
    FAILED: 'bad', DROPPED: 'bad',
    NOT_STARTED: '', NOT_ATTEMPTED: '',
    ADMIN: 'bad', INSTRUCTOR: 'warn', STUDENT: 'info',
};
function badge(value, style) {
    return h('span', { class: `badge ${style || STATUS_STYLE[value] || ''}` }, String(value).replace(/_/g, ' '));
}

function stat(label, value) {
    return h('div', { class: 'stat' }, h('div', { class: 'value' }, String(value)), h('div', { class: 'label' }, label));
}

function fmtDate(iso) {
    if (!iso) return '—';
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString();
}

function pageHeader(title, subtitle, ...actions) {
    return h('div', { class: 'page-header' },
        h('div', {}, h('h1', {}, title), subtitle ? h('div', { class: 'muted' }, subtitle) : null),
        actions.length ? h('div', { class: 'row' }, actions) : null);
}

function field(label, input, hint) {
    const id = `f-${Math.random().toString(36).slice(2, 9)}`;
    input.id = id;
    return h('div', { class: 'field' }, h('label', { for: id }, label), input, hint ? h('div', { class: 'hint' }, hint) : null);
}

function formData(form) {
    return Object.fromEntries(new FormData(form).entries());
}

function role() {
    return session.user ? session.user.role : null;
}

// ------------------------------------------------------------------ layout

function renderChrome() {
    const r = role();
    const links = [];
    if (r) {
        links.push(['#/dashboard', 'Dashboard'], ['#/courses', 'Courses']);
        if (r === 'STUDENT') links.push(['#/progress', 'My Progress'], ['#/assessments', 'Assessments']);
        if (r === 'ADMIN') links.push(['#/admin', 'Admin']);
    }
    const current = location.hash.split('?')[0];
    nav.replaceChildren(...links.map(([href, label]) =>
        h('a', { href, class: current.startsWith(href) ? 'active' : null }, label)));

    if (session.user) {
        userBox.replaceChildren(
            h('span', { class: 'small' }, session.user.fullName, ' '),
            badge(session.user.role),
            h('button', { class: 'secondary sm', onClick: logout }, 'Log out'));
    } else {
        userBox.replaceChildren();
    }
}

async function logout() {
    try { await post('/api/auth/logout'); } catch { /* token may already be invalid */ }
    session.clear();
    toast('You have been logged out');
    location.hash = '#/login';
}

window.addEventListener('lms:unauthorized', () => {
    if (!location.hash.startsWith('#/login')) {
        toast('Your session has ended. Please log in again.', 'error');
        location.hash = '#/login';
    }
});

// ------------------------------------------------------------------ auth views

const DEMO_ACCOUNTS = [
    ['STUDENT', 'student1@lms.local', 'Student@123'],
    ['STUDENT', 'student2@lms.local', 'Student@123'],
    ['INSTRUCTOR', 'alice.instructor@lms.local', 'Instructor@123'],
    ['INSTRUCTOR', 'bob.instructor@lms.local', 'Instructor@123'],
    ['ADMIN', 'admin@lms.local', 'Admin@123'],
];

function renderLogin(prefillEmail) {
    const email = h('input', { type: 'email', name: 'email', required: true, autocomplete: 'username', value: prefillEmail || '' });
    const password = h('input', { type: 'password', name: 'password', required: true, autocomplete: 'current-password' });
    const errorSlot = h('div');
    const submit = h('button', { type: 'submit', class: 'block' }, 'Log in');

    const form = h('form', {
        onSubmit: async (e) => {
            e.preventDefault();
            errorSlot.replaceChildren();
            submit.disabled = true;
            try {
                const auth = await post('/api/auth/login', { email: email.value.trim(), password: password.value });
                session.save(auth);
                toast(`Welcome, ${auth.user.fullName}`);
                location.hash = '#/dashboard';
            } catch (err) {
                errorSlot.replaceChildren(errorBox(err));
            } finally {
                submit.disabled = false;
            }
        },
    }, errorSlot, field('Email', email), field('Password', password), submit);

    const demo = h('table', { class: 'demo-table' },
        h('thead', {}, h('tr', {}, h('th', {}, 'Role'), h('th', {}, 'Email'), h('th', {}, 'Password'), h('th', {}, ''))),
        h('tbody', {}, DEMO_ACCOUNTS.map(([r, e, p]) => h('tr', {},
            h('td', {}, badge(r)), h('td', {}, h('code', {}, e)), h('td', {}, h('code', {}, p)),
            h('td', {}, h('button', {
                class: 'secondary sm', type: 'button',
                onClick: () => { email.value = e; password.value = p; },
            }, 'Use'))))));

    mount(h('div', { class: 'auth-wrap' },
        h('div', { class: 'card' }, h('h2', {}, 'Log in'), form,
            h('p', { class: 'small muted' }, 'No account? ', h('a', { href: '#/register' }, 'Register as a student'))),
        h('div', { class: 'card' }, h('h2', {}, 'Demo accounts'),
            h('p', { class: 'small muted' }, 'Created automatically on first start (demo data seeding).'),
            h('div', { class: 'table-wrap' }, demo))));
}

function renderRegister() {
    const errorSlot = h('div');
    const submit = h('button', { type: 'submit', class: 'block' }, 'Create account');
    const form = h('form', {
        onSubmit: async (e) => {
            e.preventDefault();
            errorSlot.replaceChildren();
            submit.disabled = true;
            const data = formData(e.target);
            try {
                await post('/api/auth/register', { fullName: data.fullName.trim(), email: data.email.trim(), password: data.password });
                toast('Account created. You can now log in.');
                renderLogin(data.email.trim());
                history.replaceState(null, '', '#/login');
            } catch (err) {
                errorSlot.replaceChildren(errorBox(err));
            } finally {
                submit.disabled = false;
            }
        },
    }, errorSlot,
    field('Full name', h('input', { name: 'fullName', required: true, maxlength: 100, autocomplete: 'name' })),
    field('Email', h('input', { type: 'email', name: 'email', required: true, maxlength: 150, autocomplete: 'email' })),
    field('Password', h('input', { type: 'password', name: 'password', required: true, minlength: 8, maxlength: 72, autocomplete: 'new-password' }),
        '8-72 characters with upper-case, lower-case, a digit and a special character.'),
    submit);

    mount(h('div', { class: 'auth-wrap' },
        h('div', { class: 'card' }, h('h2', {}, 'Create a student account'), form,
            h('p', { class: 'small muted' }, 'Already registered? ', h('a', { href: '#/login' }, 'Log in'))),
        h('div', { class: 'card' }, h('h2', {}, 'About accounts'),
            h('p', { class: 'muted' }, 'Self-registration always creates a STUDENT account. Instructor and admin accounts are created by an administrator.'))));
}

// ------------------------------------------------------------------ dashboards

async function renderDashboard() {
    const r = role();
    if (r === 'STUDENT') return renderStudentDashboard();
    if (r === 'INSTRUCTOR') return renderInstructorDashboard();
    if (r === 'ADMIN') return renderAdminDashboard();
    return renderLogin();
}

function courseProgressCard(c) {
    return h('div', { class: 'card course-card' },
        h('div', { class: 'spread' }, h('h3', {}, h('a', { href: `#/courses/${c.courseId}` }, c.courseTitle)), badge(c.status)),
        h('div', { class: 'meta' }, `Instructor: ${c.instructorName}`),
        progressBar(c.completionPercentage),
        h('p', { class: 'small muted' },
            `${c.lessonsCompleted}/${c.totalLessons} lessons completed · ${c.assessmentsPassed}/${c.totalAssessments} assessments passed`),
        h('a', { class: 'btn secondary sm', href: `#/progress?course=${c.courseId}` }, 'Open lesson progress'));
}

async function renderStudentDashboard() {
    const d = await get('/api/dashboard/student');
    mount(
        pageHeader(`Hi, ${d.studentName}`, 'Your learning overview'),
        h('div', { class: 'stats' },
            stat('Enrolled courses', d.enrolledCourses),
            stat('Completed courses', d.completedCourses),
            stat('Overall completion', `${d.overallCompletionPercentage}%`),
            stat('Lessons completed', `${d.lessonsCompleted}/${d.totalLessons}`),
            stat('Assessments passed', d.passedAssessments),
            stat('Assessments pending', d.pendingAssessments)),
        h('h2', {}, 'My courses'),
        d.courses.length
            ? h('div', { class: 'grid grid-2' }, d.courses.map(courseProgressCard))
            : h('div', { class: 'card empty' }, 'You are not enrolled in any course yet. ',
                h('a', { href: '#/courses' }, 'Browse courses')));
}

async function renderInstructorDashboard() {
    const d = await get('/api/dashboard/instructor');
    const rows = d.courses.map((c) => h('tr', {},
        h('td', {}, h('a', { href: `#/courses/${c.courseId}` }, c.title)),
        h('td', {}, String(c.enrolledStudents)),
        h('td', {}, progressBar(c.averageCompletionPercentage)),
        h('td', {}, String(c.studentsCompleted)),
        h('td', {}, c.submissionsAwaitingGrading ? badge(`${c.submissionsAwaitingGrading} TO GRADE`, 'warn') : '0'),
        h('td', {}, h('a', { class: 'btn sm', href: `#/courses/${c.courseId}/progress` }, 'Student progress'))));
    mount(
        pageHeader(`Hi, ${d.instructorName}`, 'Courses you teach', h('a', { class: 'btn', href: '#/courses' }, 'Create course')),
        h('div', { class: 'stats' },
            stat('Courses taught', d.coursesTaught),
            stat('Enrolled students', d.totalEnrolledStudents),
            stat('Awaiting grading', d.submissionsAwaitingGrading)),
        h('div', { class: 'card' }, h('h2', {}, 'Student progress by course'),
            d.courses.length
                ? h('div', { class: 'table-wrap' }, h('table', {},
                    h('thead', {}, h('tr', {}, ['Course', 'Students', 'Avg. completion', 'Completed', 'Awaiting grading', ''].map((t) => h('th', {}, t)))),
                    h('tbody', {}, rows)))
                : h('div', { class: 'empty' }, 'You do not teach any course yet.')));
}

async function renderAdminDashboard() {
    const d = await get('/api/dashboard/admin');
    mount(
        pageHeader('Admin dashboard', 'System overview',
            h('a', { class: 'btn', href: '#/admin' }, 'Users & enrollments'),
            h('a', { class: 'btn secondary', href: '#/courses' }, 'Courses')),
        h('h2', {}, 'Users'),
        h('div', { class: 'stats' },
            stat('Total users', d.totalUsers), stat('Students', d.students),
            stat('Instructors', d.instructors), stat('Admins', d.admins)),
        h('h2', {}, 'Learning'),
        h('div', { class: 'stats' },
            stat('Courses', d.totalCourses), stat('Lessons', d.totalLessons),
            stat('Assessments', d.totalAssessments), stat('Active enrollments', d.activeEnrollments),
            stat('Dropped enrollments', d.droppedEnrollments),
            stat('Lessons completed', `${d.lessonsCompleted}/${d.progressRecords}`),
            stat('Assessments passed', d.assessmentsPassed),
            stat('Awaiting grading', d.submissionsAwaitingGrading)));
}

// ------------------------------------------------------------------ courses

async function renderCourses() {
    const r = role();
    const courses = await get('/api/courses');
    const cards = courses.map((c) => {
        let action = null;
        if (r === 'STUDENT') {
            action = c.enrolled
                ? badge('ENROLLED')
                : h('button', {
                    class: 'sm',
                    onClick: async (e) => {
                        e.target.disabled = true;
                        try {
                            await post(`/api/courses/${c.id}/enrollment`);
                            toast(`Enrolled in ${c.title}`);
                            renderCourses().catch(showError);
                        } catch (err) { showError(err); e.target.disabled = false; }
                    },
                }, 'Enroll');
        }
        return h('div', { class: 'card course-card' },
            h('h3', {}, h('a', { href: `#/courses/${c.id}` }, c.title)),
            h('div', { class: 'meta' }, `Instructor: ${c.instructorName}`),
            h('p', { class: 'small' }, c.description || ''),
            h('div', { class: 'spread' },
                h('span', { class: 'small muted' }, `${c.lessonCount} lessons · ${c.assessmentCount} assessments · ${c.enrolledStudents} students`),
                action));
    });

    const parts = [pageHeader('Courses', `${courses.length} course(s) available`)];
    if (r === 'INSTRUCTOR' || r === 'ADMIN') parts.push(await createCourseForm());
    parts.push(courses.length ? h('div', { class: 'grid grid-3' }, cards) : h('div', { class: 'card empty' }, 'No courses yet.'));
    mount(...parts);
}

async function createCourseForm() {
    const errorSlot = h('div');
    const inputs = [
        field('Title', h('input', { name: 'title', required: true, minlength: 3, maxlength: 150 })),
        field('Description', h('textarea', { name: 'description', maxlength: 2000 })),
    ];
    if (role() === 'ADMIN') {
        const users = await get('/api/admin/users');
        const select = h('select', { name: 'instructorId', required: true },
            h('option', { value: '' }, 'Select instructor…'),
            users.filter((u) => u.role === 'INSTRUCTOR' && u.enabled).map((u) => h('option', { value: u.id }, `${u.fullName} (${u.email})`)));
        inputs.push(field('Instructor', select));
    }
    return h('details', { class: 'card' },
        h('summary', {}, h('strong', {}, 'Create a new course')),
        h('form', {
            class: 'stack',
            onSubmit: async (e) => {
                e.preventDefault();
                errorSlot.replaceChildren();
                const data = formData(e.target);
                const body = { title: data.title.trim(), description: data.description || null };
                if (data.instructorId) body.instructorId = Number(data.instructorId);
                try {
                    const created = await post('/api/courses', body);
                    toast('Course created');
                    location.hash = `#/courses/${created.id}`;
                } catch (err) { errorSlot.replaceChildren(errorBox(err)); }
            },
        }, errorSlot, inputs, h('button', { type: 'submit' }, 'Create course')));
}

async function renderCourseDetail(courseId) {
    const c = await get(`/api/courses/${courseId}`);
    const actions = [];
    if (c.enrolled === true) {
        actions.push(h('a', { class: 'btn', href: `#/progress?course=${c.id}` }, 'My progress'));
        actions.push(h('button', {
            class: 'secondary',
            onClick: async () => {
                try { await del(`/api/courses/${c.id}/enrollment`); toast('You left the course'); renderCourseDetail(courseId).catch(showError); } catch (err) { showError(err); }
            },
        }, 'Leave course'));
    } else if (c.enrolled === false) {
        actions.push(h('button', {
            onClick: async () => {
                try { await post(`/api/courses/${c.id}/enrollment`); toast('Enrolled'); renderCourseDetail(courseId).catch(showError); } catch (err) { showError(err); }
            },
        }, 'Enroll'));
    }
    if (c.canManage) actions.push(h('a', { class: 'btn', href: `#/courses/${c.id}/progress` }, 'Student progress'));

    const lessons = c.lessons.length
        ? h('ul', { class: 'lesson-list' }, c.lessons.map((l) => h('li', {},
            h('div', { class: 'spread' }, h('strong', {}, `${l.displayOrder}. ${l.title}`), h('span', { class: 'small muted' }, `${l.durationMinutes} min`)),
            l.content ? h('div', { class: 'small muted' }, l.content) : null)))
        : h('div', { class: 'empty' }, 'No lessons yet.');

    const assessments = c.assessments.length
        ? h('div', { class: 'table-wrap' }, h('table', {},
            h('thead', {}, h('tr', {}, ['Assessment', 'Max score', 'Passing score', 'Attempts'].map((t) => h('th', {}, t)))),
            h('tbody', {}, c.assessments.map((a) => h('tr', {},
                h('td', {}, h('strong', {}, a.title), a.description ? h('div', { class: 'small muted' }, a.description) : null),
                h('td', {}, String(a.maxScore)), h('td', {}, String(a.passingScore)), h('td', {}, String(a.maxAttempts)))))))
        : h('div', { class: 'empty' }, 'No assessments yet.');

    const parts = [
        pageHeader(c.title, `Instructor: ${c.instructorName} · ${c.enrolledStudents} enrolled student(s)`, ...actions),
        c.description ? h('div', { class: 'card' }, c.description) : null,
        (c.enrolled === false) ? h('div', { class: 'info-box' }, 'Enroll to see the lesson content and track your progress.') : null,
        h('div', { class: 'grid grid-2' },
            h('div', { class: 'card' }, h('h2', {}, 'Lessons'), lessons),
            h('div', { class: 'card' }, h('h2', {}, 'Assessments'), assessments)),
    ];
    if (c.canManage) parts.push(h('div', { class: 'grid grid-2' }, addLessonForm(c.id, courseId), addAssessmentForm(c.id, courseId)));
    mount(...parts.filter(Boolean));
}

function addLessonForm(id, courseId) {
    const errorSlot = h('div');
    return h('div', { class: 'card' }, h('h2', {}, 'Add lesson'),
        h('form', {
            onSubmit: async (e) => {
                e.preventDefault();
                const d = formData(e.target);
                try {
                    await post(`/api/courses/${id}/lessons`, { title: d.title.trim(), content: d.content || null, durationMinutes: Number(d.durationMinutes) });
                    toast('Lesson added');
                    renderCourseDetail(courseId).catch(showError);
                } catch (err) { errorSlot.replaceChildren(errorBox(err)); }
            },
        }, errorSlot,
        field('Title', h('input', { name: 'title', required: true, minlength: 3, maxlength: 150 })),
        field('Content', h('textarea', { name: 'content', maxlength: 5000 })),
        field('Duration (minutes)', h('input', { name: 'durationMinutes', type: 'number', min: 1, max: 600, value: '30', required: true })),
        h('button', { type: 'submit' }, 'Add lesson')));
}

function addAssessmentForm(id, courseId) {
    const errorSlot = h('div');
    return h('div', { class: 'card' }, h('h2', {}, 'Add assessment'),
        h('form', {
            onSubmit: async (e) => {
                e.preventDefault();
                const d = formData(e.target);
                try {
                    await post(`/api/courses/${id}/assessments`, {
                        title: d.title.trim(), description: d.description || null,
                        maxScore: Number(d.maxScore), passingScore: Number(d.passingScore), maxAttempts: Number(d.maxAttempts),
                    });
                    toast('Assessment added');
                    renderCourseDetail(courseId).catch(showError);
                } catch (err) { errorSlot.replaceChildren(errorBox(err)); }
            },
        }, errorSlot,
        field('Title', h('input', { name: 'title', required: true, minlength: 3, maxlength: 150 })),
        field('Description', h('textarea', { name: 'description', maxlength: 2000 })),
        h('div', { class: 'form-row' },
            field('Max score', h('input', { name: 'maxScore', type: 'number', min: 1, max: 1000, value: '100', required: true })),
            field('Passing score', h('input', { name: 'passingScore', type: 'number', min: 0, max: 1000, value: '60', required: true })),
            field('Max attempts', h('input', { name: 'maxAttempts', type: 'number', min: 1, max: 10, value: '3', required: true }))),
        h('button', { type: 'submit' }, 'Add assessment')));
}

// ------------------------------------------------------------------ student progress

function lessonRow(lesson, onSaved) {
    const input = h('input', { class: 'input-sm', type: 'number', min: 0, max: 100, step: 5, value: String(lesson.completionPercentage), 'aria-label': 'Completion percentage' });
    const save = async (percentage) => {
        try {
            await put(`/api/progress/${lesson.progressId}`, { completionPercentage: percentage });
            toast(`"${lesson.lessonTitle}" updated to ${percentage}%`);
            onSaved();
        } catch (err) { showError(err); }
    };
    return h('tr', {},
        h('td', {}, `${lesson.lessonOrder}. ${lesson.lessonTitle}`),
        h('td', {}, badge(lesson.status)),
        h('td', {}, progressBar(lesson.completionPercentage)),
        h('td', { class: 'small muted' }, fmtDate(lesson.lastAccessedAt)),
        h('td', {}, h('div', { class: 'row' },
            input,
            h('button', { class: 'secondary sm', onClick: () => save(Number(input.value)) }, 'Save'),
            lesson.status !== 'COMPLETED' ? h('button', { class: 'success sm', onClick: () => save(100) }, 'Complete') : null)));
}

function assessmentRows(assessments, { studentActions = false, gradeActions = false, onChanged }) {
    return assessments.map((a) => {
        let action = null;
        const canAttempt = a.status !== 'PASSED' && a.status !== 'SUBMITTED' && a.attempts < a.maxAttempts;
        if (studentActions && canAttempt) {
            action = h('button', {
                class: 'sm',
                onClick: async () => {
                    try { await post(`/api/assessments/${a.assessmentId}/attempts`); toast('Attempt submitted for grading'); onChanged(); } catch (err) { showError(err); }
                },
            }, a.attempts ? 'Retry' : 'Submit attempt');
        }
        if (gradeActions && a.status === 'SUBMITTED') {
            const score = h('input', { class: 'input-sm', type: 'number', min: 0, max: a.maxScore, placeholder: `/${a.maxScore}`, 'aria-label': 'Score' });
            action = h('div', { class: 'row' }, score, h('button', {
                class: 'sm',
                onClick: async () => {
                    if (score.value === '') { toast('Enter a score first', 'error'); return; }
                    try {
                        await put(`/api/assessments/results/${a.resultId}/grade`, { score: Number(score.value) });
                        toast('Graded'); onChanged();
                    } catch (err) { showError(err); }
                },
            }, 'Grade'));
        }
        return h('tr', {},
            h('td', {}, a.assessmentTitle),
            h('td', {}, badge(a.status)),
            h('td', {}, a.score === null || a.score === undefined ? '—' : `${a.score}/${a.maxScore}`),
            h('td', { class: 'small' }, `pass ≥ ${a.passingScore}`),
            h('td', {}, `${a.attempts}/${a.maxAttempts}`),
            h('td', {}, action || (a.feedback ? h('span', { class: 'small muted' }, a.feedback) : '')));
    });
}

function assessmentTable(assessments, options) {
    if (!assessments.length) return h('div', { class: 'empty' }, 'No assessments.');
    return h('div', { class: 'table-wrap' }, h('table', {},
        h('thead', {}, h('tr', {}, ['Assessment', 'Status', 'Score', 'Passing', 'Attempts', ''].map((t) => h('th', {}, t)))),
        h('tbody', {}, assessmentRows(assessments, options))));
}

async function renderMyProgress(focusCourseId) {
    const p = await get('/api/progress/me');
    const refresh = () => renderMyProgress(focusCourseId).catch(showError);
    let courses = p.courses;
    if (focusCourseId) courses = [...courses].sort((a, b) => (a.courseId === focusCourseId ? -1 : b.courseId === focusCourseId ? 1 : 0));

    const sections = courses.map((c) => h('div', { class: 'card' },
        h('div', { class: 'spread' },
            h('h2', {}, h('a', { href: `#/courses/${c.courseId}` }, c.courseTitle)),
            h('div', { class: 'row' }, badge(c.status))),
        progressBar(c.completionPercentage),
        h('p', { class: 'small muted' }, `${c.lessonsCompleted}/${c.totalLessons} lessons completed · ${c.assessmentsPassed}/${c.totalAssessments} assessments passed`),
        h('h3', {}, 'Lessons'),
        c.lessons.length
            ? h('div', { class: 'table-wrap' }, h('table', {},
                h('thead', {}, h('tr', {}, ['Lesson', 'Status', 'Progress', 'Last accessed', 'Update'].map((t) => h('th', {}, t)))),
                h('tbody', {}, c.lessons.map((l) => lessonRow(l, refresh)))))
            : h('div', { class: 'empty' }, 'No lessons yet.'),
        h('h3', {}, 'Assessments'),
        assessmentTable(c.assessments, { studentActions: true, onChanged: refresh })));

    mount(
        pageHeader('My progress', `Overall completion ${p.overallCompletionPercentage}% · ${p.completedCourses}/${p.enrolledCourses} courses completed`),
        ...(sections.length ? sections : [h('div', { class: 'card empty' }, 'You are not enrolled in any course yet. ', h('a', { href: '#/courses' }, 'Browse courses'))]));
}

async function renderMyAssessments() {
    const list = await get('/api/assessments/me');
    const refresh = () => renderMyAssessments().catch(showError);
    const byCourse = new Map();
    list.forEach((a) => { if (!byCourse.has(a.courseTitle)) byCourse.set(a.courseTitle, []); byCourse.get(a.courseTitle).push(a); });
    mount(
        pageHeader('My assessments', `${list.filter((a) => a.status === 'PASSED').length} of ${list.length} passed`),
        ...(list.length
            ? [...byCourse.entries()].map(([title, items]) => h('div', { class: 'card' }, h('h2', {}, title),
                assessmentTable(items, { studentActions: true, onChanged: refresh })))
            : [h('div', { class: 'card empty' }, 'No assessments in your courses yet.')]));
}

// ------------------------------------------------------------------ course progress report (instructor / admin / student-self)

async function renderCourseProgress(courseId) {
    const report = await get(`/api/progress/course/${courseId}`);
    const refresh = () => renderCourseProgress(courseId).catch(showError);
    const canGrade = role() === 'INSTRUCTOR' || role() === 'ADMIN';

    const rows = [];
    report.students.forEach((s) => {
        const detail = h('tr', { class: 'detail-row', hidden: true },
            h('td', { colspan: 7 },
                h('div', { class: 'stack' },
                    h('h3', {}, `Lessons - ${s.studentName}`),
                    s.lessons.length
                        ? h('div', { class: 'table-wrap' }, h('table', {},
                            h('thead', {}, h('tr', {}, ['Lesson', 'Status', 'Progress', 'Last accessed'].map((t) => h('th', {}, t)))),
                            h('tbody', {}, s.lessons.map((l) => h('tr', {},
                                h('td', {}, `${l.lessonOrder}. ${l.lessonTitle}`), h('td', {}, badge(l.status)),
                                h('td', {}, progressBar(l.completionPercentage)),
                                h('td', { class: 'small muted' }, fmtDate(l.lastAccessedAt)))))))
                        : h('div', { class: 'empty' }, 'No lessons.'),
                    h('h3', {}, `Assessments - ${s.studentName}`),
                    assessmentTable(s.assessments, { gradeActions: canGrade, onChanged: refresh }))));
        const toggle = h('button', {
            class: 'secondary sm',
            onClick: () => { detail.hidden = !detail.hidden; toggle.textContent = detail.hidden ? 'Details' : 'Hide'; },
        }, 'Details');
        const awaiting = s.assessments.filter((a) => a.status === 'SUBMITTED').length;
        rows.push(h('tr', {},
            h('td', {}, h('strong', {}, s.studentName), h('div', { class: 'small muted' }, s.studentEmail)),
            h('td', {}, progressBar(s.completionPercentage)),
            h('td', {}, badge(s.status)),
            h('td', {}, `${s.lessonsCompleted}/${s.totalLessons}`),
            h('td', {}, `${s.assessmentsPassed}/${s.totalAssessments}`),
            h('td', {}, awaiting ? badge(`${awaiting} TO GRADE`, 'warn') : ''),
            h('td', {}, toggle)), detail);
    });

    mount(
        pageHeader(report.courseTitle,
            `Instructor: ${report.instructorName} · ${report.enrolledStudents} enrolled · average completion ${report.averageCompletionPercentage}%`,
            badge(report.scope === 'SELF' ? 'YOUR PROGRESS ONLY' : 'ALL STUDENTS'),
            h('a', { class: 'btn secondary', href: `#/courses/${report.courseId}` }, 'Course details')),
        h('div', { class: 'card' },
            report.students.length
                ? h('div', { class: 'table-wrap' }, h('table', {},
                    h('thead', {}, h('tr', {}, ['Student', 'Completion', 'Status', 'Lessons', 'Assessments', 'Grading', ''].map((t) => h('th', {}, t)))),
                    h('tbody', {}, rows)))
                : h('div', { class: 'empty' }, 'No students are enrolled in this course yet.')));
}

// ------------------------------------------------------------------ admin

async function renderAdmin() {
    const [users, enrollments] = await Promise.all([get('/api/admin/users'), get('/api/admin/enrollments')]);
    const refresh = () => renderAdmin().catch(showError);
    const me = session.user;

    const userRows = users.map((u) => h('tr', {},
        h('td', {}, String(u.id)),
        h('td', {}, u.fullName),
        h('td', {}, u.email),
        h('td', {}, badge(u.role)),
        h('td', {}, u.enabled ? badge('ACTIVE') : badge('DISABLED')),
        h('td', {}, u.id === me.id ? h('span', { class: 'small muted' }, 'you') : h('button', {
            class: `sm ${u.enabled ? 'danger' : 'success'}`,
            onClick: async () => {
                try { await patch(`/api/admin/users/${u.id}/status`, { enabled: !u.enabled }); toast(`User ${u.enabled ? 'disabled' : 'enabled'}`); refresh(); } catch (err) { showError(err); }
            },
        }, u.enabled ? 'Disable' : 'Enable'))));

    const errorSlot = h('div');
    const createForm = h('form', {
        onSubmit: async (e) => {
            e.preventDefault();
            errorSlot.replaceChildren();
            const d = formData(e.target);
            try {
                await post('/api/admin/users', { fullName: d.fullName.trim(), email: d.email.trim(), password: d.password, role: d.role });
                toast('User created');
                refresh();
            } catch (err) { errorSlot.replaceChildren(errorBox(err)); }
        },
    }, errorSlot,
    h('div', { class: 'form-row' },
        field('Full name', h('input', { name: 'fullName', required: true, maxlength: 100 })),
        field('Email', h('input', { name: 'email', type: 'email', required: true, maxlength: 150 })),
        field('Password', h('input', { name: 'password', type: 'password', required: true, minlength: 8, maxlength: 72, autocomplete: 'new-password' })),
        field('Role', h('select', { name: 'role' }, ['INSTRUCTOR', 'STUDENT', 'ADMIN'].map((r) => h('option', { value: r }, r))))),
    h('button', { type: 'submit' }, 'Create user'));

    mount(
        pageHeader('Administration', `${users.length} users · ${enrollments.length} enrollments`),
        h('details', { class: 'card' }, h('summary', {}, h('strong', {}, 'Create a user (e.g. an instructor)')), createForm),
        h('div', { class: 'card' }, h('h2', {}, 'Users'), h('div', { class: 'table-wrap' }, h('table', {},
            h('thead', {}, h('tr', {}, ['ID', 'Name', 'Email', 'Role', 'Status', ''].map((t) => h('th', {}, t)))),
            h('tbody', {}, userRows)))),
        h('div', { class: 'card' }, h('h2', {}, 'Enrollments'), enrollments.length
            ? h('div', { class: 'table-wrap' }, h('table', {},
                h('thead', {}, h('tr', {}, ['Student', 'Course', 'Status', 'Enrolled at', ''].map((t) => h('th', {}, t)))),
                h('tbody', {}, enrollments.map((e) => h('tr', {},
                    h('td', {}, e.studentName), h('td', {}, e.courseTitle), h('td', {}, badge(e.status)),
                    h('td', { class: 'small muted' }, fmtDate(e.enrolledAt)),
                    h('td', {}, h('a', { class: 'btn secondary sm', href: `#/courses/${e.courseId}/progress` }, 'Progress')))))))
            : h('div', { class: 'empty' }, 'No enrollments.')));
}

// ------------------------------------------------------------------ router

const PUBLIC_ROUTES = new Set(['#/login', '#/register']);

async function route() {
    const hash = location.hash || '#/dashboard';
    const [path, query] = hash.split('?');
    const params = new URLSearchParams(query || '');

    if (!session.token && !PUBLIC_ROUTES.has(path)) {
        location.hash = '#/login';
        return;
    }
    if (session.token && PUBLIC_ROUTES.has(path)) {
        location.hash = '#/dashboard';
        return;
    }
    renderChrome();

    const r = role();
    let m;
    try {
        if (path === '#/login') return renderLogin();
        if (path === '#/register') return renderRegister();
        if (path === '#/dashboard') return await renderDashboard();
        if (path === '#/courses') return await renderCourses();
        if ((m = path.match(/^#\/courses\/(\d+)\/progress$/))) return await renderCourseProgress(Number(m[1]));
        if ((m = path.match(/^#\/courses\/(\d+)$/))) return await renderCourseDetail(Number(m[1]));
        if (path === '#/progress' && r === 'STUDENT') return await renderMyProgress(Number(params.get('course')) || null);
        if (path === '#/assessments' && r === 'STUDENT') return await renderMyAssessments();
        if (path === '#/admin' && r === 'ADMIN') return await renderAdmin();
        location.hash = '#/dashboard';
    } catch (err) {
        if (err instanceof ApiError && err.status === 401) return;
        mount(h('div', { class: 'card' }, h('h2', {}, `Error${err.status ? ` ${err.status}` : ''}`), errorBox(err),
            h('a', { class: 'btn secondary', href: '#/dashboard' }, 'Back to dashboard')));
    }
}

window.addEventListener('hashchange', route);
route();
