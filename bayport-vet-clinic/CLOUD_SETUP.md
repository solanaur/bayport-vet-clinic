# Bayport free-cloud deployment (Koyeb + Supabase + Netlify)

## Stack

| Piece | Service |
|--------|---------|
| Database | **Supabase** — PostgreSQL (free tier) |
| API | **Koyeb** — Web Service, Java 17, run the Spring Boot JAR |
| Static UI | **Netlify** — repo root `netlify.toml` builds from `bayport-vet-clinic/` |
| Files | **Local uploads** on the container (`./data/uploads`) for demos; use S3/Cloudinary later for durability |
| Email | **Gmail SMTP** via env vars |

## 1) Supabase

1. Create a project → **Project Settings → Database**.
2. Copy the **connection string** (host, user, password, database `postgres`).
3. JDBC URL format:

   `jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require`

## 2) Koyeb (backend)

- **Build**: `mvn -f bayport-vet-clinic/bayport-backend package -DskipTests`
- **Run**: `java -jar bayport-backend/target/bayport-backend-0.0.1-SNAPSHOT.jar` with profile `freecloud`.

**Environment variables**

| Variable | Example / notes |
|----------|------------------|
| `SPRING_PROFILES_ACTIVE` | `freecloud` |
| `SPRING_DATASOURCE_URL` | JDBC URL above |
| `SPRING_DATASOURCE_USERNAME` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Supabase DB password |
| `JWT_SECRET` | Long random string |
| `SPRING_WEB_CORS_ALLOWED_ORIGINS` | `https://your-site.netlify.app` (comma-separated if several) |
| `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | Gmail + app password (optional) |
| `PORT` | Set by Koyeb (defaults to 8080 locally) |

## 3) Netlify (frontend)

Repo root contains `netlify.toml` (base directory `bayport-vet-clinic`).

**Site → Environment variables**

| Variable | Value |
|----------|--------|
| `BAYPORT_API_BASE` | `https://<your-koyeb-app>.koyeb.app/api` |

Build runs `node scripts/write-deploy-env.js`, which writes `assets/deploy-env.js` before publish.

## 4) Local development (unchanged)

- Default `application.properties`: **MySQL** on localhost (or override with env).
- `--spring.profiles.active=h2` for file-based H2.
- `--spring.profiles.active=desktop` for packaged desktop MySQL profile.

Do **not** use `freecloud` locally unless you point it at Supabase.
