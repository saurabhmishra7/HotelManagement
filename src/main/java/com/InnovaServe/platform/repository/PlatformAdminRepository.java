package com.InnovaServe.platform.repository;

import com.InnovaServe.platform.entity.PlatformAdmin;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, UUID> {
  Optional<PlatformAdmin> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  List<PlatformAdmin> findAllByOrderByNameAsc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from PlatformAdmin a order by a.id")
  List<PlatformAdmin> lockAllAdmins();
}
