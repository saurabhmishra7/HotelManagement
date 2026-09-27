package com.InnovaServe.restaurant.repository;import com.InnovaServe.restaurant.entity.DiningTable;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface DiningTableRepository extends JpaRepository<DiningTable,UUID>{List<DiningTable> findAllByTenantIdOrderByTableNumber(UUID t);Optional<DiningTable> findByTenantIdAndId(UUID t,UUID id);}
