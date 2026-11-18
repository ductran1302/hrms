# Third-Party Notices — HRMS

Tài liệu này tổng hợp thông tin bản quyền và giấy phép của các thành phần bên
thứ ba được sử dụng trong HRMS. Đây là tài liệu kỹ thuật phục vụ tuân thủ giấy
phép, **không phải tư vấn pháp lý**.

## Phạm vi và cách tạo lại

| Thành phần | Báo cáo đầy đủ | Cách tạo lại |
|---|---|---|
| Backend Java/Maven (identity, gateway, attendance, hrms-common) | `THIRD-PARTY-BACKEND.txt` | `mvn org.codehaus.mojo:license-maven-plugin:2.4.0:aggregate-add-third-party -Dlicense.excludeTransitive=false` |
| Frontend React/npm | `THIRD-PARTY-FRONTEND.txt` | `cd frontend && npx license-checker --production --csv` |
| Toàn văn giấy phép | `LICENSES/` | Xem `LICENSES/README.md` |

`THIRD-PARTY-BACKEND.txt` hiện có **212 dependency** (gồm cả test scope).
`THIRD-PARTY-FRONTEND.txt` có **148 package** production. Các báo cáo này là
nguồn chi tiết theo từng artifact; phần dưới đây là tổng hợp và các nghĩa vụ
cần lưu ý.

## Backend (Java/Spring)

Các nhóm giấy phép chính trong báo cáo backend:

| Nhóm giấy phép | Ví dụ thành phần tiêu biểu |
|---|---|
| Apache-2.0 | Spring Boot/Spring Framework, Apache Kafka client, Apache Avro, Spring Cloud Gateway, springdoc, MapStruct, Flyway, Micrometer, Zipkin reporter, Confluent Avro client |
| MIT | Lombok, minimal-json, một số thư viện test |
| BSD-2-Clause / BSD-3-Clause | PostgreSQL JDBC, zstd-jni, một số thư viện JAXB |
| EPL-1.0 / EPL-2.0 | Logback (dual), Jakarta APIs, JUnit, AspectJ |
| EDL-1.0 | Jakarta Activation, JAXB, Angus Activation |
| LGPL-2.1-or-later | Hibernate ORM, Hibernate Commons Annotations |
| Public Domain / CC0 / MIT-0 | AOP Alliance, HdrHistogram, LatencyUtils, reactive-streams |
| Bouncy Castle Licence | Bouncy Castle Provider |

Lưu ý:

- **Không có GPL/AGPL/SSPL độc lập** trong báo cáo backend. Một số artifact
  Jakarta khai báo dual license EPL-2.0 hoặc GPL-2.0 kèm Classpath Exception;
  dự án sử dụng theo nhánh **EPL-2.0**.
- **Hibernate ORM (LGPL-2.1-or-later)**: chỉ dùng dạng jar không sửa đổi. Nếu
  phát hành binary, phải kèm giấy phép và bảo đảm có thể thay thế thư viện;
  nếu sửa Hibernate phải cung cấp source tương ứng.
- **Logback** dual EPL-1.0/LGPL-2.1; **JNA** dual Apache-2.0/LGPL-2.1. Dự án
  dùng unmodified, không tạo tác phẩm phái sinh.
- **Confluent Avro client** (`io.confluent:kafka-avro-serializer`,
  `kafka-schema-serializer`, `kafka-schema-registry-client`) là Apache-2.0.
  Riêng **Schema Registry server** dùng container Confluent có giấy phép khác —
  xem mục Container images.
- Các dependency test (JUnit, Testcontainers, Mockito, v.v.) không nằm trong
  artifact runtime nhưng vẫn được liệt kê để đầy đủ.
- `com.hrms:hrms-common` trong báo cáo là module nội bộ của dự án, không phải
  thành phần bên thứ ba.

## Frontend (React/npm)

| Giấy phép | Số package | Ghi chú |
|---|---:|---|
| MIT | 144 | React, React DOM, Ant Design, Pro Components, Axios, i18next, dayjs, React Router |
| Apache-2.0 | 2 | `keycloak-js`, `typescript` (dev) |
| 0BSD | 1 | `tslib` |
| UNLICENSED | 1 | `hrms-frontend` — package nội bộ, không phải bên thứ ba |

