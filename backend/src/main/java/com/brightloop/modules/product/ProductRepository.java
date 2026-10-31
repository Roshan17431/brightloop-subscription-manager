package com.brightloop.modules.product;

import com.brightloop.config.AccountStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface ProductRepository extends JpaRepository<Product, Long> {
  @Query(
      "select p from Product p where (:status is null or p.status=:status) and (lower(p.name) like"
          + " concat('%',:q,'%') or lower(p.type) like concat('%',:q,'%'))")
  Page<Product> search(String q, AccountStatus status, Pageable pageable);
}
