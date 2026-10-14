package com.hrms.identity.contract.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Gia hạn hợp đồng: ngày kết thúc mới phải sau ngày bắt đầu và sau ngày hết hạn cũ (nếu có). */
public record ContractExtendRequest(
    @NotNull LocalDate endDate,
    @Size(max = 500) String note) {}
