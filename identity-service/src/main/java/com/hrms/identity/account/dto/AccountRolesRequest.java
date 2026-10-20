package com.hrms.identity.account.dto;

import java.util.List;

/**
 * Request đổi roles của tài khoản (màn Cài đặt → Tài khoản, chỉ ADMIN).
 *
 * @param roles tập role mới — subset của {@code Roles.all()}
 *     ({@code ADMIN/GLOBAL_LEAD/DEPT_LEAD/MANAGER/EMPLOYEE}), ít nhất 1 phần tử
 */
public record AccountRolesRequest(List<String> roles) {}
