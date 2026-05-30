# Bayport Veterinary Clinic — Project Update Guide

**Last updated:** May 2026  
**Latest local commit:** `95aeeb4` — *Unify consultations and Rx pad editor; remove groomer module and improve desktop deploy.*

This document explains what changed in the project, what works today, what still needs attention, and how to roll back if something goes wrong.

---

## 1. What this project is

| Layer | Technology | Location |
|--------|------------|----------|
| Frontend | HTML, JavaScript, Tailwind CSS | `bayport-vet-clinic/` |
| Backend API | Spring Boot 3.3, Java 17 | `bayport-vet-clinic/bayport-backend/` |
| Desktop app | Electron | `bayport-vet-clinic/desktop-shell/` |
| Database (local / clinic) | MySQL (`bayport_db`) | MySQL Workbench + `database_setup.sql` |
| Database (free cloud option) | PostgreSQL (Supabase) | Profile `freecloud` |
| Static hosting (cloud UI) | Netlify | `netlify.toml` at repo root |
| API hosting (cloud) | Koyeb | See `bayport-vet-clinic/CLOUD_SETUP.md` |

**Primary folder to run and edit:**  
`c:\Users\Administrator\Desktop\bayport-main\bayport-vet-clinic\bayport-vet-clinic\`

An older copy may exist at `bayport-vet-clinic-backup-20260423-210350` — that is **not** the main app path unless you restore from it on purpose.

---

## 2. Major features added or updated

### 2.1 Consultations & Prescriptions (unified module)

The old separate **Prescriptions** sidebar item is merged into **Consultations & Rx**.

| Tab | Purpose |
|-----|---------|
| **Start Consultation** | 3-step visit: Diagnosis → Procedures → Prescription pad |
| **Finished Consultations** | History, search, print/email Rx from past visits |
| **Prescription Records** | Active/archived Rx groups: issue, edit, print, dispense, delete |

- `prescriptions.html` redirects to `consultations.html?tab=rx`.
- Sidebar label: **Consultations & Rx** (admin and vet).

### 2.2 Editable prescription pad (matches printed PDF)

Users type **directly on the pad layout** (same structure as the physical Rx form), not only through inventory pop-ups.

**New files:**

- `assets/rx-pad-editor.js` — pad UI logic
- `assets/rx-pad-editor.css` — pad styling
- `assets/rx-registry.js` — active/archived list, save, print, edit

**Pad capabilities:**

- Clinic logo, letterhead, Pet / Date / Owner
- Large **Rx**, numbered medicines (①②③…), **#** quantity, **Sig:** per line
- **Follow up** and **Veterinarian** signature area at the bottom
- **Page 1 of 1** centered below Follow up and Veterinarian
- **Pet name autocomplete** from clinic pets; owner fills automatically (still editable)
- **Medicine autocomplete** from inventory (Tab / Enter to pick)
- **Enter on Sig** adds the next medicine line; **×** or Backspace on empty line removes a line
- **+ Add medicine** in modal; consultation step 3 has **+ Add medicine line**

**PDF generation** (`PdfService.java`):

- Black Helvetica layout aligned to the physical pad
- Classpath logo: `assets/logo.png` (clinic seal — do not replace with pad scan or placeholder)
- Footer: Follow up left, signature right, **Page 1 of 1** on the row below

### 2.3 Groomer module removed

- Removed from navigation (`assets/app.js`).
- `groomer.html` only redirects to `dashboard.html` (bookmarks still work).
- Grooming **reminder text** in pet profiles is unchanged (not the old Groomer scheduling page).

### 2.4 Desktop app improvements

- `desktop-shell/scripts/ensure-backend-jar.js` — rebuilds backend JAR when source is newer
- `desktop-shell/scripts/start-desktop.js` — starts backend + Electron
- `npm run desktop` — usual way to run the clinic desktop app

### 2.5 Other fixes included in this update

- **Reminders** page: `reminders` added to **admin** role so `guard('reminders')` no longer sends admins to the dashboard by mistake
- UTF-8 text on consultations (arrows, bullets, em dashes) fixed
- `printRxGroup` and related Rx actions exposed on `window` for Bayport Desktop
- Flyway migration **V6** for prescription template-related fields
- Pet context bar on consultation flow (`assets/pet-context.js`)

---

## 3. Who sees what (roles)

| Role | Main modules |
|------|----------------|
| **Admin** | Full workflow + inventory, billing, POS, reports, reminders, settings, users |
| **Vet** | Dashboard, pets, appointments, **Consultations & Rx**, help |
| **Front office / reception / pharmacy** | Dashboard, pets, appointments, inventory, billing, POS, help (no consultations) |

---

## 4. How to run the app (clinic desktop)

```powershell
cd c:\Users\Administrator\Desktop\bayport-main\bayport-vet-clinic\bayport-vet-clinic\desktop-shell
npm install
npm run desktop
```

**Default logins** (change after first use):

| User | Password | Role |
|------|----------|------|
| admin | admin123 | Admin |
| vet | vet123 | Vet |
| frontdesk | frontdesk123 | Front office |

**Before rebuilding the backend JAR:** close Bayport Desktop (and any Java on port 8080), then:

```powershell
cd ..\bayport-backend
mvn clean package -DskipTests
```

---

## 5. Deployment status

### Ready from a code perspective

- Frontend JavaScript syntax checks pass
- Backend compiles (`mvn compile`)
- Local git commit `95aeeb4` contains all listed changes

### Blocked or manual steps

| Item | Status | What to do |
|------|--------|------------|
| **Git push to GitHub** | Failed (403 for wrong GitHub user) | Push as repo owner `solanaur`: `git push origin main` from `bayport-vet-clinic` repo folder |
| **Full JAR rebuild while app is open** | Fails (file locked) | Stop desktop, then `mvn package` |
| **Netlify frontend** | After push | Set `BAYPORT_API_BASE` to your Koyeb API URL; see `CLOUD_SETUP.md` |
| **Koyeb backend** | Manual redeploy | Profile `freecloud`, Supabase JDBC, `JWT_SECRET`, CORS for Netlify URL |
| **Database migration V6** | Required on production DB | Run Flyway on deploy or apply `V6__prescription_rx_template_fields.sql` on MySQL/Postgres as appropriate |

---

## 6. Backups (if something goes wrong)

| Backup | Location | How to use |
|--------|----------|------------|
| **Git commit** | Local repo, commit `95aeeb4` | `git checkout 95aeeb4` or `git reset --hard 95aeeb4` |
| **ZIP archive** | `bayport-vet-clinic-backup-95aeeb4.zip` (Desktop) | Unzip and copy over `bayport-vet-clinic/` folder |
| **Older folder** | `bayport-vet-clinic-backup-20260423-210350` | Previous snapshot; use only if you intend to restore that version |
| **Partial folder copy** | `bayport-vet-clinic-backup-20260517-163135` | May be incomplete if copy was interrupted |

**Previous commit on remote:** `735e2da` — *Enhance reports, Flyway migrations, and clinic UI workflows.*

---

## 7. Known issues & things to fix or watch

### High priority

1. **Push to GitHub** — Cloud deploy (Netlify) will not update until `main` is pushed with correct credentials.
2. **Rebuild JAR with app closed** — Otherwise PDF/Rx backend changes may not appear in desktop until a successful `mvn package`.
3. **Cloud DB vs local MySQL** — Flyway scripts are mostly MySQL-oriented; Supabase/Postgres (`freecloud`) may need extra verification for V6 and Rx fields.

### Medium priority

4. **Reminders not in sidebar** — Admins can open `reminders.html` by URL; add to sidebar in `app.js` if you want it in the menu.
5. **Uploads on Koyeb** — Ephemeral disk; pet photos/uploads may not persist across redeploys (see `CLOUD_SETUP.md`).
6. **Secrets** — Do not commit real MySQL passwords or `JWT_SECRET` to GitHub; use environment variables in Koyeb/Netlify.

### Low priority / optional

7. **Backup folder sync** — Keep only one “source of truth” (`bayport-vet-clinic/bayport-vet-clinic`) to avoid editing the wrong copy.
8. **Windows installer** — Run `npm run dist` in `desktop-shell` after a successful JAR build for a new `.exe` installer.
9. **Default passwords** — Change clinic passwords after go-live.

---

## 8. File map (new or important)

```
bayport-vet-clinic/
├── PROJECT_UPDATE.md          ← this guide
├── netlify.toml               ← cloud frontend build
├── CLOUD_SETUP.md             ← Koyeb + Supabase + Netlify
├── assets/
│   ├── rx-pad-editor.js       ← editable Rx pad
│   ├── rx-pad-editor.css
│   ├── rx-registry.js         ← Rx records list + modal
│   └── app.js                 ← nav, roles, guards
├── consultations.html         ← consultations + Rx tabs
├── prescriptions.html         ← redirect to consultations?tab=rx
├── groomer.html               ← redirect to dashboard
├── bayport-backend/
│   └── src/main/java/.../PdfService.java   ← Rx PDF layout
│   └── src/main/resources/db/migration/V6__*.sql
└── desktop-shell/
    └── scripts/ensure-backend-jar.js
```

---

## 9. Quick test checklist (after update)

- [ ] Login as **vet** → Consultations & Rx visible
- [ ] Start consultation → steps 1–3; pad shows pet/owner from selected pet
- [ ] Type pet name → suggestions; owner auto-fills
- [ ] Add 2+ medicines (Enter on Sig); print PDF — layout matches pad
- [ ] Prescription Records → Issue / Edit / Save / Print
- [ ] Login as **admin** → Reminders page opens (not bounced to dashboard)
- [ ] Groomer URL → redirects to dashboard
- [ ] `prescriptions.html` → opens Consultations Rx tab

---

## 10. Getting help

- **Run locally:** `README.md`, `HOW_TO_RUN_ON_WINDOWS.md` in `bayport-vet-clinic/`
- **Cloud:** `CLOUD_SETUP.md`
- **Full system docs:** `SYSTEM_DOCUMENTATION.md`

To print this summary in the terminal (Windows):

```powershell
cd c:\Users\Administrator\Desktop\bayport-main\bayport-vet-clinic
.\scripts\Show-ProjectUpdate.ps1
```

---

*Bayport Veterinary Clinic — internal update guide for staff and developers.*
