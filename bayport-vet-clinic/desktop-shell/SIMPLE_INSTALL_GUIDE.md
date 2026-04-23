# Simple Installation Guide - Download, Install, Use! 🚀

## For End Users (No Technical Knowledge Required)

### Step 1: Download
1. Get the installer file:
   - **Windows**: `Bayport Veterinary Clinic Setup 0.1.0.exe`
   - **macOS**: `Bayport-Veterinary-Clinic-0.1.0-x64.dmg` (Intel) or `Bayport-Veterinary-Clinic-0.1.0-arm64.dmg` (Apple Silicon)

### Step 2: Install Bayport Veterinary Clinic

**✅ Good News:** Java is bundled with the installer! You don't need to install Java separately.

#### Windows:
1. **Double-click** `Bayport Veterinary Clinic Setup 0.1.0.exe`
2. If Windows shows a security warning:
   - Click "More info"
   - Click "Run anyway"
3. Follow the installation wizard:
   - Choose installation folder (default is fine)
   - Check "Create desktop shortcut" (recommended)
   - Click "Install"
4. Wait for installation to complete
5. Click "Finish"

#### macOS:
1. **Double-click** the `.dmg` file
2. Drag "Bayport Veterinary Clinic" to the **Applications** folder
3. Eject the DMG
4. Open **Applications** folder
5. **Right-click** "Bayport Veterinary Clinic" → **Open** (first time only)
6. Click "Open" if macOS asks about security

### Step 3: Use!

1. **Launch the app:**
   - **Windows**: Double-click the desktop shortcut or search "Bayport Veterinary Clinic" in Start Menu
   - **macOS**: Open from Applications folder

2. **Wait for startup** (10-30 seconds on first launch):
   - You'll see a loading screen
   - The app is starting the backend server
   - **Don't close the window!**

3. **Login:**
   - **Username**: `admin`
   - **Password**: `admin123`
   - **Role**: Click "Admin" button
   - Click "Log In"

4. **Change password immediately:**
   - Go to "Manage Users"
   - Edit the admin account
   - Set a new secure password

5. **Start using the app!** 🎉

## Troubleshooting

### White Screen / App Won't Load

**Problem**: You see a white screen or the app doesn't start.

**Solution**:
1. ✅ **Wait 30-60 seconds**: First launch takes longer as the backend initializes
2. ✅ **Check error message**: The app will show a specific error if something is wrong
3. ✅ **Restart the app**: Close and reopen the application
4. ✅ **Check port 8080**: Ensure no other application is using port 8080
5. ✅ **Reinstall**: If issues persist, uninstall and reinstall the application

### "Backend Startup Error" Message

**Problem**: App shows an error about backend not starting.

**Solutions**:
1. **Port 8080 in use**: Close any other applications using port 8080
2. **Restart the app**: Close and reopen the application
3. **Check firewall**: Allow the app through Windows Firewall / macOS Security
4. **Reinstall**: If issues persist, uninstall and reinstall the application

### App Crashes Immediately

**Problem**: App opens then closes right away.

**Solution**:
1. Check Windows Event Viewer / macOS Console for error details
2. Try running as Administrator (Windows) or with elevated permissions (macOS)
3. Reinstall the application
4. Contact support if issue persists

### Can't Login

**Problem**: Login fails even with correct credentials.

**Solution**:
1. Make sure you selected the correct **Role** (Admin, Vet, etc.)
2. Username and password are **case-sensitive**
3. Default credentials:
   - Username: `admin`
   - Password: `admin123`
   - Role: **Admin**

## System Requirements

- **Windows 10/11** or **macOS 10.15+**
- **4GB RAM minimum**
- **500MB free disk space**
- **Java 17+** (bundled with installer - no separate installation needed!)

## What Gets Installed?

- **Application files**: ~200MB
- **Database**: Stored in `[Installation Folder]/data/`
- **User data**: All your pet records, appointments, etc. are stored locally

## Uninstalling

### Windows:
1. Settings → Apps → Apps & features
2. Find "PawCare Clinic"
3. Click "Uninstall"

### macOS:
1. Open Applications folder
2. Drag "PawCare Clinic" to Trash
3. Empty Trash

**Note**: Your data is stored in the installation folder. If you want to completely remove everything, delete the `data/` folder after uninstalling.

## Need Help?

1. Check this guide first
2. Make sure Java 17+ is installed
3. Try restarting your computer
4. Contact your IT support

---

**Note**: Java 17+ is bundled with the installer, so you don't need to install it separately. Just download, install, and use!

