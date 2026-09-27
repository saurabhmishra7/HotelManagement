#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 6 ]]; then
  printf 'Usage: OWNER_TOKEN=<owner-token> %s <base-url> <tenant-id> <role> <user-name> <phone> <email>\n' "$0" >&2
  exit 2
fi

if [[ -z "${OWNER_TOKEN:-}" ]]; then
  printf 'Set OWNER_TOKEN to a token with role and staff management permissions.\n' >&2
  exit 2
fi

base_url=${1%/}
tenant_id=$2
role_name=$(printf '%s' "$3" | tr '[:lower:] -' '[:upper:]__')
user_name=$4
phone=$5
email=$6

read -r -s -p 'New staff password (minimum 12 characters): ' password
printf '\n' >&2
read -r -s -p 'Approval PIN (4-8 digits): ' pin
printf '\n' >&2

role_list=$(curl --fail-with-body --silent --show-error \
  "$base_url/api/v1/roles" \
  --header "Authorization: Bearer $OWNER_TOKEN" \
  --header "X-Tenant-Id: $tenant_id")

role_id=$(printf '%s' "$role_list" | jq -r --arg name "$role_name" \
  '[.[] | select(.name == $name)] | first | .id // empty')

if [[ -z "$role_id" ]]; then
  role_response=$(jq -n --arg name "$role_name" '{name: $name}')
  created_role=$(curl --fail-with-body --silent --show-error \
    --request POST "$base_url/api/v1/roles" \
    --header "Authorization: Bearer $OWNER_TOKEN" \
    --header "X-Tenant-Id: $tenant_id" \
    --header 'Content-Type: application/json' \
    --data "$role_response")
  role_id=$(printf '%s' "$created_role" | jq -er '.id')
fi

user_list=$(curl --fail-with-body --silent --show-error \
  "$base_url/api/v1/users" \
  --header "Authorization: Bearer $OWNER_TOKEN" \
  --header "X-Tenant-Id: $tenant_id")

existing_user=$(printf '%s' "$user_list" | jq -c --arg phone "$phone" \
  '[.[] | select(.phone == $phone)] | first // empty')
if [[ -n "$existing_user" ]]; then
  existing_role=$(printf '%s' "$existing_user" | jq -r '.role_id // .roleId // empty')
  if [[ "$existing_role" != "$role_id" ]]; then
    printf 'A user with phone %s already exists with a different role. No changes made.\n' "$phone" >&2
    exit 1
  fi
else
  user_body=$(jq -n \
    --arg name "$user_name" \
    --arg phone "$phone" \
    --arg email "$email" \
    --arg password "$password" \
    --arg pin "$pin" \
    --arg role_id "$role_id" \
    '{name: $name, phone: $phone, email: $email, password: $password, pin: $pin, role_id: $role_id}')
  curl --fail-with-body --silent --show-error \
    --request POST "$base_url/api/v1/create/user" \
    --header "Authorization: Bearer $OWNER_TOKEN" \
    --header "X-Tenant-Id: $tenant_id" \
    --header 'Content-Type: application/json' \
    --data "$user_body" >/dev/null
fi

login_body=$(jq -n --arg phone "$phone" --arg password "$password" \
  '{phone: $phone, password: $password}')
unset password pin OWNER_TOKEN role_list user_list existing_user user_body role_response created_role

curl --fail-with-body --silent --show-error \
  --request POST "$base_url/api/v1/auth/login" \
  --header "X-Tenant-Id: $tenant_id" \
  --header 'Content-Type: application/json' \
  --data "$login_body" | jq --exit-status --raw-output '.token'
