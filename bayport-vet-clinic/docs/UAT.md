# User acceptance test

Mark each row Pass or Fail. Use the practice copy from `docs/STAGING.md` if the rows include real clinic names.

| Role | Scenario | Pass/Fail | Notes |
|---|---|---|---|
| Front desk | Add an owner and a pet | | |
| Front desk | Book a visit, then try to book the same veterinarian at the same time | | The second booking must be refused |
| Front desk | Cancel a visit and book that slot again | | |
| Veterinarian | Open the visit, save a consultation, and save a prescription | | |
| Front desk | Take a payment and print or view the receipt | | |
| Front desk | Record a refund or void only if that action is available to the role | | |
| Administrator | Delete a pet that has a prescription and confirm permanent delete is refused | | |
| Administrator | Restore that pet from Recycle Bin | | |
| Administrator | Export owners to CSV and prescriptions to PDF | | |
| Administrator | Import a template with one bad row and confirm nothing was saved | | |
| Administrator | Run Back up now, download the file, and confirm the status is not a warning | | |
| Administrator | Open the admin health details and confirm the database is up | | |
| Front desk | Sign out and confirm the previous token no longer opens a page | | |

## Light concurrent check

On the practice copy, two browsers book the same veterinarian and time. One must succeed and the other must be told the slot is taken. This is the load check for a small clinic. It is not a laboratory benchmark.
