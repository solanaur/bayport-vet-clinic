# Bayport Veterinary Clinic - Desktop Application

A desktop application for managing Bayport Veterinary Clinic, built with Spring Boot backend, HTML/CSS/JS frontend, and Electron desktop shell. Uses **MySQL** as the database (configure in MySQL Workbench).

## Technology Stack

- **Backend**: Spring Boot 3.3.3 (Java 17)
- **Database**: MySQL 8+ (via MySQL Workbench)
- **Frontend**: HTML/CSS/JavaScript (Tailwind CSS)
- **Desktop Shell**: Electron
- **Build Tools**: Maven, npm

## Project Structure

```
bayport-vet-clinic/
├── bayport-backend/          # Spring Boot REST API
│   ├── src/main/java/com/bayport/
│   ├── src/main/resources/   # Configuration files
│   │   ├── application.properties        # MySQL (default)
│   │   └── application-desktop.properties # MySQL (desktop)
│   └── pom.xml
├── desktop-shell/            # Electron wrapper
├── assets/                   # Frontend assets (JS, images)
├── *.html                    # Frontend pages
├── data/                     # Uploads (created at runtime)
└── database_setup.sql        # Run in MySQL Workbench first
```

## Prerequisites

- **Java 17+** (JDK)
- **Maven 3.6+**
- **Node.js 18+** and npm
- **MySQL 8+** (MySQL Workbench)

## Quick Start

From `bayport-backend`:

```bash
./mvnw clean test
```

On Windows use `mvnw.cmd`. Tests do not need a clinic database.

Desktop (one PC, embedded H2):

```bash
cd desktop-shell
npm install
npm run desktop
```

On first launch the desktop app writes `data/local.secrets.env` with `JWT_SECRET`, `BAYPORT_DB_PASSWORD`, and `TOTP_ENCRYPTION_KEY`. Do not commit that file. Copy `.env.example` when you need the full list of names. Mail and SMS stay empty until you copy `mail.env.example` and `sms.env.example` into `data/` and fill them in locally.

MySQL is for the shared clinic server, not for this single-PC desktop run. Set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` in the environment. Do not put those passwords in `application.properties`.

Before pushing, from the repository root (the folder that contains this project folder), run `powershell -File scripts/pre-push-check.ps1` or `./scripts/pre-push-check.sh`.

## Alternative: Backend + Browser

**Terminal 1 – Backend:**
```bash
cd bayport-vet-clinic/bayport-backend
mvn spring-boot:run
```

**Terminal 2 – Frontend:**
```bash
cd bayport-vet-clinic
python3 -m http.server 3000 --directory .
```

Open **http://localhost:3000** in your browser.

## Default Login Credentials

| Username | Password | Role |
|----------|----------|------|
| `admin` | `admin123` | Admin |
| `vet` | `vet123` | Veterinarian |
| `frontdesk` | `frontdesk123` | Front Office (reception + pharmacy) |

Existing `recept` / `pharm` accounts are upgraded to Front Office on startup (same passwords until you change them).

**Important**: Change default passwords after first login!

## Database

- **Database**: `bayport_db`
- **Tables**: Created automatically by Hibernate on first run
- **Seed data**: Default users and sample data from `DataInitializer`

## Building the Installer

```bash
cd bayport-backend
mvn clean package -DskipTests

cd ../desktop-shell
npm run dist
```

Output: `desktop-shell/dist/Bayport Veterinary Clinic Setup X.X.X.exe` (Windows) or `.dmg` (macOS)

End users only need to install the generated app installer. Java runtime and backend are bundled into the desktop package.

## Troubleshooting

1. **Backend won't start**: Ensure MySQL is running and `bayport_db` exists
2. **Port 8080 in use**: Stop other services using port 8080
3. **JAR not found**: Run `mvn clean package -DskipTests` in `bayport-backend/`
4. **Java not found**: Install Java 17+ from [Adoptium](https://adoptium.net/)

## License

[Add your license here]
