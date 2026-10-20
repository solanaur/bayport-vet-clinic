# Administrator guide

## Accounts

Create each staff account from Manage Users. The first account comes from `BAYPORT_ADMIN_USER` only when the database is empty. That person must change the password and enroll Google Authenticator.

Reset Google Authenticator from Manage Users when a phone is lost. The staff member enrolls again and receives new recovery codes. This is written to the activity log.

## Backups

Open Backups. Use Back up now after a large import. Download a copy to a drive that is not the clinic PC. Restore only after typing the confirmation phrase. The server takes a safety backup first.

Set `BAYPORT_BACKUP_VERIFY_DATABASE` to an empty database that is not `bayport_db`.

## Updates

Stop the server, replace `C:\Bayport\app\bayport-backend.jar`, and start it again. Schema changes run from Flyway. Do not turn on automatic schema updates.

## Health

An administrator can open `GET /api/admin/health` while signed in. It reports the database, free disk space, mail setup, the last backup, and the application version. The public health address does not include those details.
