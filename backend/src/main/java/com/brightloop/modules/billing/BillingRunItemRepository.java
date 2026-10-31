package com.brightloop.modules.billing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingRunItemRepository extends JpaRepository<BillingRunItem, Long> {
  List<BillingRunItem> findByBillingRunIdOrderByIdAsc(Long billingRunId);
}
