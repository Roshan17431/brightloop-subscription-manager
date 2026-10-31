package com.brightloop.modules.subscription;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
  @EntityGraph(attributePaths = {"customer", "plan"})
  @Query(
      "select s from Subscription s where (:customerId is null or s.customer.id=:customerId) and"
          + " (:state is null or s.state=:state) and (lower(s.number) like concat('%',:q,'%') or"
          + " lower(s.customer.name) like concat('%',:q,'%') or lower(s.planName) like"
          + " concat('%',:q,'%'))")
  Page<Subscription> search(String q, SubscriptionState state, Long customerId, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Subscription s where s.id=:id")
  Optional<Subscription> lockById(Long id);
}
