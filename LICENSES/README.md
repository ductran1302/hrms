# License texts

Thư mục này chứa toàn văn các giấy phép của thành phần bên thứ ba được dùng
trong HRMS. Danh sách dependency và license tương ứng nằm ở:

- `../THIRD-PARTY-BACKEND.txt` — dependency Java/Maven (gồm cả test).
- `../THIRD-PARTY-FRONTEND.txt` — dependency npm (production).
- `../THIRD-PARTY-NOTICES.md` — tổng hợp, nghĩa vụ và các điểm cần lưu ý.

| File | Giấy phép | Dùng cho |
|---|---|---|
| `Apache-2.0.txt` | Apache License 2.0 | Spring Boot, Kafka client, Avro, Keycloak, nhiều thư viện Apache |
| `MIT.txt` | MIT | React, Ant Design, Axios, i18next, nhiều thư viện npm |
| `BSD-2-Clause.txt` | BSD 2-Clause | zstd-jni, nginx |
| `BSD-3-Clause.txt` | BSD 3-Clause | Một số thư viện Java/npm và Go License của RE2/J |
| `0BSD.txt` | Zero-Clause BSD | tslib |
| `EPL-1.0.txt` | Eclipse Public License 1.0 | Logback (dual EPL-1.0/LGPL-2.1), JUnit 4 |
| `EPL-2.0.txt` | Eclipse Public License 2.0 | Jakarta APIs, JUnit 5, AspectJ |
| `LGPL-2.1-or-later.txt` | GNU LGPL 2.1 or later | Hibernate ORM, Hibernate Commons Annotations, JNA (dual) |
| `PostgreSQL.txt` | PostgreSQL License | PostgreSQL JDBC driver, PostgreSQL container image |
| `CC0-1.0.txt` | Creative Commons CC0 1.0 | Public-domain-style components (HdrHistogram, LatencyUtils) |
| `MIT-0.txt` | MIT No Attribution | reactive-streams |
| `Bouncy-Castle.txt` | Bouncy Castle Licence | Bouncy Castle Provider |
| `GPL-2.0-only.txt` | GNU GPL 2.0 | OpenJDK runtime (Temurin base image) |
| `Classpath-exception-2.0.txt` | Classpath Exception 2.0 | OpenJDK runtime (Temurin base image) |

Ghi chú:

- **OpenJDK/Temurin** dùng GPL-2.0 **kèm Classpath Exception**; xem
  `GPL-2.0-only.txt` + `Classpath-exception-2.0.txt`.
- **Eclipse Distribution License 1.0 (EDL-1.0)** dùng cùng nội dung
  **BSD-3-Clause**; xem `BSD-3-Clause.txt`.
- **Go License** của `com.google.re2j:re2j` là dạng BSD-3-Clause; xem
  `BSD-3-Clause.txt`.
- Các file trên lấy từ SPDX License List
  (https://github.com/spdx/license-list-data), trừ `Bouncy-Castle.txt`.
- Khi phân phối binary/container, giữ nguyên thư mục này và
  `THIRD-PARTY-NOTICES.md` trong gói phát hành.
