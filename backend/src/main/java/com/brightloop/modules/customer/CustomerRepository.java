package com.brightloop.modules.customer;

import com.brightloop.config.AccountStatus;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
  Optional<Customer> findByUserId(Long userId);

  Optional<Customer> findByEmail(String email);

  @EntityGraph(attributePaths = "user")
  @Query(
      "select c from Customer c where (:status is null or c.status=:status) and (lower(c.name) like"
          + " concat('%',:q,'%') or c.email like concat('%',:q,'%') or"
          + " lower(coalesce(c.companyName,'')) like concat('%',:q,'%'))")
  Page<Customer> search(String q, AccountStatus status, Pageable pageable);
}
