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

Spring Boot initializes the `platform` schema and the three isolated practice schemas automatically. Personal databases use generated `workspace_<internal-id>` schemas. The React app never receives database credentials.

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

For local Vite, set `VITE_API_BASE_URL=http://localhost:8080`. `VITE_DEV_ROLE` may be `user` or `admin`; this is only a temporary frontend demo role.

## 3. Deploy (free-tier friendly)

- Backend: create a Render Blueprint from `render.yaml`, then enter the four backend environment variables in Render.
- Frontend: import the repository into Vercel, use the repository root, and set `VITE_API_BASE_URL` to the Render API URL plus `VITE_DEV_ROLE` as needed.
- Finally set Render's `CORS_ALLOWED_ORIGINS` to the exact Vercel site origin and redeploy the backend.

## Verification

```powershell
cd backend
mvn test
mvn package -DskipTests
cd ..
npm run lint
npm run build
```
