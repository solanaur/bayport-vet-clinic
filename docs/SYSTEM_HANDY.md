# Bayport Veterinary Clinic — System Handy Guide
**Copy-paste ready · Last updated from codebase (solanaur/bayport-vet-clinic)**

---

## 1. What this system is

Bayport is a **veterinary clinic management system (VCMS)** for:
- Pet & owner records
- Appointments / queue
- Consultations & medical history
- Prescriptions
- Inventory & POS / billing
- Reminders (email)
- Staff users & audit logs

**Modes**
| Mode | How it runs |
|------|-------------|
| **Desktop** | Electron app + local Spring Boot + **H2** database |
| **Cloud** | Netlify (frontend) + Render (API) + **PostgreSQL/Supabase** |

---

## 2. Where the code lives (on this PC)

```
C:\Users\Administrator\Desktop\bayport-main\bayport-vet-clinic\     ← Git root
└── bayport-vet-clinic\                                              ← App root
    ├── *.html, assets\                                              ← Frontend UI
    ├── bayport-backend\                                             ← Spring Boot API
    ├── desktop-shell\                                               ← Electron desktop
    ├── netlify\functions\                                           ← API proxy + email
    └── data\                                                        ← Local H2 DB + uploads (runtime)
```

**Git remote:** `https://github.com/solanaur/bayport-vet-clinic.git`  
**Branch:** `main`

---

## 3. Tech stack

| Layer | Technology |
|-------|------------|
| Frontend | HTML + JavaScript + Tailwind CSS (CDN) |
| Backend | Java **17**, Spring Boot **3.3.3**, Spring Security, JPA/Hibernate |
| Auth | BCrypt passwords, **JWT (HS256)**, Bearer token |
| Desktop | Electron **32.x** |
| Local DB | **H2** file (`./data/bayport-db`) |
| Cloud DB | **PostgreSQL** (Supabase) via `freecloud` profile |
| Optional local DB | **MySQL 8** (`bayport_db`) |
| Email | Gmail SMTP (desktop/local); Brevo / Resend / Netlify relay (cloud) |
| Hosting | **Netlify** (UI) + **Render** (API) |

---

## 4. Live URLs (cloud)

| What | URL |
|------|-----|
| Website | https://bayport-vet-clinic-com.netlify.app |
| API | https://bayport-api.onrender.com/api |
| Health check | https://bayport-api.onrender.com/api/health |
| Email function | https://bayport-vet-clinic-com.netlify.app/.netlify/functions/send-email |

Frontend uses **same-origin `/api`** on Netlify (proxy → Render). See `assets/deploy-env.js`.

---

## 5. How to run (Desktop)

```bat
cd C:\Users\Administrator\Desktop\bayport-main\bayport-vet-clinic\bayport-vet-clinic\desktop-shell
npm install
npm run desktop
```

- Starts Electron + backend automatically (H2, no MySQL required).
- Rebuild jar if needed: `npm run desktop:rebuild`

**Browser alternative**
```bat
:: Terminal 1 — API
cd ...\bayport-backend
mvn spring-boot:run -Dspring-boot.run.profiles=h2

:: Terminal 2 — UI
cd ...\bayport-vet-clinic
python -m http.server 3000
```
Open http://localhost:3000 then in DevTools:
```js
localStorage.setItem("bayport_api_base", "http://localhost:8080/api");
location.reload();
```

---

## 6. Default logins (change after first use)

| Username | Password | Role |
|----------|----------|------|
| `admin` | `admin123` | Admin |
| `vet` | `vet123` | Veterinarian |
| `frontdesk` | `frontdesk123` | Front Office |

Legacy `recept` / `pharm` are treated as Front Office.

---

## 7. Roles (who can do what)

### UI pages by role (`assets/app.js` → `CONFIG`)

| Page | Admin | Vet | Front Office |
|------|:-----:|:---:|:------------:|
| Dashboard | ✓ | ✓ | ✓ |
| Pet Records / Profile | ✓ | ✓ | ✓ |
| Appointments | ✓ | ✓ | ✓ |
| Consultations / Rx | ✓ | ✓ | — |
| Inventory | ✓ | — | ✓ |
| Billing / POS | ✓ | — | ✓ |
| Reminders | ✓ | — | ✓ |
| Reports | ✓ | — | — |
| Activity Logs | ✓ | — | — |
| Recycle Bin | ✓ | — | — |
| Manage Users | ✓ | — | — |

**Important:** Hiding a sidebar item is **not** security. The **API enforces roles**.

### Backend role rules (simplified)

| Area | Who |
|------|-----|
| `/api/admin/**`, `/api/users/**`, `/api/ops/**` | **ADMIN only** |
| Pets / appointments / medical (scoped) | Admin + Front Office (all); **Vet = assigned pets / their appointments only** |
| Prescriptions create | Admin + Vet |
| Rx dispense | Admin + Front Office |
| Billing create/pay | Admin + Front Office (**not** Vet) |
| Billing delete | Admin only |
| Inventory write/delete | Front Office / Admin (delete = Admin) |
| Recycle bin | Admin only |

