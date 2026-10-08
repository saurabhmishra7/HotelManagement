package com.InnovaServe.restaurant.repository;

import com.InnovaServe.restaurant.entity.RestaurantBill;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantBillRepository extends JpaRepository<RestaurantBill, UUID> {
  Optional<RestaurantBill> findByTenantIdAndId(UUID t, UUID id);

  Optional<RestaurantBill> findByTenantIdAndOrderId(UUID t, UUID order);

  Optional<RestaurantBill> findByTenantIdAndInvoiceId(UUID t, UUID invoice);
}
