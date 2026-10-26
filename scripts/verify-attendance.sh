#!/usr/bin/env bash
# Smoke test attendance-service (dev): RBAC, projection, DLQ.
# Chạy trong container hrms-dev. Prereq: attendance jar đang chạy ở SERVER_PORT=8186.
set -u
ATT="${ATT_URL:-http://localhost:8186}"
GW="${GW_URL:-http://localhost:8185}"
KC_TOKEN="${KC_TOKEN_URL:-http://keycloak:8080/realms/hrms/protocol/openid-connect/token}"

token() { # $1=username $2=password
  curl -s -X POST "$KC_TOKEN" \
    -d "client_id=hrms-spa" -d "grant_type=password" \
    -d "username=$1" -d "password=$2" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
}

HR=$(token hr 'hr@123')
EMP=$(token employee1 'emp@123')
[ -n "$HR" ] || { echo "FAIL: không lấy được token hr"; exit 1; }
echo "OK  token hr (${#HR} bytes), employee1 (${#EMP} bytes)"

code() { curl -s -o /tmp/att-body.json -w '%{http_code}' "$@"; }

echo "--- RBAC ---"
s=$(code "$ATT/api/v1/attendance/employees")
echo "no token      -> $s (expect 401)"; [ "$s" = 401 ] || exit 1
s=$(code -H "Authorization: Bearer $HR" "$ATT/api/v1/attendance/employees")
echo "HR_ADMIN      -> $s (expect 200)"; [ "$s" = 200 ] || exit 1
s=$(code -H "Authorization: Bearer $EMP" "$ATT/api/v1/attendance/employees")
echo "EMPLOYEE role -> $s (expect 403)"; [ "$s" = 403 ] || exit 1

echo "--- projection hiện tại ---"
curl -s -H "Authorization: Bearer $HR" "$ATT/api/v1/attendance/employees" | tee /tmp/att-list.json
echo

echo "--- E2E: tạo NV9001 qua gateway ---"
s=$(code -X POST "$GW/api/v1/employees" \
  -H "Authorization: Bearer $HR" -H "Content-Type: application/json" \
  -d '{"employeeCode":"NV9001","fullName":"Lê Văn C","email":"levanc@example.com","status":"ACTIVE"}')
echo "POST qua gateway -> $s (expect 201)"; case "$s" in 201|409) ;; *) cat /tmp/att-body.json; exit 1;; esac

found=""
for i in $(seq 1 20); do
  sleep 1
  if curl -s -H "Authorization: Bearer $HR" "$ATT/api/v1/attendance/employees" | grep -q NV9001; then
    found=1; echo "OK  NV9001 xuất hiện trong projection sau ${i}s"; break
  fi
done
[ -n "$found" ] || { echo "FAIL: NV9001 không xuất hiện trong 20s"; exit 1; }

echo "DONE"
