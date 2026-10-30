#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 4 ]]; then
  printf 'Usage: OWNER_TOKEN=<owner-token> %s <base-url> <tenant-id> <role> <phone>\n' "$0" >&2
  printf 'Invite the staff member through Team first, then have them accept the email invitation.\n' >&2
  exit 2
fi

if [[ -z "${OWNER_TOKEN:-}" ]]; then
  printf 'Set OWNER_TOKEN to a token with role and staff management permissions.\n' >&2
  exit 2
fi

base_url=${1%/}
tenant_id=$2
role_name=$(printf '%s' "$3" | tr '[:lower:] -' '[:upper:]__')
phone=$4

read -r -s -p 'Existing staff password: ' password
printf '\n' >&2

role_list=$(curl --fail-with-body --silent --show-error \
  "$base_url/api/v1/roles" \
  --header "Authorization: Bearer $OWNER_TOKEN" \
  --header "X-Tenant-Id: $tenant_id")

role_id=$(printf '%s' "$role_list" | jq -r --arg name "$role_name" \
  '[.[] | select(.name == $name)] | first | .id // empty')

if [[ -z "$role_id" ]]; then
  printf 'Role %s does not exist. No changes made.\n' "$role_name" >&2
  exit 1
fi

user_list=$(curl --fail-with-body --silent --show-error \
  "$base_url/api/v1/users" \
  --header "Authorization: Bearer $OWNER_TOKEN" \
  --header "X-Tenant-Id: $tenant_id")

existing_user=$(printf '%s' "$user_list" | jq -c --arg phone "$phone" \
  '[.[] | select(.phone == $phone)] | first // empty')
if [[ -z "$existing_user" ]]; then
  printf 'No staff account exists for this phone. Invite the person through Team and have them accept the email link first.\n' >&2
  exit 1
fi
has_role=$(printf '%s' "$existing_user" | jq -r --arg role_id "$role_id" \
  '(.role_ids // [.role_id]) | index($role_id) != null')
if [[ "$has_role" != true ]]; then
  printf 'This staff member does not have role %s. No changes made.\n' "$role_name" >&2
  exit 1
fi

login_body=$(jq -n --arg phone "$phone" --arg password "$password" \
  '{phone: $phone, password: $password}')
unset password OWNER_TOKEN role_list user_list existing_user

curl --fail-with-body --silent --show-error \
  --request POST "$base_url/api/v1/auth/login" \
  --header "X-Tenant-Id: $tenant_id" \
  --header 'Content-Type: application/json' \
  --data "$login_body" | jq --exit-status --raw-output '.token'
