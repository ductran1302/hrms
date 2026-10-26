#!/bin/bash
# Verify identity-service — Bước 9 (identity-core):
# auth/RBAC + departments + contracts (salary gating) + history + org chart + search
# Idempotent: dùng mã ngẫu nhiên mỗi lần chạy. Chạy trong hrms-dev:
#   docker exec hrms-dev bash /workspaces/HRMS/scripts/verify-identity.sh
# API/KC override được qua env (mặc định giữ nguyên như cũ).
API=${API:-http://localhost:8183}
KC=${KC:-http://keycloak:8080/realms/hrms}
PASS=0
FAIL=0

token() {
  curl -s -X POST "$KC/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    -d "client_id=hrms-spa&grant_type=password&username=$1&password=$2&scope=openid" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
}

HR=$(token hr 'hr@123')
EMP=$(token employee1 'emp@123')
MGR=$(token manager1 'manager@123')
echo "tokens: hr=${#HR} emp=${#EMP} mgr=${#MGR}"

req() { # method token path [data] -> body + "\nHTTP <code>"
  local method=$1 tok=$2 path=$3 data=$4
  if [ -n "$data" ]; then
    curl -s -X "$method" -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' \
      -d "$data" -w '\nHTTP %{http_code}\n' "$API$path"
  else
    curl -s -X "$method" -H "Authorization: Bearer $tok" -w '\nHTTP %{http_code}\n' "$API$path"
  fi
}

code_of() { echo "$1" | tail -1 | sed 's/HTTP //'; }
id_of() { echo "$1" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -1; }

# NV mới có mật khẩu mặc định temporary (buộc đổi lần đầu) -> password-grant bị chặn.
# HR reset sang mật khẩu biết trước (temporary=false) để test các API self-service.
emp_login() { # code -> token
  local code=$1 pw="Smoke@12345"
  req PATCH "$HR" "/api/v1/accounts/$code/password" "{\"newPassword\":\"$pw\"}" >/dev/null
  token "$code" "$pw"
}
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
DEPT_CODE="D$SUF"
EMP_CODE="NV$SUF"

echo "== 0. P0-5: audience (aud) cua JWT"
jwt_aud() { # token -> gia tri aud (array hoac chuoi)
  local p pad
  p=$(echo "$1" | cut -d. -f2 | tr '_-' '/+')
  pad=$(( (4 - ${#p} % 4) % 4 ))
  [ "$pad" -gt 0 ] && p="$p$(printf '=%.0s' $(seq 1 "$pad"))"
  echo "$p" | base64 -d 2>/dev/null | sed -n 's/.*"aud":\[\?\([^]]*\)\]\?.*/\1/p'
}
check_has "token hr co aud hrms-api" "$(has "$(jwt_aud "$HR")" 'hrms-api')" yes
if [ -n "${HRMS_IDENTITY_CLIENT_SECRET:-}" ]; then
  # Token service-account (client_credentials, KHÔNG qua audience mapper) -> bị 401
  SVC_TOKEN=$(curl -s -X POST "$KC/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    -d "client_id=hrms-identity&client_secret=$HRMS_IDENTITY_CLIENT_SECRET&grant_type=client_credentials" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
  if [ -n "$SVC_TOKEN" ]; then
    R=$(req GET "$SVC_TOKEN" /api/v1/employees)
    check "token khong aud bi tu choi" 401 "$(code_of "$R")"
  else
    check "lay token service-account" ok fail
  fi
fi

echo "== 1. RBAC: danh sach nhan vien (PageResponse)"
R=$(req GET "" /api/v1/employees);               check "GET list khong token" 401 "$(code_of "$R")"
R=$(req GET "$EMP" /api/v1/employees);           check "GET list employee1" 403 "$(code_of "$R")"
R=$(req GET "$MGR" "/api/v1/employees?size=5");  check "GET list manager1" 200 "$(code_of "$R")"
check_has "PageResponse co items/total" "$(has "$R" '"items"')" yes
# P1 scoping: manager1 khong co ho so nhan vien -> fail-closed, khong thay ai
check_has "MANAGER khong gan ho so NV -> total=0" "$(has "$R" '"total":0')" yes

echo "== 2. Departments (CRUD + RBAC)"
R=$(req POST "$HR" /api/v1/departments "{\"code\":\"$DEPT_CODE\",\"name\":\"Phòng QA $SUF\"}")
check "POST tao phong ban" 201 "$(code_of "$R")"
DEPT_ID=$(id_of "$R"); echo "departmentId=$DEPT_ID"
R=$(req POST "$HR" /api/v1/departments "{\"code\":\"$DEPT_CODE\",\"name\":\"Trung\"}")
check "POST trung ma phong" 409 "$(code_of "$R")"
R=$(req POST "$MGR" /api/v1/departments '{"code":"XX1","name":"X"}')
check "POST phong boi MANAGER" 403 "$(code_of "$R")"
R=$(req GET "$HR" /api/v1/departments);          check "GET departments" 200 "$(code_of "$R")"
R=$(req GET "$MGR" /api/v1/departments/tree);    check "GET departments tree" 200 "$(code_of "$R")"

echo "== 3. Nhan vien + gan phong ban + search"
R=$(req POST "$HR" /api/v1/employees "{\"employeeCode\":\"$EMP_CODE\",\"fullName\":\"NV $SUF\",\"email\":\"$SUF@hrms.vn\",\"departmentId\":\"$DEPT_ID\",\"position\":\"Tester\",\"office\":\"Tòa nhà A - Tầng 3\",\"hiredAt\":\"2026-01-05T00:00:00Z\",\"gender\":\"FEMALE\",\"birthDate\":\"1995-04-02\",\"phone\":\"0900000001\",\"nationalId\":\"012345678901\"}")
check "POST tao nhan vien (co departmentId + office)" 201 "$(code_of "$R")"
EMP_ID=$(id_of "$R"); echo "employeeId=$EMP_ID"
check_has "resolve ten phong ban" "$(has "$R" "Phòng QA $SUF")" yes
check_has "luu office" "$(has "$R" '"office":"Tòa nhà A - Tầng 3"')" yes
R=$(req GET "$HR" "/api/v1/employees?q=$EMP_CODE")
check "GET search q=ma NV" 200 "$(code_of "$R")"
check_has "search tim thay trong items" "$(has "$R" "$EMP_CODE")" yes
R=$(req GET "$HR" "/api/v1/employees?departmentId=$DEPT_ID")
check "GET search theo departmentId" 200 "$(code_of "$R")"
R=$(req GET "$HR" "/api/v1/employees?office=T%C3%B2a%20nh%C3%A0%20A%20-%20T%E1%BA%A7ng%203")
check "GET search theo office" 200 "$(code_of "$R")"
check_has "tim thay theo office" "$(has "$R" "$EMP_CODE")" yes
R=$(req GET "$HR" "/api/v1/employees?office=Khong%20Ton%20Tai")
check_has "office khong ton tai -> khong co NV" "$(has "$R" "$EMP_CODE")" no
R=$(req POST "$HR" /api/v1/employees "{\"employeeCode\":\"$EMP_CODE\",\"fullName\":\"Trùng Mã\"}")
check "POST trung ma" 409 "$(code_of "$R")"

echo "== 3b. P1 scoping: MANAGER ngoai pham vi -> 403"
R=$(req GET "$MGR" "/api/v1/employees/$EMP_ID")
check "GET chi tiet NV ngoai pham vi boi MANAGER" 403 "$(code_of "$R")"
R=$(req GET "$MGR" "/api/v1/employees/$EMP_ID/history")
check "GET history ngoai pham vi boi MANAGER" 403 "$(code_of "$R")"

echo "== 4. PATCH nang cao (history + clear)"
R=$(req PATCH "$HR" "/api/v1/employees/$EMP_ID" '{"position":"Senior Tester"}')
check "PATCH doi vi tri" 200 "$(code_of "$R")"
R=$(req PATCH "$HR" "/api/v1/employees/$EMP_ID" '{"office":"Tòa nhà B - Tầng 1"}')
check "PATCH doi office" 200 "$(code_of "$R")"
check_has "office da doi" "$(has "$R" '"office":"Tòa nhà B - Tầng 1"')" yes
R=$(req PATCH "$HR" "/api/v1/employees/$EMP_ID" '{"status":"TERMINATED"}')
check "PATCH status=TERMINATED" 422 "$(code_of "$R")"
R=$(req PATCH "$HR" "/api/v1/employees/$EMP_ID" '{"clearDepartment":true}')
check "PATCH clearDepartment" 200 "$(code_of "$R")"
check_has "da gỡ phong ban" "$(has "$R" "\"departmentId\":null")" yes
R=$(req PATCH "$HR" "/api/v1/employees/$EMP_ID" "{\"departmentId\":\"$DEPT_ID\"}")
check "PATCH gan lai phong ban" 200 "$(code_of "$R")"

echo "== 5. Contracts (1 hop dong ACTIVE + salary gating)"
R=$(req POST "$HR" "/api/v1/employees/$EMP_ID/contracts" '{"contractType":"FULL_TIME","startDate":"2026-01-05","endDate":"2028-01-04","position":"Tester","salary":15000000,"signDate":"2026-01-04"}')
check "POST tao hop dong" 201 "$(code_of "$R")"
CONTRACT_ID=$(id_of "$R"); echo "contractId=$CONTRACT_ID"
R=$(req POST "$HR" "/api/v1/employees/$EMP_ID/contracts" '{"contractType":"PROBATION","startDate":"2026-02-01","salary":5000000}')
check "POST hop dong ACTIVE thu 2" 409 "$(code_of "$R")"
R=$(req POST "$MGR" "/api/v1/employees/$EMP_ID/contracts" '{"contractType":"INTERN","startDate":"2026-02-01"}')
check "POST hop dong boi MANAGER" 403 "$(code_of "$R")"
R=$(req GET "$HR" "/api/v1/contracts?employeeId=$EMP_ID")
check "GET contracts (HR)" 200 "$(code_of "$R")"
check_has "HR thay salary" "$(has "$R" '"salary":15000000')" yes
  R=$(req GET "$MGR" "/api/v1/contracts?employeeId=$EMP_ID")
  check "GET contracts (MANAGER)" 200 "$(code_of "$R")"
  # manager1 seed khong gan ho so NV -> scope fail-closed: khong thay hop dong/luong nao.
  check_has "MANAGER khong thay salary" "$(has "$R" '15000000')" no
R=$(req PATCH "$HR" "/api/v1/contracts/$CONTRACT_ID" '{"salary":16000000}')
check "PATCH doi luong" 200 "$(code_of "$R")"
R=$(req PATCH "$HR" "/api/v1/contracts/$CONTRACT_ID/terminate" '')
check "PATCH cham dut hop dong" 200 "$(code_of "$R")"

echo "== 6. Job history"
R=$(req GET "$HR" "/api/v1/employees/$EMP_ID/history")
check "GET history (HR)" 200 "$(code_of "$R")"
check_has "co muc position" "$(has "$R" '"field":"position"')" yes
check_has "co muc department" "$(has "$R" '"field":"department"')" yes
R=$(req GET "$EMP" "/api/v1/employees/$EMP_ID/history")
check "GET history (EMPLOYEE)" 403 "$(code_of "$R")"

echo "== 7. Org chart"
R=$(req GET "$MGR" /api/v1/org-chart)
check "GET org-chart (MANAGER)" 200 "$(code_of "$R")"
check_has "co root/nhan vien" "$(has "$R" "$EMP_CODE")" yes
R=$(req GET "$EMP" /api/v1/org-chart)
check "GET org-chart (EMPLOYEE)" 403 "$(code_of "$R")"
R=$(req GET "$MGR" /api/v1/org-chart/stats)
check "GET org-chart/stats (MANAGER)" 200 "$(code_of "$R")"
check_has "stats co totalEmployees" "$(has "$R" '"totalEmployees"')" yes
check_has "stats co totalDepartments" "$(has "$R" '"totalDepartments"')" yes
check_has "stats co employeeDelta" "$(has "$R" '"employeeDelta"')" yes
check_has "stats co departmentDelta" "$(has "$R" '"departmentDelta"')" yes
check_has "stats co managerDelta" "$(has "$R" '"managerDelta"')" yes
R=$(req GET "$EMP" /api/v1/org-chart/stats)
check "GET org-chart/stats (EMPLOYEE)" 403 "$(code_of "$R")"

echo "== 8. Bo sung Nhom A (position / ON_LEAVE / supplements / tai lieu)"
R=$(req GET "$HR" "/api/v1/employees?position=Senior%20Tester")
check "GET search theo position" 200 "$(code_of "$R")"
check_has "tim thay nhan vien theo vi tri" "$(has "$R" "$EMP_CODE")" yes
R=$(req PATCH "$HR" "/api/v1/employees/$EMP_ID" '{"status":"ON_LEAVE"}')
check "PATCH status=ON_LEAVE" 200 "$(code_of "$R")"
check_has "trang thai ON_LEAVE" "$(has "$R" '"status":"ON_LEAVE"')" yes
R=$(req PATCH "$HR" "/api/v1/employees/$EMP_ID" '{"educationLevel":"Cao đẳng","skills":"SQL, Postman","emergencyContactName":"Nguyễn Văn B","emergencyContactPhone":"0900000002","emergencyContactRelation":"Vợ"}')
check "PATCH thong tin bo sung" 200 "$(code_of "$R")"
check_has "da luu educationLevel" "$(has "$R" '"educationLevel":"Cao đẳng"')" yes
check_has "da luu emergencyContactPhone" "$(has "$R" '"emergencyContactPhone":"0900000002"')" yes

printf 'hrms verify document' > /tmp/vdoc.txt
CODE=$(curl -s -o /tmp/up.json -w '%{http_code}' -X POST -H "Authorization: Bearer $HR" \
  -F 'file=@/tmp/vdoc.txt;type=text/plain' -F 'docType=CERTIFICATE' -F 'note=Bang cap' \
  "$API/api/v1/employees/$EMP_ID/documents")
check "POST upload tai lieu (HR)" 201 "$CODE"
check_has "upload co fileName" "$(has "$(cat /tmp/up.json)" 'vdoc.txt')" yes
DOC_ID=$(id_of "$(cat /tmp/up.json)"); echo "documentId=$DOC_ID"
CODE=$(curl -s -o /tmp/list.json -w '%{http_code}' -H "Authorization: Bearer $HR" \
  "$API/api/v1/employees/$EMP_ID/documents")
check "GET list tai lieu (HR)" 200 "$CODE"
check_has "list co tai lieu" "$(has "$(cat /tmp/list.json)" 'vdoc.txt')" yes
CODE=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $MGR" \
  "$API/api/v1/employees/$EMP_ID/documents")
# P0-3: MANAGER ngoai pham vi (manager1 khong quan ly NV nay) -> 403, chong IDOR
check "GET list tai lieu (MANAGER ngoai pham vi)" 403 "$CODE"
CODE=$(curl -s -o /tmp/dl.bin -w '%{http_code}' -H "Authorization: Bearer $HR" \
  "$API/api/v1/employees/$EMP_ID/documents/$DOC_ID")
check "GET download tai lieu (HR)" 200 "$CODE"
check_has "download noi dung dung" "$(has "$(cat /tmp/dl.bin)" 'hrms verify document')" yes
CODE=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $EMP" \
  "$API/api/v1/employees/$EMP_ID/documents/$DOC_ID")
check "GET download (EMPLOYEE)" 403 "$CODE"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $EMP" \
  -F 'file=@/tmp/vdoc.txt;type=text/plain' "$API/api/v1/employees/$EMP_ID/documents")
check "POST upload boi EMPLOYEE" 403 "$CODE"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $HR" \
  -F 'file=@/tmp/vdoc.txt;type=text/plain' -F 'docType=SAI_LOAI' \
  "$API/api/v1/employees/$EMP_ID/documents")
check "POST upload docType khong hop le" 422 "$CODE"
# P0: validate noi dung server-side — file .pdf gia mao (noi dung HTML) va file thuc thi
printf '<html>not a pdf</html>' > /tmp/evil.pdf
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $HR" \
  -F 'file=@/tmp/evil.pdf;type=application/pdf' "$API/api/v1/employees/$EMP_ID/documents")
check "POST upload .pdf gia mao (noi dung sai)" 422 "$CODE"
printf 'MZ' > /tmp/evil.exe
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $HR" \
  -F 'file=@/tmp/evil.exe;type=application/octet-stream' "$API/api/v1/employees/$EMP_ID/documents")
check "POST upload .exe" 422 "$CODE"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE -H "Authorization: Bearer $EMP" \
  "$API/api/v1/employees/$EMP_ID/documents/$DOC_ID")
check "DELETE tai lieu boi EMPLOYEE" 403 "$CODE"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE -H "Authorization: Bearer $HR" \
  "$API/api/v1/employees/$EMP_ID/documents/$DOC_ID")
check "DELETE tai lieu (HR)" 204 "$CODE"

echo "== 9. Cleanup"
R=$(req DELETE "$HR" "/api/v1/employees/$EMP_ID?reason=T%E1%BB%B1%20nguy%E1%BB%8Dn")
check "DELETE nghi viec" 204 "$(code_of "$R")"
R=$(req DELETE "$HR" "/api/v1/employees/$EMP_ID")
check "DELETE lan 2" 422 "$(code_of "$R")"
R=$(req DELETE "$HR" "/api/v1/departments/$DEPT_ID")
check "DELETE phong ban" 204 "$(code_of "$R")"

echo "== 10. Danh muc chuc vu + cay to chuc seed"
R=$(req GET "$HR" /api/v1/job-titles)
check "GET job-titles (HR)" 200 "$(code_of "$R")"
check_has "co chuc vu CTO" "$(has "$R" '"code":"CTO"')" yes
check_has "co chuc vu SENIOR_SWE" "$(has "$R" '"code":"SENIOR_SWE"')" yes
R=$(req GET "$MGR" /api/v1/job-titles)
check "GET job-titles (MANAGER)" 200 "$(code_of "$R")"
R=$(req GET "$EMP" /api/v1/job-titles)
check "GET job-titles (EMPLOYEE)" 403 "$(code_of "$R")"
R=$(req GET "$HR" "/api/v1/departments/tree")
check_has "cay to chuc co ENG-DEV-BE" "$(has "$R" '"code":"ENG-DEV-BE"')" yes
check_has "cay to chuc co ORG goc" "$(has "$R" '"code":"ORG"')" yes
R=$(req GET "$MGR" "/api/v1/org-chart")
check_has "org-chart co Chairman (root)" "$(has "$R" '"fullName":"Nguyễn Hoàng Minh"')" yes
check_has "org-chart co CTO" "$(has "$R" '"fullName":"Hoàng Đức Trí"')" yes

echo "== 11. Hop dong: PENDING / gia han / thay the / phu luc / canh bao het han"
R=$(req POST "$HR" "/api/v1/employees" "{\"employeeCode\":\"nvhd1$$\",\"fullName\":\"NV Hop Dong 1\"}")
check "POST tao NV hd1" 201 "$(code_of "$R")"
EMP_HD=$(id_of "$R")
R=$(req POST "$HR" "/api/v1/employees" "{\"employeeCode\":\"nvhd2$$\",\"fullName\":\"NV Hop Dong 2\"}")
EMP_HD2=$(id_of "$R")
# a. startDate tuong lai -> PENDING (chu hieu luc) — tach trang thai nghiep vu hieu luc theo ngay
R=$(req POST "$HR" "/api/v1/employees/$EMP_HD/contracts" '{"contractType":"FULL_TIME","startDate":"2030-01-01","endDate":"2031-12-31","salary":10000000}')
check "POST HĐ startDate tương lai" 201 "$(code_of "$R")"
HD1=$(id_of "$R"); echo "HD1=$HD1"
check_has "trang thai nghiep vu PENDING" "$(has "$R" '"status":"PENDING"')" yes
check_has "hieu luc theo ngay NOT_YET_EFFECTIVE" "$(has "$R" '"effectiveStatus":"NOT_YET_EFFECTIVE"')" yes
# b. hop dong thu 2 cung nhan vien -> 409 (chan chong lan)
R=$(req POST "$HR" "/api/v1/employees/$EMP_HD/contracts" '{"contractType":"PROBATION","startDate":"2031-03-01"}')
check "POST HĐ thứ 2 (chồng lấn)" 409 "$(code_of "$R")"
# c. gia han
R=$(req POST "$HR" "/api/v1/contracts/$HD1/extend" '{"endDate":"2032-12-31","note":"Gia hạn theo thỏa thuận"}')
check "POST gia hạn" 200 "$(code_of "$R")"
check_has "endDate sau gia hạn" "$(has "$R" '"endDate":"2032-12-31"')" yes
R=$(req POST "$HR" "/api/v1/contracts/$HD1/extend" '{"endDate":"2031-06-01"}')
check "POST gia hạn endDate nhỏ hơn hiện tại" 422 "$(code_of "$R")"
R=$(req GET "$HR" "/api/v1/employees/$EMP_HD/history")
check_has "history ghi contract.endDate" "$(has "$R" '"field":"contract.endDate"')" yes
# d. thay the: cham dut cu (giu lich su) + tao moi linked
R=$(req POST "$HR" "/api/v1/contracts/$HD1/replace" '{"contractType":"FULL_TIME","startDate":"2033-01-01","endDate":"2034-12-31"}')
check "POST thay thế HĐ" 201 "$(code_of "$R")"
HD2=$(id_of "$R")
case "$R" in *"replacesContractId\":\"$HD1"*) check "HĐ mới liên kết thay thế" yes yes;; *) check "HĐ mới liên kết thay thế" "${R:0:80}" "replacesContractId=$HD1";; esac
check_has "HĐ mới PENDING (tương lai)" "$(has "$R" '"status":"PENDING"')" yes
R=$(req GET "$HR" "/api/v1/contracts?employeeId=$EMP_HD")
check_has "HĐ cũ còn nguyên (giữ lịch sử, TERMINATED)" "$(has "$R" '"status":"TERMINATED"')" yes
# e. phu luc hop dong: upload kem contractId + loc theo hop dong
printf 'hrms contract file' > /tmp/vc.txt
CODE=$(curl -s -o /tmp/vc.json -w '%{http_code}' -X POST -H "Authorization: Bearer $HR" \
  -F 'file=@/tmp/vc.txt;type=text/plain' -F 'contractId='"$HD2" \
  "$API/api/v1/employees/$EMP_HD/documents")
check "POST upload phụ lục HĐ" 201 "$CODE"
case "$(cat /tmp/vc.json)" in *"contractId\":\"$HD2"*) check "phụ lục gắn contractId" yes yes;; *) check "phụ lục gắn contractId" "$(head -c 120 /tmp/vc.json)" "contractId=$HD2";; esac
CODE=$(curl -s -o /tmp/vcl.json -w '%{http_code}' -H "Authorization: Bearer $HR" \
  "$API/api/v1/employees/$EMP_HD/documents?contractId=$HD2")
