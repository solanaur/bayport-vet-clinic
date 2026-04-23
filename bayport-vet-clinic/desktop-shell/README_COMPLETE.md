# Bayport Desktop Application - Complete Guide

## 🚀 Quick Start (3 Steps)

### Step 1: Install Dependencies
```bash
cd desktop-shell
npm install
```

### Step 2: Build Backend
```bash
npm run build-backend
```

### Step 3: Run App
```bash
npm run desktop
```

**That's it!** The app will open automatically.

---

## 📦 What This Desktop App Includes

✅ **Complete Veterinary Clinic Management System**
- Pet Records Management
- Appointment Scheduling
- Prescription Management
- User Management (Admin, Vet, Receptionist, Pharmacist)
- Activity Logs
- Reports & Analytics
- Reminders System
- Recycle Bin
- Notifications

✅ **Fully Self-Contained**
- Embedded database (H2)
- Backend server included
- No external dependencies needed after installation
- Works offline

✅ **Professional Features**
- Clean, modern UI
- Role-based access control
- Email notifications
- File uploads (pet photos)
- Data persistence
- Auto-start backend

---

## 🛠️ System Requirements

- **OS**: Windows 10+, macOS 10.13+, or Linux (Ubuntu 18.04+)
- **RAM**: 4GB minimum, 8GB recommended
- **Disk**: 500MB free space
- **Java**: 17 or higher (included in packaged version)
- **Node.js**: 18+ (only needed for building, not for end users)

---

## 📋 Detailed Setup Instructions

### For Developers (Building from Source)

1. **Prerequisites**
   ```bash
   # Check Node.js
   node --version  # Should be 18+
   
   # Check Java
   java -version   # Should be 17+
   ```

2. **Install Dependencies**
   ```bash
   cd desktop-shell
   npm install
   ```

3. **Build Backend**
   ```bash
   npm run build-backend
   ```
   This creates: `../pawcare-backend/target/pawcare-backend-0.0.1-SNAPSHOT.jar`

4. **Run in Development**
   ```bash
   npm run desktop
   ```

5. **Create Installer**
   ```bash
   # Windows
   npm run package
   
   # macOS
   npm run dist:mac
   ```

### For End Users (Installing Pre-Built App)

1. **Download Installer**
   - Windows: `PawCare Clinic Setup 0.1.0.exe`
   - macOS: `PawCare-0.1.0-x64.dmg`

2. **Run Installer**
   - Follow installation wizard
   - Choose installation directory
   - Desktop shortcut is created automatically

3. **Launch App**
   - Double-click desktop shortcut
   - Wait 10-30 seconds for backend to start
   - Login screen appears

4. **Default Login Credentials**
   - **Admin**: `admin` / `admin123`
   - **Vet**: `vet` / `vet123`
   - **Receptionist**: `recept` / `recept123`
   - **Pharmacist**: `pharm` / `pharm123`

---

## 🔧 Troubleshooting

### App Won't Start

**Problem**: "Backend JAR not found"
- **Solution**: Run `npm run build-backend` first

**Problem**: "Java runtime not found"
- **Solution**: Install Java 17+ from https://adoptium.net/
- Or run `npm run build-runtime` to create embedded Java

**Problem**: "Port 8080 already in use"
- **Solution**: Close other applications using port 8080
- Or change port in `application-desktop.properties`

### App Opens But Shows Blank Page

**Problem**: Backend not started
- **Solution**: Check if backend is running at http://localhost:8080/api/health
- Check logs in `pawcare-backend/logs/pawcare.log`

**Problem**: CORS errors
- **Solution**: Already configured in `application-desktop.properties`
- Verify CORS settings allow `file://` origin

### Database Issues

**Problem**: Data not persisting
- **Solution**: Check `data/` directory exists in app installation folder
- Verify write permissions on `data/` directory

**Problem**: Database corrupted
- **Solution**: Delete `data/pawcare-db.mv.db` and restart app
- Database will be recreated automatically

