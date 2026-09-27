package com.InnovaServe.core.repository;import com.InnovaServe.core.entity.InvoiceLineItem;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface InvoiceLineItemRepository extends JpaRepository<InvoiceLineItem,UUID>{List<InvoiceLineItem> findAllByTenantIdAndInvoiceId(UUID t,UUID invoice);}
