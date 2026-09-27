package com.InnovaServe.stay.repository;import com.InnovaServe.stay.entity.StayCharge;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface StayChargeRepository extends JpaRepository<StayCharge,UUID>{List<StayCharge> findAllByTenantIdAndStayId(UUID tenantId,UUID stayId);}
