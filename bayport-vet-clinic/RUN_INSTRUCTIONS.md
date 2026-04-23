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

Add your frontend URL to CORS in `application-desktop.properties`:

```properties
spring.web.cors.allowed-origins=file://,http://localhost:8080,https://your-app.example.com
```

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
