# Build Guide - Creating Installable Applications

This guide will help you create installable applications for **Windows** (.exe installer) and **macOS** (.dmg file) without needing to use the terminal after building.

## Prerequisites

### For Windows Builds:
- Windows 10/11
- Node.js 18+ and npm
- Java 17+ and Maven (for backend)
- Git (optional, for version control)

### For macOS Builds:
- macOS 10.15+ (Catalina or later)
- Node.js 18+ and npm
- Java 17+ and Maven (for backend)
- Xcode Command Line Tools (install via: `xcode-select --install`)

## Quick Start

### Building Windows Installer (.exe)

1. **Open PowerShell or Command Prompt** in the `desktop-shell` directory:
   ```powershell
   cd "C:\Users\Administrator\Desktop\VETCLIN-main\paw-care-vet-clinic\desktop-shell"
   ```

2. **Set execution policy** (PowerShell only, one-time):
   ```powershell
   Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope Process -Force
   ```

3. **Build the installer**:
   ```powershell
   npm run build:win
   ```

4. **Find your installer**:
   - Location: `desktop-shell/dist/`
   - File: `PawCare Clinic Setup 0.1.0.exe`
   - This is a **full installer** - users can double-click to install!

### Building macOS DMG (.dmg)

1. **Open Terminal** in the `desktop-shell` directory:
   ```bash
   cd ~/Desktop/VETCLIN-main/paw-care-vet-clinic/desktop-shell
   ```

2. **Build the DMG**:
   ```bash
   npm run build:mac
   ```

3. **Find your DMG files**:
   - Location: `desktop-shell/dist/`
   - Files: 
     - `PawCare-Clinic-0.1.0-x64.dmg` (Intel Macs)
     - `PawCare-Clinic-0.1.0-arm64.dmg` (Apple Silicon Macs)
   - Users can double-click the DMG, drag the app to Applications, and install!

## Detailed Instructions

### Step 1: Install Dependencies

If you haven't already, install all npm packages:
```bash
npm install
```

### Step 2: Add Application Icons (Optional but Recommended)

Icons make your app look professional. Place these files in the `build/` directory:

- **Windows**: `build/icon.ico` (256x256 or larger, multi-size ICO file)
- **macOS**: `build/icon.icns` (512x512 or larger, ICNS file)

**How to create icons:**
- Use online tools like [CloudConvert](https://cloudconvert.com/) or [IconConverter](https://iconconverter.com/)
- Start with a 512x512 PNG image
- Convert to ICO for Windows and ICNS for macOS

**Note:** If icons are missing, electron-builder will use default icons, but your app will look less professional.

### Step 3: Build for Your Platform

#### Windows (.exe Installer)

```powershell
# In PowerShell (from desktop-shell directory)
npm run build:win
```

**What this creates:**
- `dist/PawCare Clinic Setup 0.1.0.exe` - Full installer
- Users can:
  1. Double-click the .exe file
  2. Follow the installation wizard
  3. Choose installation directory
  4. Get desktop and Start Menu shortcuts automatically

#### macOS (.dmg File)

```bash
# In Terminal (from desktop-shell directory)
npm run build:mac
```

**What this creates:**
- `dist/PawCare-Clinic-0.1.0-x64.dmg` - For Intel Macs
- `dist/PawCare-Clinic-0.1.0-arm64.dmg` - For Apple Silicon Macs
- `dist/PawCare-Clinic-0.1.0-x64.zip` - Alternative ZIP format
- `dist/PawCare-Clinic-0.1.0-arm64.zip` - Alternative ZIP format

**Users can:**
1. Double-click the .dmg file
2. Drag "PawCare Clinic" to the Applications folder
3. Eject the DMG
4. Launch from Applications

### Step 4: Build for All Platforms (Advanced)

If you want to build for Windows, macOS, and Linux at once:

```bash
npm run build:all
```

**Note:** This requires running on macOS (for macOS builds) or using CI/CD services.

## Distribution

### Sharing Windows Installer

1. Upload `PawCare Clinic Setup 0.1.0.exe` to:
   - File sharing service (Google Drive, Dropbox, OneDrive)
   - Your website
   - USB drive

2. Users download and double-click to install - **no terminal needed!**

### Sharing macOS DMG

1. Upload the appropriate .dmg file to:
   - File sharing service
   - Your website
   - USB drive

2. Users download, double-click, and drag to Applications - **no terminal needed!**

## Troubleshooting

### Windows: "npm cannot be loaded" Error

**Solution:** Use Command Prompt (cmd.exe) instead of PowerShell, or run:
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope Process -Force
```

### macOS: "Command not found: electron-builder"

**Solution:** Install dependencies:
```bash
npm install
```

### Build Fails: "Backend JAR not found"

**Solution:** The backend must be built first. Run:
```bash
npm run build-backend
```

Then try building again.

### Icons Not Showing

**Solution:** 
- Ensure icons are in the `build/` directory
- Use proper formats: `.ico` for Windows, `.icns` for macOS
- Minimum size: 256x256 pixels

### macOS: "App is damaged" Error (Gatekeeper)

**Solution:** Users need to allow the app in System Preferences:
1. System Preferences → Security & Privacy
2. Click "Open Anyway" next to the blocked app message

Or sign the app with an Apple Developer certificate (requires paid Apple Developer account).

## File Structure After Build

```
desktop-shell/
├── dist/                          # Build output directory
│   ├── PawCare Clinic Setup 0.1.0.exe  # Windows installer
│   ├── PawCare-Clinic-0.1.0-x64.dmg    # macOS DMG (Intel)
│   └── PawCare-Clinic-0.1.0-arm64.dmg   # macOS DMG (Apple Silicon)
├── build/                         # Build resources (icons, etc.)
│   ├── icon.ico                   # Windows icon (optional)
│   └── icon.icns                  # macOS icon (optional)
└── package.json                   # Build configuration
```

## Customization

### Change App Name

Edit `package.json`:
```json
"productName": "Your App Name"
```

### Change Version

Edit `package.json`:
```json
"version": "1.0.0"
```

### Change Installer Options

Edit the `build.nsis` section in `package.json` for Windows installer customization.

## Next Steps

- **Code Signing**: For production, consider code signing your apps (requires certificates)
- **Auto-Updates**: Add update functionality using electron-updater
- **Notarization**: For macOS, notarize your app with Apple (requires Apple Developer account)

## Support

If you encounter issues:
1. Check the error message in the terminal
2. Ensure all prerequisites are installed
3. Try cleaning and rebuilding:
   ```bash
   npm run build-backend
   npm run build:win  # or build:mac
   ```

---

**Remember:** Once built, the installers (.exe for Windows, .dmg for macOS) can be distributed and installed by users without any technical knowledge - just double-click and follow the prompts!

