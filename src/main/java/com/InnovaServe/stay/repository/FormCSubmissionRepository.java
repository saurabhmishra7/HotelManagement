package com.InnovaServe.stay.repository;

import com.InnovaServe.stay.entity.FormCSubmission;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormCSubmissionRepository extends JpaRepository<FormCSubmission, UUID> {
  Optional<FormCSubmission> findByTenantIdAndStayId(UUID tenantId, UUID stayId);
}
