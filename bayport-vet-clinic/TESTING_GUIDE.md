# Detailed Installation and Testing Guide

This guide provides step-by-step instructions to install all prerequisites and test the Bayport Veterinary Clinic desktop application.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Installing Prerequisites](#installing-prerequisites)
3. [Project Setup](#project-setup)
4. [Testing in Development Mode](#testing-in-development-mode)
5. [Testing Core Flows](#testing-core-flows)
6. [Building the Installer](#building-the-installer)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

You need the following software installed:

1. **Java 17 or higher** (JDK - Java Development Kit)
2. **Maven 3.6+** (Build tool for Java)
3. **Node.js 18+** and npm (for Electron)
4. **Git** (optional, if cloning from repository)

---

## Installing Prerequisites

### Step 1: Install Java 17 (JDK)

#### Windows:

1. **Download Java 17**:
   - Go to: https://adoptium.net/temurin/releases/
   - Select:
     - **Version**: 17 (LTS)
     - **Operating System**: Windows
     - **Architecture**: x64
     - **Package Type**: JDK
   - Click "Latest release" → Download the `.msi` installer

2. **Install Java**:
   - Run the downloaded `.msi` file
   - Follow the installation wizard
   - **Important**: Check "Add to PATH" if offered
   - Complete the installation

3. **Verify Installation**:
   - Open **Command Prompt** (Win + R, type `cmd`, press Enter)
   - Type:
     ```cmd
     java -version
     ```
   - You should see something like:
     ```
     openjdk version "17.0.x" ...
     ```
   - Also check:
     ```cmd
     javac -version
     ```
   - Should show: `javac 17.0.x`

4. **Set JAVA_HOME** (if not auto-set):
   - Open "Environment Variables" (Win + X → System → Advanced system settings → Environment Variables)
   - Under "System variables", click "New"
   - Variable name: `JAVA_HOME`
   - Variable value: `C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot` (or your Java install path)
   - Click OK
   - Restart Command Prompt

#### macOS:

1. **Using Homebrew** (recommended):
   ```bash
   brew install openjdk@17
   sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
   ```

2. **Or download from**:
   - https://adoptium.net/temurin/releases/
   - Select macOS → Download `.pkg` installer
   - Run the installer

3. **Verify**:
   ```bash
   java -version
   javac -version
   ```

#### Linux (Ubuntu/Debian):

```bash
sudo apt update
sudo apt install openjdk-17-jdk
java -version
javac -version
```

---

### Step 2: Install Maven

#### Windows:

1. **Download Maven**:
   - Go to: https://maven.apache.org/download.cgi
   - Download: `apache-maven-3.9.x-bin.zip` (latest 3.9.x version)

2. **Extract Maven**:
   - Extract to: `C:\Program Files\Apache\maven` (or your preferred location)
   - You should have: `C:\Program Files\Apache\maven\apache-maven-3.9.x\`

3. **Add to PATH**:
   - Open "Environment Variables"
   - Edit "Path" under System variables
   - Add: `C:\Program Files\Apache\maven\apache-maven-3.9.x\bin`
   - Click OK

4. **Verify**:
   - Open **new** Command Prompt
   - Type:
     ```cmd
     mvn -version
     ```
   - Should show Maven version and Java version

#### macOS:

```bash
brew install maven
mvn -version
```

#### Linux (Ubuntu/Debian):

```bash
sudo apt install maven
mvn -version
```

---

### Step 3: Install Node.js and npm

#### Windows:

1. **Download Node.js**:
   - Go to: https://nodejs.org/
   - Download: **LTS version** (18.x or higher)
   - Choose: `node-v18.x.x-x64.msi` (Windows Installer)

2. **Install**:
   - Run the `.msi` installer
   - Follow the wizard (default options are fine)
   - **Important**: Check "Add to PATH" if offered

3. **Verify**:
   - Open **new** Command Prompt
   - Type:
     ```cmd
     node -v
     npm -v
     ```
   - Should show versions like: `v18.x.x` and `9.x.x`

#### macOS:

```bash
brew install node
node -v
npm -v
```

#### Linux (Ubuntu/Debian):

```bash
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install -y nodejs
node -v
npm -v
```

---

## Project Setup

### Step 1: Navigate to Project Directory

Open Command Prompt (Windows) or Terminal (macOS/Linux) and navigate to the project:

```bash
cd "C:\Users\aggon\OneDrive\Documents\VETCLINN\negra-main\VETCLIN-main\paw-care-vet-clinic"
```

Or use the full path to your project location.

### Step 2: Verify Project Structure

You should see these directories:
- `pawcare-backend/` - Spring Boot backend
- `desktop-shell/` - Electron wrapper
- `assets/` - Frontend assets
- `*.html` - Frontend pages

### Step 3: Install Node.js Dependencies

```bash
cd desktop-shell
npm install
```

This will install:
- `electron`
- `electron-builder`
- `electron-log`

Wait for installation to complete (may take 2-5 minutes).

---

## Testing in Development Mode

### Step 1: Build the Backend JAR

First, build the Spring Boot application:

```bash
cd ..\pawcare-backend
mvn clean package -DskipTests
```

**What this does**:
- Cleans previous builds
- Compiles Java code
- Packages into JAR: `target/pawcare-backend-0.0.1-SNAPSHOT.jar`
- Skips tests (faster build)

**Expected output**:
```
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

**Time**: 2-5 minutes (first time), 30 seconds (subsequent builds)

**If you see errors**:
- Check Java version: `java -version` (must be 17+)
- Check Maven: `mvn -version`
- See [Troubleshooting](#troubleshooting) section

### Step 2: Run Desktop App in Development Mode

From the project root, run:

```bash
cd desktop-shell
npm run desktop
```

**What this does**:
1. Builds the backend JAR (if needed)
2. Starts Electron
3. Electron starts Spring Boot backend with H2 database
4. Opens the login window

**Expected behavior**:
- Command window shows: "Starting Spring Boot backend..."
- Backend logs appear (Spring Boot startup)
- After 10-30 seconds, Electron window opens
- Login page appears

**First launch**: May take 30+ seconds (database initialization)

**If window doesn't open**:
- Check for errors in the command window
- See [Troubleshooting](#troubleshooting)

---

## Testing Core Flows

### Test 1: Admin Login

1. **In the login window**:
   - Click the **"Admin"** role button (left side)
   - Username: `admin`
   - Password: `admin123`
   - Click **"Log In"**

2. **Expected result**:
   - Login succeeds
   - Redirects to Dashboard
   - Shows welcome message for Admin

3. **If login fails**:
   - Check backend is running (look for "Started PawcareApplication" in logs)
   - Verify credentials (case-sensitive)
   - Check browser console (F12) for errors

### Test 2: Create a New User

1. **Navigate to Manage Users**:
   - In the sidebar, click **"Manage Users"**
   - Or go to: `manage-users.html`

2. **Add a New User**:
   - Click **"Add User"** button
   - Fill in the form:
     - **Name**: `Test Receptionist`
     - **Username**: `testreception`
     - **Temporary Password**: `test1234` (minimum 6 characters)
     - **Role**: Select `receptionist` from dropdown
   - Click **"Save"**

3. **Expected result**:
   - Modal closes
   - New user appears in the users table
   - User shows: Name, Username, Role

4. **Verify in backend** (optional):
   - Check `data/pawcare-db.mv.db` exists (database file created)
   - User should be persisted

### Test 3: Login with New User

1. **Log out**:
   - Click **"Logout"** button (top right)
   - Should return to login page

2. **Login with new user**:
   - Click **"Receptionist"** role button
   - Username: `testreception`
   - Password: `test1234`
   - Click **"Log In"**

3. **Expected result**:
   - Login succeeds
   - Redirects to Dashboard
   - Shows welcome for Receptionist role
   - Sidebar shows only Receptionist-accessible pages

4. **Verify role restrictions**:
   - Receptionist should NOT see "Manage Users" in sidebar
   - Receptionist should see: Dashboard, Pet Records, Appointments

### Test 4: Create Pet Record

1. **Navigate to Pet Records**:
   - Click **"Pet Records"** in sidebar

2. **Add a Pet**:
   - Click **"Add Pet"** or similar button
   - Fill in pet information:
     - Name: `Fluffy`
     - Species: `Canine`
     - Breed: `Golden Retriever`
     - Gender: `Male`
     - Age: `3`
   - Save

3. **Expected result**:
   - Pet is saved
   - Appears in pet list
   - Can view/edit pet details

### Test 5: Create Appointment

1. **Navigate to Appointments**:
   - Click **"Appointments"** in sidebar

2. **Create Appointment**:
   - Click **"Create Appointment"** or similar
   - Fill in:
     - Pet: Select from dropdown (or enter pet name)
     - Date: Select future date
     - Time: `10:00`
     - Vet: `Dr. Andrea Cruz` (or any vet name)
   - Save

3. **Expected result**:
   - Appointment created
   - Shows in appointments list
   - Status: "Pending"

### Test 6: Database Persistence

1. **Close the application**:
   - Close Electron window
   - Backend process should stop automatically

2. **Restart the application**:
   ```bash
   npm run desktop
   ```

3. **Login again**:
   - Login with `testreception` / `test1234`

4. **Expected result**:
   - Login succeeds
   - Previously created pets/appointments still exist
   - Data persisted in `data/pawcare-db.mv.db`

---

## Building the Installer

### Step 1: Ensure Backend is Built

```bash
cd pawcare-backend
mvn clean package -DskipTests
```

Verify JAR exists:
```bash
dir target\pawcare-backend-0.0.1-SNAPSHOT.jar
```

### Step 2: Build Windows Installer

```bash
cd ..\desktop-shell
npm run dist
```

**What this does**:
- Runs `prepackage` script (builds backend JAR)
- Packages Electron app
- Creates NSIS installer
- Outputs to: `desktop-shell/dist/`

**Expected output**:
```
Packaging app for platform win32 x64 using electron-builder
...
Building installer...
```

**Time**: 5-10 minutes (first time), 2-3 minutes (subsequent)

**Output file**:
- `desktop-shell/dist/PawCare Clinic Setup 0.1.0.exe`

### Step 3: Test the Installer

1. **Run the installer**:
   - Double-click `PawCare Clinic Setup 0.1.0.exe`
   - Follow installation wizard
   - Install to default location (or choose custom)

2. **Launch installed app**:
   - Desktop shortcut or Start Menu
   - App should start (may take 10-30 seconds)

3. **Verify**:
   - Login with `admin` / `admin123`
   - Create a test user
   - Verify data persists

---

## Troubleshooting

### Issue: "Java not found" or "java: command not found"

**Solution**:
1. Verify Java is installed: `java -version`
2. If not found, reinstall Java and add to PATH
3. Restart Command Prompt after adding to PATH

### Issue: "Maven not found" or "mvn: command not found"

**Solution**:
1. Verify Maven: `mvn -version`
2. If not found, add Maven `bin` directory to PATH
3. Restart Command Prompt

### Issue: "npm: command not found"

**Solution**:
1. Verify Node.js: `node -v` and `npm -v`
2. Reinstall Node.js if needed
3. Restart Command Prompt

### Issue: Backend won't start - "Port 8080 already in use"

**Solution**:
1. Find process using port 8080:
   ```cmd
   netstat -ano | findstr :8080
   ```
2. Kill the process (replace PID with actual process ID):
   ```cmd
   taskkill /PID <PID> /F
   ```
3. Or change port in `application-desktop.properties`:
   ```
   server.port=8081
   ```
   (Then update `main.js` BACKEND_PORT constant)

### Issue: "Backend JAR not found"

**Solution**:
1. Build the JAR first:
   ```bash
   cd pawcare-backend
   mvn clean package -DskipTests
   ```
2. Verify JAR exists:
   ```bash
   dir target\pawcare-backend-0.0.1-SNAPSHOT.jar
   ```

### Issue: "Backend failed to start within 60 seconds"

**Possible causes**:
1. Java not installed or not in PATH
2. Port 8080 blocked by firewall
3. JAR file corrupted

**Solution**:
1. Check Java: `java -version`
2. Try running JAR manually:
   ```bash
   cd pawcare-backend
   java -jar target/pawcare-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=desktop
   ```
3. Check for errors in console output

### Issue: Login fails with "Invalid username or password"

**Solution**:
1. Verify backend is running (check console logs)
2. Check credentials are correct (case-sensitive)
3. Try default admin: `admin` / `admin123`
4. If database is corrupted, delete `data/pawcare-db.mv.db` and restart

### Issue: Database file locked

**Solution**:
1. Close all instances of the app
2. Check for Java processes:
   ```cmd
   tasklist | findstr java
   ```
3. Kill any remaining Java processes
4. Delete `data/pawcare-db.mv.db.lock` if it exists

### Issue: Electron window is blank or shows errors

**Solution**:
1. Check browser console (F12 in Electron window)
2. Verify backend is running (check command window)
3. Check `assets/api.js` is loading correctly
4. Try refreshing (Ctrl+R)

### Issue: Maven build fails

**Common errors**:

1. **"JAVA_HOME not set"**:
   - Set JAVA_HOME environment variable
   - Point to JDK installation directory

2. **"Could not resolve dependencies"**:
   - Check internet connection
   - Maven needs to download dependencies
   - Try: `mvn clean install -U` (force update)

3. **"Compilation failure"**:
   - Check Java version: `java -version` (must be 17+)
   - Check `pom.xml` for correct Java version

---

## Quick Reference Commands

```bash
# Check versions
java -version
mvn -version
node -v
npm -v

# Build backend
cd pawcare-backend
mvn clean package -DskipTests

# Run desktop app (dev mode)
cd desktop-shell
npm run desktop

# Build installer
cd desktop-shell
npm run dist

# Clean build (if issues)
cd pawcare-backend
mvn clean
cd ../desktop-shell
npm run build-backend
```

---

## Next Steps After Testing

1. **Verify all core flows work**
2. **Test on a clean Windows machine** (with Java installed)
3. **Create user documentation** for your clinic staff
4. **Set up regular backups** of the `data/` folder
5. **Consider bundling Java** with the installer (future enhancement)

---

**Need Help?**
- Check `README.md` for developer documentation
- Check `INSTALLATION.md` for end-user guide
- Review error messages in console/logs
- Verify all prerequisites are installed correctly