check "GET list theo contractId" 200 "$CODE"
check_has "list có phụ lục của HĐ" "$(has "$(cat /tmp/vcl.json)" 'vc.txt')" yes
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $HR" \
  -F 'file=@/tmp/vc.txt;type=text/plain' -F 'contractId='"$HD2" \
  "$API/api/v1/employees/$EMP_HD2/documents")
check "upload phụ lục HĐ sai nhân viên" 422 "$CODE"
# f. ngay ket thuc trong hop dong khong bat buoc (hop dong xac dinh han)
R=$(req POST "$HR" "/api/v1/employees/$EMP_HD2/contracts" '{"contractType":"FULL_TIME","startDate":"2026-01-01"}')
check "POST HĐ không giới hạn ngày hết hạn" 201 "$(code_of "$R")"
check_has "endDate=null được chấp nhận" "$(has "$R" '"endDate":null')" yes
HD3=$(id_of "$R")
# g. canh bao het han: HĐ ACTIVE keo den trong 30 ngay
R=$(req PATCH "$HR" "/api/v1/contracts/$HD3/terminate" '')
check "terminate HĐ không giới hạn" 200 "$(code_of "$R")"
R=$(req POST "$HR" "/api/v1/employees/$EMP_HD2/contracts" '{"contractType":"FULL_TIME","startDate":"2026-01-01","endDate":"2026-10-28"}')
HD4=$(id_of "$R"); echo "HD4=$HD4"
check_has "HĐ gần hạn IN_EFFECT" "$(has "$R" '"effectiveStatus":"IN_EFFECT"')" yes
R=$(req GET "$HR" "/api/v1/contracts/expiring-soon?days=30")
check "GET expiring-soon (HR)" 200 "$(code_of "$R")"
check_has "expiring-soon có HĐ sắp hết hạn" "$(has "$R" "$HD4")" yes
R=$(req GET "$EMP" "/api/v1/contracts/expiring-soon")
check "GET expiring-soon (EMPLOYEE)" 403 "$(code_of "$R")"
# h. chấm dứt có lý do lưu vào history
R=$(req PATCH "$HR" "/api/v1/contracts/$HD4/terminate" '')
check "terminate HĐ sắp hạn" 200 "$(code_of "$R")"
R=$(req GET "$HR" "/api/v1/employees/$EMP_HD2/history")
check_has "history HĐ có mục contract.status" "$(has "$R" '"field":"contract.status"')" yes
# i. cleanup
R=$(req DELETE "$HR" "/api/v1/employees/$EMP_HD?reason=cleanup")
check "cleanup NV hd1" 204 "$(code_of "$R")"
R=$(req DELETE "$HR" "/api/v1/employees/$EMP_HD2?reason=cleanup")
check "cleanup NV hd2" 204 "$(code_of "$R")"

