#!/usr/bin/env bash
# P0-5 — gan audience mapper "hrms-api" vao client hrms-spa tren realm HRMS dang chay.
# Fresh setup lay realm-hrms.json (da co protocolMappers); script nay cho KC da co du lieu.
# Idempotent: chay lai nhieu lan khong tao trung mapper.
# Chay trong dev container (nạp .env truoc — can KEYCLOAK_ADMIN*):
#   docker exec hrms-dev bash -c "cd /workspaces/HRMS && set -a && . ./.env && set +a && bash scripts/kc-add-audience.sh"
set -u

KC_BASE=${KC_BASE:-http://keycloak:8080}
REALM=${REALM:-hrms}
CLIENT_ID=${CLIENT_ID:-hrms-spa}
MAPPER_NAME=${MAPPER_NAME:-hrms-api-audience}
AUDIENCE=${AUDIENCE:-hrms-api}
PASS=0
FAIL=0

ok()  { PASS=$((PASS + 1)); echo "OK    $1"; }
bad() { FAIL=$((FAIL + 1)); echo "FAIL  $1"; }

ADMIN=$(curl -s -X POST "$KC_BASE/realms/master/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d "grant_type=password&client_id=admin-cli&username=${KEYCLOAK_ADMIN:?KEYCLOAK_ADMIN chưa set - nạp .env}&password=${KEYCLOAK_ADMIN_PASSWORD:?KEYCLOAK_ADMIN_PASSWORD chưa set - nạp .env}" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
[ -n "$ADMIN" ] || { echo "FAIL  lay admin token Keycloak"; exit 1; }
AUTH="Authorization: Bearer $ADMIN"

echo "== Lay internal id client $CLIENT_ID"
CID=$(curl -s -H "$AUTH" "$KC_BASE/admin/realms/$REALM/clients?clientId=$CLIENT_ID" | jq -r '.[0].id // empty')
if [ -z "$CID" ]; then
  bad "khong tim thay client $CLIENT_ID trong realm $REALM"
  echo "=== RESULT: PASS=$PASS FAIL=$FAIL"
  exit 1
fi
ok "client id = $CID"

echo "== Kiem tra mapper $MAPPER_NAME"
EXISTS=$(curl -s -H "$AUTH" "$KC_BASE/admin/realms/$REALM/clients/$CID/protocol-mappers/models" \
  | jq -r --arg n "$MAPPER_NAME" '.[] | select(.name==$n) | .name' | head -n1)

if [ -n "$EXISTS" ]; then
  ok "mapper $MAPPER_NAME da ton tai — bo qua"
else
  BODY=$(jq -nc --arg n "$MAPPER_NAME" --arg a "$AUDIENCE" \
    '{name:$n,protocol:"openid-connect",protocolMapper:"oidc-audience-mapper",consentRequired:false,
      config:{"included.custom.audience":$a,"id.token.claim":"false","access.token.claim":"true"}}')
  CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "$AUTH" -H 'Content-Type: application/json' \
    -d "$BODY" "$KC_BASE/admin/realms/$REALM/clients/$CID/protocol-mappers/models")
  if [ "$CODE" = "201" ]; then ok "tao mapper $MAPPER_NAME (aud=$AUDIENCE)"
  else bad "tao mapper $MAPPER_NAME (HTTP $CODE)"; fi
fi

echo "== Xac minh: token hrms-spa co aud=$AUDIENCE"
TOKEN=$(curl -s -X POST "$KC_BASE/realms/$REALM/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d "client_id=$CLIENT_ID&grant_type=password&username=${AUD_USER:-hr}&password=${AUD_PASS:-hr@123}&scope=openid" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
if [ -z "$TOKEN" ]; then
  bad "khong lay duoc token $CLIENT_ID (kiem tra AUD_USER/AUD_PASS)"
else
  PAYLOAD=$(echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+')
  PAD=$(( (4 - ${#PAYLOAD} % 4) % 4 ))
  PAYLOAD="$PAYLOAD$(printf '=%.0s' $(seq 1 $PAD) 2>/dev/null)"
  AUD=$(echo "$PAYLOAD" | base64 -d 2>/dev/null | jq -r '.aud // [] | if type=="array" then join(",") else . end')
  if echo ",$AUD," | grep -q ",$AUDIENCE,"; then
    ok "token co aud=$AUDIENCE (aud=$AUD)"
  else
    bad "token thieu aud=$AUDIENCE (aud=$AUD)"
  fi
fi

echo "=== RESULT: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ]
