#!/usr/bin/env bash
# Verify seed phòng IT + idempotent (restart không nhân đôi).
# API/KC override được qua env (mặc định giữ nguyên như cũ).
set -u
API=${API:-http://localhost:8183}
KC=${KC:-http://keycloak:8080/realms/hrms}
TOK=$(curl -s -X POST "$KC/protocol/openid-connect/token" \
  -d 'client_id=hrms-spa&grant_type=password&username=hr&password=hr@123&scope=openid' \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
[ -n "$TOK" ] || { echo "FAIL lay token"; exit 1; }
fail=0

check() { # label expected code
  if [ "$3" = "$2" ]; then echo "PASS  $1 -> $3"; else echo "FAIL  $1 -> $3 (want $2)"; fail=1; fi
}

echo "== Org chart có root Khoa + 3 trưởng phòng"
TREE=$(curl -s -H "Authorization: Bearer $TOK" $API/api/v1/org-chart)
echo "$TREE" | grep -q '"Trần Đình Khoa"' && echo "PASS  root Trần Đình Khoa" || { echo "FAIL root"; fail=1; }
echo "$TREE" | grep -q '"Lê Thu Hà"' && echo "PASS  Lê Thu Hà" || { echo "FAIL Hà"; fail=1; }
echo "$TREE" | grep -q '"Hoàng Gia Huy"' && echo "PASS  Hoàng Gia Huy" || { echo "FAIL Huy"; fail=1; }

echo "== Departments tree có phòng IT + 3 bộ phận"
DTREE=$(curl -s -H "Authorization: Bearer $TOK" $API/api/v1/departments/tree)
echo "$DTREE" | grep -q '"Phòng Công nghệ thông tin"' && echo "PASS  phong IT" || { echo "FAIL IT"; fail=1; }
echo "$DTREE" | grep -q '"Bộ phận Vận hành & Hạ tầng"' && echo "PASS  bo phan OPS" || { echo "FAIL OPS"; fail=1; }

echo "== Đếm nhân sự NVIT (phải = 8 — không nhân đôi sau restart)"
CNT=$(curl -s -H "Authorization: Bearer $TOK" "$API/api/v1/employees?q=NVIT&size=100" \
  | sed -n 's/.*"total":\([0-9]*\).*/\1/p')
check "so nhan vien NVIT = 8" 8 "${CNT:-0}"

echo "RESULT: $([ $fail -eq 0 ] && echo ALL OK || echo FAILED)"
exit $fail
