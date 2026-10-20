# Go-live

## Checklist

- [ ] Phases 1 through 9 are in the build you are starting
- [ ] A backup has been restored into a practice database, not only downloaded
- [ ] Factory accounts and the old wipe action are gone
- [ ] `JWT_SECRET`, `TOTP_ENCRYPTION_KEY`, database password, and mail password are new values
- [ ] HTTPS works on the clinic PC and staff computers trust the certificate
- [ ] Each staff member has a personal account and has enrolled Google Authenticator
- [ ] Front desk, veterinarian, and administrator have completed `docs/UAT.md`
- [ ] `docs/USER_GUIDE.md`, `docs/ADMIN_GUIDE.md`, `docs/DOWNTIME.md`, and `docs/INCIDENT.md` are printed or saved where staff can open them

Before real records are loaded, switch strict mode: do not commit `/data`, database files, uploads, or logs. Purge test accounts from the production database. Rotate every secret. Confirm `.gitignore` ignores data and logs. The old JWT fallback string was in source history, so history should be cleaned before a public repository is shared.

## Parallel run

Run Bayport beside the current paper or spreadsheet process for one to two weeks.

Each afternoon, compare:

- number of visits booked
- number of payments taken and the total amount
- prescriptions written
- stock items that went below their reorder level

Switch fully when those counts match for five consecutive clinic days and staff can complete a day without the paper file except as a fallback.

## Support for the first month

Staff write problems in a shared sheet: date, person, what they clicked, what they expected, what happened, and whether patients are waiting.

| Severity | Meaning | Response |
|---|---|---|
| 1 | Cannot sign in, cannot record a visit, or backup failed | Same day |
| 2 | A record is wrong but a workaround exists | Next clinic day |
| 3 | Wording or layout | The weekly check-in |

Meet once a week for the first four weeks and review that sheet.

## Known limits

- The access token lasts eight hours unless a password, role, or sign-out changes it.
- Logout ends every session for that account.
- H2 restore needs the server to be stopped. MySQL restore runs from the Backups page.
- V9, V11, and V12 cannot be applied to a MySQL database that has not already passed them. New databases use the baseline.
- Email and SMS still depend on outside providers. A failed appointment email or reminder is recorded and does not undo the saved visit.
- Voiding an invoice removes it from paid billing totals. A point-of-sale sale row that was already stored is not rewritten.
- The longest lists are not paginated yet. Lookup indexes for appointments, pets, invoices, and prescriptions are in migration V15.
- The HttpOnly cookie move described in Phase 1 is not implemented. The token remains in browser storage.
- Role screens still show some shared pages. The server checks the sensitive actions.

## After launch

1. Move the sign-in token to an HttpOnly cookie.
2. Shorten the access token only after a refresh flow exists.
3. Add batch quantities per expiry date when the clinic stocks more than one batch of the same item.
4. Add pagination to the longest lists after the first year of records.
5. Have counsel review `docs/SECURITY.md` before any public privacy posting.
