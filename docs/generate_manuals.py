#!/usr/bin/env python3
"""Generate Bayport VCMS User Manual and Technical Documentation PDFs."""

from __future__ import annotations

from datetime import date
from pathlib import Path

from fpdf import FPDF

OUT_DIR = Path(__file__).resolve().parent
LOGO = Path(__file__).resolve().parents[1] / "bayport-vet-clinic" / "assets" / "logo.png"

COURSE = "CSS152L - Systems Analysis and Design"
GROUP = "Bayport Veterinary Clinic"
PROJECT = "Bayport Veterinary Clinic Management System (VCMS)"
VERSION = "1.0"
DOC_DATE = date.today().strftime("%B %d, %Y")


def ascii_safe(text: str) -> str:
    """Helvetica in fpdf2 is Latin-1 only; normalize common Unicode punctuation."""
    if not text:
        return text
    replacements = {
        "\u2014": "-",
        "\u2013": "-",
        "\u2018": "'",
        "\u2019": "'",
        "\u201c": '"',
        "\u201d": '"',
        "\u2026": "...",
        "\u00f1": "n",  # n with tilde
        "\u00e9": "e",
    }
    for old, new in replacements.items():
        text = text.replace(old, new)
    return text.encode("latin-1", errors="replace").decode("latin-1")


