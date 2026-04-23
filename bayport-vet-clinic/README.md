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

### 1. Create MySQL Database

In **MySQL Workbench**, run the setup script:

```sql
-- Or run: database_setup.sql
CREATE DATABASE IF NOT EXISTS bayport_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### 2. Configure MySQL Credentials

Edit `bayport-backend/src/main/resources/application.properties`:

```properties
spring.datasource.username=root
spring.datasource.password=YOUR_MYSQL_PASSWORD
```

### 3. Run the Application

```bash
cd bayport-vet-clinic/desktop-shell
npm install
npm run desktop
```

This builds the backend and launches the Electron app.

Desktop mode now starts the backend in embedded H2 mode automatically, so no MySQL setup is required for local desktop usage.

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
