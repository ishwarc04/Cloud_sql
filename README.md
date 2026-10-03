# CloudSQL Lab

React/Vite frontend, Spring Boot API, and PostgreSQL runtime. H2 is used only by automated tests.

## 1. Create the free Neon database

1. Create a Neon project and database.
2. Copy the pooled connection details from Neon (do not commit them).
3. Set these environment variables in the terminal that starts Spring Boot:

```powershell
$env:DATABASE_URL="jdbc:postgresql://YOUR-NEON-HOST/YOUR-DATABASE?sslmode=require"
$env:DATABASE_USERNAME="YOUR_NEON_USERNAME"
$env:DATABASE_PASSWORD="YOUR_NEON_PASSWORD"
$env:CORS_ALLOWED_ORIGINS="http://localhost:5173"
```

Spring Boot initializes the `platform` schema and 15 isolated practice schemas automatically. Initialization is idempotent: existing practice datasets are preserved, and empty schemas are seeded transactionally under a PostgreSQL advisory lock. Personal databases use generated `workspace_<internal-id>` schemas. The React app never receives database credentials.

## 2. Run locally

```powershell
cd backend
mvn spring-boot:run
```

In another terminal:

```powershell
Copy-Item .env.example .env.local
npm install
npm run dev
```

Use Node.js 22 or later and Java 21 or later. For local Vite, set `VITE_API_BASE_URL=http://localhost:8080`. Set backend `AUTH_COOKIE_SECURE=false` only for local HTTP, with `AUTH_COOKIE_SAME_SITE=Lax`. Use the same hostname for both services (for example, `localhost`). Backend configuration reads process environment variables; copying `.env.example` does not automatically load backend variables.

## User accounts and progress

- Public signup always creates `USER`, regardless of any submitted role field. Passwords require 12–128 characters and are stored with salted PBKDF2-HMAC-SHA256 (600,000 iterations).
- Login creates a random 256-bit opaque session token in an HttpOnly cookie. Only its SHA-256 hash is stored in PostgreSQL. Sessions expire after `AUTH_SESSION_HOURS` (default 24), survive backend restarts, rotate on login, and are revoked on logout. The browser never stores tokens in localStorage or sessionStorage.
- All application APIs require a session except signup, login, logout, and `/api/health`. Admin APIs additionally require `ADMIN`. Personal database and learning data are scoped to the authenticated account.
- Mutations require `X-CloudSQL-Request: 1`; browser requests use exact-origin credentialed CORS. Failed logins are limited to 10 per normalized email in a rolling 15-minute window, persisted in PostgreSQL.
- Easy/Medium/Hard solutions earn 10/20/30 points once. Progress, points, and submission history commit in one transaction with an account-row lock, preventing concurrent duplicate awards. Wrong answers and query errors count as attempts; Run Query does not.
- The dashboard shows points, solved problems, attempt count, difficulty/category progress, and the 10 most recent submissions. Twelve new original makerspace problems cover Basic Select, Advanced Select, Aggregation, Basic Join, Advanced Join, and Alternative Queries. The three existing problems remain available.
- Learner SQL uses a conservative supported-function list and blocks platform schemas, session-changing functions, alternate escaping, and qualified tables. Some advanced PostgreSQL functions are intentionally unavailable.
- Existing `local-user` demo workspaces/progress remain unassigned and are never automatically attributed to a real account.

### Trusted admin provisioning

Set **backend-only** `ADMIN_EMAIL` and `ADMIN_PASSWORD` environment variables before startup to seed an admin. The password must be 12–128 characters. Provisioning runs once for a new email, preserves an existing admin on restart, and refuses to promote a public user with the same email. Use a fresh provisioning email. Remove these variables after provisioning if desired; the persisted account remains. Never prefix secrets with `VITE_`, commit them, or expose an admin signup form. There is no role-change API.

## Admin operations