echo "== 12. NGAY LE (holiday calendars + holidays + assign + attendance projection)"
R=$(req GET "$HR" "/api/v1/holiday-calendars")
check "GET holiday-calendars (HR)" 200 "$(code_of "$R")"
check_has "co lich VN (seed)" "$(has "$R" '"code":"VN"')" yes
R=$(req GET "$EMP" "/api/v1/holiday-calendars")
check "GET holiday-calendars (EMPLOYEE)" 200 "$(code_of "$R")"
# a. CRUD lich
CAL_CODE="LC$SUF"
R=$(req POST "$HR" "/api/v1/holiday-calendars" "{\"code\":\"$CAL_CODE\",\"name\":\"Lich verify $SUF\",\"country\":\"VN\",\"description\":\"verify section 12\"}")
check "POST tao lich" 201 "$(code_of "$R")"
CAL=$(id_of "$R"); echo "holidayCalendarId=$CAL"
R=$(req POST "$HR" "/api/v1/holiday-calendars" "{\"code\":\"$CAL_CODE\",\"name\":\"Trung ma\",\"country\":\"VN\"}")
check "POST lich trung code" 409 "$(code_of "$R")"
R=$(req PATCH "$HR" "/api/v1/holiday-calendars/$CAL" "{\"name\":\"Lich verify $SUF renamed\"}")
check "PATCH doi ten lich" 200 "$(code_of "$R")"
check_has "ten lich da doi" "$(has "$R" 'renamed')" yes
# b. CRUD ngay le
HOL_NAME="Le verify $SUF"
R=$(req POST "$HR" "/api/v1/holiday-calendars/$CAL/holidays" "{\"name\":\"$HOL_NAME\",\"dateFrom\":\"2026-11-02\",\"dateTo\":\"2026-11-04\",\"type\":\"COMPANY\",\"halfDay\":true,\"note\":\"nhieu ngay\"}")
check "POST tao ngay le (nhieu ngay + halfDay)" 201 "$(code_of "$R")"
HOL=$(id_of "$R")
R=$(req GET "$HR" "/api/v1/holiday-calendars/$CAL/holidays?year=2026")
check "GET holidays theo lich + nam" 200 "$(code_of "$R")"
check_has "danh sach co ngay le verify" "$(has "$R" "$HOL_NAME")" yes
R=$(req GET "$HR" "/api/v1/holidays?calendarCode=$CAL_CODE&year=2026")
check "GET /holidays tim kiem" 200 "$(code_of "$R")"
check_has "search tim thay" "$(has "$R" "$HOL_NAME")" yes
R=$(req POST "$HR" "/api/v1/holiday-calendars/$CAL/holidays" "{\"name\":\"$HOL_NAME\",\"dateFrom\":\"2026-11-02\",\"dateTo\":\"2026-11-02\",\"type\":\"COMPANY\"}")
check "POST ngay le trung ten + ngay bat dau" 409 "$(code_of "$R")"
R=$(req POST "$HR" "/api/v1/holiday-calendars/$CAL/holidays" '{"name":"Sai nam","dateFrom":"2026-12-30","dateTo":"2027-01-02","type":"OTHER"}')
check "POST ngay le khac nam" 422 "$(code_of "$R")"
R=$(req PATCH "$HR" "/api/v1/holidays/$HOL" '{"halfDay":false,"note":"sua note"}')
check "PATCH sua ngay le" 200 "$(code_of "$R")"
check_has "halfDay=false sau PATCH" "$(has "$R" '"halfDay":false')" yes
# c. my-holidays fallback VN + today
R=$(req GET "$HR" "/api/v1/holidays/my?year=2017")
check "GET my-holidays (HR, nam khong co le)" 200 "$(code_of "$R")"
R=$(req GET "$HR" "/api/v1/holidays/my?year=2026")
check "GET my-holidays (HR)" 200 "$(code_of "$R")"
check_has "fallback lich VN (calendarCode=VN)" "$(has "$R" '"calendarCode":"VN"')" yes
R=$(req GET "$HR" "/api/v1/holidays/today")
check "GET holidays/today" 200 "$(code_of "$R")"
# d. RBAC: EMPLOYEE khong duoc ghi
R=$(req POST "$EMP" "/api/v1/holiday-calendars" '{"code":"XXE","name":"x","country":"VN"}')
check "POST lich (EMPLOYEE)" 403 "$(code_of "$R")"
R=$(req POST "$EMP" "/api/v1/holiday-calendars/$CAL/holidays" '{"name":"x","dateFrom":"2026-11-03","dateTo":"2026-11-03","type":"OTHER"}')
check "POST ngay le (EMPLOYEE)" 403 "$(code_of "$R")"
# e. xoa lich khi con ngay le bi chan
R=$(req DELETE "$HR" "/api/v1/holiday-calendars/$CAL")
check "DELETE lich con ngay le" 409 "$(code_of "$R")"
# f. gan lich cho NV + my-holidays dung lich (nv2xx — khong trung Keycloak user NVxx)
LE_CODE="nv2$SUF"
R=$(req POST "$HR" "/api/v1/employees" "{\"employeeCode\":\"$LE_CODE\",\"fullName\":\"NV Le $SUF\",\"email\":\"le$SUF@hrms.vn\"}")
check "POST tao NV cho verify ngay le" 201 "$(code_of "$R")"
LE_ID=$(id_of "$R")
R=$(req PATCH "$HR" "/api/v1/employees/$LE_ID/calendar" "{\"calendarId\":\"$CAL\"}")
check "PATCH gan lich nghi le" 200 "$(code_of "$R")"
check_has "response co calendarId" "$(has "$R" "$CAL")" yes
R=$(req GET "$HR" "/api/v1/employees/$LE_ID")
check_has "GET NV co calendarId" "$(has "$R" "$CAL")" yes
LE_TOK=$(emp_login "$LE_CODE")
check "token NV moi" 0 "$([ -n "$LE_TOK" ] && echo 0 || echo missing)"
R=$(req GET "$LE_TOK" "/api/v1/holidays/my?year=2026")
check "GET my-holidays (NV gan lich)" 200 "$(code_of "$R")"
check_has "my-holidays dung lich da gan" "$(has "$R" "$HOL_NAME")" yes
# g. attendance projection nhan event holiday.changed (outbox poll ~1-2s)
# NOTE: verify chạy trong hrms-dev — localhost:8183 là dev identity (không qua gateway);
# goi attendance service truc tiep qua DNS container.
ATT=http://attendance:8080
sleep 3
R=$(curl -s -H "Authorization: Bearer $HR" "$ATT/api/v1/attendance/holidays?calendarCode=$CAL_CODE&year=2026" -w '\nHTTP %{http_code}\n')
check "GET attendance holidays (projection)" 200 "$(code_of "$R")"
check_has "attendance co ngay le verify" "$(has "$R" "$HOL_NAME")" yes
# h. cleanup: xoa ngay le -> xoa lich -> bo gán -> xoa NV
R=$(req DELETE "$HR" "/api/v1/holidays/$HOL")
check "DELETE ngay le" 204 "$(code_of "$R")"
sleep 3
R=$(curl -s -H "Authorization: Bearer $HR" "$ATT/api/v1/attendance/holidays?calendarCode=$CAL_CODE&year=2026" -w '\nHTTP %{http_code}\n')
check_has "attendance xoa sau DELETED event" "$(has "$R" "$HOL_NAME")" no
R=$(req PATCH "$HR" "/api/v1/employees/$LE_ID/calendar" '{"calendarId":null}')
check "PATCH bo gan lich (null)" 200 "$(code_of "$R")"
R=$(req DELETE "$HR" "/api/v1/holiday-calendars/$CAL")
check "DELETE lich trong" 204 "$(code_of "$R")"
R=$(req DELETE "$HR" "/api/v1/employees/$LE_ID?reason=cleanup")
check "cleanup NV ngay le" 204 "$(code_of "$R")"

