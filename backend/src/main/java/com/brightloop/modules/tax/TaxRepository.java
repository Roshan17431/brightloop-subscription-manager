package com.brightloop.modules.tax;

import com.brightloop.config.AccountStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TaxRepository extends JpaRepository<TaxRule, Long> {
  @EntityGraph(attributePaths = {"product"})
  @Query(
      "select t from TaxRule t where (:status is null or t.status = :status) and"
          + " (lower(t.name) like concat('%', :q, '%') or lower(coalesce(t.description, '')) like"
          + " concat('%', :q, '%') or lower(coalesce(t.productType, '')) like concat('%', :q, '%'))")
  Page<TaxRule> search(String q, AccountStatus status, Pageable pageable);

  @EntityGraph(attributePaths = {"product"})
  @Query("select t from TaxRule t where t.status = com.brightloop.config.AccountStatus.ACTIVE order by t.id desc")
  List<TaxRule> findAllActive();
}
