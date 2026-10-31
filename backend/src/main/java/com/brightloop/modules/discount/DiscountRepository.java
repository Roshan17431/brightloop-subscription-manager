package com.brightloop.modules.discount;

import com.brightloop.config.AccountStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DiscountRepository extends JpaRepository<Discount, Long> {
  @EntityGraph(attributePaths = {"product", "subscription"})
  @Query(
      "select d from Discount d where (:status is null or d.status = :status) and"
          + " (lower(d.name) like concat('%', :q, '%') or lower(coalesce(d.code, '')) like"
          + " concat('%', :q, '%') or lower(coalesce(d.description, '')) like concat('%', :q,"
          + " '%'))")
  Page<Discount> search(String q, AccountStatus status, Pageable pageable);

  @EntityGraph(attributePaths = {"product", "subscription"})
  @Query(
      "select d from Discount d where d.status = com.brightloop.config.AccountStatus.ACTIVE and"
          + " (d.usageLimit is null or d.usedCount < d.usageLimit) order by d.id desc")
  List<Discount> findAllActiveWithCapacity();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from Discount d where d.id = :id")
  Optional<Discount> lockById(Long id);
}
