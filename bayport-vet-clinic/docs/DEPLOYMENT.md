# Bayport clinic deployment

Bayport Veterinary Clinic runs on **one Windows PC in the clinic**. That PC runs MySQL and the Bayport server. Front desk, vet, and pharmacy computers open a browser to that PC. They do not install the database.

H2 remains for development and the desktop app on a single computer. Do not use H2 when more than one computer saves records at the same time.

## Why this setup

| | Clinic PC + MySQL | Cloud host |
|---|---|---|
| Internet outage | The clinic can keep working | The clinic stops |
| Records | Stay on the clinic PC | Leave the building |
| Schema | Flyway scripts, which are MySQL SQL | The freecloud profile uses Hibernate and is not the clinic database |
| Cost | One PC the clinic already has | A monthly host and a remote database |

PostgreSQL is not the clinic database. The existing Flyway files use MySQL statements, and rewriting them would risk the schema. The `freecloud` profile is a separate demo path and is not part of this setup.

## What runs where

- MySQL listens on `127.0.0.1` only. Other computers must not connect to port 3306.
- The Bayport server listens on the clinic network so browsers can reach it.
- Staff open `https://CLINIC-PC:8443/login.html`.
- Uploaded photos stay in `C:\Bayport\data\uploads`.
- Secrets stay in `C:\Bayport\config\bayport.env`. That file is not in git.

## Before the first start

1. Install JDK 17 and MySQL 8 on the clinic PC.
2. In the MySQL server configuration, set `bind-address=127.0.0.1`.
3. Create the database with `deploy/clinic/create-database.sql`.
4. Create a MySQL user `bayport_app`@`127.0.0.1` with a password of at least 12 characters. Grant it rights on `bayport_db` only. Do not use `root` for the app.
5. Build the server from `bayport-backend`:

```text
mvnw -DskipTests package
```

6. From an elevated PowerShell window, run `deploy/clinic/install-service.ps1`.
7. Edit `C:\Bayport\config\bayport.env`. Replace every `replace-with` value. Do not commit that file.
8. Run `deploy/clinic/new-local-cert.ps1`. It writes the certificate location into `bayport.env` and does not print the keystore password.
9. Set `SPRING_WEB_CORS_ALLOWED_ORIGINS` to `https://CLINIC-PC:8443` using the clinic PC's real name. Add the PC's IP only if staff will type the IP. Do not use `*` or `file://`.
10. On each staff computer, install `C:\Bayport\config\bayport.crt` into Trusted Root Certification Authorities. Until that is done, the browser warns about the certificate.
11. Allow inbound TCP 8443 on the clinic PC firewall. Do not allow inbound TCP 3306.
12. Start the task **Bayport Veterinary Clinic**, or run `deploy/clinic/start-bayport.ps1`.

The first start creates the tables from `db/baseline/mysql-clinic-schema.sql` and marks Flyway at version 13. Later schema changes are new Flyway files only. Hibernate is set to `validate` and will not change tables.

If the database has no staff accounts, set `BAYPORT_ADMIN_USER` and `BAYPORT_ADMIN_PASSWORD` before that first start. The first sign-in must change that password and enroll Google Authenticator.

## HTTPS and the private-network exception

Production refuses to start on plain HTTP unless `BAYPORT_ALLOW_LAN_HTTP=true`. Use that flag only when every staff computer is on the clinic's private network and the certificate is not installed yet. Turn it off after `new-local-cert.ps1`.

`POST /api/notifications/test-email` is not loaded in the production profile.

## Start, stop, and restart

| Script | What it does |
|---|---|
| `deploy/clinic/install-service.ps1` | Copies the pages and jar into `C:\Bayport`, and registers a startup task |
| `deploy/clinic/bayport-service.ps1` | Runs the server and starts it again 5 seconds after a crash |
| `deploy/clinic/start-bayport.ps1` | Runs the server in the current window |
| `deploy/clinic/stop-bayport.ps1` | Stops the server |
| `deploy/clinic/uninstall-service.ps1` | Removes the startup task |
| `deploy/clinic/new-local-cert.ps1` | Creates the clinic HTTPS certificate |

The scheduled task starts at boot. If the task itself stops, Windows starts it again. If Java exits, `bayport-service.ps1` starts it again.

## Environment variables

Required for production:

