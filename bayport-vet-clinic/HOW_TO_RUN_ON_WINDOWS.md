## Running Bayport Veterinary Clinic on this PC (Windows)

1. **Install prerequisites (one-time only)**
   - **Node.js**: Install the latest LTS from `https://nodejs.org/`
   - **Java 17 or newer**: Install from `https://adoptium.net/` (Temurin JDK)

2. **Start the desktop app in development mode**
   - Open **PowerShell** or **Command Prompt**
   - Run:
     - `cd bayport-vet-clinic\desktop-shell`
     - `QUICK_START.bat`
   - This script will:
     - Install Node.js dependencies
     - Build the Spring Boot backend
     - Start the desktop application

3. **Build a Windows installer (optional, for QA or deployment)**
   - From the same `desktop-shell` folder, run:
     - `BUILD_WINDOWS.bat`
   - When it finishes successfully, you’ll get an installer at:
     - `dist\Bayport Veterinary Clinic Setup 0.1.0.exe`
   - Double‑click the installer to install and run the app like a normal Windows program.

4. **Online vs Offline behavior**
   - The **core clinic system** (records, appointments, prescriptions, etc.) runs **locally** and works **with or without internet**, as long as the backend service is running.
   - **Features that send emails or OTP codes** (e.g., user creation, credential edits, reminder emails) **require internet**.
   - If there is **no internet connection** when you try to send an email or code, the app will show a popup saying: **"No Internet Connection."** The rest of the system will continue to work.

