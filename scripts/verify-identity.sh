#!/bin/bash
# Verify identity-service runtime: auth + CRUD + RBAC (chạy trong hrms-dev)
API=http://localhost:8080
KC=http://keycloak:8080/realms/hrms

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

req() { # method token path [data]
  local method=$1 tok=$2 path=$3 data=$4
  if [ -n "$data" ]; then
    curl -s -X "$method" -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' \
      -d "$data" -w '\nHTTP %{http_code}\n' "$API$path"
  else
    curl -s -X "$method" -H "Authorization: Bearer $tok" -w '\nHTTP %{http_code}\n' "$API$path"
  fi
}

echo "== 1. GET list khong token -> 401"
req GET "" /api/v1/employees | tail -1

echo "== 2. GET list hr (HR_ADMIN) -> 200"
req GET "$HR" /api/v1/employees | tail -1

echo "== 3. GET list employee1 (EMPLOYEE) -> 403"
req GET "$EMP" /api/v1/employees | tail -1

echo "== 4. POST tao nhan vien (hr) -> 201"
CREATE=$(req POST "$HR" /api/v1/employees '{"employeeCode":"NV0001","fullName":"Nguyễn Văn A","email":"a@hrms.vn","departmentName":"Engineering","position":"Backend Developer","hiredAt":"2026-01-05T00:00:00Z"}')
echo "$CREATE"
ID=$(echo "$CREATE" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -1)
echo "employeeId=$ID"

echo "== 5. POST trung ma -> 409"
req POST "$HR" /api/v1/employees '{"employeeCode":"NV0001","fullName":"Trùng Mã"}' | tail -2

echo "== 6. POST boi manager1 (MANAGER) -> 403"
req POST "$MGR" /api/v1/employees '{"employeeCode":"NV0002","fullName":"X"}' | tail -1

echo "== 7. POST boi employee1 -> 403"
req POST "$EMP" /api/v1/employees '{"employeeCode":"NV0003","fullName":"X"}' | tail -1

echo "== 8. PATCH cap nhat (hr) -> 200"
req PATCH "$HR" "/api/v1/employees/$ID" '{"position":"Senior Backend"}' | tail -2

echo "== 9. PATCH status=TERMINATED -> 422"
req PATCH "$HR" "/api/v1/employees/$ID" '{"status":"TERMINATED"}' | tail -2

echo "== 10. POST body thieu fullName -> 400"
req POST "$HR" /api/v1/employees '{"employeeCode":"NV0009"}' | tail -2

echo "== 11. DELETE nghi viec (hr) -> 204"
req DELETE "$HR" "/api/v1/employees/$ID?reason=T%E1%BB%B1%20nguy%E1%BB%8Dn" | tail -1

echo "== 12. DELETE lan 2 -> 422"
req DELETE "$HR" "/api/v1/employees/$ID" | tail -2

echo "== 13. GET list status=TERMINATED -> 200"
req GET "$HR" "/api/v1/employees?status=TERMINATED" | head -c 400; echo
