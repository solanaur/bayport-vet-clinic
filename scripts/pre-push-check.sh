#!/usr/bin/env bash
# Fails when the repo is not safe to push.
#   ./scripts/pre-push-check.sh
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
cd "$root"
fail=0

while IFS= read -r rel; do
  [ -z "$rel" ] && continue
  case "$rel" in
    *node_modules/*|node_modules/*|*/target/*|target/*)
      echo "Tracked build output: $rel"
      fail=1
      ;;
  esac
  base="${rel##*/}"
  case "$base" in
    *.env.example|.env.example) ;;
    .env|*.env|.env.*)
      echo "Tracked env file: $rel"
      fail=1
      ;;
  esac
  if [ -f "$rel" ]; then
    size=$(wc -c < "$rel" | tr -d ' ')
    case "$rel" in
      *maven-wrapper.jar) ;;
      *)
        if [ "$size" -gt 5242880 ]; then
          echo "Tracked file larger than 5 MB: $rel"
          fail=1
        fi
        ;;
    esac
  fi
done < <(git ls-files)

while IFS= read -r rel; do
  [ -z "$rel" ] && continue
  case "$rel" in
    *.png|*.jpg|*.jpeg|*.gif|*.pdf|*.docx|*.jar|*.zip|*.db) continue ;;
  esac
  [ -f "$rel" ] || continue
  if grep -nE 'AKIA[0-9A-Z]{16}|-----BEGIN (RSA |OPENSSH |EC |DSA )?PRIVATE KEY-----|ghp_[A-Za-z0-9]{20,}|xox[baprs]-[A-Za-z0-9-]{10,}|sk_live_[A-Za-z0-9]{10,}|AIza[0-9A-Za-z_-]{20,}' "$rel" \
      | grep -vE 'replace-with|YOUR_|your-clinic|placeholder|example\.com' >/dev/null; then
    echo "Secret-like string in $rel"
    fail=1
  fi
  if grep -nE '\$\{JWT_SECRET:[^}]{8,}\}' "$rel" | grep -vE 'replace-with|YOUR_|placeholder' >/dev/null; then
    echo "JWT default secret in $rel"
    fail=1
  fi
done < <(git ls-files)

if [ "$fail" -ne 0 ]; then
  echo "Pre-push check failed."
  exit 1
fi

if [ "${1:-}" = "--skip-tests" ]; then
  echo "Pre-push scan passed (tests skipped)."
  exit 0
fi

backend="$root/bayport-vet-clinic/bayport-backend"
if [ ! -x "$backend/mvnw" ] && [ ! -f "$backend/mvnw" ]; then
  echo "mvnw not found"
  exit 1
fi
(cd "$backend" && ./mvnw test)
echo "Pre-push check passed."