---

## 8. UI map (what each screen is for)

| File / screen | Purpose |
|---------------|---------|
| `index.html` | Landing → Sign In |
| `login.html` | Username/password + OTP |
| `dashboard.html` | Role home / shortcuts |
| `pet-records.html` | Search/list pets, add pet |
| `pet-profile.html` | Pet details, visits, medical records, reminders |
| `appointments.html` | Queue / schedule |
| `appointments-calendar.html` | Calendar view |
| `consultations.html` | Clinical visit + Rx pad (`?tab=rx`) |
| `billing.html` | Invoices, payments (`?checkout=1` = POS) |
| `inventory.html` | Products/services stock |
| `reminders.html` | Owner reminder emails |
| `reports.html` | Summaries / Excel-style exports |
| `manage-users.html` | Staff accounts (admin) |
| `activity-logs.html` | Audit trail (admin) |
| `recycle-bin.html` | Soft-deleted pets (admin) |

Brand color: **Bayport blue `#0057b8`** (`--soft-teal`).

---

## 9. Authentication & security (what you need to know)

### Login flow
1. User enters username + password  
2. **Desktop/dev:** seed accounts (`admin`, `vet`, `frontdesk`, …) may **skip OTP** if `bayport.security.otp-bypass-enabled=true`  
3. **Production/cloud:** OTP required → email 4-digit code (5 min) → verify → JWT  
4. JWT stored in browser **`localStorage`** as `token` / `jwt`  
5. Every API call: `Authorization: Bearer <token>`

### Password reset
- `POST /api/auth/password-reset/request` `{ "email": "..." }`  
- `POST /api/auth/password-reset/confirm` `{ "email", "code", "newPassword" }`  
- Same response whether email exists (no account enumeration)

### Security features in place
- BCrypt password hashes (passwords **cannot** be viewed in Manage Users)
- JWT validation (signature + expiry)
- Login rate limit + temporary lockout after failed attempts
- OTP hashed, attempt limits, send/verify rate limits
- Role checks on sensitive APIs
- Vet **record-level** checks (`RecordAccessService` + `assignedVeterinarianId` on pets)
- Safer API error messages (no stack traces to clients)
- Security headers (HSTS, nosniff, frame options) on API; Netlify headers on static site
- Electron: `contextIsolation=true`, `nodeIntegration=false`, `sandbox=true`

### Security still to remember
- JWT in **localStorage** (XSS risk — don’t inject untrusted HTML)
- Desktop OTP bypass is for **local seed accounts only** — keep **off** in production
- Set strong `JWT_SECRET` in cloud env (never use the default string in production)

### Key security settings
```
bayport.security.otp-bypass-enabled=false   # production
bayport.security.login.max-failures=5
bayport.security.login.lock-seconds=900
bayport.security.otp.max-verify-attempts=5
bayport.security.rate-limit.login-max=20
bayport.security.rate-limit.otp-send-max=5
jwt.expiration=86400000                     # 24 hours
```

---

## 10. Database

### Profiles

| Profile | When | Database |
|---------|------|----------|
| `h2` / `desktop` | Local Electron / quick run | H2 file `./data/bayport-db` |
| (MySQL) | Local with Workbench | MySQL `bayport_db` |
| `freecloud` | Render production | PostgreSQL (Supabase) |

### Main entities (conceptually)
- **Users** (+ roles: ADMIN, VET, FRONT_OFFICE, …)
- **Owners**, **Pets** (`assigned_veterinarian_id`, soft-delete)
- **Appointments** (vet name, status, consultation time)
- **Procedures** / visits on pets
- **Pet medical records** (vaccination, notes, diagnostics, surgery, external docs)
- **Prescriptions**
- **Billing records**, **Sales / POS**
- **Inventory items**
- **Reminders**, **Notifications**
- **Audit logs**, **Operation logs**, MFA codes

Schema: Hibernate `ddl-auto=update` (local/cloud freecloud); Flyway SQL under `db/migration` for MySQL-oriented installs.

---

## 11. API handbook (main endpoints)

Base URL local: `http://localhost:8080/api`  
Base URL cloud: `https://bayport-api.onrender.com/api` (or same-origin `/api` on Netlify)

### Auth (public)
| Method | Path | Notes |
|--------|------|-------|
| POST | `/auth/login` | password → JWT or `MFA_REQUIRED` |
| POST | `/auth/mfa/verify` | OTP → JWT |
| POST | `/auth/password-reset/request` | email |
| POST | `/auth/password-reset/confirm` | email + code + newPassword |
| GET | `/health` | status / DB / email provider |

### Pets
| Method | Path |
|--------|------|
| GET/POST | `/pets` |
| GET/PUT/DELETE | `/pets/{id}` |
| POST | `/pets/{id}/photo` |
| POST/PUT/DELETE | `/pets/{id}/procedures`… |
| GET/POST… | `/pets/{petId}/medical-records`… |

