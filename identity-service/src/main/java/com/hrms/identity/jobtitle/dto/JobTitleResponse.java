package com.hrms.identity.jobtitle.dto;

public record JobTitleResponse(
    String code, String name, String nameEn, String category, int sortOrder) {}
