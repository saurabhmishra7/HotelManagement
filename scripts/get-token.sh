#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  printf 'Usage: %s <base-url> <tenant-id> <phone-or-email>\n' "$0" >&2
  exit 2
fi

base_url=${1%/}
tenant_id=$2
identity=$3

read -r -s -p 'Password: ' password
printf '\n' >&2

if [[ "$identity" == *@* ]]; then
  body=$(jq -n --arg email "$identity" --arg password "$password" '{email: $email, password: $password}')
else
  body=$(jq -n --arg phone "$identity" --arg password "$password" '{phone: $phone, password: $password}')
fi
unset password

curl --fail-with-body --silent --show-error \
  --request POST "$base_url/api/v1/auth/login" \
  --header "X-Tenant-Id: $tenant_id" \
  --header 'Content-Type: application/json' \
  --data "$body" | jq --exit-status --raw-output '.token'
