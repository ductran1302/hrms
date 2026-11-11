package com.hrms.identity.jobtitle.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** PATCH: field null = không đổi. */
public record JobTitleUpdateRequest(
    @Size(max = 30) String code,
    @Size(max = 100) String name,
    @Size(max = 100) String nameEn,
    @Size(max = 30) String category,
    @Min(0) Integer sortOrder) {}
