# Bayport Veterinary Clinic

The application lives in `bayport-vet-clinic/`.

Quick start from that folder:

```bash
cd bayport-vet-clinic/bayport-backend
./mvnw clean test
```

Desktop:

```bash
cd bayport-vet-clinic/desktop-shell
npm install
npm run desktop
```

Copy `bayport-vet-clinic/.env.example` for environment names. Real values stay in `data/local.secrets.env`, `data/mail.env`, and `data/sms.env`, which are not committed.

Before a push, from this repository root:

```bash
powershell -File scripts/pre-push-check.ps1
```
