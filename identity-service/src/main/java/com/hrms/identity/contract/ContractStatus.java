package com.hrms.identity.contract;

/**
 * Trạng thái nghiệp vụ của hợp đồng — tách khỏi trạng thái hiệu lực tính theo ngày
 * (xem {@code ContractService#effectiveStatus}): hợp đồng PENDING đã ký nhưng chưa đến
 * ngày bắt đầu vẫn chưa có hiệu lực; ACTIVE quá ngày hết hạn chờ job chuyển EXPIRED.
 */
public enum ContractStatus {
  PENDING,
  ACTIVE,
  EXPIRED,
  TERMINATED
}