The admin console reads `/api/admin/analytics` and `/api/admin/users/{id}`. Both require a persisted `ADMIN` account; ordinary users receive 403 and anonymous requests receive 401. Use trusted backend admin provisioning described above, then sign in normally and open **Admin Dashboard** in the sidebar. No public role selection or role-change endpoint exists.

- **Overview:** all-account totals, 14-day UTC activity chart with submissions/signup toggle, outcome donut, category completion bars, configured quotas, recorded storage, and current database probe latency.
- **People:** search names/emails, filter learners/admins, inspect points, solved problems, attempts, databases, unexpired sessions, and recent submissions for one account.
- **Workspaces:** named owners, status, creation date, and recorded storage versus allocation; legacy demo resources are clearly unassigned.
- **Problems:** unique solvers, attempts, acceptance rate, and query errors by problem, with difficulty filtering.
- **Activity:** recent submission outcomes and awarded points, with outcome filtering. Original SQL query text, database credentials, internal schema IDs, password hashes, and session tokens are not returned.

Account/workspace tables show the latest 200 records; submission lists show the latest 50. Summary metrics cover all records. Acceptance includes repeat submissions; solved counts represent distinct account–problem pairs, so they may exceed the number of catalogue problems. Empty chart dates show zero. Refresh retrieves a consistent PostgreSQL snapshot. Storage is the last measurement recorded by workspace operations, rather than an expensive scan of every database on dashboard load. Database connectivity does not represent a full Render/Vercel uptime check.

## 3. Deploy (free-tier friendly)

- Backend: create a Render Blueprint from `render.yaml`, then set `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, and `CORS_ALLOWED_ORIGINS`. Keep `AUTH_COOKIE_SECURE=true` and `AUTH_COOKIE_SAME_SITE=Lax` in production. Render's public health check is `/api/health`.
- Frontend: import the repository root into Vercel using Vite. The proxy reuses the existing `VITE_API_BASE_URL` environment variable, or you can explicitly set server-side `BACKEND_API_URL=https://YOUR-BACKEND.onrender.com` (origin only, no `/api`). Production browser requests always use `/api/*`, handled by the fixed-upstream serverless proxy before the SPA rewrite. It forwards session cookies and request protection headers without caching responses; sessions belong to the Vercel site origin, avoiding third-party cookie dependence.
- Set Render's `CORS_ALLOWED_ORIGINS` to the **exact** Vercel site origin, with no trailing slash or wildcard (for example, `https://your-project.vercel.app`). Multiple trusted origins may be comma-separated. Browser preview origins require explicit inclusion; avoid wildcard preview access. The proxy preserves Origin so the backend still enforces this allowlist.
- Existing Render services created without Blueprint synchronization may still have `/api/problems` as their health-check path. Change it to `/api/health`; application APIs now require authentication. Database and exact-origin CORS environment variables remain on the existing service.

These are configuration instructions only. No push or deployment occurs automatically.

## Verification

Admins land directly in `/admin`. Learner dashboard, practice, and personal-database routes and APIs reject ADMIN accounts; admins inspect platform resources through the admin views. **Create learner** creates a USER account with a hashed initial password and keeps the current admin session. No admin creation or role selection is exposed in either signup or the admin form.

**Add question** publishes original SQL questions with a structured dataset editor (up to 6 tables, 12 columns per table, 100 rows per table, and 3,000 cells in total). Supported types are INTEGER, DECIMAL, VARCHAR, DATE, and BOOLEAN. Both the starter SELECT and reference SELECT are validated against the isolated dataset before publication, with a 5-second timeout and 200-row limit. Questions and datasets persist in PostgreSQL and appear immediately in learner practice and admin analytics. Reference answers stay backend-only. Initialization reuses existing dataset schemas on restart. SQL seed scripts cannot be supplied through the admin API.

```powershell
cd backend
mvn test
mvn package -DskipTests
cd ..
npm run lint
npm run build
```
