#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  printf 'Usage: OWNER_TOKEN=<owner-token> %s <base-url> <tenant-id>\n' "$0" >&2
  exit 2
fi
if [[ -z "${OWNER_TOKEN:-}" ]]; then
  printf 'Set OWNER_TOKEN to a token for the tenant OWNER.\n' >&2
  exit 2
fi

BASE_URL=${1%/}
TENANT_ID=$2

api_get() {
  curl --fail-with-body --silent --show-error \
    "$BASE_URL$1" \
    --header "Authorization: Bearer $OWNER_TOKEN" \
    --header "X-Tenant-Id: $TENANT_ID"
}

api_post() {
  curl --fail-with-body --silent --show-error \
    --request POST "$BASE_URL$1" \
    --header "Authorization: Bearer $OWNER_TOKEN" \
    --header "X-Tenant-Id: $TENANT_ID" \
    --header 'Content-Type: application/json' \
    --data "$2"
}

today=$(date '+%Y-%m-%d')
checkout=$(date -v+3d '+%Y-%m-%dT11:00:00')
rooms=$(api_get /api/v1/rooms)
room_102=$(jq -er '.[] | select(.roomNumber == "102") | .id' <<<"$rooms")
room_202=$(jq -er '.[] | select(.roomNumber == "202") | .id' <<<"$rooms")
stay_202=$(api_get '/api/v1/stays/active?room_number=202' | jq -er '.stay_id')
tables=$(api_get /api/v1/dining-tables)
qr_codes=$(api_get /api/v1/qr-codes)
if jq -e --arg room "$room_102" \
  'any(.[]; .targetType == "room" and .targetId == $room)' <<<"$qr_codes" >/dev/null \
  && jq -e '[.[] | select(.tableNumber == "D-01" or .tableNumber == "D-02")] | length >= 2' \
    <<<"$tables" >/dev/null; then
  printf 'The demo dataset is already present for tenant %s. No changes made.\n' "$TENANT_ID"
  exit 0
fi

tax_rules=$(api_get /api/v1/tax-rules)
room_tax=$(jq -er '.[] | select(.appliesTo == "room" and .ratePercent == 12.00) | .id' <<<"$tax_rules")
restaurant_tax=$(jq -er '.[] | select(.appliesTo == "restaurant" and .ratePercent == 5.00) | .id' <<<"$tax_rules")

active_102=$(api_get '/api/v1/stays/active?room_number=102' || true)
if [[ -n "$active_102" && "$active_102" != null ]]; then
  stay_102=$(jq -er '.stay_id' <<<"$active_102")
else
  foreign_stay=$(api_post /api/v1/stays \
    "$(jq -nc --arg room "$room_102" --arg checkout "$checkout" \
      '{roomId:$room,customer:{name:"Demo International Guest",phone:"9000000099",idProofType:"passport",idProofNumber:"P-DEMO-2026-01",address:"Demo address"},guestCount:1,plan:"CP",tariff:3800,advancePaid:1000,isForeignGuest:true,expectedCheckOutAt:$checkout}')")
  stay_102=$(jq -er '.stay_id' <<<"$foreign_stay")
fi
form_c=$(api_get "/api/v1/stays/$stay_102/form-c" | jq -er '.id')

charge_rows=$(api_get "/api/v1/stays/$stay_202")
if ! jq -e '.charges[]? | select(.description == "Demo minibar and laundry")' <<<"$charge_rows" >/dev/null; then
  api_post "/api/v1/stays/$stay_202/charges" \
    '{"description":"Demo minibar and laundry","amount":850.00}' >/dev/null
fi
stay_invoice=$(api_post "/api/v1/stays/$stay_202/checkout" '' | jq -er '.invoices[] | select(.sourceModule == "stay") | .id')

table_1=$(jq -r '.[] | select(.tableNumber == "D-01") | .id' <<<"$tables")
table_2=$(jq -r '.[] | select(.tableNumber == "D-02") | .id' <<<"$tables")
if [[ -z "$table_1" ]]; then
  table_1=$(api_post /api/v1/dining-tables \
    '{"table_number":"D-01","section":"Main dining"}' | jq -er '.id')
fi
if [[ -z "$table_2" ]]; then
  table_2=$(api_post /api/v1/dining-tables \
    '{"table_number":"D-02","section":"Patio"}' | jq -er '.id')
