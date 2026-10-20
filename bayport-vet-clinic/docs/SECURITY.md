# Security controls

This note describes how Bayport is built. It is not legal advice. The clinic should review it with its own adviser for the Data Privacy Act of 2012 (Republic Act No. 10173).

## Controls in the software

- Staff sign in with a password and Google Authenticator. Recovery codes are stored as hashes and work once.
- Access tokens carry a version. Password changes, role changes, disabling an account, and sign-out invalidate older tokens.
- Secrets come from the environment. The server will not start with a missing or short JWT secret, authenticator key, or weak database password.
- Authenticator secrets are encrypted at rest.
- Login attempts are limited per username and IP address.
- Uploaded files require a signed-in request.
- Clinical deletes go to a recycle bin. Invoices and prescriptions cannot be permanently erased. A pet that still has either cannot be permanently erased.
- Backups are stored outside the application folder. A failed or stale backup is shown to staff and emailed to administrators.
- API errors return a short message. Stack traces are not returned to the browser.
- Logs pass through a scrubber intended to remove passwords, tokens, one-time codes, and authenticator secrets.

## Privacy notice for the clinic to review

Bayport Veterinary Clinic keeps owner and pet information so it can schedule care, record treatment, dispense medicine, and collect payment. Staff accounts are kept so each person is responsible for the records they change. The clinic does not sell this information. Owners may ask the clinic what is stored about them and may ask for a correction. The clinic keeps backups so a computer failure does not destroy the medical record. Questions about a record go to the clinic's administrator.

Staff notice: your name is stored with the changes you make. Do not share your password or authenticator. Sign out when you leave the desk.
