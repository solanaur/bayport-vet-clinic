# Bayport online — Netlify + Render + Supabase (free tier)

**Stack:** Supabase (database) → Render (API) → Netlify (website)

**Repo:** https://github.com/1una229/bayport-vet-clinic_1

---

## STEP 1 — Supabase (you already did this)

| Item | Your value |
|------|------------|
| Project ID | `gmyoopqvqvpybloqsdix` |
| JDBC URL | `jdbc:postgresql://db.gmyoopqvqvpybloqsdix.supabase.co:5432/postgres?sslmode=require` |
| Username | `postgres` |
| Password | *(your Supabase DB password — set in Render only, never in Git)* |

---

## STEP 2 — Render (backend API) — **DO THIS NOW**

### Option A — Blueprint (easiest)

1. Go to [render.com](https://render.com) → sign up / sign in (free)
2. **New +** → **Blueprint**
3. Connect GitHub → select **`1una229/bayport-vet-clinic_1`**
4. Render reads `render.yaml` at repo root
5. When prompted, enter **secret** values:
   - `SPRING_DATASOURCE_PASSWORD` → your Supabase DB password
   - `SPRING_MAIL_USERNAME` → `bayportveterinaryclinic@gmail.com`
   - `SPRING_MAIL_PASSWORD` → Gmail app password
   - `SPRING_WEB_CORS_ALLOWED_ORIGINS` → leave blank for now (add after Step 3)
6. Click **Apply** → wait until **Live**
7. Copy your URL, e.g. `https://bayport-api.onrender.com`
8. Test: `https://YOUR-APP.onrender.com/api/health` → should show `"status":"UP"`

### Option B — Manual Web Service

1. **New +** → **Web Service** → connect GitHub repo
2. **Root Directory:** `bayport-vet-clinic/bayport-vet-clinic/bayport-backend`
3. **Runtime:** Docker
4. **Instance type:** Free
5. Add environment variables:

```
SPRING_PROFILES_ACTIVE=freecloud
SPRING_DATASOURCE_URL=jdbc:postgresql://db.gmyoopqvqvpybloqsdix.supabase.co:5432/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=postgres
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
| Render build fails | Check logs; ensure root dir is `bayport-vet-clinic/bayport-vet-clinic/bayport-backend` |
| `/api/health` timeout | Free tier sleeping — wait 60s and retry |
| Database connection error | Verify Supabase password; try Supabase **Connect → Session pooler** JDBC if direct host fails |
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
