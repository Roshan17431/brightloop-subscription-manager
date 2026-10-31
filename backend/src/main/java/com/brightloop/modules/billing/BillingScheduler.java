package com.brightloop.modules.billing;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "app.billing.scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class BillingScheduler {
  private static final Logger log = LoggerFactory.getLogger(BillingScheduler.class);
  private final BillingService billingService;

  public BillingScheduler(BillingService billingService) {
    this.billingService = billingService;
  }

  @Scheduled(cron = "${app.billing.cron:0 0 2 * * *}")
  public void executeDailyScheduledBilling() {
    var today = LocalDate.now();
    log.info("Running scheduled recurring billing for {}", today);
    var result = billingService.runScheduledBilling(today);
    log.info(
        "Scheduled billing run {} finished with status {} (invoices={})",
        result.id(),
        result.status(),
        result.invoicesGenerated());
  }
}
