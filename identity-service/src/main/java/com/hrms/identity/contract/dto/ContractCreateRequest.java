package com.hrms.identity.contract.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ContractCreateRequest(
    @NotNull String contractType,
    @NotNull LocalDate startDate,
    LocalDate endDate,
    @Size(max = 100) String position,
    @PositiveOrZero Long salary,
    LocalDate signDate,
    @Size(max = 500) String note) {}