fi
qr_room=$(jq -r --arg id "$room_102" \
  '.[] | select(.targetType == "room" and .targetId == $id) | .id' <<<"$qr_codes")
qr_table=$(jq -r --arg id "$table_1" \
  '.[] | select(.targetType == "dining_table" and .targetId == $id) | .id' <<<"$qr_codes")
if [[ -z "$qr_room" ]]; then
  qr_room=$(api_post /api/v1/qr-codes \
    "$(jq -nc --arg id "$room_102" '{target_type:"room",target_id:$id}')" | jq -er '.id')
fi
if [[ -z "$qr_table" ]]; then
  qr_table=$(api_post /api/v1/qr-codes \
    "$(jq -nc --arg id "$table_1" '{target_type:"dining_table",target_id:$id}')" | jq -er '.id')
fi

categories=$(api_get /api/v1/menu-categories)
starters=$(jq -r '.[] | select(.name == "Demo Starters") | .id' <<<"$categories")
main_course=$(jq -r '.[] | select(.name == "Demo Main Course") | .id' <<<"$categories")
beverages=$(jq -r '.[] | select(.name == "Demo Beverages") | .id' <<<"$categories")
if [[ -z "$starters" ]]; then starters=$(api_post /api/v1/menu-categories '{"name":"Demo Starters","sort_order":1}' | jq -er '.id'); fi
if [[ -z "$main_course" ]]; then main_course=$(api_post /api/v1/menu-categories '{"name":"Demo Main Course","sort_order":2}' | jq -er '.id'); fi
if [[ -z "$beverages" ]]; then beverages=$(api_post /api/v1/menu-categories '{"name":"Demo Beverages","sort_order":3}' | jq -er '.id'); fi

menu_items=$(api_get /api/v1/menu-items)
paneer=$(jq -r '.[] | select(.name == "Demo Paneer Tikka") | .id' <<<"$menu_items")
thali=$(jq -r '.[] | select(.name == "Demo Maharaja Thali") | .id' <<<"$menu_items")
tea=$(jq -r '.[] | select(.name == "Demo Masala Chai") | .id' <<<"$menu_items")
if [[ -z "$paneer" ]]; then
  paneer=$(api_post /api/v1/menu-items \
    "$(jq -nc --arg cat "$starters" --arg tax "$restaurant_tax" \
      '{category_id:$cat,name:"Demo Paneer Tikka",price:420.00,tax_rule_id:$tax,station:"kitchen",veg_flag:true}')" | jq -er '.id')
fi
if [[ -z "$thali" ]]; then
  thali=$(api_post /api/v1/menu-items \
    "$(jq -nc --arg cat "$main_course" --arg tax "$restaurant_tax" \
      '{category_id:$cat,name:"Demo Maharaja Thali",price:850.00,tax_rule_id:$tax,station:"kitchen",veg_flag:true}')" | jq -er '.id')
fi
if [[ -z "$tea" ]]; then
  tea=$(api_post /api/v1/menu-items \
    "$(jq -nc --arg cat "$beverages" --arg tax "$restaurant_tax" \
      '{category_id:$cat,name:"Demo Masala Chai",price:90.00,tax_rule_id:$tax,station:"bar",veg_flag:true}')" | jq -er '.id')
fi

orders=$(api_get /api/v1/orders)
dine_order=$(jq -r --arg table "$table_1" \
  '.[] | select(.tableId == $table and .status == "open") | .id' <<<"$orders")
if [[ -z "$dine_order" ]]; then
  dine_table=$table_1
  if [[ $(jq -r --arg id "$table_1" '.[] | select(.id == $id) | .status' <<<"$tables") != "free" ]]; then
    dine_table=$table_2
  fi
  dine_order=$(api_post /api/v1/orders \
    "$(jq -nc --arg table "$dine_table" '{order_type:"dine_in",table_id:$table}')" | jq -er '.order_id')
  table_1=$dine_table
  api_post "/api/v1/orders/$dine_order/items" \
    "$(jq -nc --arg paneer "$paneer" --arg tea "$tea" \
      '{items:[{menu_item_id:$paneer,quantity:1,notes:"Demo dine-in"},{menu_item_id:$tea,quantity:2,notes:"No sugar"}]}')" >/dev/null