Không có GPL/AGPL/SSPL trong dependency frontend production.

## Container images và runtime

| Image | Giấy phép | Ghi chú |
|---|---|---|
| `postgres:16-alpine` | PostgreSQL License | Permissive |
| `apache/kafka:3.7.1` | Apache-2.0 | Apache Kafka |
| `confluentinc/cp-schema-registry:7.6.3` | **Confluent Community License** | Source-available, **không phải OSI**; xem cảnh báo bên dưới |
| `quay.io/keycloak/keycloak:25.0.6` | Apache-2.0 | Keycloak |
| `openzipkin/zipkin:3.4.1` | Apache-2.0 | Zipkin |
| `provectuslabs/kafka-ui:v0.7.2` | Apache-2.0 | Kafka UI |
| `nginx:1.27-alpine` | BSD-2-Clause | Nginx |
| `node:22-bookworm-slim` | MIT (Node.js) + giấy phép Debian | Chỉ dùng ở build stage frontend |
| `eclipse-temurin:21-jre-jammy` | GPL-2.0 **kèm Classpath Exception** (OpenJDK) | Runtime backend; dùng image chính thức không sửa đổi |

### Cảnh báo Confluent Community License

- Được phép dùng, sửa, phân phối cho mục đích nội bộ.
- **Không được** cung cấp Schema Registry (hoặc phần mềm dựa trên nó) như
  SaaS/PaaS/IaaS cạnh tranh với sản phẩm/dịch vụ tương ứng của Confluent.
- Nếu phân phối HRMS on-prem, phải kèm giấy phép Confluent Community License
  (`LICENSES/Confluent-Community-1.0.txt`) và nên được legal review.
- Phương án thay thế sạch giấy phép: **Apicurio Registry 3.x (Apache-2.0)**,
  có API tương thích Confluent tại `/apis/ccompat/v7`. Cần đổi image/env
  `schema-registry`, đổi `schema.registry.url` sang URL ccompat và migrate dữ
  liệu schema — chưa thực hiện trong repo này.

## Tài sản hình ảnh

- `keycloak/themes/hrms/login/resources/img/favicon.svg`: tài sản tự tạo của
  dự án.
- `keycloak/themes/hrms/login/resources/img/login-hero.jpg`: **nguồn gốc chưa
  được ghi nhận**. Cần xác nhận một trong các trường hợp:
  - ảnh do dự án tự tạo/chụp;
  - ảnh AI được tạo bằng công cụ có điều khoản cho phép dùng thương mại;
  - ảnh mua/được cấp phép, kèm bằng chứng license.
  Nếu không xác định được, thay bằng ảnh có license rõ ràng trước khi phát hành.
- Không dùng Google Fonts/CDN bên ngoài; font lấy từ hệ điều hành/Keycloak
  theme.
- Chi tiết nguồn/hash từng asset: xem `ASSETS.md` trong repo.

## Nghĩa vụ khi phân phối

- **Apache-2.0**: kèm bản sao giấy phép, giữ thông báo bản quyền/attribution và
  NOTICE (nếu có); ghi rõ nếu sửa đổi.
- **MIT/BSD/0BSD/MIT-0**: kèm copyright notice và permission notice.
- **EPL/LGPL**: kèm toàn văn giấy phép; không sửa đổi nếu không muốn phát sinh
  nghĩa vụ công bố source; giữ thư viện ở dạng có thể thay thế.
- **Confluent Community License**: kèm giấy phép; không dùng cho dịch vụ cạnh
  tranh; legal review trước khi bán on-prem/SaaS.
- **Container image**: giữ notice/SBOM của image gốc; không xóa thông tin bản
  quyền trong image.
- Với SaaS nội bộ, việc phân phối binary có thể không xảy ra, nhưng vẫn nên giữ
  các tài liệu này để audit và tuân thủ CCL.

## Việc cần làm trước khi phát hành thương mại

1. Xác nhận nguồn/license của `login-hero.jpg`.
2. Legal review Confluent Community License và nghĩa vụ LGPL/EPL.
3. Cân nhắc thay Schema Registry bằng Apicurio Registry (Apache-2.0).
4. Đóng gói `THIRD-PARTY-NOTICES.md`, các báo cáo và `LICENSES/` vào artifact
   phát hành (Docker image đã được cấu hình copy các file này).
