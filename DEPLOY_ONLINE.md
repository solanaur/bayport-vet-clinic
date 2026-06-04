# Bayport online — Netlify + Render + Supabase (free tier)

**Stack:** Supabase (database) → Render (API) → Netlify (website)

**Repo:** https://github.com/1una229/bayport-vet-clinic_1

---

## STEP 1 — Supabase: get the **Session pooler** connection (Render needs this)

> **Do not use** the direct `db.gmyoopqvqvpybloqsdix.supabase.co` host on Render — it is IPv6-only and fails there.

1. Open [supabase.com/dashboard/project/gmyoopqvqvpybloqsdix](https://supabase.com/dashboard/project/gmyoopqvqvpybloqsdix)
2. Click the green **Connect** button (top of project home)
3. Open **Connection string** (or **ORMs** tab)
4. Select **Session pooler** (port **5432**)
5. Copy these three values **exactly** (your host may be `aws-0` **or** `aws-1` — do not guess):

| From Supabase | Render env var | Example shape |
|---------------|----------------|---------------|
| Host | part of `SPRING_DATASOURCE_URL` | `aws-1-ap-northeast-2.pooler.supabase.com` |
| User | `SPRING_DATASOURCE_USERNAME` | `postgres.gmyoopqvqvpybloqsdix` |
| Password | `SPRING_DATASOURCE_PASSWORD` | your DB password |

**Build the JDBC URL** (replace `HOST` with what Supabase shows):

```
jdbc:postgresql://HOST:5432/postgres?sslmode=require
```

Example (yours may differ on `aws-0` vs `aws-1`):

```
jdbc:postgresql://aws-1-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require
```

| Item | Your value |
|------|------------|
| Project ID | `gmyoopqvqvpybloqsdix` |
| Pooler user | `postgres.gmyoopqvqvpybloqsdix` |
| Password | *(set in Render only, never in Git)* |

---

## STEP 2 — Render (backend API) — **DO THIS NOW**

### Option A — Blueprint (easiest)

1. Go to [render.com](https://render.com) → sign up / sign in (free)
2. **New +** → **Blueprint**
3. Connect GitHub → select **`1una229/bayport-vet-clinic_1`**
4. Render reads `render.yaml` at repo root
5. When prompted, enter values (copy pooler URL + user from **Step 1**):
   - `SPRING_DATASOURCE_URL` → Session pooler JDBC URL from Supabase Connect
   - `SPRING_DATASOURCE_USERNAME` → `postgres.gmyoopqvqvpybloqsdix`
   - `SPRING_DATASOURCE_PASSWORD` → your Supabase DB password
   - `SPRING_MAIL_USERNAME` → `bayportveterinaryclinic@gmail.com`
   - `SPRING_MAIL_PASSWORD` → Gmail app password
   - `SPRING_WEB_CORS_ALLOWED_ORIGINS` → leave blank for now (add after Step 3)
6. Click **Apply** → wait until **Live**
7. Copy your URL, e.g. `https://bayport-api.onrender.com`
8. Test: `https://YOUR-APP.onrender.com/api/health` → should show `"status":"UP"`

### Option B — Manual Web Service

1. **New +** → **Web Service** → connect GitHub repo
2. **Root Directory:** `bayport-vet-clinic/bayport-backend`
3. **Runtime:** Docker
4. **Instance type:** Free
5. Add environment variables:

```
SPRING_PROFILES_ACTIVE=freecloud
SPRING_DATASOURCE_URL=jdbc:postgresql://<COPY-HOST-FROM-SUPABASE-CONNECT>:5432/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=postgres.gmyoopqvqvpybloqsdix
SPRING_DATASOURCE_PASSWORD=<your-supabase-password>
JWT_SECRET=<long-random-string>
SPRING_MAIL_USERNAME=bayportveterinaryclinic@gmail.com
SPRING_MAIL_PASSWORD=<gmail-app-password>
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
```

6. Deploy → test `/api/health`

> **Note:** Free Render services sleep after ~15 min idle. First request may take 30–60 seconds to wake up.

---

## STEP 3 — Netlify (website)

1. Go to [netlify.com](https://netlify.com) → **Add new site** → **Import from Git**
2. GitHub → **`1una229/bayport-vet-clinic_1`**, branch **`main`**
3. Netlify auto-reads `netlify.toml` — click **Deploy**
4. **Site configuration → Environment variables** → add:

```
BAYPORT_API_BASE=https://YOUR-APP.onrender.com/api
```

*(Replace with your real Render URL from Step 2)*

5. **Trigger deploy** (Deploys → Trigger deploy) so the env var is baked in
6. Copy your site URL, e.g. `https://bayport-vet.netlify.app`

---

## STEP 4 — Connect frontend ↔ backend (CORS)

1. **Render** → your service → **Environment**
2. Add or update:

```
SPRING_WEB_CORS_ALLOWED_ORIGINS=https://YOUR-SITE.netlify.app
```

3. Save → Render redeploys automatically

---

## STEP 5 — Login & go-live

1. Open your **Netlify URL**
2. Log in: `admin` / `admin123`
3. **Change all default passwords** (Settings → Users)
4. Test: add pet, reminders, send email

---

## Checklist

- [ ] Render `/api/health` returns UP
- [ ] Netlify site loads login page
- [ ] No CORS errors in browser (F12 → Console)
- [ ] Login works
- [ ] Admin passwords changed

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Render build fails | Check logs; ensure root dir is `bayport-vet-clinic/bayport-backend` |
| `/api/health` timeout | Free tier sleeping — wait 60s and retry |
| `tenant/user postgres.gmyoopqvqvpybloqsdix not found` | Wrong **pooler host** — copy Session pooler host from Supabase **Connect** (often `aws-1-…` not `aws-0-…`) |
| Render “Exited with status 1” | Open **Logs** → database error; use Session pooler (not `db.*` direct host); set all three DB env vars |
| Netlify “Set BAYPORT_API_BASE” | Add env var on Netlify, redeploy |
| CORS error on login | `SPRING_WEB_CORS_ALLOWED_ORIGINS` must match Netlify URL exactly (https, no trailing `/`) |
| Red “backend” banner | Wrong `BAYPORT_API_BASE` or Render not running |

---

## Cost

| Service | Tier |
|---------|------|
| Supabase | Free |
| Render | Free (sleeps when idle) |
| Netlify | Free |
| Gmail SMTP | Free |

For a single clinic PC with no website, use **desktop app** instead (`npm run desktop`) — no hosting needed.
