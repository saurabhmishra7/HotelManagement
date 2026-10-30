package com.InnovaServe.platform.repository;

import com.InnovaServe.platform.entity.PublicSignupIntent;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicSignupIntentRepository extends JpaRepository<PublicSignupIntent, UUID> {
  boolean existsByOwnerEmailIgnoreCaseAndStatus(String ownerEmail, String status);
  boolean existsByOwnerEmailIgnoreCaseAndStatusAndIdNot(String ownerEmail, String status, UUID id);

  boolean existsByOwnerEmailIgnoreCaseAndStatusInAndExpiresAtAfter(
      String ownerEmail, List<String> statuses, Instant now);
  boolean existsByOwnerEmailIgnoreCaseAndStatusInAndIdNotAndExpiresAtAfter(
      String ownerEmail, List<String> statuses, UUID id, Instant now);
  List<PublicSignupIntent> findAllByOwnerEmailIgnoreCaseAndStatusInAndExpiresAtBefore(
      String ownerEmail, List<String> statuses, Instant now);
  List<PublicSignupIntent> findAllByStatusInAndExpiresAtBefore(List<String> statuses, Instant now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from PublicSignupIntent i where i.emailVerificationTokenHash = :hash")
  Optional<PublicSignupIntent> lockByEmailVerificationTokenHash(@Param("hash") String hash);

  List<PublicSignupIntent> findAllByStatusAndExpiresAtBefore(String status, Instant now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from PublicSignupIntent i where i.id = :id")
  Optional<PublicSignupIntent> lockById(@Param("id") UUID id);
}
