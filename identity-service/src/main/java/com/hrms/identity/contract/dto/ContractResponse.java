package com.hrms.identity.contract.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * salary = null khi caller không có role HR_ADMIN (đóng dấu tại service —
 * không dựa vào FE để che lương).
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
    String status) {}
