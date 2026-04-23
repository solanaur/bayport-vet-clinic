# Quick Build Guide 🚀

## For Windows Users

### Option 1: Double-Click (Easiest!)
1. Double-click `BUILD_WINDOWS.bat`
2. Wait for the build to complete
3. Find your installer at: `dist\PawCare Clinic Setup 0.1.0.exe`
4. **Done!** Share the .exe file - users can install by double-clicking!

### Option 2: Command Line
```powershell
cd "C:\Users\Administrator\Desktop\VETCLIN-main\paw-care-vet-clinic\desktop-shell"
npm run build:win
```

## For macOS Users

### Option 1: Terminal (Easiest!)
1. Open Terminal
2. Navigate to the desktop-shell folder
3. Run:
   ```bash
   chmod +x BUILD_MACOS.sh
   ./BUILD_MACOS.sh
   ```
4. Find your DMG files at: `dist/PawCare-Clinic-0.1.0-*.dmg`
5. **Done!** Share the .dmg file - users can install by dragging to Applications!

### Option 2: Direct Command
```bash
cd ~/Desktop/VETCLIN-main/paw-care-vet-clinic/desktop-shell
npm run build:mac
```

## What You Get

### Windows:
- ✅ **PawCare Clinic Setup 0.1.0.exe** - Full installer
- Users double-click → Install → Done!
- Creates desktop shortcut automatically

### macOS:
- ✅ **PawCare-Clinic-0.1.0-x64.dmg** - For Intel Macs
- ✅ **PawCare-Clinic-0.1.0-arm64.dmg** - For Apple Silicon Macs
- Users double-click DMG → Drag to Applications → Done!

## First Time Setup

Before building, make sure you have:
1. ✅ Node.js installed (`node --version` should work)
2. ✅ All dependencies installed: `npm install`
3. ✅ Java 17+ installed (for backend)

## Need Help?

See `BUILD_GUIDE.md` for detailed instructions and troubleshooting.

---

**Remember:** Once built, the installers work like any other software - users don't need technical knowledge, just double-click and install! 🎉