fi
dine_details=$(api_get "/api/v1/orders/$dine_order")
dine_kot=$(jq -er '.items[] | select(.kotBatchId != null) | .kotBatchId' <<<"$dine_details" | head -n 1)
api_post "/api/v1/kot-batches/$dine_kot/mark-printed" '{}' >/dev/null
if [[ $(jq -r '.order.status' <<<"$dine_details") == "open" ]]; then
  dine_bill_response=$(api_post "/api/v1/orders/$dine_order/bill" '{}')
  dine_bill=$(jq -er '.restaurant_bill_id' <<<"$dine_bill_response")
  api_post "/api/v1/bills/$dine_bill/settle" '{"settlement_mode":"cash"}' >/dev/null
fi

room_order=$(api_post /api/v1/orders \
  "$(jq -nc --arg stay "$stay_202" '{order_type:"room_service",stay_id:$stay}')" | jq -er '.order_id')
api_post "/api/v1/orders/$room_order/items" \
  "$(jq -nc --arg thali "$thali" '{items:[{menu_item_id:$thali,quantity:1,notes:"Demo room service"}]}')" >/dev/null
room_bill_response=$(api_post "/api/v1/orders/$room_order/bill" '{}')
room_bill=$(jq -er '.restaurant_bill_id' <<<"$room_bill_response")
room_invoice=$(jq -er '.invoice_id' <<<"$room_bill_response")
api_post "/api/v1/bills/$room_bill/settle" \
  '{"settlement_mode":"account","room_number":"202"}' >/dev/null
credit_note=$(api_post /api/v1/credit-notes \
  "$(jq -nc --arg invoice "$room_invoice" \
    '{original_invoice_id:$invoice,reason:"Demo adjustment for a cancelled item",amount:50.00}')" | jq -er '.id')

rooms_expense_cat=$(api_post /api/v1/expense-categories '{"name":"Demo Rooms Supplies"}' | jq -er '.id')
food_expense_cat=$(api_post /api/v1/expense-categories '{"name":"Demo Restaurant Supplies"}' | jq -er '.id')
api_post /api/v1/expenses \
  "$(jq -nc --arg cat "$rooms_expense_cat" --arg date "$today" \
    '{category_id:$cat,department:"rooms",vendor_name:"Demo Linen Supplier",amount:2400.00,payment_mode:"upi",receipt_file_ref:"demo/receipts/linen-001.pdf",expense_date:$date}')" >/dev/null
api_post /api/v1/expenses \
  "$(jq -nc --arg cat "$food_expense_cat" --arg date "$today" \
    '{category_id:$cat,department:"restaurant",vendor_name:"Demo Produce Market",amount:1350.00,payment_mode:"petty_cash",receipt_file_ref:"demo/receipts/produce-001.jpg",expense_date:$date}')" >/dev/null
ledger=$(api_post /api/v1/petty-cash/open \
  "$(jq -nc --arg date "$today" '{shift_date:$date,opening_float:5000.00}')" | jq -er '.id')
api_post "/api/v1/petty-cash/$ledger/top-up" '{"amount":2000.00}' >/dev/null
api_post "/api/v1/petty-cash/$ledger/reconcile" '{"amount":5650.00}' >/dev/null
recurring=$(api_post /api/v1/recurring-expenses \
  "$(jq -nc --arg cat "$rooms_expense_cat" --arg date "$today" \
    '{category_id:$cat,description:"Demo monthly internet service",amount:1800.00,frequency:"monthly",next_due_date:$date}')" | jq -er '.id')
api_post "/api/v1/recurring-expenses/$recurring/mark-paid" \
  "$(jq -nc --arg date "$today" '{expense_date:$date,create_expense:true}')" >/dev/null

printf 'Demo records created for tenant %s.\n' "$TENANT_ID"
printf 'Foreign stay: %s (pending Form C: %s)\n' "$stay_102" "$form_c"
printf 'Stay with charge and generated invoice: %s / %s\n' "$stay_202" "$stay_invoice"
printf 'Restaurant dine-in order: %s; room-service order: %s\n' "$dine_order" "$room_order"
printf 'Room-service invoice: %s; credit note: %s\n' "$room_invoice" "$credit_note"
printf 'Tables: %s, %s; QR codes: %s, %s\n' "$table_1" "$table_2" "$qr_room" "$qr_table"
printf 'Petty cash ledger: %s; recurring expense: %s\n' "$ledger" "$recurring"
