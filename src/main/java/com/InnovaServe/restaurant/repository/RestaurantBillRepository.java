package com.InnovaServe.restaurant.repository;import com.InnovaServe.restaurant.entity.RestaurantBill;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface RestaurantBillRepository extends JpaRepository<RestaurantBill,UUID>{Optional<RestaurantBill> findByTenantIdAndId(UUID t,UUID id);Optional<RestaurantBill> findByTenantIdAndOrderId(UUID t,UUID order);}
