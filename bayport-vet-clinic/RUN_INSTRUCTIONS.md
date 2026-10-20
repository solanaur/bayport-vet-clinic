# Bayport Veterinary Clinic – Run Instructions (Desktop + Cloud)

The desktop app now runs with an embedded offline database profile by default, and still supports MySQL/cloud.

---

## Fast Desktop Run (Windows / macOS)

### 1. Install prerequisites

- Node.js 18+
- Java 17+ (for build time)

### 2. Run desktop app

```bash
cd bayport-vet-clinic/desktop-shell
npm install
npm run desktop
```

The app starts with embedded backend + local H2 file database (`./data`), so it works without MySQL.

---

## Build Verifications (One Command)

From `bayport-vet-clinic/desktop-shell`:

```bash
npm run verify:win
```

```bash
npm run verify:mac
```

```bash
npm run verify:all
```

---

## Local Setup (MySQL Workbench - Optional)

### 1. Create database

In **MySQL Workbench**, run `database_setup.sql` or:

```sql
CREATE DATABASE IF NOT EXISTS bayport_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### 2. Configure credentials

Do not put passwords in `application.properties`. Set environment variables, or let the desktop app create `data/local.secrets.env` (that file is gitignored).

Required before the server will start:

| Variable | Purpose |
|----------|---------|
| `JWT_SECRET` | Signing key. At least 32 bytes. No default. |
| `TOTP_ENCRYPTION_KEY` | Encrypts authenticator secrets. At least 32 bytes. |
| `BAYPORT_DB_PASSWORD` | Desktop H2 password. At least 12 characters. |
| `SPRING_DATASOURCE_URL` | MySQL JDBC URL when the clinic server is not using H2. Example: `jdbc:mysql://127.0.0.1:3306/bayport_db`. |
| `SPRING_DATASOURCE_USERNAME` | MySQL user when not using H2. Use a dedicated account, not root. |
| `SPRING_DATASOURCE_PASSWORD` | MySQL password when not using H2. At least 12 characters. |
| `SERVER_URL` | Address workstations use to reach the clinic server, such as `http://192.168.1.10:8080`. Placeholder only. |
| `BAYPORT_ADMIN_USER` | Fixed administrator username: `R_Admin`. |
| `BAYPORT_ADMIN_PASSWORD` | Password for `R_Admin` on this computer. Used only when that account is not already in the database. At least 10 characters. |
| `BAYPORT_ADMIN_EMAIL` | Email for that first administrator. |

Optional:

| Variable | Purpose |
|----------|---------|
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | Clinic mailbox. Copy `mail.env.example` to `data/mail.env`. |
| `BAYPORT_SMS_ENABLED`, `BAYPORT_SMS_PROVIDER`, `BAYPORT_SMS_API_KEY`, `BAYPORT_SMS_API_SECRET`, `BAYPORT_SMS_SENDER`, `BAYPORT_SMS_ACCOUNT_SID`, `BAYPORT_SMS_ENDPOINT` | Text messages. Leave disabled until a provider is chosen. Copy `sms.env.example` to `data/sms.env`. |
| `JWT_EXPIRATION` | Access-token lifetime in milliseconds. Default 8 hours (`28800000`). |
| `SPRING_WEB_CORS_ALLOWED_ORIGINS` | Browser origins allowed to call the API. |
| `BACKUP_ENCRYPTION_KEY` | Optional key for encrypting backup archives. Leave blank to store them unencrypted. |
| `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT` | Optional web-push keys. Unused until notification approval is turned on. |

Names and placeholders are in `.env.example` and `deploy/clinic/bayport.env.example`.

The desktop shell writes `JWT_SECRET`, `BAYPORT_DB_PASSWORD`, `TOTP_ENCRYPTION_KEY`, and the `R_Admin` password into `data/local.secrets.env` on first launch. The same steps work on Windows and Mac. Java 17 is found from `JAVA_HOME`, a normal install, or the bundled runtime.

### 3. Run the app

```bash
cd bayport-vet-clinic/desktop-shell
npm install
npm run desktop
```

---

## Clinic production

The clinic runs on one Windows PC with MySQL. Other computers use a browser on the clinic network. The checklist, environment variables, HTTPS, and the start/stop scripts are in `docs/DEPLOYMENT.md`. H2 remains the development database.

---

## CI/CD Desktop Releases (GitHub Actions)

Two workflows are included:

- `.github/workflows/desktop-verify.yml`  
  Runs `verify:mac` on macOS and `verify:win` on Windows for pull requests.
- `.github/workflows/desktop-release.yml`  
  Builds installers on native runners when you push a tag like `desktop-v0.1.1`, then publishes artifacts to a GitHub Release.

For signed macOS releases, set these repository secrets:

- `CSC_LINK`
- `CSC_KEY_PASSWORD`
- `APPLE_ID`
- `APPLE_APP_SPECIFIC_PASSWORD`
- `APPLE_TEAM_ID`

---

## Requirements

| Component | Required |
|-----------|----------|
| Java 17+ | Yes |
| Node.js 18+ | Yes |
| MySQL 8+ | Optional for desktop, required for cloud profile |
| Port 8080 | Free |

---

## First administrator

The administrator username is always `R_Admin`. If that account is already in the database, startup leaves its password and authenticator alone. On a new computer, the desktop creates `R_Admin` and writes the password into `data/local.secrets.env`. That file is not committed.

`admin` / `admin123` is only created when `R_Admin` is missing and no local password was set. It is removed once `R_Admin` exists. Google Authenticator is required for `R_Admin`.
