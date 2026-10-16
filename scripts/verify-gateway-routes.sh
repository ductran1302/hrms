#!/usr/bin/env bash
# Kiểm tra route gateway cho các API identity mới.
# Default: dev gateway (8085). Override: GW=http://gateway:8080 (app stack) ./verify-gateway-routes.sh
set -u
GW=${GW:-http://localhost:8085}
KC=http://keycloak:8080/realms/hrms
TOK=$(curl -s -X POST "$KC/protocol/openid-connect/token" \
  -d 'client_id=hrms-spa&grant_type=password&username=hr&password=hr@123&scope=openid' \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
[ -n "$TOK" ] || { echo "FAIL lay token"; exit 1; }
fail=0
for p in departments org-chart contracts "employees?size=1"; do
  code=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOK" \
    "$GW/api/v1/$p")
  if [ "$code" = 200 ]; then
    echo "OK   gw /api/v1/$p -> 200"
  else
    echo "FAIL gw /api/v1/$p -> $code (want 200)"
    fail=1
  fi
done
# route attendance van dang chu (khong rot vao identity-api)
code=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOK" \
  "$GW/api/v1/attendance/employees")
if [ "$code" = 200 ]; then
  echo "OK   gw attendance -> 200"
else
  echo "FAIL gw attendance -> $code (want 200)"
  fail=1
fi
echo "RESULT: $([ $fail -eq 0 ] && echo ALL OK || echo FAILED)"
exit $fail
