# Security

## Approach

PayLens uses **Spring Security with a single HR Manager account** and an **HTTP session cookie** (`PAYLENSSESSION`).

| Piece | Choice |
| --- | --- |
| Identity | One in-memory HR Manager user (assessment persona) |
| Password storage | BCrypt hash in memory after startup; secrets from env |
| Session | Server-side HTTP session, `HttpOnly` + `SameSite=Lax` cookie |
| API protection | `/api/v1/**` requires `ROLE_HR_MANAGER` |
| Public | `POST /api/v1/auth/login`, `GET /actuator/health`, OpenAPI/Swagger |
| Frontend | Login page, `authGuard`, credentials on every request, 401 → login |

This is intentionally **not** OAuth/SSO, LDAP, or a multi-tenant IdP. Those add deploy complexity without helping an HR-demo assessment.

## Secrets (never in source)

Set environment variables before running the API:

```bash
export PAYLENS_HR_USERNAME='hr.manager'
# Preferred: precomputed BCrypt hash
export PAYLENS_HR_PASSWORD_HASH='$2a$10$...'
# Or for local/demo bootstrap only (encoded at startup, not written to disk):
export PAYLENS_HR_PASSWORD='choose-a-strong-password'
export PAYLENS_CORS_ORIGINS='http://localhost:4200'
```

PowerShell:

```powershell
$env:PAYLENS_HR_USERNAME = "admin"
$env:PAYLENS_HR_PASSWORD = "admin"
# Start the API in THIS same PowerShell window (or restart the IDE debug session after setting env).
```

**`dev` profile** defaults to `admin` / `admin` when env vars are missing (IDE-friendly).

**Important:** `PAYLENS_HR_PASSWORD_HASH` must be a real BCrypt string (starts with `$2a$` / `$2b$` / `$2y$`). Putting plaintext like `admin` in the hash field breaks login — the app used to treat that as the stored password hash. Use `PAYLENS_HR_PASSWORD` for local plaintext bootstrap.
Generate a BCrypt hash (example with Spring):

```text
new BCryptPasswordEncoder().encode("your-password")
```

**Rules**

- Do not commit plaintext passwords or hashes that unlock production.
- Prefer `PAYLENS_HR_PASSWORD_HASH` in deployed environments.
- `PAYLENS_HR_PASSWORD` is accepted only as a bootstrap convenience: the app encodes it with BCrypt at startup and does not persist the plaintext.

The `test` profile uses a local-only password (`test-hr-password`) for automated tests.

## CORS

`CorsConfigurationSource` allows configured origins (`PAYLENS_CORS_ORIGINS`, default `http://localhost:4200`) with **credentials**. Session cookies therefore work for the Angular app (including `ng serve` proxy and direct `localhost:4200` → `8081` calls).

## CSRF trade-off

CSRF protection is **disabled** for this assessment API.

**Why:** The Angular SPA uses JSON APIs and a session cookie. Enabling cookie CSRF correctly requires a CSRF bootstrap endpoint and header plumbing on every mutating call. That is valuable in production browser apps, but noisy for a short assessment.

**Mitigations in this design:**

- Cookie is `HttpOnly` (not readable by JS)
- `SameSite=Lax` reduces cross-site POST risk for typical third-party cases
- CORS allow-list restricts which browser origins may call the API with credentials

**If this were production HR software:** enable Spring CSRF (`CookieCsrfTokenRepository`) or move to short-lived bearer tokens with careful XSS controls.

## Actuator

Only `health` is exposed. `show-details: never` so env/db credentials are not leaked via actuator.

## Frontend session handling

1. `authGuard` calls `/api/v1/auth/me` when the in-memory user is empty (cookie restore).
2. Global HTTP interceptor sends `withCredentials: true`.
3. On **401** (except login/me), client clears auth state and routes to `/login?reason=session`.
4. Shell **Sign out** calls `POST /api/v1/auth/logout` and invalidates the server session.

## Rejected alternatives

| Option | Why not for this assessment |
| --- | --- |
| OAuth2 / OIDC | Needs external IdP, redirect plumbing, secrets management |
| JWT in localStorage | Simpler cross-origin, but XSS can steal tokens; logout is client-only unless denylist added |
| Full user admin UI | Out of scope — one HR Manager persona |

## Manual check

1. Start API with `PAYLENS_HR_USERNAME` + password env vars.
2. Open UI → redirected to `/login`.
3. Sign in → dashboard/employees load.
4. Call `/api/v1/employees` without cookie → `401`.
5. Sign out → protected routes require login again.
