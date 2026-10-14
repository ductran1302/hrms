package com.hrms.identity.jobtitle;

import com.hrms.identity.jobtitle.dto.JobTitleResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobTitleService {

  private final JobTitleRepository jobTitleRepository;

  @Transactional(readOnly = true)
  public List<JobTitleResponse> list() {
    return jobTitleRepository.findAllByOrderBySortOrderAsc().stream()
        .map(t -> new JobTitleResponse(
            t.getCode(), t.getName(), t.getNameEn(), t.getCategory(), t.getSortOrder()))
        .toList();
  }
}
