package com.brightloop.modules.user;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
  Optional<UserAccount> findByEmail(String email);

  boolean existsByEmail(String email);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select u from UserAccount u where u.role=com.brightloop.modules.user.Role.ADMIN order by"
          + " u.id")
  java.util.List<UserAccount> lockAdministrators();

  long countByRole(Role role);

  @Query(
      "select u from UserAccount u where lower(u.name) like concat('%',:q,'%') or u.email like"
          + " concat('%',:q,'%')")
  Page<UserAccount> search(String q, Pageable pageable);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from UserAccount u where u.id=:id")
  Optional<UserAccount> lockById(Long id);
}
