package com.hrms.identity.jobtitle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobTitleRepository extends JpaRepository<JobTitle, UUID> {

  Optional<JobTitle> findByCode(String code);

  boolean existsByCode(String code);

  boolean existsByCodeAndIdNot(String code, UUID id);

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, UUID id);

  List<JobTitle> findAllByOrderBySortOrderAsc();
}
