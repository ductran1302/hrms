#!/usr/bin/env bash
# Smoke lịch làm việc (Phase 4 — derived): lịch SUY RA on-read từ policy tuần, không sinh row.
#
# Chạy trong dev container:
#   docker exec hrms-dev bash -lc "cd /workspaces/HRMS && set -a && . ./.env && set +a \
#     && API=http://gateway:8080 bash scripts/verify-shift-calendar.sh"
#
# Script chọn 4 nhân sự, tạo 4 policy (Off/All/Half/Alternating), kiểm tra lịch suy ra đúng
# T2–T6/T7/CN, resolve được cả ngày xa (không còn cửa sổ sinh), manual override giữ nguyên,
# bỏ override quay về policy; rồi DỌN SẠCH (gỡ gán, xoá policy/ca tạm).
set -u

API=${API:-http://localhost:8183}
KC=${KC:-http://keycloak:8080/realms/hrms}
USER_NAME=${USER_NAME:-hr}
USER_PASS=${USER_PASS:-hr@123}
RANGE_DAYS=${RANGE_DAYS:-61}

PASS=0
FAIL=0
SUF=$(date +%s)
TODAY=$(date +%F)
END=$(date -d "$TODAY + $RANGE_DAYS days" +%F)
FAR=$(date -d "$TODAY + 200 days" +%F)
EXPECT_DAYS=$((RANGE_DAYS + 1))
U=$(date +%u)
MONDAY=$(date -d "$TODAY + $(( (8 - U) % 7 )) days" +%F)
ANCHOR=$(date -d "$TODAY + $(( (6 - U + 7) % 7 )) days" +%F)

check() { # label expected actual
  if [ "$2" = "$3" ]; then
    PASS=$((PASS + 1)); echo "PASS  $1"
  else
    FAIL=$((FAIL + 1)); echo "FAIL  $1: expected=$2 actual=$3"
  fi
}

json_count() { echo "$1" | jq 'length' 2>/dev/null; }

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
jstatus() { curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" "$API$1"; }

echo "=== Lịch làm việc (derived) smoke: $TODAY → $END (anchor T7=$ANCHOR) ==="

# ---- 0. Policy mặc định ----
DEFAULT_ID=$(jget "/api/v1/attendance/schedule-policies/default" | jq -r '.id // empty')
check "policy mặc định tồn tại" "yes" "$([ -n "$DEFAULT_ID" ] && echo yes || echo no)"
[ -n "$DEFAULT_ID" ] || exit 1

# ---- 1. Nhân sự ----
EMPS=$(jget "/api/v1/attendance/employees")
mapfile -t EIDS < <(echo "$EMPS" | jq -r --arg today "$TODAY" --arg lastDay "$END" '[.[] | select(.status != "TERMINATED") | select((.hiredAt == null) or (.hiredAt[0:10] <= $today)) | select((.terminatedAt == null) or (.terminatedAt[0:10] >= $lastDay))] | .[0:4][].employeeId')
check "đủ 4 nhân sự để test" "4" "${#EIDS[@]}"
[ "${#EIDS[@]}" -eq 4 ] || exit 1

# ---- 1b. Giới hạn range + lọc theo employeeIds (Phase 5 hiệu năng) ----
CAP_TO=$(date -d "$TODAY + 90 days" +%F)
check "range > 62 ngày → 422" "422" \
  "$(jstatus "/api/v1/attendance/rosters?from=$TODAY&to=$CAP_TO")"
EIDS_CSV=$(printf '%s,' "${EIDS[@]}"); EIDS_CSV=${EIDS_CSV%,}
BATCH=$(jget "/api/v1/attendance/rosters?employeeIds=$EIDS_CSV&from=$TODAY&to=$END")
check "employeeIds batch trả lịch đủ 4 NV" "$((EXPECT_DAYS * 4))" "$(json_count "$BATCH")"
check "employeeIds batch chỉ chứa NV yêu cầu" "0" \
  "$(echo "$BATCH" | jq --arg ids "$EIDS_CSV" '[.[] | select((.employeeId as $e | ($ids | split(",")) | index($e)) == null)] | length')"

# ---- 2. Ca (full/half) ----
SHIFTS=$(jget "/api/v1/attendance/work-shifts")
FULL_ID=$(echo "$SHIFTS" | jq -r '[.[] | select(.active and .shiftType=="FIXED" and .workingTime>=480)][0].id // empty')
if [ -z "$FULL_ID" ]; then
  FULL_ID=$(jpost "/api/v1/attendance/work-shifts" \
    "{\"name\":\"Smoke full $SUF\",\"shiftType\":\"FIXED\",\"startWorkTime\":\"08:00:00\",\"endWorkTime\":\"17:00:00\",\"startBreakTime\":\"12:00:00\",\"endBreakTime\":\"13:00:00\",\"workingTime\":480,\"breakingTime\":60,\"active\":true}" \
    | jq -r '.id // empty')
fi
HALF_ID=$(echo "$SHIFTS" | jq -r '[.[] | select(.active and .shiftType=="FIXED" and .workingTime==240)][0].id // empty')
HALF_CREATED=no
if [ -z "$HALF_ID" ]; then
  HALF_ID=$(jpost "/api/v1/attendance/work-shifts" \
    "{\"name\":\"Smoke half $SUF\",\"shiftType\":\"FIXED\",\"startWorkTime\":\"08:00:00\",\"endWorkTime\":\"12:00:00\",\"workingTime\":240,\"breakingTime\":0,\"active\":true}" \
    | jq -r '.id // empty')
  HALF_CREATED=yes
fi
[ -n "$FULL_ID" ] && [ -n "$HALF_ID" ] || { echo "Thiếu ca test"; exit 1; }

# ---- helpers ----
policy_body() { # mode name
  local mode=$1 name=$2 sat=null alt=null anchor=null
  case "$mode" in
    OFF) sat=null ;;
    ALL) sat="\"$FULL_ID\"" ;;
    HALF) sat="\"$HALF_ID\"" ;;
    ALTERNATING) sat="\"$FULL_ID\""; anchor="\"$ANCHOR\"" ;;
  esac
  printf '{"name":"%s","description":"smoke","monShiftId":"%s","tueShiftId":"%s","wedShiftId":"%s","thuShiftId":"%s","friShiftId":"%s","satShiftId":%s,"satAlternateShiftId":%s,"sunShiftId":null,"rotationAnchorDate":%s,"defaultPolicy":false,"active":true}' \
    "$name" "$FULL_ID" "$FULL_ID" "$FULL_ID" "$FULL_ID" "$FULL_ID" "$sat" "$alt" "$anchor"
}

