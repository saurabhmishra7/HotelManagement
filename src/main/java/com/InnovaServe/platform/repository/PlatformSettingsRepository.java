package com.InnovaServe.platform.repository;

import com.InnovaServe.platform.entity.PlatformSettings;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from PlatformSettings s where s.id = :id")
  Optional<PlatformSettings> lockById(@Param("id") String id);
}