echo "== 13. Quy phep (attendance leave quota — versioning 6 states, snapshot policy_id)"
ATT=http://attendance:8080
areq() { # method token path [data] — goi attendance service truc tiep (khong qua gateway)
  local method=$1 tok=$2 path=$3 data=$4
  if [ -n "$data" ]; then
    curl -s -X "$method" -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' \
      -d "$data" -w '\nHTTP %{http_code}\n' "$ATT$path"
  else
    curl -s -X "$method" -H "Authorization: Bearer $tok" -w '\nHTTP %{http_code}\n' "$ATT$path"
  fi
}
YEAR=$(date +%Y)
R=$(areq GET "$HR" "/api/v1/attendance/leave/policies/$YEAR")
check "GET leave policy (self-heal)" 200 "$(code_of "$R")"
check_has "policy co baseDays" "$(has "$R" 'baseDays')" yes
check_has "policy co versionNo + status" "$(has "$R" 'versionNo')" yes
R=$(areq POST "$HR" "/api/v1/attendance/leave/quotas/recalculate?year=$YEAR")
check "POST recalculate leave quota" 200 "$(code_of "$R")"
R=$(areq GET "$HR" "/api/v1/attendance/leave/quotas?year=$YEAR")
check "GET quotas list (HR)" 200 "$(code_of "$R")"
check_has "quotas co NV" "$(has "$R" 'employeeCode')" yes
# NV verify quy phep: username = ma NV (auto-provision, role EMPLOYEE)
QP_CODE="nvq$SUF"
R=$(req POST "$HR" "/api/v1/employees" "{\"employeeCode\":\"$QP_CODE\",\"fullName\":\"NV Quy Phep $SUF\",\"email\":\"qp$SUF@hrms.vn\"}")
check "POST tao NV verify quy phep" 201 "$(code_of "$R")"
QP_ID=$(id_of "$R")
QP_TOK=$(emp_login "$QP_CODE")
check "token NV quy phep" 0 "$([ -n "$QP_TOK" ] && echo 0 || echo missing)"
# cho attendance projection nhan employee.created (outbox poll ~1-2s)
sleep 3
R=$(req GET "$QP_TOK" "/api/v1/employees/me")
check "GET /employees/me (NV auto-provision)" 200 "$(code_of "$R")"
check_has "me dung ma NV" "$(has "$R" "$QP_CODE")" yes
R=$(areq GET "$QP_TOK" "/api/v1/attendance/leave/quotas/$QP_ID?year=$YEAR")
check "GET self quota (EMPLOYEE)" 200 "$(code_of "$R")"
check_has "quota co baseDays" "$(has "$R" 'baseDays')" yes
# recalculate lai khi da co NV -> snapshot policy_id/calculated_at tren dong auto (effective-dated)
R=$(areq POST "$HR" "/api/v1/attendance/leave/quotas/recalculate?year=$YEAR")
check "POST recalculate (da co NV)" 200 "$(code_of "$R")"
R=$(areq GET "$HR" "/api/v1/attendance/leave/quotas/$QP_ID?year=$YEAR")
check_has "auto quota co policyId snapshot" "$(has "$R" '"policyId":"')" yes
# cap tay: thapphan dung chinh xac (0.25 + 0.1 — khong co loi float)
R=$(areq POST "$HR" "/api/v1/attendance/leave/quotas/$QP_ID/entitlements?year=$YEAR" '{"source":"BONUS","days":0.25,"reason":"verify"}')
check "POST grant BONUS 0.25" 201 "$(code_of "$R")"
check_has "BONUS luu 0.2500" "$(has "$R" '0.2500')" yes
BONUS_ID=$(id_of "$R")
R=$(areq POST "$HR" "/api/v1/attendance/leave/quotas/$QP_ID/entitlements?year=$YEAR" '{"source":"CARRYOVER","days":0.1,"reason":"verify"}')
check "POST grant CARRYOVER 0.1" 201 "$(code_of "$R")"
check_has "CARRYOVER luu 0.1000" "$(has "$R" '0.1000')" yes
# validation
R=$(areq POST "$HR" "/api/v1/attendance/leave/quotas/$QP_ID/entitlements?year=$YEAR" '{"source":"BASE","days":1}')
check "grant BASE (nguon auto) bi tu choi" 422 "$(code_of "$R")"
R=$(areq POST "$HR" "/api/v1/attendance/leave/quotas/$QP_ID/entitlements?year=$YEAR" '{"source":"BONUS","days":0}')
check "grant 0 ngay bi tu choi" 422 "$(code_of "$R")"
R=$(areq POST "$HR" "/api/v1/attendance/leave/quotas/$QP_ID/entitlements?year=$YEAR" '{"source":"CARRYOVER","days":99}')
check "grant CARRYOVER vuot tran" 422 "$(code_of "$R")"
# RBAC
R=$(areq GET "$EMP" "/api/v1/attendance/leave/quotas?year=$YEAR")
check "EMPLOYEE list quotas bi 403" 403 "$(code_of "$R")"
R=$(areq GET "$EMP" "/api/v1/attendance/leave/policies/$YEAR")
check "EMPLOYEE get policy bi 403" 403 "$(code_of "$R")"
# --- Versioning 6 states (effective-dated) — nam 2099, idempotent qua nhieu lan chay ---
R=$(areq GET "$HR" "/api/v1/attendance/leave/policies/2099")
check "GET policy 2099 (self-heal)" 200 "$(code_of "$R")"
# dọn version DRAFT/PENDING/APPROVED truoc moi cycle (DRAFT xoa, PENDING/APPROVED huy)
R=$(areq GET "$HR" "/api/v1/attendance/leave/policies?year=2099")
check "GET versions ?year=2099" 200 "$(code_of "$R")"
CLEAN_IDS=$(echo "$R" | head -n -1 | jq -r '.[] | select(.status=="DRAFT" or .status=="PENDING_APPROVAL" or .status=="APPROVED") | .id')
for vid in $CLEAN_IDS; do
  ST=$(echo "$R" | head -n -1 | jq -r --arg id "$vid" '.[] | select(.id==$id) | .status')
  if [ "$ST" = "DRAFT" ]; then
    areq DELETE "$HR" "/api/v1/attendance/leave/policies/versions/$vid" >/dev/null
  else
    areq POST "$HR" "/api/v1/attendance/leave/policies/versions/$vid/cancel" >/dev/null
  fi
