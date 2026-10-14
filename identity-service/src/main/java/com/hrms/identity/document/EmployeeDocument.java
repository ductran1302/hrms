package com.hrms.identity.document;

import com.hrms.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Metadata tài liệu đính kèm — file nằm trên đĩa tại
 * {@code hrms.storage.documents-dir}/{@code employeeId}/{mã}. Path trong DB là relative để
 * đổi chỗ mount volume không phải sửa data.
 */
@Entity
@Table(name = "employee_documents")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeDocument extends BaseEntity {

  @Column(name = "employee_id", nullable = false)
  private UUID employeeId;

  /** Tên hiển thị của loại — lưu string để mở rộng không cần đổi enum/DB. */
  @Column(name = "doc_type", nullable = false, length = 50)
  private String docType;

  @Column(name = "file_name", nullable = false)
  private String fileName;

  @Column(name = "content_type", length = 100)
  private String contentType;

  @Column(name = "file_size", nullable = false)
  private long fileSize;

  /** Relative tới documents-dir, ví dụ {@code <employeeId>/<uuid>.pdf}. */
  @Column(name = "file_path", nullable = false, length = 500)
  private String filePath;

  @Column(name = "note", length = 500)
  private String note;

  @Column(name = "uploaded_by", length = 100)
  private String uploadedBy;

  @Column(name = "uploaded_at", nullable = false)
  private Instant uploadedAt;
}
