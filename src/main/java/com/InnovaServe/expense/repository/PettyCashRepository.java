package com.InnovaServe.expense.repository;

import com.InnovaServe.expense.entity.PettyCashLedger;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PettyCashRepository extends JpaRepository<PettyCashLedger, UUID> {
  Optional<PettyCashLedger> findByTenantIdAndId(UUID t, UUID id);
}
