#!/usr/bin/env bash
# Kiểm tra springdoc + Bearer Auth cho identity (8183) và attendance (8186).
set -u
fail=0

check() { # $1=tên $2=url $3=grep-pattern $4=file
  code=$(curl -s -o "$4" -w '%{http_code}' "$2")
  hits=$(grep -cF "$3" "$4" 2>/dev/null || echo 0)
  if [ "$code" = 200 ] && [ "$hits" -gt 0 ]; then
    echo "OK   $1: HTTP $code, '$3' tìm thấy $hits lần"
  else
    echo "FAIL $1: HTTP $code, '$3' hits=$hits"
    fail=1
  fi
}

check "identity api-docs"     http://localhost:8183/v3/api-docs bearerAuth /tmp/api-id.json
check "attendance api-docs"   http://localhost:8186/v3/api-docs bearerAuth /tmp/api-att.json
check "identity Title"        http://localhost:8183/v3/api-docs "Identity API" /tmp/api-id.json
check "attendance Title"      http://localhost:8186/v3/api-docs "Attendance API" /tmp/api-att.json
check "attendance summary"    http://localhost:8186/v3/api-docs "/api/v1/attendance/employees" /tmp/api-att.json
check "attendance bearer def" http://localhost:8186/v3/api-docs '"bearerAuth":{"type":"http","scheme":"bearer","bearerFormat":"JWT"}' /tmp/api-att.json
check "attendance op security" http://localhost:8186/v3/api-docs '"security":[{"bearerAuth"' /tmp/api-att.json
check "identity op security"  http://localhost:8183/v3/api-docs '"security":[{"bearerAuth"' /tmp/api-id.json

for svc in "identity:8183" "attendance:8186"; do
  name=${svc%%:*}; port=${svc##*:}
  loc=$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' "http://localhost:$port/swagger-ui.html")
  echo "swagger-ui $name ($port): $loc"
  idx=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$port/swagger-ui/index.html")
  [ "$idx" = 200 ] || { echo "FAIL swagger index $name: $idx"; fail=1; }
done

echo "RESULT: $([ $fail = 0 ] && echo ALL OK || echo SOME FAILED)"
exit $fail
