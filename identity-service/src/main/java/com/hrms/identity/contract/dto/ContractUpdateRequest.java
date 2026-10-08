package com.hrms.identity.contract.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** PATCH: field null = không đổi; status nhận ACTIVE/EXPIRED/TERMINATED. */
public record ContractUpdateRequest(
    String contractType,
    LocalDate startDate,
    LocalDate endDate,
    @Size(max = 100) String position,
    @PositiveOrZero Long salary,
    LocalDate signDate,
    @Size(max = 500) String note,
    String status) {}
