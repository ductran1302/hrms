#!/usr/bin/env bash
# Smoke Phase 2 — nối employee_shift_rosters vào tính ngày công quỹ phép.
#
# Kịch bản (1 nhân sự ACTIVE, kỳ công chứa hôm nay):
#   1. Sinh roster cho cả kỳ → đọc lại "ngày công của kỳ" (mẫu số) = theo roster.
#   2. Override TẤT CẢ ngày làm còn lại trong kỳ thành NGHỈ → mẫu số giảm đúng Δ.
#   3. Tính lại quỹ → tử số (work-days DEFAULT) giảm đúng Δ; nếu kỳ rơi khỏi ngưỡng
#      minWorkPercent → BASE giảm đúng baseDays/12.
#   4. Bỏ override + tính lại → mọi số trở về đúng như ban đầu (không sót data).
#
# Chạy trong dev container (app stack): API=http://gateway:8080
set -u

API=${API:-http://localhost:8186}
KC=${KC:-http://keycloak:8080/realms/hrms}
USER_NAME=${USER_NAME:-hr}
USER_PASS=${USER_PASS:-hr@123}

PASS=0
FAIL=0
TODAY=$(date +%F)

check() { # label expected actual
  if [ "$2" = "$3" ]; then
    PASS=$((PASS + 1)); echo "PASS  $1"
  else
    FAIL=$((FAIL + 1)); echo "FAIL  $1: expected=$2 actual=$3"
  fi
}

# so sánh số với sai số (trả yes/no)
numclose() { # a b tolerance
  awk -v a="$1" -v b="$2" -v t="$3" 'BEGIN {
    d = a - b; if (d < 0) d = -d; print (d <= t + 0) ? "yes" : "no"; exit }'
}

# a < b ?
numlt() { awk -v a="$1" -v b="$2" 'BEGIN { print (a + 0 < b + 0) ? "yes" : "no" }'; }

TOKEN=$(curl -s -X POST "$KC/protocol/openid-connect/token" \
  -d "client_id=hrms-spa&grant_type=password&username=$USER_NAME&password=$USER_PASS&scope=openid" \
  | jq -r '.access_token // empty')
if [ -z "$TOKEN" ]; then
  echo "Không lấy được token cho $USER_NAME"; exit 1
fi
AUTH="Authorization: Bearer $TOKEN"

jget() { curl -s -H "$AUTH" "$API$1"; }
jpost() { curl -s -X POST -H "$AUTH" -H "Content-Type: application/json" -d "$2" "$API$1"; }
jput() { curl -s -X PUT -H "$AUTH" -H "Content-Type: application/json" -d "$2" "$API$1"; }
jdel() { curl -s -o /dev/null -w '%{http_code}' -X DELETE -H "$AUTH" "$API$1"; }

echo "=== Leave ↔ Roster smoke ($TODAY) ==="

# ---- 1. Settings → năm TC hiện tại + ngưỡng ----
S=$(jget "/api/v1/attendance/leave/settings")
FY_M=$(echo "$S" | jq -r '.fyStartMonth')
FY_D=$(echo "$S" | jq -r '.fyStartDay')
MIN_WORK=$(echo "$S" | jq -r '.minWorkPercent')
YEAR=$(date +%Y)
if [[ "$(date +%m-%d)" < "$(printf '%02d-%02d' "$FY_M" "$FY_D")" ]]; then
  YEAR=$((YEAR - 1))
fi
check "đọc được leave settings" "yes" "$([ -n "$FY_M" ] && [ "$MIN_WORK" != "null" ] && echo yes || echo no)"

# ---- 2. Nhân sự test ----
EID=$(jget "/api/v1/attendance/employees" | jq -r --arg t "$TODAY" \
  '[.[] | select(.status == "ACTIVE")
       | select((.hiredAt == null) or (.hiredAt[0:10] <= $t))
       | select(.terminatedAt == null)][0].employeeId // empty')
check "có nhân sự ACTIVE để test" "yes" "$([ -n "$EID" ] && echo yes || echo no)"
[ -n "$EID" ] || exit 1

# ---- 3. Kỳ công chứa hôm nay ----
workdays() { # periodStart periodEnd full actual source
  jget "/api/v1/attendance/leave/quotas/$EID/work-days?year=$YEAR" \
    | jq -r --arg t "$TODAY" \
      '.[] | select(.periodStart <= $t and .periodEnd >= $t)
           | "\(.periodStart) \(.periodEnd) \(.periodWorkingDays) \(.workDays) \(.source)"'
}
read -r PSTART PEND FULL0 ACTUAL0 SRC0 <<EOF
$(workdays)
EOF
check "kỳ công chứa hôm nay tồn tại" "yes" "$([ -n "$PSTART" ] && echo yes || echo no)"
[ -n "$PSTART" ] || exit 1
echo "INFO  kỳ $PSTART..$PEND (năm TC $YEAR, ngưỡng ${MIN_WORK}%) NV=$EID"

# ---- 4. BASE ban đầu ----
BASE0=$(jget "/api/v1/attendance/leave/quotas/$EID?year=$YEAR" | jq -r '.baseDays')
POLICY_BASE=$(jget "/api/v1/attendance/leave/policies/$YEAR" | jq -r '.baseDays // empty')
check "đọc được BASE + baseDays policy" "yes" \
  "$([ "$BASE0" != "null" ] && [ -n "$POLICY_BASE" ] && echo yes || echo no)"

# ---- 5. Sinh roster cho cả kỳ (backfill quá khứ, sinh tương lai) ----
GEN=$(jpost "/api/v1/attendance/rosters/generate?from=$PSTART&to=$PEND&employeeId=$EID" "")
check "generate roster kỳ hiện tại" "yes" \
  "$(echo "$GEN" | jq -r '((.created + .updated + .unchanged) > 0) | if . then "yes" else "no" end')"

read -r PSTART PEND FULL_A ACTUAL_A SRC_A <<EOF
$(workdays)
EOF
check "mẫu số sau generate > 0" "yes" "$(awk -v v="$FULL_A" 'BEGIN{print (v+0>0)?"yes":"no"}')"

# ---- 6. Δ kỳ vọng = tổng trọng số các ngày làm còn lại ----
SHIFT_MAP=$(jget "/api/v1/attendance/work-shifts" \
  | jq '[.[] | {key: .id, value: (.workingTime // 480)}] | from_entries')
ROWS=$(jget "/api/v1/attendance/rosters?employeeId=$EID&from=$TODAY&to=$PEND")
OVERRIDE_DATES=$(echo "$ROWS" | jq -r '[.[] | select(.dayOff == false)][].workDate')
K=$(echo "$OVERRIDE_DATES" | grep -c . || true)
check "có ngày làm tương lai để override" "yes" "$([ "$K" -gt 0 ] && echo yes || echo no)"
if [ "$K" -eq 0 ]; then
  echo "=== RESULT: PASS=$PASS FAIL=$FAIL (kỳ không còn ngày làm — bỏ qua)"
  [ "$FAIL" -eq 0 ]
  exit $?
fi

DELTA=$(echo "$ROWS" | jq --argjson m "$SHIFT_MAP" '
  [.[] | select(.dayOff == false)
       | ((($m[.shiftId] // 480) / 480)
            | if . > 1 then 1 elif . < 0 then 0 else . end
            | .*10000 | round) / 10000]
  | add // 0 | (. * 100 | round) / 100')
echo "INFO  override $K ngày, Δ kỳ vọng = $DELTA ngày"

# ---- 7. Override toàn bộ ngày làm còn lại → NGHĨ ----
OVERRIDDEN=0
for d in $OVERRIDE_DATES; do
  OV=$(jput "/api/v1/attendance/rosters/$EID/$d" '{"shiftId":null,"note":"smoke-phase2"}')
  if [ "$(echo "$OV" | jq -r '.dayOff')" = "true" ]; then
    OVERRIDDEN=$((OVERRIDDEN + 1))
  fi
done
check "override hết $K ngày → nghỉ" "$K" "$OVERRIDDEN"

# ---- 8. Mẫu số giảm đúng Δ ----
read -r _ _ FULL_B ACTUAL_B _ <<EOF
$(workdays)
EOF
DIFF_AB=$(awk -v a="$FULL_A" -v b="$FULL_B" 'BEGIN{printf "%.2f", a - b}')
check "mẫu số giảm đúng Δ" "yes" "$(numclose "$DIFF_AB" "$DELTA" 0.02)"
check "mẫu số sau override < trước" "yes" "$(numlt "$FULL_B" "$FULL_A")"

# ---- 9. Tính lại quỹ → tử số giảm đúng Δ (nếu nguồn DEFAULT) ----
if [ "$SRC_A" = "DEFAULT" ]; then
  jpost "/api/v1/attendance/leave/quotas/recalculate?year=$YEAR" "" > /dev/null
  read -r _ _ _ ACTUAL_C SRC_C <<EOF
$(workdays)
EOF
  DIFF_AA=$(awk -v a="$ACTUAL_A" -v b="$ACTUAL_C" 'BEGIN{printf "%.2f", a - b}')
  check "tử số (work-days DEFAULT) giảm đúng Δ" "yes" "$(numclose "$DIFF_AA" "$DELTA" 0.02)"
  check "nguồn work-days vẫn DEFAULT" "DEFAULT" "$SRC_C"

  # ---- 10. BASE: kỳ rơi khỏi ngưỡng → giảm đúng baseDays/12 ----
  BASE1=$(jget "/api/v1/attendance/leave/quotas/$EID?year=$YEAR" | jq -r '.baseDays')
  Q_BEFORE=$(awk -v a="$ACTUAL_A" -v f="$FULL_A" -v m="$MIN_WORK" \
    'BEGIN{print (a*100 >= f*m) ? 1 : 0}')
  Q_AFTER=$(awk -v a="$ACTUAL_C" -v f="$FULL_B" -v m="$MIN_WORK" \
    'BEGIN{print (a*100 >= f*m) ? 1 : 0}')
  EXPECTED_DROP=$(awk -v q1="$Q_BEFORE" -v q2="$Q_AFTER" -v b="$POLICY_BASE" \
    'BEGIN{ if (q1==1 && q2==0) printf "%.4f", b/12; else printf "%.4f", 0 }')
  ACTUAL_DROP=$(awk -v a="$BASE0" -v b="$BASE1" 'BEGIN{printf "%.4f", a - b}')
  check "BASE đổi đúng baseDays/12 (kỳ đủ→không đủ)" "$EXPECTED_DROP" \
    "$(awk -v x="$ACTUAL_DROP" -v y="$EXPECTED_DROP" \
      'BEGIN{d=x-y; if(d<0)d=-d; printf "%.4f", (d<=0.0001)?x:y}')"
  echo "INFO  BASE $BASE0 → $BASE1 (Δ=$ACTUAL_DROP, kỳ trước=$Q_BEFORE sau=$Q_AFTER)"
else
  echo "SKIP  work-days nguồn $SRC_A (không phải DEFAULT) — bỏ kiểm tử số/BASE"
fi

# ---- 11. Khôi phục: bỏ override + tính lại → về đúng ban đầu ----
RESTORED=0
for d in $OVERRIDE_DATES; do
  # DELETE trả 200 (không annotate NO_CONTENT) — chấp nhận cả 200/204
  CODE=$(jdel "/api/v1/attendance/rosters/$EID/$d")
  { [ "$CODE" = "200" ] || [ "$CODE" = "204" ]; } && RESTORED=$((RESTORED + 1))
done
check "gỡ hết override" "$K" "$RESTORED"

read -r _ _ FULL_C ACTUAL_D SRC_D <<EOF
$(workdays)
EOF
check "mẫu số trở lại đúng ban đầu" "yes" "$(numclose "$FULL_C" "$FULL_A" 0.001)"

if [ "$SRC_A" = "DEFAULT" ]; then
  jpost "/api/v1/attendance/leave/quotas/recalculate?year=$YEAR" "" > /dev/null
  read -r _ _ _ ACTUAL_E _ <<EOF
$(workdays)
EOF
  BASE2=$(jget "/api/v1/attendance/leave/quotas/$EID?year=$YEAR" | jq -r '.baseDays')
  check "tử số trở lại đúng ban đầu" "yes" "$(numclose "$ACTUAL_E" "$ACTUAL_A" 0.001)"
  check "BASE trở lại đúng ban đầu" "yes" "$(numclose "$BASE2" "$BASE0" 0.0001)"
fi

echo "=== RESULT: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
