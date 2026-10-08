#!/usr/bin/env bash
# Kiểm tra route gateway dev (8085) cho các API identity mới.
set -u
KC=http://keycloak:8080/realms/hrms
TOK=$(curl -s -X POST "$KC/protocol/openid-connect/token" \
  -d 'client_id=hrms-spa&grant_type=password&username=hr&password=hr@123&scope=openid' \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
[ -n "$TOK" ] || { echo "FAIL lay token"; exit 1; }
fail=0
for p in departments org-chart contracts "employees?size=1"; do
  code=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOK" \
    "http://localhost:8085/api/v1/$p")
  if [ "$code" = 200 ]; then
    echo "OK   dev gw /api/v1/$p -> 200"
  else
    echo "FAIL dev gw /api/v1/$p -> $code (want 200)"
    fail=1
  fi
done
# route attendance vẫn đúng chỗ (không rơi vào identity-api)
code=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOK" \
  "http://localhost:8085/api/v1/attendance/employees")
if [ "$code" = 200 ]; then
  echo "OK   dev gw /api/v1/attendance/employees -> 200"
else
  echo "FAIL dev gw attendance -> $code (want 200)"
  fail=1
fi
echo "RESULT: $([ $fail -eq 0 ] && echo ALL OK || echo FAILED)"
exit $fail
