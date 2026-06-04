# Bayport — Deploy online (Supabase + Koyeb + Netlify)

Follow these steps in order. Total time: about **45–60 minutes** (first time).

Your code is already on GitHub: **https://github.com/1una229/bayport-vet-clinic_1**

---

## Overview

| Layer | Service | What it does |
|-------|---------|--------------|
| Database | **Supabase** (PostgreSQL) | Stores pets, users, appointments, medical records |
| API | **Koyeb** (Java 17) | Spring Boot backend (`freecloud` profile) |
| Website | **Netlify** | Static HTML/JS UI for browsers |
| Email | **Gmail SMTP** | OTP, reminders (optional but recommended) |

---

## STEP 1 — Supabase (database)

1. Go to [supabase.com](https://supabase.com) → sign in → **New project**
2. Name: `bayport-vet-clinic`, choose a region close to the Philippines, set a **strong DB password** (save it)
3. Wait until the project is ready
4. Open **Project Settings → Database**
5. Under **Connection string**, choose **URI** or **JDBC**. You need:
   - **Host:** `db.xxxxxxxxx.supabase.co`
   - **Port:** `5432`
   - **Database:** `postgres`
   - **User:** `postgres`
   - **Password:** (the one you saved)

6. Build the JDBC URL (copy this pattern):

```
jdbc:postgresql://db.YOUR-PROJECT-REF.supabase.co:5432/postgres?sslmode=require
```

> Schema is created automatically on first API start (`hibernate.ddl-auto=update`). Flyway is off for `freecloud`.

---

## STEP 2 — Koyeb (backend API)

1. Go to [koyeb.com](https://www.koyeb.com) → sign up / sign in
2. **Create Web Service**
3. **Deploy from GitHub** → authorize GitHub → select **`1una229/bayport-vet-clinic_1`**
4. Configure build:

| Setting | Value |
|---------|--------|
| **Branch** | `main` |
| **Builder** | Dockerfile |
| **Dockerfile** | `bayport-vet-clinic/bayport-vet-clinic/bayport-backend/Dockerfile` |
| **Work directory** (if asked) | `bayport-vet-clinic/bayport-vet-clinic/bayport-backend` |

5. **Environment variables** — add all of these:

| Variable | Value |
|----------|--------|
| `SPRING_PROFILES_ACTIVE` | `freecloud` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db.YOUR-REF.supabase.co:5432/postgres?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | *(Supabase DB password)* |
| `JWT_SECRET` | *(long random string, 32+ chars)* |
| `SPRING_WEB_CORS_ALLOWED_ORIGINS` | `https://YOUR-SITE.netlify.app` *(update after Step 3)* |
| `SPRING_MAIL_USERNAME` | `bayportveterinaryclinic@gmail.com` |
| `SPRING_MAIL_PASSWORD` | *(Gmail 16-char app password)* |
| `SPRING_MAIL_HOST` | `smtp.gmail.com` |
| `SPRING_MAIL_PORT` | `587` |

Example JWT secret (generate your own for production):

```
bayport-jwt-2026-change-me-to-64-random-characters-minimum
```

6. **Instance:** Free tier is OK for demo; use at least 512 MB RAM
7. Click **Deploy** and wait until status is **Healthy**
8. Copy your public URL, e.g. `https://bayport-api-xxx.koyeb.app`
9. Test in browser: `https://YOUR-APP.koyeb.app/api/health`  
   You should see JSON with `"status":"UP"`

---

## STEP 3 — Netlify (website)

1. Go to [netlify.com](https://www.netlify.com) → sign in
2. **Add new site → Import an existing project → GitHub**
3. Select **`1una229/bayport-vet-clinic_1`**
4. Build settings (should auto-read `netlify.toml`):

| Setting | Value |
|---------|--------|
| **Branch** | `main` |
| **Base directory** | *(leave empty — netlify.toml sets it)* |
| **Build command** | *(from netlify.toml)* |
| **Publish directory** | *(from netlify.toml)* |

5. **Site configuration → Environment variables** → add:

| Key | Value |
|-----|--------|
| `BAYPORT_API_BASE` | `https://YOUR-APP.koyeb.app/api` |

*(Use your real Koyeb URL from Step 2, ending in `/api`)*

6. **Deploy site**
7. Copy your Netlify URL, e.g. `https://bayport-vet-clinic.netlify.app`

---

## STEP 4 — Link CORS (Koyeb ↔ Netlify)

1. Go back to **Koyeb → your service → Environment variables**
2. Update:

```
SPRING_WEB_CORS_ALLOWED_ORIGINS=https://YOUR-SITE.netlify.app
```

3. **Redeploy** the Koyeb service (or save — Koyeb may restart automatically)

---

## STEP 5 — First login & security

1. Open your **Netlify URL** in Chrome/Edge
2. Log in with default admin (change immediately after):

| Username | Password |
|----------|----------|
| `admin` | `admin123` |

3. Go to **Settings → Users** and change all default passwords
4. Test:
   - Add a pet (health profile + medical history sections)
   - **Reminders** → email banner should show green if SMTP works
   - **Manage Users → Send OTP** (needs SMTP)

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Netlify build fails “Set BAYPORT_API_BASE” | Add `BAYPORT_API_BASE` env var on Netlify, redeploy |
| Login fails / red backend banner | Check Koyeb logs; verify Supabase JDBC URL and password |
| CORS error in browser console | `SPRING_WEB_CORS_ALLOWED_ORIGINS` must exactly match Netlify URL (https, no trailing slash) |
| Email auth failed | Use Gmail **app password**, not normal password |
| Pet photos disappear after redeploy | Koyeb disk is ephemeral — expected on free tier; use desktop for full file storage |
| `/api/health` 404 | URL must be `.../api/health` not root `/` |

---

## Quick checklist

- [ ] Supabase project created, JDBC URL saved
- [ ] Koyeb deployed, `/api/health` returns UP
- [ ] Netlify deployed with `BAYPORT_API_BASE`
- [ ] CORS updated with Netlify URL
- [ ] Admin password changed
- [ ] SMTP tested (OTP or reminder email)

---

## Optional: custom domain

- **Netlify:** Domain settings → add `clinic.yourdomain.com`
- **Koyeb:** Settings → custom domain for API
- Update `SPRING_WEB_CORS_ALLOWED_ORIGINS` and `BAYPORT_API_BASE` to match

---

## Need help?

If a step fails, note:
1. Which step (1–5)
2. Screenshot or exact error message
3. Koyeb deploy logs (last 20 lines)