class BayportPDF(FPDF):
    def __init__(self, doc_title: str):
        super().__init__()
        self.doc_title = doc_title
        self.set_margins(15, 20, 15)
        self.set_auto_page_break(auto=True, margin=20)

    def _content_width(self) -> float:
        return self.w - self.l_margin - self.r_margin

    def _reset_x(self):
        self.set_x(self.l_margin)

    def header(self):
        if self.page_no() == 1:
            return
        self.set_font("Helvetica", "I", 8)
        self.set_text_color(100, 100, 100)
        self.cell(0, 8, self.doc_title, align="L")
        self.cell(0, 8, f"Page {self.page_no()}", align="R", new_x="LMARGIN", new_y="NEXT")
        self.line(10, 16, 200, 16)
        self.ln(4)

    def footer(self):
        self.set_y(-15)
        self.set_font("Helvetica", "I", 8)
        self.set_text_color(120, 120, 120)
        self.cell(0, 10, f"Bayport VCMS | {DOC_DATE} | Confidential", align="C")

    def cover_page(self, manual_type: str, subtitle: str):
        self.add_page()
        if LOGO.exists():
            try:
                self.image(str(LOGO), x=75, y=28, w=60)
            except Exception:
                pass
        self.ln(70 if LOGO.exists() else 40)
        self.set_font("Helvetica", "B", 11)
        self.set_text_color(0, 87, 184)
        self.cell(0, 8, ascii_safe(COURSE), align="C", new_x="LMARGIN", new_y="NEXT")
        self.ln(4)
        self.set_font("Helvetica", "B", 20)
        self.set_text_color(30, 30, 30)
        self.multi_cell(0, 10, ascii_safe(manual_type), align="C")
        self.ln(2)
        self.set_font("Helvetica", "", 14)
        self.set_text_color(60, 60, 60)
        self.multi_cell(0, 8, ascii_safe(PROJECT), align="C")
        self.ln(6)
        self.set_font("Helvetica", "", 12)
        self.cell(0, 8, ascii_safe(subtitle), align="C", new_x="LMARGIN", new_y="NEXT")
        self.ln(20)
        self.set_font("Helvetica", "", 11)
        self.cell(0, 7, GROUP, align="C", new_x="LMARGIN", new_y="NEXT")
        self.cell(0, 7, ascii_safe("322 Quirino Avenue, Brgy. Don Galo, Paranaque City"), align="C", new_x="LMARGIN", new_y="NEXT")
        self.cell(0, 7, "0968 633 2940", align="C", new_x="LMARGIN", new_y="NEXT")
        self.ln(16)
        self.set_font("Helvetica", "I", 10)
        self.cell(0, 7, f"Document Version {VERSION}", align="C", new_x="LMARGIN", new_y="NEXT")
        self.cell(0, 7, DOC_DATE, align="C")

    def doc_control_table(self):
        self.add_page()
        self.section_title("Document Control")
        rows = [
            ("Document Title", self.doc_title),
            ("Version", VERSION),
            ("Date", DOC_DATE),
            ("Prepared For", "Bayport Veterinary Clinic staff and stakeholders"),
            ("System URL (Cloud)", "https://bayport-vet-clinic-com.netlify.app"),
            ("API URL", "https://bayport-api.onrender.com/api"),
        ]
        self.set_font("Helvetica", "", 10)
        col_w = (50, self._content_width() - 50)
        self._reset_x()
        for label, value in rows:
            self.set_fill_color(240, 247, 255)
            self.cell(col_w[0], 8, label, border=1, fill=True)
            self.cell(col_w[1], 8, ascii_safe(value), border=1, new_x="LMARGIN", new_y="NEXT")
        self.ln(6)
        self.body_text(
            "Revision history: Version 1.0 - Initial release covering desktop and cloud deployment, "
            "role-based workflows, and operational procedures."
        )

    def section_title(self, title: str, level: int = 1):
        self.ln(4 if level == 1 else 2)
        sizes = {1: 14, 2: 12, 3: 11}
        self.set_font("Helvetica", "B", sizes.get(level, 11))
        if level == 1:
            self.set_text_color(0, 87, 184)
        else:
            self.set_text_color(40, 40, 40)
        self._reset_x()
        self.multi_cell(self._content_width(), 7, ascii_safe(title))
        self.set_text_color(30, 30, 30)
        self.ln(2)

    def body_text(self, text: str):
        self._reset_x()
        self.set_font("Helvetica", "", 10)
        self.multi_cell(self._content_width(), 5.5, ascii_safe(text))
        self.ln(2)

    def bullet_list(self, items: list[str]):
        self.set_font("Helvetica", "", 10)
        for item in items:
            self._reset_x()
            self.multi_cell(self._content_width(), 5.5, ascii_safe(f"  - {item}"))
        self.ln(2)

    def numbered_steps(self, steps: list[str]):
        self.set_font("Helvetica", "", 10)
        for i, step in enumerate(steps, 1):
            self._reset_x()
            self.multi_cell(self._content_width(), 5.5, ascii_safe(f"  {i}. {step}"))
        self.ln(2)

    def table(self, headers: list[str], rows: list[list[str]], widths: list[int] | None = None):
        if not widths:
            w = int(self._content_width() // len(headers))
            widths = [w] * len(headers)
        self._reset_x()
        self.set_font("Helvetica", "B", 9)
        self.set_fill_color(0, 87, 184)
        self.set_text_color(255, 255, 255)
        for i, h in enumerate(headers):
            self.cell(widths[i], 8, h, border=1, fill=True, align="C")
        self.ln()
        self.set_font("Helvetica", "", 9)
        self.set_text_color(30, 30, 30)
        fill = False
        for row in rows:
            if fill:
                self.set_fill_color(245, 248, 252)
            else:
                self.set_fill_color(255, 255, 255)
            line_h = 7
            for i, cell in enumerate(row):
                self.cell(widths[i], line_h, ascii_safe(cell[:80]), border=1, fill=True)
            self.ln()
            fill = not fill
        self._reset_x()
        self.ln(3)


def build_user_manual() -> Path:
    pdf = BayportPDF("Bayport VCMS - User Manual")
    pdf.cover_page(
        "USER MANUAL",
        "Step-by-step guide for clinic staff using Bayport VCMS",
    )
    pdf.doc_control_table()

    pdf.add_page()
    pdf.section_title("Table of Contents")
    toc = [
        "1. Introduction",
        "2. System Requirements",
        "3. Getting Started - Sign In",
        "4. User Roles and Access",
        "5. Dashboard",
        "6. Pet Profiles",
        "7. Appointments",
        "8. Consultations and Prescriptions",
        "9. Billing and Point of Sale",
        "10. Inventory",
        "11. Reminders",
        "12. Reports (Administrator)",
        "13. Settings and User Management (Administrator)",
        "14. Troubleshooting",
        "15. Contact and Support",
    ]
    pdf.set_font("Helvetica", "", 10)
    for line in toc:
        pdf.cell(0, 7, line, new_x="LMARGIN", new_y="NEXT")

    pdf.add_page()
    pdf.section_title("1. Introduction")
    pdf.body_text(
        "Bayport Veterinary Clinic Management System (VCMS) is a web-based application that helps "
        "Bayport Veterinary Clinic manage daily operations: pet and owner records, appointments, "
        "clinical consultations, prescriptions, billing, inventory, and email reminders."
    )
    pdf.body_text(
        "The system can run as a desktop application (offline-capable at the clinic) or as a cloud "
        "deployment accessible through a web browser. Staff sign in with a role-specific account so "
        "each user sees only the menus and actions relevant to their job."
    )

    pdf.section_title("2. System Requirements")
    pdf.section_title("2.1 Cloud (Browser)", 2)
    pdf.bullet_list([
        "Modern web browser: Google Chrome, Microsoft Edge, or Firefox (latest version)",
        "Stable internet connection",
        "Screen resolution 1280 x 720 or higher recommended",
    ])
    pdf.section_title("2.2 Desktop Application", 2)
    pdf.bullet_list([
        "Windows 10/11 or macOS 11+",
        "4 GB RAM minimum (8 GB recommended)",
        "500 MB free disk space",
        "Optional: USB receipt printer for POS (configured in Settings)",
    ])

    pdf.section_title("3. Getting Started - Sign In")
    pdf.numbered_steps([
        "Open the Bayport login page (desktop shortcut or https://bayport-vet-clinic-com.netlify.app).",
        "Select your role: Admin, Vet, or Front Office.",
        "Enter your username and password provided by the clinic administrator.",
        "Check the Terms and Conditions box, then click Log In.",
        "If prompted for a one-time passcode (OTP), enter the 4-digit code sent to your email.",
        "On first cloud visit, wait for the blue banner 'Waking up server' to disappear (up to one minute).",
    ])
    pdf.body_text(
        "Default training accounts (change passwords after go-live): admin/admin123, vet/vet123, "
        "frontdesk/frontdesk123."
    )

    pdf.section_title("4. User Roles and Access")
    pdf.table(
        ["Role", "Primary responsibilities", "Key modules"],
        [
            ["Administrator", "Full system control", "All modules + Users, Reports, Settings, Logs"],
            ["Veterinarian", "Clinical care", "Pets, Appointments, Consultations and Rx"],
            ["Front Office", "Reception and checkout", "Pets, Appointments, Billing, POS, Inventory, Reminders"],
        ],
        [35, 55, 100],
    )
    pdf.body_text(
        "The left sidebar shows only modules your role can access. If you need additional access, "
        "contact an administrator."
    )

    pdf.section_title("5. Dashboard")
    pdf.body_text(
        "After login, the Dashboard displays clinic information, quick-action buttons, and summary "
        "statistics (pets, appointments, prescriptions, users). Quick actions vary by role - for example, "
        "veterinarians see shortcuts to start consultations; front office staff see POS and billing links."
    )

    pdf.section_title("6. Pet Profiles")
    pdf.numbered_steps([
        "Open Pet profiles from the sidebar.",
        "Click Add pet (or Edit on an existing card) to open the form.",
        "Enter pet details: name, species, breed, age, gender, and optional photo (webcam or upload).",
        "Enter owner information: name, phone, email, and address in the same form.",
        "Complete the health profile: allergies, current medications, last vaccination date.",
        "Optionally add medical history records.",
        "Click Save. The pet appears in the list; use search and filters to find records quickly.",
    ])
    pdf.body_text("Click a pet card to open the full Pet profile page with vaccination schedule and history.")

    pdf.section_title("7. Appointments")
    pdf.numbered_steps([
        "Open Appointments from the sidebar.",
        "Click Book appointment and select pet, date, time slot, visit type, and assigned veterinarian.",
        "New requests start as Pending until approved.",
        "Administrators and front office can approve appointments; veterinarians see their own schedule.",
        "After the visit, mark the appointment Done.",
        "Use the calendar view for a monthly overview of scheduled visits.",
    ])

    pdf.section_title("8. Consultations and Prescriptions")
    pdf.body_text("Available to Administrators and Veterinarians only.")
    pdf.numbered_steps([
        "Open Consultations and Rx from the sidebar.",
        "Start consultation: select a pet (often from an approved appointment).",
        "Step 1 - Diagnosis: enter clinical findings and notes.",
        "Step 2 - Procedures: add billable procedures from the fee catalog.",
        "Step 3 - Prescriptions: complete the Rx pad (medications, dosage, instructions).",
        "Save the visit. Finished consultations appear under the Finished tab.",
        "Print or email prescription PDFs to the pet owner when needed.",
    ])

    pdf.section_title("9. Billing and Point of Sale")
    pdf.section_title("9.1 Billing", 2)
    pdf.numbered_steps([
        "Open Billing from the sidebar.",
        "Review open invoices and balances.",
        "Click Receive payment to record partial or full payment.",
        "Create a new invoice when billing outside the POS flow.",
    ])
    pdf.section_title("9.2 Point of Sale (POS)", 2)
    pdf.numbered_steps([
        "Open Point of sale from the sidebar.",
        "Add items from the procedure fee catalog and/or inventory products.",
        "Apply quantities; stock is deducted for inventory items.",
        "Complete checkout and print a receipt (desktop) or queue a print job (cloud).",
    ])

    pdf.section_title("10. Inventory")
    pdf.numbered_steps([
        "Open Inventory from the sidebar.",
        "Browse categories: vaccines, medications, supplies, grooming, and services.",
        "Update stock quantities and prices as needed.",
        "Low-stock items should be reordered before they affect POS checkout.",
    ])

    pdf.section_title("11. Reminders")
    pdf.numbered_steps([
        "Open Reminders from the sidebar.",
        "Create a PET reminder (vaccination, follow-up) or a GENERAL clinic announcement.",
        "Select template or write a custom message; placeholders include owner and pet names.",
        "Schedule the send date or dispatch due reminders (administrator).",
        "Use the Reminders calendar for a visual schedule.",
    ])
    pdf.body_text("Email delivery requires configured mail settings (Gmail on desktop, Resend on cloud).")

    pdf.section_title("12. Reports (Administrator)")
    pdf.body_text(
        "Administrators open Reports to view revenue summaries, charts by period, and operational metrics. "
        "Export summary PDFs for management review."
    )

    pdf.section_title("13. Settings and User Management (Administrator)")
    pdf.bullet_list([
        "Settings: clinic name, address, logo, receipt printer, Rx template, device lock, email test.",
        "Users and roles: create accounts, assign roles, reset passwords.",
        "Activity logs: review audit trail of system actions.",
        "Recycle bin: restore or permanently delete soft-deleted records.",
    ])

    pdf.section_title("14. Troubleshooting")
    pdf.table(
        ["Problem", "Suggested action"],
        [
            ["Cannot connect to backend", "Check internet; on cloud wait for server wake-up; contact admin"],
            ["Wrong role after login", "Select the role matching your account on the login screen"],
            ["OTP not received", "Check spam; ask admin (code may appear in admin notifications)"],
            ["Receipt will not print", "Desktop: verify printer in Settings; install print helper"],
            ["Changes not visible", "Refresh the page; confirm you saved the form successfully"],
        ],
        [55, 135],
    )

    pdf.section_title("15. Contact and Support")
    pdf.bullet_list([
        "Clinic phone: 0968 633 2940",
        "Address: 322 Quirino Avenue, Brgy. Don Galo, Paranaque City",
        "In-app help: Help and support module (all roles)",
    ])
    pdf.body_text(
        "Tip: Complete a full test appointment and consultation in a training environment before go-live."
    )

    out = OUT_DIR / "Bayport_VCMS_User_Manual.pdf"
    pdf.output(str(out))
    return out


def build_technical_manual() -> Path:
    pdf = BayportPDF("Bayport VCMS - Technical Documentation")
    pdf.cover_page(
        "TECHNICAL DOCUMENTATION",
        "Architecture, implementation, deployment, and maintenance guide",
    )
    pdf.doc_control_table()

    pdf.add_page()
    pdf.section_title("Table of Contents")
    toc = [
        "1. Introduction",
        "2. System Overview",
        "3. Architecture",
        "4. Technology Stack",
        "5. Database Design",
        "6. Backend API",
        "7. Frontend Application",
        "8. Security",
        "9. Email and Notifications",
        "10. Deployment - Cloud",
        "11. Deployment - Desktop",
        "12. Environment Variables",
        "13. Maintenance and Monitoring",
        "14. Known Limitations",
        "15. References",
    ]
    pdf.set_font("Helvetica", "", 10)
    for line in toc:
        pdf.cell(0, 7, line, new_x="LMARGIN", new_y="NEXT")

    pdf.add_page()
    pdf.section_title("1. Introduction")
    pdf.body_text(
        "This document describes the technical design and implementation of the Bayport Veterinary Clinic "
        "Management System (VCMS). It is intended for developers, system administrators, and IT staff "
        "responsible for deployment, integration, and maintenance."
    )
    pdf.body_text(
        "The system follows a three-tier architecture: static web client, REST API server, and relational "
        "database. The same codebase supports local desktop packaging (Electron) and cloud hosting."
    )

    pdf.section_title("2. System Overview")
    pdf.body_text("Core functional modules:")
    pdf.bullet_list([
        "Authentication and role-based access control (JWT, optional MFA)",
        "Pet and owner records with medical history",
        "Appointment scheduling and workflow (Pending, Approved, Done)",
        "Clinical consultations: diagnosis, procedures, prescriptions, PDF generation",
        "Billing, invoicing, and point-of-sale with inventory integration",
        "Email reminders (templates, scheduled dispatch)",
        "Administrative reports, audit logs, recycle bin, user management",
    ])

    pdf.section_title("3. Architecture")
    pdf.body_text(
        "Client (HTML/CSS/JavaScript) communicates with Spring Boot REST API over HTTPS. "
        "The API persists data via JPA/Hibernate to PostgreSQL (cloud) or H2/MySQL (desktop). "
        "File uploads (pet photos) use local disk storage or configurable cloud storage provider."
    )
    pdf.body_text("Logical flow:")
    pdf.numbered_steps([
        "User browser loads static assets from Netlify (or Electron shell locally).",
        "JavaScript resolves API base URL (deploy-env.js or localStorage).",
        "Authenticated requests include JWT Bearer token.",
        "Spring Boot controllers delegate to service layer and repositories.",
        "Flyway migrations apply schema changes on startup.",
    ])

    pdf.section_title("4. Technology Stack")
    pdf.table(
        ["Layer", "Technology", "Version / Notes"],
        [
            ["Frontend", "HTML5, Tailwind CSS, vanilla JS", "Static pages, Chart.js for reports"],
            ["Desktop shell", "Electron + Node.js", "Bundles JRE and backend JAR"],
            ["Backend", "Java, Spring Boot 3", "REST, JPA, Flyway, JWT"],
            ["Database (cloud)", "PostgreSQL via Supabase", "Session pooler, IPv4"],
            ["Database (desktop)", "H2 embedded file DB", "Default; optional MySQL"],
            ["Hosting (UI)", "Netlify", "netlify.toml build pipeline"],
            ["Hosting (API)", "Render", "Docker, freecloud profile"],
            ["Email (cloud)", "Resend HTTP API", "SMTP blocked on Render free tier"],
            ["Email (desktop)", "Gmail SMTP", "App password in mail.env"],
        ],
        [40, 55, 95],
    )

    pdf.section_title("5. Database Design")
    pdf.body_text("Primary entities (PostgreSQL tables managed by JPA/Flyway):")
    pdf.table(
        ["Table", "Purpose"],
        [
            ["users, roles", "Authentication and RBAC"],
            ["owners, pets", "Client and patient records"],
            ["pet_medical_records", "Historical clinical notes"],
            ["appointments", "Scheduling and status workflow"],
            ["procedures, prescriptions", "Clinical visit data"],
            ["billing_records, sales, sale_lines", "Financial transactions"],
            ["inventory_items", "Stock and catalog"],
            ["notifications", "In-app and reminder queue"],
            ["audit_log, operation_logs", "Compliance and auditing"],
            ["mfa_code", "One-time passcodes"],
        ],
        [55, 135],
    )
    pdf.body_text(
        "Supabase project reference: gmyoopqvqvpybloqadix. Use Session pooler host "
        "(aws-1-ap-northeast-2.pooler.supabase.com) on Render - not the direct IPv6-only host."
    )

    pdf.section_title("6. Backend API")
    pdf.body_text("Base path: /api. Representative endpoints:")
    pdf.table(
        ["Endpoint", "Method", "Description"],
        [
            ["/api/health", "GET", "Service status, mail configuration"],
            ["/api/auth/login", "POST", "Authenticate; may return MFA_REQUIRED"],
            ["/api/auth/mfa/verify", "POST", "Verify OTP"],
            ["/api/pets", "GET/POST/PUT", "Pet CRUD"],
            ["/api/appointments", "GET/POST/PUT", "Appointment management"],
            ["/api/prescriptions", "GET/POST", "Prescription records"],
            ["/api/sales", "POST", "POS checkout"],
            ["/api/notifications", "GET/POST", "Reminders and alerts"],
            ["/api/admin/users", "GET/POST", "User administration"],
        ],
        [55, 25, 110],
    )
    pdf.body_text(
        "Authorization: @PreAuthorize and service-layer checks enforce ADMIN, VET, and FRONT_OFFICE roles. "
        "CORS is configured via SPRING_WEB_CORS_ALLOWED_ORIGINS for cloud deployments."
    )

    pdf.section_title("7. Frontend Application")
    pdf.body_text("Key client files:")
    pdf.bullet_list([
        "assets/api.js — API base resolution, HTTP client, cloud timeout (90s)",
        "assets/app.js — Role config, sidebar, guard(), login helpers",
        "assets/deploy-env.js — Generated at Netlify build from BAYPORT_API_BASE",
        "index.html — Login, MFA, role selection",
        "Per-module HTML pages with shared header (app-header.js)",
    ])
    pdf.body_text(
        "Role keys: admin, vet, front_office (UI label: Front Office). Legacy receptionist/pharmacist "
        "map to front_office."
    )

    pdf.section_title("8. Security")
    pdf.bullet_list([
        "JWT tokens stored in localStorage; sent as Authorization Bearer header",
        "BCrypt password hashing",
        "Optional MFA (4-digit OTP via email) for non-default accounts",
        "Device lock: trusted device list in Settings (admin approval)",
        "Terms of Service acceptance on login",
        "Audit and data-access logging for sensitive operations",
        "HTTPS enforced in cloud (Netlify, Render)",
    ])

    pdf.section_title("9. Email and Notifications")
    pdf.body_text(
        "Reminder and MFA emails use EmailService with HTML templates (message-first layout, hosted logo URL). "
        "Cloud: Resend API (RESEND_API_KEY, RESEND_FROM). Desktop: Gmail SMTP. "
        "If email is not configured, MFA codes appear in admin notifications bell."
    )

    pdf.section_title("10. Deployment - Cloud")
    pdf.numbered_steps([
        "Supabase: create project, note Session pooler JDBC URL and credentials.",
        "Render: deploy bayport-api from GitHub (render.yaml), set freecloud profile and env vars.",
        "Netlify: connect repo, set BAYPORT_API_BASE to Render /api URL, trigger deploy.",
        "Render CORS: SPRING_WEB_CORS_ALLOWED_ORIGINS must match Netlify URL exactly.",
        "Verify: GET /api/health returns status UP; login from Netlify site succeeds.",
    ])
    pdf.body_text("Production URLs:")
    pdf.bullet_list([
        "Frontend: https://bayport-vet-clinic-com.netlify.app",
        "API: https://bayport-api.onrender.com/api",
        "Database dashboard: supabase.com/dashboard/project/gmyoopqvqvpybloqadix",
    ])

    pdf.section_title("11. Deployment - Desktop")
    pdf.numbered_steps([
        "cd bayport-vet-clinic/desktop-shell && npm install && npm run desktop",
        "Backend starts on localhost:8080 with H2 database in ./data",
        "Optional: configure MySQL in application.properties for Workbench integration",
        "Build installers: npm run verify:win or verify:mac",
    ])

    pdf.section_title("12. Environment Variables")
    pdf.table(
        ["Variable", "Service", "Purpose"],
        [
            ["BAYPORT_API_BASE", "Netlify", "Render API URL for frontend build"],
            ["SPRING_DATASOURCE_URL", "Render", "Supabase JDBC connection string"],
            ["SPRING_DATASOURCE_USERNAME", "Render", "postgres.gmyoopqvqvpybloqadix"],
            ["SPRING_DATASOURCE_PASSWORD", "Render", "Supabase DB password"],
            ["SPRING_WEB_CORS_ALLOWED_ORIGINS", "Render", "Netlify site origin"],
            ["JWT_SECRET", "Render", "Token signing key"],
            ["RESEND_API_KEY", "Render", "Transactional email (cloud)"],
            ["RESEND_FROM", "Render", "Sender address for Resend"],
            ["BAYPORT_EMAIL_LOGO_URL", "Render", "Hosted logo for email HTML"],
        ],
        [58, 28, 104],
    )

    pdf.section_title("13. Maintenance and Monitoring")
    pdf.bullet_list([
        "Health endpoint: /api/health — monitor mailConfigured and status UP",
        "Render logs: database connection errors, email failures",
        "Supabase Table Editor: verify data after app changes (manual refresh)",
        "Flyway migrations: db/migration/V*.sql applied on startup",
        "Rotate default passwords and JWT_SECRET before production",
        "Render free tier: cold start 30-60s after idle - document for users",
    ])

    pdf.section_title("14. Known Limitations")
    pdf.bullet_list([
        "Render free tier blocks outbound SMTP; Resend required for email",
        "Render free tier sleeps after inactivity; first request may be slow",
        "Cloud file uploads may be ephemeral without attached volume or S3",
        "Inbox sender avatar requires Gmail profile photo or BIMI + custom domain",
        "Supabase Realtime disabled by default; not used by application",
    ])

    pdf.section_title("15. References")
    pdf.bullet_list([
        "Repository: https://github.com/1una229/bayport-vet-clinic_1",
        "DEPLOY_ONLINE.md - Netlify + Render + Supabase guide",
        "RUN_INSTRUCTIONS.md - Desktop setup",
        "docs/EMAIL_SENDER_AVATAR.md - Email branding and BIMI",
        "Resend documentation: https://resend.com/docs",
        "Supabase documentation: https://supabase.com/docs",
    ])

    out = OUT_DIR / "Bayport_VCMS_Technical_Documentation.pdf"
    pdf.output(str(out))
    return out


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    user = build_user_manual()
    tech = build_technical_manual()
    print(f"Created: {user}")
    print(f"Created: {tech}")


if __name__ == "__main__":
    main()
