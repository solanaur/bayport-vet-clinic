# Staging copy

Use a separate copy when staff practice with a copy of clinic records. Do not point the practice copy at the live database.

1. Stop the practice copy if it is already running.
2. On the clinic PC, copy the latest backup zip from `C:\Bayport\backups` to the practice PC.
3. Install MySQL on the practice PC and create a database named `bayport_practice`. Do not reuse `bayport_db`.
4. Use a different `JWT_SECRET`, `TOTP_ENCRYPTION_KEY`, and database password in the practice `bayport.env`.
5. Set `SPRING_DATASOURCE_URL` to `jdbc:mysql://127.0.0.1:3306/bayport_practice`.
6. Restore the backup into `bayport_practice` with the MySQL client. Do not restore it into the live database.
7. Start the practice copy with `--spring.profiles.active=production` and a different port, such as `8444`.
8. Tell staff the practice address. Appointments, sales, and reminders on that copy are not the live clinic.

When practice is finished, stop that copy and delete `bayport_practice` if it contains a copy of real records and the clinic is done with the exercise.
