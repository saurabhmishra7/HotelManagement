package com.InnovaServe.core.repository;

import com.InnovaServe.core.entity.InvoiceLineItem;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceLineItemRepository extends JpaRepository<InvoiceLineItem, UUID> {
  List<InvoiceLineItem> findAllByTenantIdAndInvoiceId(UUID t, UUID invoice);
}
