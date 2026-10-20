# Defects fixed in the daily-operations review

| Defect | Fix |
|---|---|
| Updating an appointment could keep a second booking when the overlap list contained both the current visit and another visit | The slot check now fails when any other appointment id is in that list |
| Cancelled visits were excluded, but a recycled appointment could still occupy the slot | Overlap queries ignore deleted appointments |
| Deleting an appointment, prescription, invoice, or owner removed the row | Those deletes now mark the row and leave it for Recycle Bin |
| Permanent delete of a pet with a prescription or invoice removed the clinical history | That permanent delete is refused |
| The admin data-wipe endpoint could erase pets, appointments, and prescriptions | The endpoint now refuses and writes an audit entry |
| Reminders could still be sent for a pet marked deceased or inactive | Dispatch skips those pets |
| An owner who opts out could still receive reminders | Dispatch skips owners with reminder opt-out |
| A paid or pending invoice could not be voided with a reason | Administrators and front office can void an invoice once; the reason is audited |
| Uploads accepted any file type and size | PDF, JPG, and PNG files up to 5 MB are accepted |
| Two stock updates could overwrite each other | Inventory rows carry a version, and a conflicting save asks the user to refresh |
