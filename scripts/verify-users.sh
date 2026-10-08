#!/usr/bin/env bash
# Verify 3 user HRMS vẫn đăng nhập được sau khi realm được import lại.
set -u
KC="http://keycloak:8080/realms/hrms/protocol/openid-connect/token"
ok=0
for u in "hr:hr@123" "manager1:manager@123" "employee1:emp@123"; do
  usr=${u%%:*}
  pwd=${u##*:}
  resp=$(curl -s -X POST "$KC" -d "client_id=hrms-spa&grant_type=password&username=$usr&password=$pwd&scope=openid")
  if echo "$resp" | grep -q '"access_token"'; then
    token=$(echo "$resp" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
    roles=$(echo "$token" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | sed -n 's/.*"roles":\[\([^]]*\)\].*/\1/p' | tr -d '"')
    echo "OK   $usr — roles: $roles"
    ok=$((ok+1))
  else
    echo "FAIL $usr — $resp"
  fi
done
[ $ok = 3 ] && echo "RESULT: 3/3 OK" || echo "RESULT: SOME FAILED"
