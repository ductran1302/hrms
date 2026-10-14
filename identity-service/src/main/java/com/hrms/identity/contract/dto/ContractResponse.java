package com.hrms.identity.contract.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * salary = null khi caller không có role HR_ADMIN (che tại service — không đưa vào FE
 * để che lương).
 * effectiveStatus = trạng thái hiệu lực tính theo ngày (tách khỏi status nghiệp vụ):
 * NOT_YET_EFFECTIVE / IN_EFFECT / ENDED / EXPIRED / TERMINATED.
 */
public record ContractResponse(
    UUID id,
    UUID employeeId,
    String employeeCode,
    String employeeName,
    String contractType,
    LocalDate startDate,
    LocalDate endDate,
    String position,
    Long salary,
    LocalDate signDate,
    String note,
    String status,
    String effectiveStatus,
    UUID replacesContractId) {}
