#!/usr/bin/env bash
# Step 4 — mo hinh 5 role tren realm HRMS dang chay:
#   1) rename HR_ADMIN -> ADMIN (fresh setup lay realm-hrms.json, script nay cho KC da co du lieu)
#   2) tao 2 role moi GLOBAL_LEAD, DEPT_LEAD (idempotent)
#   3) gan realmRoles cua group "HR" = ["ADMIN"]
# Chay trong dev container (nạp .env truoc — can KEYCLOAK_ADMIN*):
#   docker exec hrms-dev bash -c "cd /workspaces/HRMS && set -a && . ./.env && set +a && bash scripts/kc-init-roles.sh"
set -u

KC_BASE=${KC_BASE:-http://keycloak:8080}
REALM=${REALM:-hrms}
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

role_http() { # method role-name [json] -> http code
  local method=$1 name=$2 data=${3:-}
  if [ -n "$data" ]; then
    curl -s -o /dev/null -w '%{http_code}' -X "$method" -H "$AUTH" -H 'Content-Type: application/json' \
      -d "$data" "$KC_BASE/admin/realms/$REALM/roles/$name"
  else
    curl -s -o /dev/null -w '%{http_code}' -X "$method" -H "$AUTH" "$KC_BASE/admin/realms/$REALM/roles/$name"
  fi
}

echo "== 1. Rename HR_ADMIN -> ADMIN"
HR_CODE=$(role_http GET HR_ADMIN)
ADMIN_CODE=$(role_http GET ADMIN)
if [ "$HR_CODE" = "200" ] && [ "$ADMIN_CODE" != "200" ]; then
  CODE=$(role_http PUT HR_ADMIN '{"name":"ADMIN","description":"Quản trị hệ thống — toàn quyền","composite":false,"clientRole":false}')
  if [ "$CODE" = "204" ]; then ok "rename HR_ADMIN -> ADMIN"
  else bad "rename HR_ADMIN -> ADMIN (HTTP $CODE)"; fi
elif [ "$ADMIN_CODE" = "200" ] && [ "$HR_CODE" != "200" ]; then
  ok "ADMIN da ton tai, HR_ADMIN khong con — bo qua"
elif [ "$ADMIN_CODE" = "200" ] && [ "$HR_CODE" = "200" ]; then
  bad "ca HR_ADMIN va ADMIN deu ton tai — gop tay (xóa HR_ADMIN sau khi chuyển group)"
else
  bad "khong tim thay HR_ADMIN/ADMIN (realm $REALM?)"
fi

echo "== 2. Tao role GLOBAL_LEAD, DEPT_LEAD (idempotent)"
for R in GLOBAL_LEAD DEPT_LEAD; do
  CODE=$(role_http GET "$R")
  if [ "$CODE" = "200" ]; then
    ok "$R da ton tai"
  else
    case $R in
      GLOBAL_LEAD) DESC="Lãnh đạo toàn cầu — đọc toàn bộ, xem lương" ;;
      *)           DESC="Trưởng phòng — scope phòng ban của mình" ;;
    esac
    CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "$AUTH" -H 'Content-Type: application/json' \
      -d "{\"name\":\"$R\",\"description\":\"$DESC\"}" "$KC_BASE/admin/realms/$REALM/roles")
    if [ "$CODE" = "201" ]; then ok "tao $R"
    else bad "tao $R (HTTP $CODE)"; fi
  fi
done

echo "== 3. Group HR -> realmRoles [ADMIN] (Keycloak chi co POST add / DELETE remove)"
GROUP=$(curl -s -H "$AUTH" "$KC_BASE/admin/realms/$REALM/groups?name=HR&exact=true")
GID=$(echo "$GROUP" | jq -r '.[0].id // empty')
if [ -z "$GID" ]; then
  bad "khong tim thay group HR"
else
  ALL_ROLES=$(curl -s -H "$AUTH" "$KC_BASE/admin/realms/$REALM/roles")
  ADMIN_ID=$(echo "$ALL_ROLES" | jq -r '.[] | select(.name=="ADMIN") | .id')
  CUR=$(curl -s -H "$AUTH" "$KC_BASE/admin/realms/$REALM/groups/$GID/role-mappings/realm")
  CUR_IDS=$(echo "$CUR" | jq -r '.[].id')

  # add ADMIN neu chua co
  if echo "$CUR_IDS" | grep -qx "$ADMIN_ID"; then
    ok "group HR da co role ADMIN"
  else
    BODY=$(echo "$ALL_ROLES" | jq -c '[.[] | select(.name=="ADMIN")]')
    CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "$AUTH" -H 'Content-Type: application/json' \
      -d "$BODY" "$KC_BASE/admin/realms/$REALM/groups/$GID/role-mappings/realm")
    if [ "$CODE" = "204" ]; then ok "them ADMIN vao group HR"
    else bad "them ADMIN vao group HR (HTTP $CODE)"; fi
  fi

  # remove cac role con lai (neu co)
  EXTRA=$(comm -23 <(echo "$CUR_IDS" | sort) <(echo "$ADMIN_ID" | sort) | jq -R . | jq -s -c 'map({id:.})')
  if [ "$EXTRA" = "[]" ]; then
    ok "group HR khong con role thua"
  else
    CODE=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE -H "$AUTH" -H 'Content-Type: application/json' \
      -d "$EXTRA" "$KC_BASE/admin/realms/$REALM/groups/$GID/role-mappings/realm")
    if [ "$CODE" = "204" ]; then ok "xoa role thua khoi group HR"
    else bad "xoa role thua khoi group HR (HTTP $CODE)"; fi
  fi
fi

echo "=== RESULT: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ]
