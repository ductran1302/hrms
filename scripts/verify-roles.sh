#!/usr/bin/env bash
# Smoke test mo hinh 5 role: KC roles + auto-assign + PATCH accounts/{u}/roles + scoping.
# Chay trong dev container (nạp .env trước):
#   docker exec hrms-dev bash -c "cd /workspaces/HRMS && set -a && . ./.env && set +a && bash scripts/verify-roles.sh"
# Override: API=... KC=... KC_BASE=... GW_URL=... ATT_URL=... (xem header verify-p1-scoping.sh)
set -u

API=${API:-http://localhost:8080}
KC=${KC:-http://keycloak:8080/realms/hrms}
KC_BASE=${KC_BASE:-http://keycloak:8080}
PASS=0
FAIL=0

token() { curl -s -X POST "$KC/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    -d "client_id=hrms-spa&grant_type=password&username=$1&password=$2&scope=openid" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'; }

req() { # method token path [json]
  local method=$1 tok=$2 path=$3 data=${4:-}
  if [ -n "$data" ]; then
    curl -s -X "$method" -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' \
      -d "$data" -w '\nHTTP %{http_code}\n' "$API$path"
  else
    curl -s -X "$method" -H "Authorization: Bearer $tok" -w '\nHTTP %{http_code}\n' "$API$path"
  fi
}

code_of() { echo "$1" | tail -1 | sed 's/HTTP //'; }
fld() { echo "$1" | sed -n "s/.*\"$2\":\"\([^\"]*\)\".*/\1/p" | head -1; }
has() { case "$1" in *"$2"*) echo yes;; *) echo no;; esac; }

check() { # label expected actual
  if [ "$3" = "$2" ]; then PASS=$((PASS + 1)); echo "PASS  $1 -> $3"
  else FAIL=$((FAIL + 1)); echo "FAIL  $1 -> $3 (want $2)"; fi
}
ck() { # label yes|no expected
  if [ "$2" = "$3" ]; then PASS=$((PASS + 1)); echo "PASS  $1 -> $2"
  else FAIL=$((FAIL + 1)); echo "FAIL  $1 -> $2 (want $3)"; fi
}

HR=$(token hr 'hr@123')
MGR=$(token manager1 'manager@123')
EMP=$(token employee1 'emp@123')
[ -n "$HR" ] && [ -n "$MGR" ] && [ -n "$EMP" ] || { echo "FAIL  lay token (kc-init-roles chua chay?)"; exit 1; }

KCA=$(curl -s -X POST "$KC_BASE/realms/master/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d "grant_type=password&client_id=admin-cli&username=${KEYCLOAK_ADMIN:?KEYCLOAK_ADMIN chưa set - nạp .env}&password=${KEYCLOAK_ADMIN_PASSWORD:?KEYCLOAK_ADMIN_PASSWORD chưa set - nạp .env}" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

echo "== A. Realm co 5 role, khong con HR_ADMIN"
ROLES=$(curl -s -H "Authorization: Bearer $KCA" "$KC_BASE/admin/realms/hrms/roles" | jq -r '.[].name')
for R in ADMIN GLOBAL_LEAD DEPT_LEAD MANAGER EMPLOYEE; do
  ck "realm role $R" "$(echo "$ROLES" | grep -cx "$R")" 1
done
ck "realm role HR_ADMIN da xoa" "$(echo "$ROLES" | grep -cx HR_ADMIN)" 0

echo "== B. Auto-assign khi tao NV (khong manager -> EMPLOYEE)"
SUF=$RANDOM$RANDOM
R=$(req POST "$HR" /api/v1/employees "{\"employeeCode\":\"RLE$SUF\",\"fullName\":\"Role Smoke $SUF\"}")
check "POST tao NV test" 201 "$(code_of "$R")"
CODE=$(fld "$R" employeeCode); EID=$(fld "$R" id)
T=$(token "$CODE" "${CODE}@hrms")
ck "login NV bang <ma>@hrms" "$([ -n "$T" ] && echo yes || echo no)" yes
A=$(req GET "$HR" "/api/v1/accounts?q=$CODE")
case "$A" in *'"roles":["EMPLOYEE"]'*) ck "auto-assign = [EMPLOYEE]" yes yes
  ;; *) ck "auto-assign = [EMPLOYEE]" "$(has "$A" '"roles":[')" 'roles:[EMPLOYEE]';; esac

echo "== C. PATCH /api/v1/accounts/{u}/roles"
R=$(req PATCH "$HR" "/api/v1/accounts/$CODE/roles" '{"roles":["MANAGER"]}')
check "ADMIN doi NV -> MANAGER" 204 "$(code_of "$R")"
A=$(req GET "$HR" "/api/v1/accounts?q=$CODE")
ck "roles = [MANAGER]" "$(has "$A" '"roles":["MANAGER"]')" yes

R=$(req PATCH "$HR" "/api/v1/accounts/$CODE/roles" '{"roles":["BOGUS_ROLE"]}')
check "role khong hop le -> 422" 422 "$(code_of "$R")"

R=$(req PATCH "$HR" "/api/v1/accounts/hr/roles" '{"roles":["EMPLOYEE"]}')
check "tu ha minh ADMIN -> EMPLOYEE = 422" 422 "$(code_of "$R")"

R=$(req PATCH "$MGR" "/api/v1/accounts/$CODE/roles" '{"roles":["EMPLOYEE"]}')
check "MANAGER PATCH roles -> 403" 403 "$(code_of "$R")"

R=$(req PATCH "$EMP" "/api/v1/accounts/$CODE/roles" '{"roles":["EMPLOYEE"]}')
check "EMPLOYEE PATCH roles -> 403" 403 "$(code_of "$R")"

echo "== D. Scoping theo role (identity employees list)"
R=$(req GET "$EMP" "/api/v1/employees")
check "EMPLOYEE GET /employees -> 403" 403 "$(code_of "$R")"

R=$(req PATCH "$HR" "/api/v1/accounts/manager1/roles" '{"roles":["MANAGER","DEPT_LEAD"]}')
check "ADMIN them DEPT_LEAD cho manager1" 204 "$(code_of "$R")"
R=$(req GET "$MGR" "/api/v1/employees")
check "DEPT_LEAD GET /employees -> 200" 200 "$(code_of "$R")"

echo "== E. Attendance list theo role"
R=$(req GET "$EMP" "/api/v1/attendance/employees")
check "EMPLOYEE GET attendance/employees -> 403" 403 "$(code_of "$R")"
R=$(req GET "$MGR" "/api/v1/attendance/employees")
check "DEPT_LEAD GET attendance/employees -> 200" 200 "$(code_of "$R")"
R=$(req GET "$HR" "/api/v1/attendance/employees")
check "ADMIN GET attendance/employees -> 200" 200 "$(code_of "$R")"

echo "== F. Cleanup"
req PATCH "$HR" "/api/v1/accounts/manager1/roles" '{"roles":["MANAGER"]}' >/dev/null
req PATCH "$HR" "/api/v1/accounts/$CODE/roles" '{"roles":["EMPLOYEE"]}' >/dev/null
R=$(req DELETE "$HR" "/api/v1/employees/$EID")
check "DELETE NV test" 204 "$(code_of "$R")"

echo "=== RESULT: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ]
