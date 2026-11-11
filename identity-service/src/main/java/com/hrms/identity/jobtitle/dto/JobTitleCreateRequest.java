package com.hrms.identity.jobtitle.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobTitleCreateRequest(
    @NotBlank @Size(max = 30) String code,
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 100) String nameEn,
    @NotBlank @Size(max = 30) String category,
    @Min(0) int sortOrder) {}
