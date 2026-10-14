#!/bin/bash
# Verify identity-service — Bước 9 (identity-core):
# auth/RBAC + departments + contracts (salary gating) + history + org chart + search
# Idempotent: dùng mã ngẫu nhiên mỗi lần chạy. Chạy trong hrms-dev:
#   docker exec hrms-dev bash /workspaces/HRMS/scripts/verify-identity.sh
API=http://localhost:8080
KC=http://keycloak:8080/realms/hrms
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

echo "== 1. RBAC: danh sach nhan vien (PageResponse)"
R=$(req GET "" /api/v1/employees);               check "GET list khong token" 401 "$(code_of "$R")"
R=$(req GET "$EMP" /api/v1/employees);           check "GET list employee1" 403 "$(code_of "$R")"
R=$(req GET "$MGR" "/api/v1/employees?size=5");  check "GET list manager1" 200 "$(code_of "$R")"
check_has "PageResponse co items/total" "$(has "$R" '"items"')" yes

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
R=$(req POST "$HR" /api/v1/employees "{\"employeeCode\":\"$EMP_CODE\",\"fullName\":\"NV $SUF\",\"email\":\"$SUF@hrms.vn\",\"departmentId\":\"$DEPT_ID\",\"position\":\"Tester\",\"hiredAt\":\"2026-01-05T00:00:00Z\",\"gender\":\"FEMALE\",\"birthDate\":\"1995-04-02\",\"phone\":\"0900000001\",\"nationalId\":\"012345678901\"}")
check "POST tao nhan vien (co departmentId)" 201 "$(code_of "$R")"
EMP_ID=$(id_of "$R"); echo "employeeId=$EMP_ID"
check_has "resolve ten phong ban" "$(has "$R" "Phòng QA $SUF")" yes
R=$(req GET "$HR" "/api/v1/employees?q=$EMP_CODE")
check "GET search q=ma NV" 200 "$(code_of "$R")"
check_has "search tim thay trong items" "$(has "$R" "$EMP_CODE")" yes
R=$(req GET "$HR" "/api/v1/employees?departmentId=$DEPT_ID")
check "GET search theo departmentId" 200 "$(code_of "$R")"
R=$(req POST "$HR" /api/v1/employees "{\"employeeCode\":\"$EMP_CODE\",\"fullName\":\"Trùng Mã\"}")
check "POST trung ma" 409 "$(code_of "$R")"

echo "== 4. PATCH nang cao (history + clear)"
R=$(req PATCH "$HR" "/api/v1/employees/$EMP_ID" '{"position":"Senior Tester"}')
check "PATCH doi vi tri" 200 "$(code_of "$R")"
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
check_has "MANAGER khong thay salary" "$(has "$R" '"salary":null')" yes
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
check "GET list tai lieu (MANAGER)" 200 "$CODE"
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

echo "=== RESULT: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
