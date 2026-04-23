# Bayport Veterinary Clinic - Complete System Documentation

## Table of Contents
1. [System Overview](#system-overview)
2. [System Architecture](#system-architecture)
3. [Technology Stack](#technology-stack)
4. [Core Features](#core-features)
5. [User Roles & Permissions](#user-roles--permissions)
6. [System Modules](#system-modules)
7. [Database Schema](#database-schema)
8. [Security Features](#security-features)
9. [Installation & Deployment](#installation--deployment)
10. [System Requirements](#system-requirements)
11. [API Documentation](#api-documentation)
12. [Troubleshooting](#troubleshooting)

---

## System Overview

**Bayport Veterinary Clinic** is a comprehensive desktop application designed for managing all aspects of a veterinary clinic's operations. The system provides a complete solution for pet record management, appointment scheduling, prescription handling, billing, inventory management, and reporting.

### Key Characteristics
- **Desktop Application**: Fully offline-capable, self-contained application
- **Cross-Platform**: Supports Windows and macOS
- **Embedded Database**: Uses H2 embedded database for desktop mode (no external database required)
- **Role-Based Access**: Multiple user roles with different permissions
- **Secure**: JWT authentication, MFA support, audit logging
- **Professional UI**: Modern, responsive interface built with Tailwind CSS

### Target Users
- **Veterinary Clinics**: Small to medium-sized clinics
- **Veterinarians**: For managing patient records and treatments
- **Receptionists**: For scheduling and client management
- **Pharmacists**: For prescription management
- **Administrators**: For system management and reporting

---

## System Architecture

### Architecture Overview
The system follows a **three-tier architecture**:

```
┌─────────────────────────────────────────┐
│         Presentation Layer              │
│  (HTML/CSS/JavaScript Frontend)         │
│  - Tailwind CSS UI                      │
│  - Vanilla JavaScript                   │
│  - Electron Desktop Shell               │
└─────────────────────────────────────────┘
                    ↕
┌─────────────────────────────────────────┐
│         Application Layer                │
│  (Spring Boot REST API)                 │
│  - RESTful Controllers                  │
│  - Business Logic Services              │
│  - Security & Authentication            │
└─────────────────────────────────────────┘
                    ↕
┌─────────────────────────────────────────┐
│         Data Layer                       │
│  (H2 Embedded Database)                 │
│  - JPA/Hibernate ORM                    │
│  - Entity Relationships                 │
│  - Data Persistence                     │
└─────────────────────────────────────────┘
```

### Component Structure

**Frontend (Desktop Shell)**
- Electron-based desktop wrapper
- HTML pages for each module
- JavaScript for client-side logic
- API communication layer

**Backend (Spring Boot)**
- REST API endpoints
- Business logic services
- Data access layer (JPA Repositories)
- Security configuration

**Database**
- H2 Embedded Database (desktop mode)
- MySQL (optional, for development)
- Automatic schema migration

---

## Technology Stack

### Backend Technologies
- **Framework**: Spring Boot 3.3.3
- **Language**: Java 17
- **Build Tool**: Maven
- **Database**: 
  - H2 Database (embedded, desktop mode)
  - MySQL 8+ (optional, development)
- **ORM**: JPA/Hibernate
- **Security**: Spring Security with JWT
- **PDF Generation**: OpenPDF/iText
- **Email**: JavaMail API

### Frontend Technologies
- **UI Framework**: HTML5, CSS3, JavaScript (ES6+)
- **CSS Framework**: Tailwind CSS
- **Desktop Shell**: Electron 31.2.1
- **Charts**: Chart.js
- **Calendar**: FullCalendar.js
- **PDF Client**: jsPDF

### Development Tools
- **Node.js**: 18+ (for Electron and build tools)
- **npm**: Package management
- **Maven**: Java dependency management
- **Git**: Version control

---

## Core Features

### 1. User Management
- **Multi-Role System**: Admin, Vet, Receptionist, Pharmacist
- **User Creation**: Admin can create and manage user accounts
- **Password Management**: Secure password storage with encryption
- **MFA Support**: Multi-factor authentication via email OTP
- **Terms of Service**: TOS acceptance tracking

### 2. Pet Records Management
- **Pet Profiles**: Complete pet information (name, species, breed, age, etc.)
- **Photo Upload**: Pet photo management
- **Medical History**: Track procedures and treatments
- **Owner Information**: Link pets to owners
- **Search & Filter**: Advanced search by name, species, owner
- **View Modes**: Grid view and list view
- **PDF Export**: Print pet profiles and clinic reports

### 3. Appointment Scheduling
- **Calendar View**: Visual calendar interface
- **Time Slot Management**: 30-minute intervals (8:00 AM - 9:30 PM)
- **Vet Assignment**: Assign appointments to specific veterinarians
- **Status Tracking**: Pending → Approved → Done workflow
- **Appointment History**: Track completed appointments
- **Capacity Management**: Up to 5 patients per 30-minute slot

### 4. Prescription Management
- **Prescription Creation**: Issue prescriptions with multiple products
- **Product Selection**: Categorized inventory (Vaccines, Antibiotics, etc.)
- **Quantity Management**: Track product quantities
- **Directions**: Detailed medication instructions
- **Additional Notes**: Per-product notes
- **Dispensing**: Mark prescriptions as dispensed
- **Archiving**: Automatic archiving of dispensed prescriptions
- **PDF Generation**: Professional prescription PDFs with vet signature

### 5. Procedure Management
- **Procedure Records**: Track medical procedures performed
- **Procedure History**: Link procedures to pets
- **Vet Attribution**: Record which vet performed the procedure
- **Notes & Documentation**: Detailed procedure notes
- **Date Tracking**: Procedure date and time

### 6. Inventory Management
- **Product Catalog**: Comprehensive inventory of medications and supplies
- **Categories**: 
  - Vaccines
  - Dewormers
  - Antibiotics
  - Antiparasitics
  - Pain Relief / Anti-Inflammatory
  - Vitamins & Supplements
  - Grooming Products
  - Diagnostic Supplies
  - Wound Care
  - Emergency Medications
  - Surgical Supplies
  - Pet Food & Diet Products
- **Stock Tracking**: Quantity management
- **Pricing**: Product pricing information

### 7. Billing & Sales
- **Billing Records**: Track payments and invoices
- **Procedure Sales**: Sales from completed procedures
- **Prescription Sales**: Sales from dispensed prescriptions
- **Payment Tracking**: Record payment status
- **Sales Reports**: Comprehensive sales analytics

### 8. Reports & Analytics
- **Sales Reports**: Procedure and prescription sales
- **Summary Reports**: Period-based summaries (daily, weekly, monthly, yearly)
- **System Statistics**: Overview of total pets, appointments, prescriptions, users
- **Charts & Graphs**: Visual representation of data
- **PDF Export**: Export reports as PDF

### 9. Reminders System
- **Appointment Reminders**: Automated email reminders
- **Customizable Templates**: Email template management
- **Scheduled Reminders**: Automatic reminder sending
- **Calendar Integration**: Visual reminder calendar

### 10. Activity Logging
- **Audit Logs**: Track all CRUD operations
- **Data Access Logs**: Monitor data reads, exports, prints
- **User Activity**: Track user actions
- **IP Tracking**: Record IP addresses for security
- **Operation History**: Complete operation history

### 11. Recycle Bin
- **Soft Delete**: Deleted records are moved to recycle bin
- **Restore Functionality**: Restore deleted records
- **Permanent Delete**: Option to permanently delete records
- **Deletion Tracking**: Track who deleted and when

### 12. Notifications
- **In-App Notifications**: Real-time notification system
- **Notification Types**: Various notification categories
- **Mark as Read**: Notification management
- **Notification History**: View all notifications

---

## User Roles & Permissions

### Admin
**Full System Access**
- Create and manage all user accounts
- View and manage all pet records
- Schedule and manage all appointments
- Issue and dispense prescriptions
- Access all reports and analytics
- View audit logs and activity logs
- Manage recycle bin
- System configuration
- User role management

### Veterinarian (Vet)
**Clinical Operations**
- View assigned appointments
- View and update pet records
- Issue prescriptions
- Record procedures
- View own appointment calendar
- Access pet medical history
- Update procedure notes

### Receptionist
**Administrative Operations**
- Schedule appointments
- View all appointments
- Create and manage pet records
- View owner information
- Manage appointment calendar
- Basic pet record access

### Pharmacist
**Prescription Management**
- View prescriptions
- Dispense prescriptions
- Access prescription history
- View inventory
- Update prescription status

---

## System Modules

### 1. Dashboard Module
**Location**: `dashboard.html`
- System overview statistics
- Quick actions
- Recent activity
- Notifications
- Clinic information

### 2. Pet Records Module
**Location**: `pet-records.html`
- Pet list (grid/list view)
- Add/Edit/Delete pets
- Pet profile viewing
- Search and filter
- PDF export (individual and bulk)

### 3. Appointments Module
**Location**: `appointments.html`, `appointments-calendar.html`
- Calendar view
- List view
- Create appointments
- Approve appointments
- Mark as done
- Vet assignment

### 4. Prescriptions Module
**Location**: `prescriptions.html`
- Create prescriptions
- Product selection
- Quantity management
- Dispense prescriptions
- Archive management
- PDF generation

### 5. Reports Module
**Location**: `reports.html`
- Sales reports
- Summary reports
- Charts and graphs
- PDF export
- Period selection

### 6. Reminders Module
**Location**: `reminders.html`, `reminders-calendar.html`
- Reminder management
- Email templates
- Scheduled reminders
- Calendar view

### 7. Activity Logs Module
**Location**: `activity-logs.html`
- Audit logs
- Data access logs
- Operation logs
- Filter and search

### 8. Recycle Bin Module
**Location**: `recycle-bin.html`
- Deleted records
- Restore functionality
- Permanent delete

### 9. User Management Module
**Location**: `manage-users.html`
- Create users
- Edit users
- Delete users
- Role assignment
- Password management

---

## Database Schema

### Core Entities

**User**
- id, name, username, password, email
- roles (many-to-many)
- mfaEnabled, tosAccepted
- createdAt, updatedAt

**Pet**
- id, name, species, breed, gender, age
- microchip, owner, address, federation
- photo, ownerId (FK to Owner)
- deleted, deletedAt, deletedBy (soft delete)

**Owner**
- id, fullName, email, phone, address

**Appointment**
- id, petId (FK), owner, date, time
- vet, status, completedAt

**Prescription**
- id, petId (FK), pet, owner
- prescriber, date, dispensed, dispensedAt
- archived, prescriptionDetails (one-to-many)

**PrescriptionDetail**
- id, prescriptionId (FK)
- productName, category, quantity
- directions, additionalNotes, price

**Procedure**
- id, petId (FK), procedureDate
- procedureName, notes, vet

**InventoryItem**
- id, name, category, quantity, price
- description

**BillingRecord**
- id, amount, paymentStatus
- relatedEntity (procedure/prescription)

**Reminder**
- id, petId (FK), reminderDate
- message, sent, emailTemplateId

**AuditLog**
- id, userId, entityType, operation
- entityId, details, ipAddress, timestamp

**DataAccessLog**
- id, userId, accessType, resourceType
- resourceId, ipAddress, timestamp

**Notification**
- id, userId (FK), title, message
- type, read, createdAt

---

## Security Features

### Authentication
- **JWT Tokens**: Secure token-based authentication
- **Password Encryption**: BCrypt password hashing
- **Session Management**: Token expiration and refresh

### Authorization
- **Role-Based Access Control (RBAC)**: Multiple roles with different permissions
- **Endpoint Protection**: Spring Security configuration
- **Method-Level Security**: Service-level authorization

### Multi-Factor Authentication (MFA)
- **OTP Generation**: 6-digit codes
- **Email Delivery**: OTP sent via email
- **Expiration**: Time-limited codes
- **Verification**: Secure code validation

### Audit & Logging
- **Audit Logs**: All CRUD operations logged
- **Data Access Logs**: Read, export, print operations tracked
- **IP Tracking**: IP address recording
- **User Activity**: Complete user action history

### Data Protection
- **Soft Delete**: Records can be restored
- **Recycle Bin**: Deleted data recovery
- **Data Validation**: Input validation and sanitization
- **SQL Injection Prevention**: Parameterized queries via JPA

---

## Installation & Deployment

### Desktop Application Installation

#### Prerequisites
- **Windows 10/11** or **macOS 10.15+**
- **Java 17+** (bundled in packaged version)
- **4GB RAM minimum**
- **500MB free disk space**

#### Installation Steps

1. **Download Installer**
   - Windows: `PawCare Clinic Setup X.X.X.exe`
   - macOS: `PawCare-Clinic-X.X.X-x64.dmg` or `-arm64.dmg`

2. **Run Installer**
   - Windows: Double-click `.exe` → Follow wizard
   - macOS: Double-click `.dmg` → Drag to Applications

3. **Launch Application**
   - Wait 10-30 seconds for backend initialization
   - Login with default credentials:
     - Username: `admin`
     - Password: `admin123`
     - Role: Admin

4. **Change Default Password**
   - Go to "Manage Users"
   - Edit admin account
   - Set new secure password

### Development Setup

1. **Clone Repository**
   ```bash
   git clone <repository-url>
   cd paw-care-vet-clinic
   ```

2. **Backend Setup**
   ```bash
   cd pawcare-backend
   mvn clean install
   ```

3. **Frontend Setup**
   ```bash
   cd desktop-shell
   npm install
   ```

4. **Run Application**
   ```bash
   npm run desktop
   ```

### Building Installers

**Windows:**
```bash
cd desktop-shell
npm run build:win
```

**macOS:**
```bash
cd desktop-shell
npm run build:mac
```

---

## System Requirements

### Minimum Requirements
- **OS**: Windows 10/11, macOS 10.15+, or Linux (Ubuntu 18.04+)
- **RAM**: 4GB
- **Storage**: 500MB
- **Java**: 17+ (bundled in packaged version)
- **Display**: 1024x768 minimum resolution

### Recommended Requirements
- **OS**: Latest version of Windows/macOS
- **RAM**: 8GB
- **Storage**: 1GB
- **Display**: 1920x1080 or higher

---

## API Documentation

### Base URL
- **Development**: `http://localhost:8080/api`
- **Desktop**: `http://localhost:8080/api`

### Authentication
All API requests (except login) require JWT token in header:
```
Authorization: Bearer <token>
```

### Main Endpoints

**Authentication**
- `POST /api/auth/login` - User login
- `POST /api/auth/verify-otp` - MFA verification
- `POST /api/auth/logout` - User logout

**Pets**
- `GET /api/pets` - List all pets
- `GET /api/pets/{id}` - Get pet by ID
- `POST /api/pets` - Create pet
- `PUT /api/pets/{id}` - Update pet
- `DELETE /api/pets/{id}` - Delete pet
- `GET /api/pets/{id}/pdf` - Generate pet profile PDF
- `GET /api/pets/pdf/all` - Generate all pets PDF

**Appointments**
- `GET /api/appointments` - List appointments
- `POST /api/appointments` - Create appointment
- `PUT /api/appointments/{id}` - Update appointment
- `POST /api/appointments/{id}/approve` - Approve appointment
- `POST /api/appointments/{id}/done` - Mark as done

**Prescriptions**
- `GET /api/prescriptions` - List prescriptions
- `POST /api/prescriptions` - Create prescription
- `PUT /api/prescriptions/{id}` - Update prescription
- `POST /api/prescriptions/{id}/dispense` - Dispense prescription
- `PATCH /api/prescriptions/{id}/archive` - Archive prescription
- `GET /api/prescriptions/{id}/pdf` - Generate prescription PDF

**Reports**
- `GET /api/reports/summary` - Get summary report
- `GET /api/reports/summary/pdf` - Generate summary PDF

**Users**
- `GET /api/users` - List users
- `POST /api/users` - Create user
- `PUT /api/users/{id}` - Update user
- `DELETE /api/users/{id}` - Delete user

---

## Troubleshooting

### Common Issues

**White Screen on Launch**
- Ensure Java 17+ is installed
- Check that backend JAR exists
- Verify port 8080 is available
- Check application logs

**Backend Won't Start**
- Verify Java installation: `java -version`
- Check port 8080 availability
- Review error logs
- Ensure data directory is writable

**Login Fails**
- Verify credentials (case-sensitive)
- Check role selection
- Ensure backend is running
- Check network connectivity (localhost)

**Database Errors**
- Check data directory permissions
- Verify H2 database file exists
- Review database logs
- Restore from backup if needed

**PDF Generation Fails**
- Check file permissions
- Verify image paths
- Review PDF service logs
- Ensure sufficient disk space

---

## Version Information

**Current Version**: 0.1.0

**Release Date**: [Current Date]

**Last Updated**: [Current Date]

---

## Support & Contact

For technical support or questions:
1. Review this documentation
2. Check troubleshooting section
3. Review application logs
4. Contact system administrator

---

## License & Copyright

**Bayport Veterinary Clinic**
© 2025 Bayport Veterinary Clinic
All rights reserved.

---

*This documentation is maintained and updated regularly. For the latest version, please refer to the repository.*

