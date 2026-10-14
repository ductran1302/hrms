package com.hrms.identity.jobtitle;

import com.hrms.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Danh mục chức vụ — nguồn cho dropdown "Chức danh" (mã code không đổi). */
@Entity
@Table(name = "job_titles")
@Getter
@Setter
@NoArgsConstructor
public class JobTitle extends BaseEntity {

  @Column(name = "code", nullable = false, length = 30)
  private String code;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "name_en", nullable = false, length = 100)
  private String nameEn;

  @Column(name = "category", nullable = false, length = 30)
  private String category;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;
}
