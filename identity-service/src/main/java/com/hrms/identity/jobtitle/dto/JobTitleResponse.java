package com.hrms.identity.jobtitle.dto;

import java.util.UUID;

public record JobTitleResponse(
    UUID id, String code, String name, String nameEn, String category, int sortOrder) {}