done
# ngay hieu luc = ngay sau max eff hien co (eff tang dan qua cac lan chay)
R=$(areq GET "$HR" "/api/v1/attendance/leave/policies?year=2099")
MAXEFF=$(echo "$R" | head -n -1 | jq -r '[.[].effectiveFrom] | max')
if [ "$MAXEFF" = "null" ] || [ -z "$MAXEFF" ]; then MAXEFF="2099-01-01"; fi
EFF=$(date -d "$MAXEFF +1 day" +%F)
EFF2=$(date -d "$MAXEFF +2 day" +%F)
EFF3=$(date -d "$MAXEFF +3 day" +%F)
# a. tao V moi -> sua DRAFT -> submit -> approve -> activate -> ACTIVE
R=$(areq POST "$HR" "/api/v1/attendance/leave/policies" "{\"year\":2099,\"baseDays\":14,\"seniorityStepYears\":1,\"seniorityBonusDays\":1,\"seniorityMaxBonus\":5,\"carryoverMax\":5,\"roleQuotas\":[],\"effectiveFrom\":\"$EFF\"}")
check "POST tao version moi" 201 "$(code_of "$R")"
check_has "version moi la DRAFT" "$(has "$R" '"status":"DRAFT"')" yes
V2_ID=$(id_of "$R")
R=$(areq PUT "$HR" "/api/v1/attendance/leave/policies/versions/$V2_ID" "{\"year\":2099,\"baseDays\":15,\"seniorityStepYears\":1,\"seniorityBonusDays\":1,\"seniorityMaxBonus\":5,\"carryoverMax\":5,\"roleQuotas\":[],\"effectiveFrom\":\"$EFF\"}")
check "PUT sua DRAFT" 200 "$(code_of "$R")"
check_has "DRAFT da sua baseDays 15" "$(has "$R" '15.0000')" yes
R=$(areq POST "$HR" "/api/v1/attendance/leave/policies/versions/$V2_ID/submit")
check "submit DRAFT->PENDING" 200 "$(code_of "$R")"
check_has "trang thai PENDING_APPROVAL" "$(has "$R" '"status":"PENDING_APPROVAL"')" yes
R=$(areq POST "$HR" "/api/v1/attendance/leave/policies/versions/$V2_ID/submit")
check "submit khi dang PENDING bi 422" 422 "$(code_of "$R")"
R=$(areq POST "$HR" "/api/v1/attendance/leave/policies/versions/$V2_ID/approve")
check "approve PENDING->APPROVED" 200 "$(code_of "$R")"
check_has "trang thai APPROVED" "$(has "$R" '"status":"APPROVED"')" yes
R=$(areq POST "$HR" "/api/v1/attendance/leave/policies/versions/$V2_ID/approve")
check "approve khi da APPROVED bi 422" 422 "$(code_of "$R")"
R=$(areq POST "$HR" "/api/v1/attendance/leave/policies/versions/$V2_ID/activate")
check "activate APPROVED->ACTIVE" 200 "$(code_of "$R")"
check_has "trang thai ACTIVE" "$(has "$R" '"status":"ACTIVE"')" yes
# ACTIVE khong sua/xoa duoc (tao version moi thay the)
R=$(areq PUT "$HR" "/api/v1/attendance/leave/policies/versions/$V2_ID" "{\"year\":2099,\"baseDays\":16,\"seniorityStepYears\":1,\"seniorityBonusDays\":1,\"seniorityMaxBonus\":5,\"carryoverMax\":5,\"roleQuotas\":[]}")
check "PUT sua version ACTIVE bi 422" 422 "$(code_of "$R")"
R=$(areq DELETE "$HR" "/api/v1/attendance/leave/policies/versions/$V2_ID")
check "DELETE version ACTIVE bi 422" 422 "$(code_of "$R")"
# in-force 2099 = version ACTIVE moi nhat
R=$(areq GET "$HR" "/api/v1/attendance/leave/policies/2099")
check "GET in-force 2099 = ACTIVE" 200 "$(code_of "$R")"
check_has "in-force trang thai ACTIVE" "$(has "$R" '"status":"ACTIVE"')" yes
# b. reject: PENDING -> DRAFT -> xoa duoc
R=$(areq POST "$HR" "/api/v1/attendance/leave/policies" "{\"year\":2099,\"baseDays\":14,\"seniorityStepYears\":1,\"seniorityBonusDays\":1,\"seniorityMaxBonus\":5,\"carryoverMax\":5,\"roleQuotas\":[],\"effectiveFrom\":\"$EFF2\"}")
check "POST tao version 2" 201 "$(code_of "$R")"
V3_ID=$(id_of "$R")
R=$(areq POST "$HR" "/api/v1/attendance/leave/policies/versions/$V3_ID/submit")
check "submit version 2" 200 "$(code_of "$R")"
R=$(areq POST "$HR" "/api/v1/attendance/leave/policies/versions/$V3_ID/reject")
check "reject PENDING->DRAFT" 200 "$(code_of "$R")"
check_has "reject tro ve DRAFT" "$(has "$R" '"status":"DRAFT"')" yes
R=$(areq POST "$HR" "/api/v1/attendance/leave/policies/versions/$V3_ID/reject")
check "reject khi la DRAFT bi 422" 422 "$(code_of "$R")"
R=$(areq DELETE "$HR" "/api/v1/attendance/leave/policies/versions/$V3_ID")
check "DELETE version DRAFT" 204 "$(code_of "$R")"
# c. cancel: PENDING -> CANCELLED (khong kich hoat duoc nua)
R=$(areq POST "$HR" "/api/v1/attendance/leave/policies" "{\"year\":2099,\"baseDays\":14,\"seniorityStepYears\":1,\"seniorityBonusDays\":1,\"seniorityMaxBonus\":5,\"carryoverMax\":5,\"roleQuotas\":[],\"effectiveFrom\":\"$EFF3\"}")
check "POST tao version 3" 201 "$(code_of "$R")"
V4_ID=$(id_of "$R")
R=$(areq POST "$HR" "/api/v1/attendance/leave/policies/versions/$V4_ID/submit")
check "submit version 3" 200 "$(code_of "$R")"
R=$(areq POST "$HR" "/api/v1/attendance/leave/policies/versions/$V4_ID/cancel")
check "cancel PENDING->CANCELLED" 200 "$(code_of "$R")"
check_has "trang thai CANCELLED" "$(has "$R" '"status":"CANCELLED"')" yes
R=$(areq POST "$HR" "/api/v1/attendance/leave/policies/versions/$V4_ID/activate")
check "activate version CANCELLED bi 422" 422 "$(code_of "$R")"
# d. lich su version + RBAC ghi policy
R=$(areq GET "$HR" "/api/v1/attendance/leave/policies?year=2099")
check "GET history ?year=2099" 200 "$(code_of "$R")"
check_has "history co CANCELLED" "$(has "$R" '"CANCELLED"')" yes
R=$(areq DELETE "$EMP" "/api/v1/attendance/leave/policies/versions/$V2_ID")
check "EMPLOYEE delete policy version bi 403" 403 "$(code_of "$R")"
# API moi (thiet ke quyg phep): settings + note + work-days + resync roles
R=$(areq GET "$HR" "/api/v1/attendance/leave/settings")
check "GET leave settings" 200 "$(code_of "$R")"
check_has "settings co fyStartMonth" "$(has "$R" 'fyStartMonth')" yes
R=$(areq PUT "$HR" "/api/v1/attendance/leave/settings" '{"fyStartMonth":1,"fyStartDay":1,"periodStartDay":10,"periodEndDay":5,"saturdayPolicy":"OFF","minWorkPercent":50}')
check "PUT settings ky hong (10->5)" 422 "$(code_of "$R")"
R=$(areq PUT "$HR" "/api/v1/attendance/leave/settings" '{"fyStartMonth":1,"fyStartDay":1,"periodStartDay":26,"periodEndDay":25,"saturdayPolicy":"OFF","minWorkPercent":50}')
check "PUT settings hop le (26->25)" 200 "$(code_of "$R")"
R=$(areq GET "$HR" "/api/v1/attendance/leave/settings")
check_has "settings luu periodStartDay 26" "$(has "$R" '"periodStartDay":26')" yes
check_has "settings co requirePolicyApproval" "$(has "$R" 'requirePolicyApproval')" yes
# ghi chu NV/nam (PUT 200 void, GET tra JSON string)
R=$(areq PUT "$HR" "/api/v1/attendance/leave/quotas/$QP_ID/note?year=$YEAR" '{"note":"ghi chu verify"}')
check "PUT note" 200 "$(code_of "$R")"
# GET note chi cho staff (EMPLOYEE bi 403) → goi bang HR
R=$(areq GET "$HR" "/api/v1/attendance/leave/quotas/$QP_ID/note?year=$YEAR")
check_has "note luu" "$(has "$R" 'ghi chu verify')" yes
# ngay cong theo ky: lay periodStart tu GET, PUT = MANUAL
R=$(areq GET "$HR" "/api/v1/attendance/leave/quotas/$QP_ID/work-days?year=$YEAR")
check "GET work-days" 200 "$(code_of "$R")"
PS=$(echo "$R" | head -n -1 | jq -r '.[0].periodStart')
check "lay duoc periodStart" 0 "$([ -n "$PS" ] && [ "$PS" != null ] && echo 0 || echo missing)"
R=$(areq PUT "$HR" "/api/v1/attendance/leave/quotas/$QP_ID/work-days" "{\"periodStart\":\"$PS\",\"workDays\":1}")
check "PUT work-days" 200 "$(code_of "$R")"
check_has "work-day tro thanh MANUAL" "$(has "$R" 'MANUAL')" yes
# backfill roles -> attendance mirror (1 lan, idempotent)
R=$(req POST "$HR" "/api/v1/accounts/resync-role-events")
check "POST resync-role-events" 201 "$(code_of "$R")"
# cleanup: xoa BONUS da cap + nghi viec NV
R=$(areq DELETE "$HR" "/api/v1/attendance/leave/entitlements/$BONUS_ID")
check "DELETE entitlement BONUS" 204 "$(code_of "$R")"
R=$(req DELETE "$HR" "/api/v1/employees/$QP_ID?reason=cleanup")
check "cleanup NV quy phep" 204 "$(code_of "$R")"

