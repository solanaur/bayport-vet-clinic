# Bayport Veterinary Clinic - MySQL Setup Guide

This guide helps you set up the Bayport Veterinary Clinic application with **MySQL Workbench** as the database.

## Prerequisites

1. **MySQL Server** 8.0 or higher
2. **MySQL Workbench**
3. **Java 17** JDK
4. **Maven** and **Node.js**

## Step 1: Create Database in MySQL Workbench

1. Open **MySQL Workbench** and connect to your MySQL server
2. Open the setup script: **File → Open SQL Script** → select `database_setup.sql`
3. Or run this SQL directly:

```sql
CREATE DATABASE IF NOT EXISTS bayport_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE bayport_db;
```

4. Click the **Execute** (lightning bolt) button
5. Tables will be created automatically by Hibernate when you first run the app

### Optional: Create a dedicated user

```sql
CREATE USER 'bayport_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON bayport_db.* TO 'bayport_user'@'localhost';
FLUSH PRIVILEGES;
```

## Step 2: Configure Application

Edit `bayport-backend/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/bayport_db
spring.datasource.username=root
spring.datasource.password=YOUR_MYSQL_PASSWORD
```

Replace `YOUR_MYSQL_PASSWORD` with your MySQL root password (or use the dedicated user if created).

## Step 3: Run the Application

```bash
cd bayport-vet-clinic/desktop-shell
npm install
npm run desktop
```

Or run backend only:

```bash
cd bayport-vet-clinic/bayport-backend
mvn spring-boot:run
```

## Step 4: Verify

1. Backend starts on port 8080
2. Check MySQL Workbench: **bayport_db** should have new tables after first run
3. Login with: `admin` / `admin123`

## Project Structure

```
bayport-vet-clinic/
├── bayport-backend/
│   ├── src/main/java/com/bayport/
│   └── src/main/resources/
│       ├── application.properties
│       └── application-desktop.properties
├── database_setup.sql
└── ...
```

## Troubleshooting

- **Connection refused**: Ensure MySQL service is running
- **Access denied**: Check username/password in `application.properties`
- **Database doesn't exist**: Run `database_setup.sql` in MySQL Workbench first
