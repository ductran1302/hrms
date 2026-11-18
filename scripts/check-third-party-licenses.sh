#!/usr/bin/env bash
# Kiểm tra báo cáo license backend (license-maven-plugin) cho license bị cấm.
# Dùng trong CI sau khi chạy:
#   mvn -B -q org.codehaus.mojo:license-maven-plugin:2.4.0:aggregate-add-third-party \
#     -Dlicense.excludeTransitive=false
set -euo pipefail

REPORT="${1:-target/generated-sources/license/THIRD-PARTY.txt}"

if [[ ! -f "$REPORT" ]]; then
  echo "FAIL  Không tìm thấy báo cáo license: $REPORT" >&2
  exit 2
fi

# Không chặn LGPL (Hibernate/JNA) hay GPL2 w/ CPE (Jakarta dual license).
# Chỉ chặn AGPL/SSPL và GPL độc lập.
PATTERN='\b(AGPL|SSPL)\b|\bGNU General Public License\b|\bGPL-2\.0\b|\bGPL-3\b|\bGPLv3\b'

if grep -Eiq "$PATTERN" "$REPORT"; then
  echo "FAIL  Phát hiện license bị cấm trong $REPORT:" >&2
  grep -Ein "$PATTERN" "$REPORT" >&2
  exit 1
fi

echo "PASS  Backend không có AGPL/SSPL/GPL độc lập ($REPORT)"
