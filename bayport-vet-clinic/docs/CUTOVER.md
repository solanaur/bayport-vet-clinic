# Go-live day

## The day before

- Take a backup and confirm the restore test passed.
- Freeze new entries in the old paper or spreadsheet process after the last patient, or agree the exact freeze time.
- Export the final owner, pet, vaccination, and inventory files.
- Import them on the staging copy first. Fix every row error. A commit with any error saves nothing.

## Cutover

1. Stop staff from adding records to the old process.
2. Export the final files again.
3. On the clinic server, open an administrator session and import owners, then pets, then vaccinations, then inventory. Use preview first. Commit only when the error list is empty.
4. Compare counts: owners, pets, vaccines, and stock items against the source files.
5. Spot-check five owners, five pets, two invoices if any were entered by hand, and two stock items.
6. Staff sign in with their own accounts and confirm Google Authenticator.
7. Take a fresh backup.

## If the new system cannot be used

1. Stop the Bayport server with `deploy/clinic/stop-bayport.ps1`.
2. Staff return to the previous paper or spreadsheet process.
3. Restore the safety backup taken before the import only if the import itself damaged records. A failed preview does not need a restore.
4. Write down every patient seen during the outage and enter those visits after Bayport is back.
