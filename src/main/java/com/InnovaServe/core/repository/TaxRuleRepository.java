package com.InnovaServe.core.repository;

import com.InnovaServe.core.entity.TaxRule;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxRuleRepository extends JpaRepository<TaxRule, UUID> {
  List<TaxRule> findAllByTenantIdAndAppliesToAndEffectiveFromLessThanEqualAndEffectiveToIsNull(
      UUID t, String applies, LocalDate date);

  List<TaxRule>
      findAllByTenantIdAndAppliesToAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqual(
          UUID t, String applies, LocalDate from, LocalDate to);

  List<TaxRule> findAllByTenantId(UUID t);

  Optional<TaxRule> findByTenantIdAndId(UUID t, UUID id);
}