echo "== 14. Ca lam viec (work shift + gan ca theo ngay hieu luc)"
# a. tao ca FIXED + validation (areq/ATT dinh nghia o section 13)
R=$(areq POST "$HR" "/api/v1/attendance/work-shifts" "{\"name\":\"Ca verify $SUF\",\"shiftType\":\"FIXED\",\"startWorkTime\":\"08:00:00\",\"endWorkTime\":\"17:00:00\",\"startBreakTime\":\"12:00:00\",\"endBreakTime\":\"13:00:00\",\"workingTime\":480,\"breakingTime\":60}")
check "POST tao ca FIXED" 201 "$(code_of "$R")"
FIX_ID=$(id_of "$R")
R=$(areq GET "$HR" "/api/v1/attendance/work-shifts?q=$SUF")
check "GET work-shifts theo q" 200 "$(code_of "$R")"
check_has "co ca FIXED verify" "$(has "$R" "Ca verify $SUF")" yes
R=$(areq POST "$HR" "/api/v1/attendance/work-shifts" "{\"name\":\"Ca verify $SUF\",\"shiftType\":\"FIXED\",\"startWorkTime\":\"08:00:00\",\"endWorkTime\":\"17:00:00\",\"workingTime\":480,\"breakingTime\":0}")
check "POST trung ten ca" 409 "$(code_of "$R")"
R=$(areq POST "$HR" "/api/v1/attendance/work-shifts" "{\"name\":\"Ca loi break $SUF\",\"shiftType\":\"FIXED\",\"startWorkTime\":\"08:00:00\",\"endWorkTime\":\"17:00:00\",\"startBreakTime\":\"07:00:00\",\"endBreakTime\":\"08:00:00\",\"workingTime\":480,\"breakingTime\":60}")
check "POST break ngoai khung" 422 "$(code_of "$R")"
R=$(areq POST "$HR" "/api/v1/attendance/work-shifts" "{\"name\":\"Ca vuot tong $SUF\",\"shiftType\":\"FIXED\",\"startWorkTime\":\"08:00:00\",\"endWorkTime\":\"17:00:00\",\"workingTime\":500,\"breakingTime\":60}")
check "POST working+break vuot ca" 422 "$(code_of "$R")"
R=$(areq POST "$HR" "/api/v1/attendance/work-shifts" "{\"name\":\"Ca dem $SUF\",\"shiftType\":\"FIXED\",\"startWorkTime\":\"22:00:00\",\"endWorkTime\":\"06:00:00\",\"workingTime\":480,\"breakingTime\":0}")
check "POST ca qua nua dem" 422 "$(code_of "$R")"
# b. ca FLEXIBLE khong can khung gio nghi (chi breakingTime) + PATCH
R=$(areq POST "$HR" "/api/v1/attendance/work-shifts" "{\"name\":\"Ca linh hoa verify $SUF\",\"shiftType\":\"FLEXIBLE\",\"startWorkTime\":\"07:00:00\",\"endWorkTime\":\"18:00:00\",\"workingTime\":480,\"breakingTime\":60}")
check "POST tao ca FLEXIBLE (khong khung nghi)" 201 "$(code_of "$R")"
FLEX_ID=$(id_of "$R")
check_has "FLEXIBLE khong luu khung nghi" "$(has "$R" "\"startBreakTime\":null")" yes
R=$(areq PATCH "$HR" "/api/v1/attendance/work-shifts/$FIX_ID" "{\"name\":\"Ca verify $SUF v2\"}")
check "PATCH doi ten ca" 200 "$(code_of "$R")"
# c. NV verify cho gan ca (auto-provision → token theo ma NV)
NVW_CODE="nvc$SUF"
R=$(req POST "$HR" "/api/v1/employees" "{\"employeeCode\":\"$NVW_CODE\",\"fullName\":\"NV Ca Lam Viec $SUF\",\"email\":\"clv$SUF@hrms.vn\"}")
check "POST tao NV verify ca" 201 "$(code_of "$R")"
NVW_ID=$(id_of "$R")
# cho den projection attendance co NV (Kafka async — sections truoc tao backlog event)
proj_ok=""
for i in $(seq 1 15); do
  sleep 1
  if curl -s "$ATT/api/v1/attendance/employees" -H "Authorization: Bearer $HR" | grep -q "$NVW_CODE"; then
    proj_ok=yes
    break
  fi
