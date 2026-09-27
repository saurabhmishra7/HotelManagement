package com.InnovaServe.core.repository;import com.InnovaServe.core.entity.StaffRole;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface StaffRoleRepository extends JpaRepository<StaffRole,UUID>{List<StaffRole> findAllByTenantIdOrderByName(UUID tenantId);Optional<StaffRole> findByTenantIdAndId(UUID tenantId,UUID id);}
