package com.hrms.identity.department;

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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
public class Department extends BaseEntity {

  @Column(name = "code", nullable = false, length = 20)
  private String code;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private Department parent;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "manager_id")
  private Employee manager;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private DepartmentStatus status;

  public UUID getParentId() {
    return parent == null ? null : parent.getId();
  }

  public UUID getManagerId() {
    return manager == null ? null : manager.getId();
  }

  public String getManagerName() {
    return manager == null ? null : manager.getFullName();
  }
}