done
check "NV verify co trong projection" yes "${proj_ok:-no}"
# d. gan ca: lan 1 OK, cung ngay/truoc ngay bi 422, chuyen ca (ke lich) OK
R=$(areq POST "$HR" "/api/v1/attendance/shift-assignments" "{\"employeeId\":\"$NVW_ID\",\"workShiftId\":\"$FIX_ID\",\"effectiveFrom\":\"2020-01-01\"}")
check "POST gan ca lan 1" 201 "$(code_of "$R")"
AS1=$(id_of "$R")
R=$(areq POST "$HR" "/api/v1/attendance/shift-assignments" "{\"employeeId\":\"$NVW_ID\",\"workShiftId\":\"$FIX_ID\",\"effectiveFrom\":\"2020-01-01\"}")
check "POST gan cung ngay bi tu choi" 422 "$(code_of "$R")"
R=$(areq POST "$HR" "/api/v1/attendance/shift-assignments" "{\"employeeId\":\"$NVW_ID\",\"workShiftId\":\"$FIX_ID\",\"effectiveFrom\":\"2019-12-31\"}")
check "POST gan truoc ngay hieu luc truoc" 422 "$(code_of "$R")"
R=$(areq POST "$HR" "/api/v1/attendance/shift-assignments" "{\"employeeId\":\"$NVW_ID\",\"workShiftId\":\"$FLEX_ID\",\"effectiveFrom\":\"2021-01-01\"}")
check "POST gan ca 2 (ke lich)" 201 "$(code_of "$R")"
AS2=$(id_of "$R")
R=$(areq GET "$HR" "/api/v1/attendance/shift-assignments?q=$NVW_CODE")
check "GET assignments list (q ma NV)" 200 "$(code_of "$R")"
check_has "list co ca da gan" "$(has "$R" "Ca verify $SUF v2")" yes
# e. xem ca cua minh (NV auto-provision username = ma NV)
NVW_TOK=$(emp_login "$NVW_CODE")
check "token NV ca" 0 "$([ -n "$NVW_TOK" ] && echo 0 || echo missing)"
R=$(areq GET "$NVW_TOK" "/api/v1/attendance/shift-assignments/my")
check "GET my shift" 200 "$(code_of "$R")"
check_has "my shift dung ca hien tai" "$(has "$R" "Ca linh hoa verify")" yes
# f. RBAC + quy tac xoa
R=$(areq POST "$EMP" "/api/v1/attendance/work-shifts" "{\"name\":\"x $SUF\",\"shiftType\":\"FIXED\",\"startWorkTime\":\"08:00:00\",\"endWorkTime\":\"12:00:00\",\"workingTime\":240,\"breakingTime\":0}")
check "EMPLOYEE tao ca bi 403" 403 "$(code_of "$R")"
R=$(areq DELETE "$HR" "/api/v1/attendance/work-shifts/$FIX_ID")
check "DELETE ca con gan NV bi 422" 422 "$(code_of "$R")"
R=$(areq DELETE "$HR" "/api/v1/attendance/shift-assignments/$AS1")
check "DELETE ban gan cu khong phai moi nhat" 422 "$(code_of "$R")"
R=$(areq DELETE "$HR" "/api/v1/attendance/shift-assignments/$AS2")
check "DELETE ban gan moi nhat" 204 "$(code_of "$R")"
R=$(areq DELETE "$HR" "/api/v1/attendance/shift-assignments/$AS1")
check "DELETE ban gan cu sau do" 204 "$(code_of "$R")"
R=$(areq DELETE "$HR" "/api/v1/attendance/work-shifts/$FIX_ID")
check "DELETE ca khi khong con gan" 204 "$(code_of "$R")"
R=$(areq DELETE "$HR" "/api/v1/attendance/work-shifts/$FLEX_ID")
check "DELETE ca flexible" 204 "$(code_of "$R")"
R=$(req DELETE "$HR" "/api/v1/employees/$NVW_ID?reason=cleanup")
check "cleanup NV ca lam viec" 204 "$(code_of "$R")"

