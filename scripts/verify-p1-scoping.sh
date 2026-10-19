#!/usr/bin/env bash
# Verify P1: MANAGER scoping + mask PII trên /api/v1/employees
#
# Flow: HR tạo 3 NV (quản lý / thuộc cấp / NV ngoài phạm vi) → gán role MANAGER
# cho user quản lý qua client service-account (hrms-identity) → login bằng mật
# khẩu mặc định <ma>@hrms → assert:
#   - list chỉ thấy self + trực thuộc (total=2), không thấy NV ngoài phạm vi
#   - PII của thuộc cấp bị mask (nationalId ****xxxx, address/emergencyPhone ***)
#   - xem chính mình / /me vẫn đầy đủ; chi tiết/history ngoài phạm vi → 403
#   - dọn dẹp: nghỉ việc cả 3 NV (tắt luôn user Keycloak)
#
# Env (fail-fast, devcontainer/app stack nạp từ .env):
#   HRMS_IDENTITY_CLIENT_SECRET
# Override mạng (mặc định như các script khác):
#   API, KC, KC_BASE
# Chạy trong hrms-dev:
#   docker exec hrms-dev bash /workspaces/HRMS/scripts/verify-p1-scoping.sh
set -u

API=${API:-http://localhost:8080}
KC=${KC:-http://keycloak:8080/realms/hrms}
KC_BASE=${KC_BASE:-http://keycloak:8080}
PASS=0
FAIL=0

CLIENT_SECRET=${HRMS_IDENTITY_CLIENT_SECRET:?HRMS_IDENTITY_CLIENT_SECRET chưa set - nạp .env}

token() { # username password
  curl -s -X POST "$KC/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    -d "client_id=hrms-spa&grant_type=password&username=$1&password=$2&scope=openid" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
}

req() { # method token path [data] -> body + "\nHTTP <code>"
  local method=$1 tok=$2 path=$3 data=${4:-}
  if [ -n "$data" ]; then
    curl -s -X "$method" -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' \
      -d "$data" -w '\nHTTP %{http_code}\n' "$API$path"
  else
    curl -s -X "$method" -H "Authorization: Bearer $tok" -w '\nHTTP %{http_code}\n' "$API$path"
  fi
}

code_of() { echo "$1" | tail -1 | sed 's/HTTP //'; }
id_of() { echo "$1" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -1; }
has() { case "$1" in *"$2"*) echo yes;; *) echo no;; esac; }

check() { # label expected code
  if [ "$3" = "$2" ]; then
    PASS=$((PASS + 1)); echo "PASS  $1 -> $3"
  else
    FAIL=$((FAIL + 1)); echo "FAIL  $1 -> $3 (want $2)"
  fi
}

check_has() { # label yes|no expected
  if [ "$2" = "$3" ]; then
    PASS=$((PASS + 1)); echo "PASS  $1 -> $2"
  else
    FAIL=$((FAIL + 1)); echo "FAIL  $1 -> $2 (want $3)"
  fi
}

SUF=$RANDOM$RANDOM
MGR_CODE="pm$SUF"   # quản lý (sau đó được gán role MANAGER)
RPT_CODE="pt$SUF"   # thuộc cấp (managerId = quản lý)
OT_CODE="ot$SUF"    # nhân viên ngoài phạm vi

HR=$(token hr 'hr@123')
echo "hr token len=${#HR}"

echo "== 1. Tao 3 nhan vien (HR)"
R=$(req POST "$HR" /api/v1/employees "{\"employeeCode\":\"$MGR_CODE\",\"fullName\":\"Quan ly P1 $SUF\",\"email\":\"$MGR_CODE@hrms.vn\",\"position\":\"Truong nhom\",\"phone\":\"0911000001\",\"nationalId\":\"098765432109\",\"address\":\"10 Pho Quan, Ha Noi\",\"gender\":\"MALE\",\"birthDate\":\"1990-01-01\",\"hiredAt\":\"2026-01-01T00:00:00Z\"}")
check "POST tao quan ly" 201 "$(code_of "$R")"
MGR_EMP_ID=$(id_of "$R"); echo "managerEmployeeId=$MGR_EMP_ID"

R=$(req POST "$HR" /api/v1/employees "{\"employeeCode\":\"$RPT_CODE\",\"fullName\":\"Thuoc cap P1 $SUF\",\"email\":\"$RPT_CODE@hrms.vn\",\"managerId\":\"$MGR_EMP_ID\",\"position\":\"Thanh vien\",\"phone\":\"0911000111\",\"nationalId\":\"012345678901\",\"address\":\"20 Pho Tho, Ha Noi\",\"gender\":\"FEMALE\",\"birthDate\":\"1995-05-05\",\"hiredAt\":\"2026-02-01T00:00:00Z\"}")
check "POST tao thuoc cap (co managerId)" 201 "$(code_of "$R")"
RPT_EMP_ID=$(id_of "$R"); echo "reportEmployeeId=$RPT_EMP_ID"

R=$(req POST "$HR" /api/v1/employees "{\"employeeCode\":\"$OT_CODE\",\"fullName\":\"NV Khac P1 $SUF\",\"email\":\"$OT_CODE@hrms.vn\",\"position\":\"Tester\",\"phone\":\"0911000222\",\"nationalId\":\"077700011122\",\"address\":\"5 Pho Nho, Ha Noi\"}")
check "POST tao NV ngoai pham vi" 201 "$(code_of "$R")"
OT_EMP_ID=$(id_of "$R"); echo "otherEmployeeId=$OT_EMP_ID"

R=$(req PATCH "$HR" "/api/v1/employees/$RPT_EMP_ID" '{"emergencyContactName":"Nguoi than X","emergencyContactPhone":"0900999888","emergencyContactRelation":"Vo"}')
check "PATCH them Lien he khung cap cho thuoc cap" 200 "$(code_of "$R")"

echo "== 2. Gan role MANAGER cho user quan ly (service account hrms-identity)"
ADMIN=$(curl -s -X POST "$KC/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d "grant_type=client_credentials&client_id=hrms-identity&client_secret=$CLIENT_SECRET" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
echo "admin token len=${#ADMIN}"
check "lay admin token (client_credentials)" 40 "$([ ${#ADMIN} -gt 100 ] && echo 40 || echo 0)"

UID_JSON=$(curl -s -H "Authorization: Bearer $ADMIN" \
  "$KC_BASE/admin/realms/hrms/users?username=$MGR_CODE&exact=true")
KC_USER_ID=$(echo "$UID_JSON" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -1)
echo "kcUserId=$KC_USER_ID"
check "tim user Keycloak tuong ung" 40 "$([ -n "$KC_USER_ID" ] && echo 40 || echo 0)"

ROLE_JSON=$(curl -s -H "Authorization: Bearer $ADMIN" "$KC_BASE/admin/realms/hrms/roles/MANAGER")
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d "[$ROLE_JSON]" "$KC_BASE/admin/realms/hrms/users/$KC_USER_ID/role-mappings/realm")
check "gan realm role MANAGER" 204 "$CODE"

echo "== 3. Login bang quan ly + assert scoping/mask"
MGR_TOK=$(token "$MGR_CODE" "$MGR_CODE@hrms")
echo "manager token len=${#MGR_TOK}"
check "login quan ly (mat khau mac dinh)" 40 "$([ ${#MGR_TOK} -gt 100 ] && echo 40 || echo 0)"

R=$(req GET "$MGR_TOK" "/api/v1/employees?size=100")
check "GET list (MANAGER co ho so)" 200 "$(code_of "$R")"
check_has "list total=2 (self + truc thuoc)" "$(has "$R" '"total":2')" yes
check_has "thay thuoc cap trong list" "$(has "$R" "$RPT_CODE")" yes
check_has "khong thay NV ngoai pham vi" "$(has "$R" "$OT_CODE")" no
check_has "khong thay NV seed (nhminh)" "$(has "$R" 'nhminh')" no
check_has "mask nationalId thuoc cap trong list" "$(has "$R" '"****8901"')" yes
check_has "khong ro so CCCD thuoc cap" "$(has "$R" '012345678901')" no

R=$(req GET "$MGR_TOK" "/api/v1/employees/$RPT_EMP_ID")
check "GET chi tiet thuoc cap" 200 "$(code_of "$R")"
check_has "nationalId mask ****8901" "$(has "$R" '"nationalId":"****8901"')" yes
check_has "address mask ***" "$(has "$R" '"address":"***"')" yes
check_has "emergencyContactPhone mask ***" "$(has "$R" '"emergencyContactPhone":"***"')" yes
check_has "phone giu nguyen (quan he cong viec)" "$(has "$R" '"phone":"0911000111"')" yes
check_has "ten lien he khung cap giu nguyen" "$(has "$R" 'Nguoi than X')" yes

R=$(req GET "$MGR_TOK" "/api/v1/employees/$MGR_EMP_ID")
check "GET chi tiet chinh minh" 200 "$(code_of "$R")"
check_has "self: nationalId day du" "$(has "$R" '"nationalId":"098765432109"')" yes
check_has "self: address day du" "$(has "$R" '"address":"10 Pho Quan, Ha Noi"')" yes

R=$(req GET "$MGR_TOK" "/api/v1/employees/me")
check "GET /me (MANAGER = chinh minh)" 200 "$(code_of "$R")"
check_has "/me day du PII" "$(has "$R" '"nationalId":"098765432109"')" yes

R=$(req GET "$MGR_TOK" "/api/v1/employees/$OT_EMP_ID")
check "GET chi tiet NGOAI pham vi -> 403" 403 "$(code_of "$R")"
R=$(req GET "$MGR_TOK" "/api/v1/employees/$OT_EMP_ID/history")
check "GET history NGOAI pham vi -> 403" 403 "$(code_of "$R")"
R=$(req GET "$MGR_TOK" "/api/v1/employees/$RPT_EMP_ID/history")
check "GET history TRONG pham vi -> 200" 200 "$(code_of "$R")"

echo "== 4. Cleanup: nghi viec 3 NV (tat luon user Keycloak)"
for id in "$RPT_EMP_ID" "$OT_EMP_ID" "$MGR_EMP_ID"; do
  R=$(req DELETE "$HR" "/api/v1/employees/$id?reason=cleanup%20verify-p1-scoping")
  check "cleanup nghi viec $id" 204 "$(code_of "$R")"
done

echo "=== RESULT: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
