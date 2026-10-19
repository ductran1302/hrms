#!/usr/bin/env bash
# Kiem tra 2 tinh nang moi: (1) ma NV = username Keycloak, sinh tu ho ten
# (2) dong voi login/nghi viec -> disable user. Chay trong hrms-dev:
#   docker exec hrms-dev bash /workspaces/HRMS/scripts/verify-new-features.sh
API=http://localhost:8080
KC=http://keycloak:8080/realms/hrms
KC_BASE=http://keycloak:8080
PASS=0
FAIL=0

token() {
  curl -s -X POST "$KC/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    -d "client_id=hrms-spa&grant_type=password&username=$1&password=$2&scope=openid" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
}

req() { # method token path [json]
  local method=$1 tok=$2 path=$3 data=$4
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
NAME='Nguyễn Trung Hiếu'
[ -n "$HR" ] || { echo "FAIL  lay token HR"; exit 1; }

# admin token (kiem tra enabled trong Keycloak) — credential lấy từ env
# (devcontainer nạp .env; fail-fast nếu thiếu — không còn default trong git)
ADMIN=$(curl -s -X POST "$KC_BASE/realms/master/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d "grant_type=password&client_id=admin-cli&username=${KEYCLOAK_ADMIN:?KEYCLOAK_ADMIN chưa set - nạp .env}&password=${KEYCLOAK_ADMIN_PASSWORD:?KEYCLOAK_ADMIN_PASSWORD chưa set - nạp .env}" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
kc_user() { # username -> json user
  curl -s -H "Authorization: Bearer $ADMIN" \
    "$KC_BASE/admin/realms/hrms/users?username=$1&exact=true"
}

echo "== A. Auto sinh ma tu ho ten"
R=$(req POST "$HR" /api/v1/employees "{\"employeeCode\":\"\",\"fullName\":\"$NAME\"}")
check "POST tao NV khong truyen ma" 201 "$(code_of "$R")"
CODE1=$(fld "$R" employeeCode); ID1=$(fld "$R" id)
echo "code1=$CODE1 id1=$ID1"
case "$CODE1" in nthieu*) ck "ma1 bat dau 'nthieu'" yes yes;; *) ck "ma1 bat dau 'nthieu'" "$CODE1" "nthieu*";; esac

echo "== B. Dang nhap bang ma sinh ra + mat khau mac dinh"
T=$(token "$CODE1" 'hrms@123')
ck "login ma NV + hrms@123" "$([ -n "$T" ] && echo yes || echo no)" yes

echo "== C. Trung ho ten -> them hau to"
R=$(req POST "$HR" /api/v1/employees "{\"employeeCode\":\"\",\"fullName\":\"$NAME\"}")
check "POST trung ho ten" 201 "$(code_of "$R")"
CODE2=$(fld "$R" employeeCode); ID2=$(fld "$R" id)
echo "code2=$CODE2 id2=$ID2"
if [ -n "$CODE2" ] && [ "$CODE2" != "$CODE1" ]; then ck "ma2 khac ma1" yes yes
else ck "ma2 khac ma1" "$CODE2" "$CODE1+suffix"; fi
T2=$(token "$CODE2" 'hrms@123')
ck "login code2" "$([ -n "$T2" ] && echo yes || echo no)" yes

echo "== D. Nhap ma da co (NVIT001) -> 409"
R=$(req POST "$HR" /api/v1/employees '{"employeeCode":"NVIT001","fullName":"Trung Ma Test"}')
check "POST trung ma NVIT001" 409 "$(code_of "$R")"

echo "== E. Nghi viec -> disable user Keycloak + luu ly do vao history"
R=$(req DELETE "$HR" "/api/v1/employees/$ID1?reason=T%E1%BB%B1%20nguy%E1%BB%87n%20chuy%E1%BB%83n%20c%C3%B4ng%20ty")
check "DELETE nghi viec NV1" 204 "$(code_of "$R")"
T=$(token "$CODE1" 'hrms@123')
ck "login sau nghi viec bi chan" "$([ -z "$T" ] && echo yes || echo no)" yes
CK=$(kc_user "$CODE1")
ck "user KC truong enabled=false" "$(has "$CK" '"enabled":false')" yes
CK2=$(kc_user "$CODE2")
ck "user KC2 van enabled (chua nghi)" "$(has "$CK2" '"enabled":true')" yes
H=$(req GET "$HR" "/api/v1/employees/$ID1/history")
ck "GET history NV1 sau nghi viec (HR)" 200 "$(code_of "$H")"
case "$H" in *"Tự nguyện chuyển công ty"*) ck "history co ly do nghi viec" yes yes;; *) ck "history co ly do nghi viec" "${H:0:120}" "Tự nguyện chuyển công ty";; esac
case "$H" in *'"field":"status"'*) ck "history co muc status=TERMINATED" yes yes;; *) ck "history co muc status=TERMINATED" "${H:0:120}" 'field=status';; esac

echo "== F. Cleanup"
R=$(req DELETE "$HR" "/api/v1/employees/$ID2")
check "DELETE nghi viec NV2" 204 "$(code_of "$R")"
T2=$(token "$CODE2" 'hrms@123')
ck "login NV2 bi chan" "$([ -z "$T2" ] && echo yes || echo no)" yes

echo "== RESULT: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = "0" ]