echo "== 15. Tai khoan (Cai dat: danh sach / doi mat khau / reset / enable-disable)"
# a. tao NV -> tai khoan tu tao, mat khau mac dinh <ma>@hrms nhung temporary (P0-4)
ACC_CODE="ac$SUF"
R=$(req POST "$HR" "/api/v1/employees" "{\"employeeCode\":\"$ACC_CODE\",\"fullName\":\"NV Tai Khoan $SUF\",\"email\":\"acct$SUF@hrms.vn\"}")
check "POST tao NV cho verify tai khoan" 201 "$(code_of "$R")"
ACC_ID=$(id_of "$R")
ACC_TOK=$(token "$ACC_CODE" "${ACC_CODE}@hrms")
check "mat khau mac dinh temporary -> password-grant bi chan" "$([ -z "$ACC_TOK" ] && echo yes || echo no)" yes
# HR reset sang mat khau biet truoc (temporary=false) de test tiep cac API self-service
R=$(req PATCH "$HR" "/api/v1/accounts/$ACC_CODE/password" '{"newPassword":"tkmoi@1234"}')
check "admin reset mat khau ban dau" 204 "$(code_of "$R")"
ACC_TOK=$(token "$ACC_CODE" 'tkmoi@1234')
check "token NV sau admin reset" 0 "$([ -n "$ACC_TOK" ] && echo 0 || echo missing)"
# b. RBAC + noi dung danh sach tai khoan
R=$(req GET "" "/api/v1/accounts")
check "GET accounts khong token" 401 "$(code_of "$R")"
R=$(req GET "$EMP" "/api/v1/accounts")
check "GET accounts (EMPLOYEE)" 403 "$(code_of "$R")"
R=$(req GET "$HR" "/api/v1/accounts?q=$ACC_CODE&size=5")
check "GET accounts (HR_ADMIN)" 200 "$(code_of "$R")"
check_has "accounts tim thay NV verify" "$(has "$R" "$ACC_CODE")" yes
check_has "accounts co roles" "$(has "$R" '"roles"')" yes
check_has "accounts co audit createdBy" "$(has "$R" '"createdBy"')" yes
check_has "accounts enabled=true" "$(has "$R" '"enabled":true')" yes
# b2. P0-6 van hanh: hang doi dong bo Keycloak chi ADMIN, size bi cap 100
R=$(req GET "" "/api/v1/accounts/sync-tasks")
check "GET sync-tasks khong token" 401 "$(code_of "$R")"
R=$(req GET "$EMP" "/api/v1/accounts/sync-tasks")
check "GET sync-tasks (EMPLOYEE)" 403 "$(code_of "$R")"
R=$(req GET "$HR" "/api/v1/accounts/sync-tasks?status=DONE&size=5")
check "GET sync-tasks (HR_ADMIN)" 200 "$(code_of "$R")"
check_has "sync-tasks co task DONE" "$(has "$R" '"status":"DONE"')" yes
R=$(req GET "$HR" "/api/v1/accounts/sync-tasks?size=1000")
check_has "sync-tasks cap size=100" "$(has "$R" '"size":100')" yes
R=$(req GET "$HR" "/api/v1/accounts/sync-tasks?status=FAILED&size=5")
check "GET sync-tasks status=FAILED" 200 "$(code_of "$R")"
# c. tu doi mat khau: sai mat khau cu -> 422, dung -> 204, login bang mat khau moi
R=$(req POST "$ACC_TOK" "/api/v1/accounts/change-password" '{"currentPassword":"sai-mat-khau-1","newPassword":"tkmoi2@1234"}')
check "change-password sai mat khau hien tai" 422 "$(code_of "$R")"
R=$(req POST "$ACC_TOK" "/api/v1/accounts/change-password" '{"currentPassword":"tkmoi@1234","newPassword":"tkmoi2@1234"}')
check "change-password dung mat khau cu" 204 "$(code_of "$R")"
T=$(token "$ACC_CODE" 'tkmoi2@1234')
check "login bang mat khau moi" 0 "$([ -n "$T" ] && echo 0 || echo missing)"
# d. admin dat lai mat khau
R=$(req PATCH "$HR" "/api/v1/accounts/$ACC_CODE/password" '{"newPassword":"reset1234"}')
check "admin reset mat khau" 204 "$(code_of "$R")"
T=$(token "$ACC_CODE" 'reset1234')
check "login bang mat khau admin reset" 0 "$([ -n "$T" ] && echo 0 || echo missing)"
# e. admin tat/bat tai khoan
R=$(req PATCH "$HR" "/api/v1/accounts/$ACC_CODE/enabled" '{"enabled":false}')
check "admin tat tai khoan" 204 "$(code_of "$R")"
T=$(token "$ACC_CODE" 'reset1234')
check "tai khoan tat khong login duoc" "$([ -z "$T" ] && echo yes || echo no)" yes
R=$(req PATCH "$HR" "/api/v1/accounts/$ACC_CODE/enabled" '{"enabled":true}')
check "admin bat lai tai khoan" 204 "$(code_of "$R")"
T=$(token "$ACC_CODE" 'reset1234')
check "login lai sau khi bat" 0 "$([ -n "$T" ] && echo 0 || echo missing)"
# f. quy tac + validation
R=$(req PATCH "$HR" "/api/v1/accounts/hr/enabled" '{"enabled":false}')
check "admin khong tat duoc tai khoan minh" 422 "$(code_of "$R")"
R=$(req PATCH "$EMP" "/api/v1/accounts/$ACC_CODE/password" '{"newPassword":"reset1234"}')
check "reset mat khau (EMPLOYEE)" 403 "$(code_of "$R")"
R=$(req PATCH "$HR" "/api/v1/accounts/$ACC_CODE/password" '{"newPassword":"1234567"}')
check "mat khau qua ngan bi 400" 400 "$(code_of "$R")"
R=$(req PATCH "$HR" "/api/v1/accounts/khong-ton-tai/password" '{"newPassword":"reset1234"}')
check "reset tai khoan khong ton tai" 404 "$(code_of "$R")"
# g. cleanup
R=$(req DELETE "$HR" "/api/v1/employees/$ACC_ID?reason=cleanup")
check "cleanup NV tai khoan" 204 "$(code_of "$R")"

echo "=== RESULT: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
