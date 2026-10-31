package com.brightloop.modules.plan;

import com.brightloop.config.AccountStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface PlanRepository extends JpaRepository<Plan, Long> {
  @Query(
      "select p from Plan p where (:status is null or p.status=:status) and lower(p.name) like"
          + " concat('%',:q,'%')")
  Page<Plan> search(String q, AccountStatus status, Pageable pageable);
}
