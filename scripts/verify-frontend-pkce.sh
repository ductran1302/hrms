#!/usr/bin/env bash
# E2E PKCE y hệt SPA: authorize → form login Keycloak → code → token exchange → gọi API qua gateway.
set -u
KC="http://keycloak:8080/realms/hrms"
FE_ORIGIN="${FE_ORIGIN:-http://localhost:5173/}"
GATEWAY="${GATEWAY:-http://localhost:8185}"
REDIR_ENC=$(node -e 'process.stdout.write(encodeURIComponent(process.argv[1]))' "$FE_ORIGIN")
REDIR="$FE_ORIGIN"
JAR=/tmp/pkce-cookies.txt
fail=1

VERIFIER=$(head -c 64 /dev/urandom | base64 | tr '+/' '-_' | tr -d '=\n')
# truyền qua env — argv có thể bắt đầu bằng '-' làm node hiểu nhầm là option
export VERIFIER
CHALLENGE=$(node -e 'const c=require("crypto");process.stdout.write(c.createHash("sha256").update(process.env.VERIFIER).digest("base64url"))')
[ ${#CHALLENGE} -ge 43 ] || { echo "FAIL tao challenge"; exit 1; }

# 1. authorize (chưa đăng nhập → 200 login form)
code=$(curl -s -c "$JAR" -o /tmp/kc-login.html -w '%{http_code}' \
  "$KC/protocol/openid-connect/auth?client_id=hrms-spa&redirect_uri=$REDIR_ENC&response_type=code&scope=openid&code_challenge=$CHALLENGE&code_challenge_method=S256")
echo "authorize: $code (expect 200, login form)"
[ "$code" = 200 ] || exit 1

ACTION=$(grep -o 'action="[^"]*"' /tmp/kc-login.html | head -1 | cut -d'"' -f2 | sed 's/&amp;/\&/g')
[ -n "$ACTION" ] || { echo "FAIL tim form action"; exit 1; }
# form action là URL public (localhost:8181) — trong container phải gọi keycloak:8080
ACTION=${ACTION/http:\/\/localhost:8181/http:\/\/keycloak:8080}

# 2. submit username/password → 302 về redirect_uri?code=...
redirect=$(curl -s -b "$JAR" -c "$JAR" -o /dev/null -w '%{redirect_url}' \
  -d 'username=hr&password=hr@123&credentialId=' "$ACTION")
echo "login submit → $redirect"
case "$redirect" in
  "$FE_ORIGIN"*) ;;
  *) echo "FAIL redirect khong tro ve SPA"; exit 1 ;;
esac
CODE=$(printf '%s' "$redirect" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
[ -n "$CODE" ] || { echo "FAIL lay authorization code"; exit 1; }

# 3. token exchange (PKCE)
TOKEN=$(curl -s -X POST "$KC/protocol/openid-connect/token" \
  -d "grant_type=authorization_code&client_id=hrms-spa&redirect_uri=$REDIR&code=$CODE&code_verifier=$VERIFIER" \
  | node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{try{process.stdout.write(JSON.parse(d).access_token||"")}catch(e){process.stdout.write("")}})')
[ -n "$TOKEN" ] || { echo "FAIL doi token"; exit 1; }
echo "token: ${#TOKEN} bytes"

# 4. gọi API qua gateway (giống axios interceptor của SPA)
gcode=$(curl -s -o /tmp/pkce-att.json -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$GATEWAY/api/v1/attendance/employees")
echo "gateway attendance: $gcode (expect 200)"
[ "$gcode" = 200 ] || { cat /tmp/pkce-att.json; exit 1; }

gcode=$(curl -s -o /tmp/pkce-emp.json -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$GATEWAY/api/v1/employees")
echo "gateway employees: $gcode (expect 200)"
[ "$gcode" = 200 ] || { cat /tmp/pkce-emp.json; exit 1; }

fail=0
echo "RESULT: ALL OK"
exit $fail
