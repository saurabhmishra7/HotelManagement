package com.InnovaServe.expense.repository;import com.InnovaServe.expense.entity.PettyCashLedger;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface PettyCashRepository extends JpaRepository<PettyCashLedger,UUID>{Optional<PettyCashLedger> findByTenantIdAndId(UUID t,UUID id);}
