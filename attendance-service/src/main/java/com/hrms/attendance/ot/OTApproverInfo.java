package com.hrms.attendance.ot;

/** Thông tin người duyệt trực tiếp (manager) của 1 nhân viên — FE hiển thị thẻ approver. */
public record OTApproverInfo(String fullName, String employeeCode, String position) {}
