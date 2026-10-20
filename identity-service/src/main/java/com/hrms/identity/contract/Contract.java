package com.hrms.identity.contract;

import com.hrms.common.base.BaseEntity;
import com.hrms.identity.employee.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "contracts")
@Getter
@Setter
@NoArgsConstructor
public class Contract extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "employee_id", nullable = false)
  private Employee employee;

  @Enumerated(EnumType.STRING)
  @Column(name = "contract_type", nullable = false, length = 30)
  private ContractType contractType;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "position", length = 100)
  private String position;

  /** Lương VND — chỉ ADMIN/GLOBAL_LEAD được đọc (ContractResponse ẩn khi không đủ quyền). */
  @Column(name = "salary")
  private Long salary;

  @Column(name = "sign_date")
  private LocalDate signDate;

  @Column(name = "note", length = 500)
  private String note;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ContractStatus status;

  /** Hợp đồng mà hợp đồng này thay thế (null nếu không phải hợp đồng ký thay). */
  @Column(name = "replaces_contract_id")
  private UUID replacesContractId;

  public UUID getEmployeeId() {
    return employee == null ? null : employee.getId();
  }
}
