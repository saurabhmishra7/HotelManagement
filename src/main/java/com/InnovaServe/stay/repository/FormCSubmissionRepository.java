package com.InnovaServe.stay.repository;import com.InnovaServe.stay.entity.FormCSubmission;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface FormCSubmissionRepository extends JpaRepository<FormCSubmission,UUID>{Optional<FormCSubmission> findByTenantIdAndStayId(UUID tenantId,UUID stayId);}
