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

Edit `bayport-backend/src/main/resources/application.properties` (and `application-desktop.properties` if needed):

```properties
spring.datasource.username=root
spring.datasource.password=YOUR_MYSQL_PASSWORD
```

### 3. Run the app

```bash
cd bayport-vet-clinic/desktop-shell
npm install
npm run desktop
```

---

## Cloud Deployment

Set these environment variables on your cloud host:

| Variable | Example |
|----------|---------|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://your-db-host:3306/bayport_db` |
| `SPRING_DATASOURCE_USERNAME` | `bayport_user` |
| `SPRING_DATASOURCE_PASSWORD` | `your_secure_password` |
| `JWT_SECRET` | `long-random-secret-at-least-32-bytes` |
| `SPRING_WEB_CORS_ALLOWED_ORIGINS` | `https://desktop-api.example.com,file://` |

Use the production profile when running in cloud:

```bash
java -jar bayport-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=production
```

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

## Default Login

| Username | Password | Role |
|----------|----------|------|
| admin | admin123 | Admin |
| vet | vet123 | Veterinarian |
| frontdesk | frontdesk123 | Front Office |
