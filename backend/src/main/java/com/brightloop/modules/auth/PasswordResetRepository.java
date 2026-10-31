package com.brightloop.modules.auth;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface PasswordResetRepository extends JpaRepository<PasswordResetToken, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from PasswordResetToken t join fetch t.user where t.tokenHash=:hash")
  Optional<PasswordResetToken> lockByHash(String hash);

  @Modifying
  @Query(
      "update PasswordResetToken t set t.usedAt=:now where t.user.id=:userId and t.usedAt is null")
  int invalidate(Long userId, java.time.Instant now);
}
