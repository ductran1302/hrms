#!/usr/bin/env bash
# Smoke: route attendance qua gateway (8085) — RBAC.
set -u
TOKEN=$(curl -s -X POST http://keycloak:8080/realms/hrms/protocol/openid-connect/token \
  -d 'client_id=hrms-spa&grant_type=password&username=hr&password=hr@123&scope=openid' \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] || { echo "FAIL lay token"; exit 1; }

code=$(curl -s -o /tmp/gw-att.json -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  ${GW:-http://localhost:8085}/api/v1/attendance/employees)
echo "gw attendance (HR): $code (expect 200)"
[ "$code" = 200 ] || { cat /tmp/gw-att.json; exit 1; }
grep -q employeeCode /tmp/gw-att.json && echo "OK co employeeCode trong body"

code=$(curl -s -o /dev/null -w '%{http_code}' ${GW:-http://localhost:8085}/api/v1/attendance/employees)
echo "gw attendance (no token): $code (expect 401)"
[ "$code" = 401 ] || exit 1

TOKEN_E=$(curl -s -X POST http://keycloak:8080/realms/hrms/protocol/openid-connect/token \
  -d 'client_id=hrms-spa&grant_type=password&username=employee1&password=emp@123&scope=openid' \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
code=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN_E" \
  ${GW:-http://localhost:8085}/api/v1/attendance/employees)
echo "gw attendance (employee1): $code (expect 403)"
[ "$code" = 403 ] || exit 1

echo "RESULT: ALL OK"
