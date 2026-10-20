# Incidents

## Lost password or phone

The staff member tells an administrator. The administrator resets the password or Google Authenticator from Manage Users. Old sign-in tokens stop working. The staff member enrolls again and stores the new recovery codes.

## Records look wrong

1. Stop further edits to that record.
2. Compare it with the paper slip or the last backup.
3. If the whole database is wrong, restore a backup from the Backups page. That replaces current records with the backup, after a safety copy.
4. Write what was wrong and what was restored in the activity log notes you keep with the clinic.

## Backup failed or is older than 24 hours

The Health page and an email to administrators report this. Run Back up now. If it fails, check that MySQL is running and that `C:\Bayport\backups` has free space. Do not ignore this past the next morning.

## Suspected breach

1. Disconnect the clinic PC from the internet if it is connected.
2. Sign out staff and change the administrator password.
3. Rotate `JWT_SECRET`, `TOTP_ENCRYPTION_KEY`, the database password, and the mail password.
4. Keep the current database and logs. Do not delete them while the clinic reviews what happened.
5. Tell the clinic's own adviser. This runbook is an operational checklist, not a legal notice.
