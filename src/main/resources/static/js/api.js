// Thin fetch wrapper for the REST API.
// The JWT is kept in sessionStorage (cleared when the tab closes) and sent as a Bearer header.
// It is never put in a cookie, which is why the API does not need CSRF tokens.

const TOKEN_KEY = 'lms.token';
const USER_KEY = 'lms.user';

function read(key) {
    try { return sessionStorage.getItem(key); } catch { return null; }
}

function write(key, value) {
    try {
        if (value === null) sessionStorage.removeItem(key); else sessionStorage.setItem(key, value);
    } catch { /* storage unavailable - keep working for this page view */ }
}

let memoryToken = read(TOKEN_KEY);
let memoryUser = (() => {
    try { return JSON.parse(read(USER_KEY) || 'null'); } catch { return null; }
})();

export const session = {
    get token() { return memoryToken; },
    get user() { return memoryUser; },
    save(auth) {
        memoryToken = auth.accessToken;
        memoryUser = auth.user;
        write(TOKEN_KEY, memoryToken);
        write(USER_KEY, JSON.stringify(memoryUser));
    },
    clear() {
        memoryToken = null;
        memoryUser = null;
        write(TOKEN_KEY, null);
        write(USER_KEY, null);
    },
};

export class ApiError extends Error {
    constructor(status, body) {
        super((body && body.message) || `Request failed (${status})`);
        this.status = status;
        this.body = body || {};
    }
}

export async function api(method, path, body) {
    const headers = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (session.token) headers.Authorization = `Bearer ${session.token}`;

    let response;
    try {
        response = await fetch(path, {
            method,
            headers,
            body: body !== undefined ? JSON.stringify(body) : undefined,
            credentials: 'same-origin',
        });
    } catch {
        throw new ApiError(0, { message: 'Cannot reach the server. Is the backend running?' });
    }

    if (response.status === 204) return null;

    const text = await response.text();
    let data = null;
    if (text) {
        try { data = JSON.parse(text); } catch { data = { message: text.slice(0, 200) }; }
    }

    if (!response.ok) {
        if (response.status === 401 && !path.startsWith('/api/auth/login')) {
            session.clear();
            window.dispatchEvent(new CustomEvent('lms:unauthorized'));
        }
        throw new ApiError(response.status, data);
    }
    return data;
}

export const get = (path) => api('GET', path);
export const post = (path, body) => api('POST', path, body);
export const put = (path, body) => api('PUT', path, body);
export const patch = (path, body) => api('PATCH', path, body);
export const del = (path) => api('DELETE', path);