### Appointments
| Method | Path |
|--------|------|
| GET | `/appointments` (scoped for vets) |
| GET | `/appointments/{id}` |
| POST | `/appointments` |
| POST | `/appointments/{id}/approve` \| `/done` \| `/cancel` |

### Prescriptions
| Method | Path |
|--------|------|
| GET/POST | `/prescriptions` |
| GET/PUT/DELETE | `/prescriptions/{id}` |
| POST | `/prescriptions/{id}/dispense` |
| GET | `/prescriptions/{id}/pdf` |

### Billing / sales / inventory
| Method | Path |
|--------|------|
| GET/POST | `/billing` |
| POST | `/billing/{id}/pay` |
| DELETE | `/billing/{id}` (admin) |
| GET… | `/inventory` |
| GET | `/sales/pos-recent` |

### Admin
| Method | Path |
|--------|------|
| CRUD | `/users/**` |
| GET | `/admin/audit-logs` |
| Recycle | `/admin/recycle-bin/**` |
| GET | `/ops/log` |
| GET | `/reports/summary` |

**Always send:** `Authorization: Bearer <token>` on protected routes.

---

## 12. Email / reminders

| Environment | Provider |
|-------------|----------|
| Desktop | Gmail SMTP (`SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD`) or `data/mail.env` |
| Cloud | Prefer **Brevo** (`BREVO_API_KEY`, `BREVO_FROM_EMAIL`) or Netlify relay + `BAYPORT_EMAIL_HOOK_SECRET` |
| Fallback | Resend (needs verified domain — not `onboarding@resend.dev` for owner mail) |

Reminders module emails pet owners for vaccines / follow-ups. Check `/api/health` → `emailProvider`, `canSendToAnyRecipient`.

---

## 13. Environment variables (cloud checklist)

**Render (API)**
- `SPRING_PROFILES_ACTIVE=freecloud`
- `SPRING_DATASOURCE_URL` / `USERNAME` / `PASSWORD`
- `JWT_SECRET` (long random)
- `SPRING_WEB_CORS_ALLOWED_ORIGINS` (Netlify URL)
- `BREVO_API_KEY`, `BREVO_FROM_EMAIL` **or** `BAYPORT_EMAIL_HOOK_SECRET` (+ Netlify function)
- `BAYPORT_OTP_BYPASS=false` (or rely on freecloud default)

**Netlify (UI)**
- Publish directory: `bayport-vet-clinic`
- `BREVO_API_KEY`, `BREVO_FROM_EMAIL`, `BAYPORT_EMAIL_HOOK_SECRET` (for send-email function)
- Optional: `BAYPORT_API_BASE` (usually same-origin proxy)

---

## 14. Frontend storage keys

| Key | Meaning |
|-----|---------|
| `token` / `jwt` | Access JWT |
| `role` | UI role (`admin`, `vet`, `front_office`) |
| `bayport_api_base` | Override API URL (local) |
| `bayport_sidebar_collapsed` | Sidebar UI state |

Logout clears auth keys (`assets/app.js`).

---

## 15. Common workflows

1. **New pet:** Pet Records → Add Pet → open Pet Profile → Add medical record / Add visit  
2. **Visit day:** Appointments → assign vet → Approve → Consultations  
3. **Prescribe:** Consultations → Rx tab → save / PDF / email  
4. **Charge:** Billing → New invoice / Receive payment (or POS checkout)  
5. **Stock:** Inventory → products/services  
6. **Remind owners:** Reminders → send (needs email configured)  
7. **Staff:** Manage Users → create with OTP to email (admin)  
8. **Undo delete:** Recycle Bin (admin)

---

## 16. Troubleshooting

| Problem | Check |
|---------|--------|
| Login timeout (cloud) | Render free tier cold start; wait / retry; Netlify `/api` proxy |
| OTP not received | Email env vars; health `emailProvider`; Brevo sender verified |
| “Resend test mode” | Don’t use `onboarding@resend.dev` for owners — use Brevo |
| 401 on API | Token missing/expired — log in again |
| 403 Forbidden | Wrong role or vet accessing another vet’s pet |
| Desktop won’t start | Java 17+, `npm run desktop:rebuild`, port 8080 free |
| Empty vet pet list | Pet must be **assigned** to that vet or have their appointment |

---

## 17. Quick commands cheat sheet

```bat
:: Pull latest
cd C:\Users\Administrator\Desktop\bayport-main\bayport-vet-clinic
git pull origin main

:: Run desktop
cd bayport-vet-clinic\desktop-shell
npm run desktop

:: Compile API only
cd ..\bayport-backend
mvn -q compile -DskipTests

:: Health
curl http://127.0.0.1:8080/api/health
```

---

## 18. One-line summary

**Bayport = role-based clinic app (Admin / Vet / Front Office) with JWT+OTP security, H2 on desktop / Postgres in cloud, HTML UI + Spring Boot `/api`, Electron or Netlify+Render hosting.**

---

*Keep this handy next to your repo. Update it when roles, URLs, or auth settings change.*