| Variable | Rule |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `production` |
| `JAVA_HOME` | JDK 17 |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://127.0.0.1:3306/bayport_db` |
| `SPRING_DATASOURCE_USERNAME` | The MySQL application user |
| `SPRING_DATASOURCE_PASSWORD` | At least 12 characters |
| `JWT_SECRET` | At least 32 bytes |
| `TOTP_ENCRYPTION_KEY` | At least 32 bytes. Keep this key. |
| `SPRING_WEB_CORS_ALLOWED_ORIGINS` | The clinic `https://` address. No `*` and no `file://` |
| `BAYPORT_FRONTEND_DIR` | Folder with the HTML pages and `assets` |
| `BAYPORT_UPLOAD_DIR` | Folder for photos and attachments |
| `BAYPORT_ADMIN_USER` | First administrator, only when no staff accounts exist |
| `BAYPORT_ADMIN_PASSWORD` | At least 10 characters. First login must change it |
| `BAYPORT_SSL_ENABLED` | `true` for normal clinic use |
| `BAYPORT_SERVER_PORT` | `8443` when HTTPS is on |
| `SERVER_SSL_KEY_STORE` | `file:C:/Bayport/config/bayport.p12` |
| `SERVER_SSL_KEY_STORE_PASSWORD` | Written by `new-local-cert.ps1` |
| `SERVER_SSL_KEY_STORE_TYPE` | `PKCS12` |
| `SERVER_SSL_KEY_ALIAS` | `bayport` |

Optional:

| Variable | Purpose |
|---|---|
| `BAYPORT_ADMIN_EMAIL` | Defaults to `<username>@localhost` |
| `BAYPORT_ALLOW_LAN_HTTP` | `true` only for a private network before HTTPS is ready |
| `BAYPORT_LOG_FILE` | Defaults to `logs/bayport.log` under the working folder |
| `BAYPORT_HOME` | Defaults to `C:\Bayport` |
| `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | Email for password reset and reminders |
| `BAYPORT_SMS_ENABLED` and the SMS key variables | Text messages. Leave disabled until a provider is chosen |
| `JWT_EXPIRATION` | Access token lifetime in milliseconds. Default is 8 hours |

Placeholders are in `deploy/clinic/bayport.env.example` and `.env.example`.

## Checklist

- [ ] MySQL is installed and listens on `127.0.0.1` only
- [ ] `bayport_db` exists and `bayport_app` is not `root`
- [ ] `C:\Bayport\config\bayport.env` has real secrets and is not in git
- [ ] HTTPS certificate is created and trusted on each staff PC
- [ ] CORS lists the clinic address only
- [ ] Firewall allows 8443 and blocks 3306 from other computers
- [ ] Startup task is registered and comes back after a reboot
- [ ] First administrator can sign in, change the password, and enroll Google Authenticator
- [ ] A second computer can open `https://CLINIC-PC:8443/login.html` and sign in
- [ ] `https://CLINIC-PC:8443/api/notifications/test-email` does not exist
- [ ] `C:\Bayport\backups` exists and Back up now creates a zip there
- [ ] `bayport_restore_check` exists so the nightly backup can test a restore

## Backups

The server writes backups to `BAYPORT_BACKUP_DIR` (default `C:\Bayport\backups` on Windows), outside the application folder. Set `BAYPORT_BACKUP_EXTERNAL_DIR` to copy each backup to another drive. The default schedule is 02:15. Administrators use the Backups page to run a backup, download it, or restore it.

Restore asks for the phrase `RESTORE` plus the backup id. The server saves a safety backup first. MySQL restore uses the `mysql` client. Create an empty database named in `BAYPORT_BACKUP_VERIFY_DATABASE` and grant `bayport_app` rights on it. That database is only for the restore test. It must not be `bayport_db`.

The desktop H2 file can be backed up while the server is running. Restoring it requires the server to be stopped. The Backups page explains that.

## Updating the server

1. Run `stop-bayport.ps1`.
2. Build the new jar and copy it to `C:\Bayport\app\bayport-backend.jar`.
3. Run `install-service.ps1` again if the HTML pages changed.
4. Start the task.

New schema changes belong in the next Flyway file. Do not turn on `ddl-auto=update` in production. The baseline SQL is only for an empty database. Do not raise its version unless that file is edited to include the new tables.

## Known limit

V9, V11, and V12 use `ADD COLUMN IF NOT EXISTS`, which MySQL does not accept. A **new empty** database does not run those files. It uses the baseline instead. Do not point this profile at an older MySQL database whose Flyway history is still below version 13.