JQ_DOW='def dow: (.workDate | strptime("%Y-%m-%d") | mktime | strftime("%u") | tonumber);'

clear_today_assignments() { # employeeId
  local eid=$1 aid
  for aid in $(jget "/api/v1/attendance/schedule-assignments?employeeId=$eid" \
      | jq -r --arg d "$TODAY" '.[] | select(.effectiveFrom >= $d) | .id'); do
    jdel "/api/v1/attendance/schedule-assignments/$aid" >/dev/null
  done
}

# ---- 3. Test từng mode ----
MODES=(OFF ALL HALF ALTERNATING)
for i in 0 1 2 3; do
  MODE=${MODES[$i]}
  EID=${EIDS[$i]}

  POL=$(jpost "/api/v1/attendance/schedule-policies" "$(policy_body "$MODE" "Smoke $MODE $SUF")")
  PID=$(echo "$POL" | jq -r '.id // empty')
  check "[$MODE] tạo policy" "yes" "$([ -n "$PID" ] && echo yes || echo no)"
  [ -n "$PID" ] || continue

  clear_today_assignments "$EID"
  ASG=$(jpost "/api/v1/attendance/schedule-assignments" \
    "{\"employeeId\":\"$EID\",\"policyId\":\"$PID\",\"effectiveFrom\":\"$TODAY\"}")
  AID=$(echo "$ASG" | jq -r '.id // empty')
  check "[$MODE] gán policy cho NV" "yes" "$([ -n "$AID" ] && echo yes || echo no)"

  ROWS=$(jget "/api/v1/attendance/rosters?employeeId=$EID&from=$TODAY&to=$END")
  check "[$MODE] lịch suy ra đủ ngày" "$EXPECT_DAYS" "$(json_count "$ROWS")"

  check "[$MODE] CN đều nghỉ" "true" \
    "$(echo "$ROWS" | jq "$JQ_DOW [.[]|select(dow==7)|.dayOff]|all")"
  check "[$MODE] T2-T6 đúng ca full" "0" \
    "$(echo "$ROWS" | jq --arg f "$FULL_ID" "$JQ_DOW [.[]|select(dow>=1 and dow<=5)|select(.dayOff or .shiftId != \$f)]|length")"

  case "$MODE" in
    OFF)
      check "[$MODE] T7 nghỉ hết" "0" \
        "$(echo "$ROWS" | jq "$JQ_DOW [.[]|select(dow==6)|select(.dayOff|not)]|length")"
      ;;
    ALL)
      check "[$MODE] T7 làm đủ ca" "0" \
        "$(echo "$ROWS" | jq --arg f "$FULL_ID" "$JQ_DOW [.[]|select(dow==6)|select(.dayOff or .shiftId != \$f)]|length")"
      ;;
    HALF)
      check "[$MODE] T7 dùng ca nửa ngày" "0" \
        "$(echo "$ROWS" | jq --arg h "$HALF_ID" "$JQ_DOW [.[]|select(dow==6)|select(.dayOff or .shiftId != \$h)]|length")"
      ;;
    ALTERNATING)
      check "[$MODE] T7 luân phiên chẵn/lẻ" "true" \
        "$(echo "$ROWS" | jq "$JQ_DOW [.[]|select(dow==6)]|sort_by(.workDate) as \$s | [range(0;(\$s|length))|(\$s[.].dayOff == ((.%2)==1))]|all")"
      check "[$MODE] T7 đầu tiên đi làm" "false" \
        "$(echo "$ROWS" | jq "$JQ_DOW [.[]|select(dow==6)]|sort_by(.workDate)|.[0].dayOff")"
      ;;
  esac

  # Ngày xa ngoài cửa sổ 45/90 cũ — derived phải resolve được (không cần "sinh lịch").
  ROW_FAR=$(jget "/api/v1/attendance/rosters/$EID/$FAR")
  check "[$MODE] resolve ngày xa ($FAR)" "$EID" "$(echo "$ROW_FAR" | jq -r '.employeeId // empty')"
  check "[$MODE] ngày xa không phải MANUAL_OVERRIDE" "no" \
    "$(echo "$ROW_FAR" | jq -r 'if .source == "MANUAL_OVERRIDE" then "yes" else "no" end')"

  # Manual override (chỉ future) — GET lại vẫn giữ; bỏ override → về policy.
  OV=$(jput "/api/v1/attendance/rosters/$EID/$MONDAY" '{"shiftId":null,"note":"smoke"}')
  check "[$MODE] override → nghỉ" "true" "$(echo "$OV" | jq '.dayOff')"
  ROW=$(jget "/api/v1/attendance/rosters/$EID/$MONDAY")
  check "[$MODE] đọc lại giữ MANUAL_OVERRIDE" "MANUAL_OVERRIDE" "$(echo "$ROW" | jq -r '.source')"
  jdel "/api/v1/attendance/rosters/$EID/$MONDAY" >/dev/null
  ROW=$(jget "/api/v1/attendance/rosters/$EID/$MONDAY")
  check "[$MODE] bỏ override → về policy" "false" "$(echo "$ROW" | jq '.dayOff')"

  # ---- cleanup: gỡ gán rồi xoá policy (xoá được vì không còn tham chiếu) ----
  [ -n "$AID" ] && jdel "/api/v1/attendance/schedule-assignments/$AID" >/dev/null
  DEL_CODE=$(jdel "/api/v1/attendance/schedule-policies/$PID")
  if [ "$DEL_CODE" != "204" ]; then
    jput "/api/v1/attendance/schedule-policies/$PID" \
      "$(policy_body "$MODE" "Smoke $MODE $SUF" | jq '.active=false')" >/dev/null
    check "[$MODE] dọn policy (xoá 204 hoặc ngưng)" "yes" "yes"
  fi
done

# ---- 4. Dọn ca tạm ----
if [ "$HALF_CREATED" = "yes" ]; then
  CODE=$(jdel "/api/v1/attendance/work-shifts/$HALF_ID")
  echo "INFO  dọn ca nửa ngày tạm: HTTP $CODE"
fi

echo "=== RESULT: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
