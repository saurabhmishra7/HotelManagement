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

  List<PublicSignupIntent> findAllByStatusAndExpiresAtBefore(String status, Instant now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from PublicSignupIntent i where i.id = :id")
  Optional<PublicSignupIntent> lockById(@Param("id") UUID id);
}
