package com.hrms.identity.contract;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job nightly: chuyển hợp đồng ACTIVE quá ngày hết hạn sang EXPIRED — hợp đồng ký nhưng
 * chưa đến ngày bắt đầu không bị job đụng tới (PENDING giữ nguyên).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContractExpiryJob {

  private final ContractService contractService;

  @Scheduled(cron = "0 15 1 * * *")
  public void expireOverdueContracts() {
    int expired = contractService.expireOverdue();
    if (expired > 0) {
      log.info("ContractExpiryJob: chuyển {} hợp đồng quá hạn sang EXPIRED", expired);
    }
  }
}