---

## 📁 Project Structure

```
paw-care-vet-clinic/
├── desktop-shell/          # Electron desktop app
│   ├── main.js            # Main Electron process
│   ├── preload.js         # Preload script
│   ├── package.json       # Node.js dependencies
│   ├── QUICK_START.bat    # Windows quick start
│   ├── QUICK_START.sh     # Linux/macOS quick start
│   └── VERIFY_SETUP.js    # Setup verification script
│
├── pawcare-backend/        # Spring Boot backend
│   ├── src/
│   │   └── main/
│   │       ├── java/      # Java source code
│   │       └── resources/
│   │           ├── application.properties
│   │           └── application-desktop.properties  # Desktop config
│   └── target/
│       └── pawcare-backend-0.0.1-SNAPSHOT.jar
│
├── *.html                  # Frontend pages
├── assets/                 # Images, logos, etc.
└── data/                   # Database & uploads (created at runtime)
    ├── pawcare-db.mv.db
    └── uploads/
```

---

## 🎯 Key Features

### Automatic Backend Management
- Backend starts automatically when app launches
- Backend stops when app closes
- Health checks ensure backend is ready before showing UI

### Data Persistence
- H2 embedded database (no MySQL needed)
- Data stored in `data/` folder
- Survives app restarts
- Easy backup (just copy `data/` folder)

### Security
- JWT authentication
- Role-based access control
- Secure password hashing
- CORS protection

### User Experience
- Clean, modern interface
- Responsive design
- Fast loading
- No internet required (after initial setup)

---

## 📝 Available Commands

```bash
# Development
npm run desktop          # Build backend + start app
npm run dev              # Start Electron only
npm run build-backend    # Build backend JAR only

# Packaging
npm run package          # Create Windows installer
npm run dist:mac         # Create macOS DMG/ZIP
npm run dist:all         # Create all platform packages

# Utilities
npm run check            # Check Electron version
npm run build-runtime    # Build embedded Java runtime
node VERIFY_SETUP.js     # Verify setup is correct
```

---

## 🔐 Security Notes

1. **Default Passwords**: Change default passwords after first login
2. **JWT Secret**: Change `jwt.secret` in `application-desktop.properties` for production
3. **Email Credentials**: Email password is in config file (consider environment variables for production)
4. **Database**: H2 database file is not encrypted (consider encryption for sensitive data)

---

## 📊 Performance

- **Startup Time**: 10-30 seconds (backend initialization)
- **Memory Usage**: ~200-400MB (backend + Electron)
- **Database Size**: Grows with usage (typically <100MB for small clinics)
- **File Uploads**: Stored in `data/uploads/` (manage disk space)

---

## 🆘 Getting Help

1. **Check Logs**
   - Backend: `pawcare-backend/logs/pawcare.log`
   - Electron: Check console (View → Toggle Developer Tools)

2. **Verify Setup**
   ```bash
   node VERIFY_SETUP.js
   ```

3. **Common Issues**
   - See Troubleshooting section above
   - Check DESKTOP_APP_SETUP.md for detailed guide

4. **Reset Everything**
   - Delete `data/` folder
   - Restart app (database will be recreated)

---

## ✅ Verification Checklist

Before distributing, ensure:

- [ ] App starts without errors
- [ ] Backend starts automatically
- [ ] Login works with default credentials
- [ ] All features work (pets, appointments, etc.)
- [ ] Data persists after restart
- [ ] File uploads work
- [ ] Installer creates shortcuts
- [ ] App uninstalls cleanly
- [ ] No console errors

---

## 🎉 Success!

Your Bayport Veterinary Clinic is now a fully functional desktop application!

**Next Steps:**
1. Test all features
2. Customize branding (icons, name)
3. Create installer
4. Distribute to users

For detailed instructions, see: `DESKTOP_APP_SETUP.md`

