package com.InnovaServe.restaurant.service;

import com.InnovaServe.core.entity.*;
import com.InnovaServe.core.repository.*;
import com.InnovaServe.core.service.BillingService;
import com.InnovaServe.core.service.TenantContext;
import com.InnovaServe.core.service.ModuleEntitlementService;
import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.contracts.StayLookupPort;
import com.InnovaServe.contracts.StayLookupPort.StaySummary;
import com.InnovaServe.restaurant.entity.*;
import com.InnovaServe.restaurant.repository.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GuestStaySpendService {
  private final TenantContext tenant;
  private final StayLookupPort stayLookup;
  private final CustomerRepository customers;
  private final RestaurantOrderRepository orders;
  private final OrderItemRepository orderItems;
  private final RestaurantBillRepository bills;
  private final InvoiceLineItemRepository invoiceLines;
  private final PaymentRepository payments;
  private final CreditNoteRepository creditNotes;
  private final BillingService billing;
  private final ModuleEntitlementService moduleEntitlements;

  public GuestStaySpendService(
      TenantContext tenant,
      StayLookupPort stayLookup,
      CustomerRepository customers,
      RestaurantOrderRepository orders,
      OrderItemRepository orderItems,
      RestaurantBillRepository bills,
      InvoiceLineItemRepository invoiceLines,
      PaymentRepository payments,
      CreditNoteRepository creditNotes,
      BillingService billing,
      ModuleEntitlementService moduleEntitlements) {
    this.tenant = tenant;
    this.stayLookup = stayLookup;
    this.customers = customers;
    this.orders = orders;
    this.orderItems = orderItems;
    this.bills = bills;
    this.invoiceLines = invoiceLines;
    this.payments = payments;
    this.creditNotes = creditNotes;
    this.billing = billing;
    this.moduleEntitlements = moduleEntitlements;
  }

  public Map<String, Object> forStay(UUID stayId, String requestedSource) {
    String source = normalizeSource(requestedSource);
    UUID tenantId = tenant.tenantId();
    Set<String> entitledModules = Set.copyOf(moduleEntitlements.activeModules(tenantId));
    if ("restaurant".equals(source))
      requireRestaurant(entitledModules);
    StaySummary stay =
        stayLookup
            .findById(tenantId, stayId)
            .orElseThrow(() -> new NoSuchElementException("Stay not found"));
    Customer customer =
        customers
            .findByTenantIdAndId(tenantId, stay.customerId())
            .orElseThrow(() -> new NoSuchElementException("Stay customer not found"));

    List<RestaurantOrder> stayOrders =
        "stay".equals(source) || !entitledModules.contains(ModuleType.RESTAURANT.key())
            ? List.of()
            : orders.findAllByTenantIdAndStayIdOrderByCreatedAtDesc(tenantId, stayId);
    Map<UUID, Invoice> invoices = new LinkedHashMap<>();
    Map<UUID, List<UUID>> orderIdsByInvoice = new HashMap<>();
    for (Invoice invoice : billing.invoicesForAccount(stay.accountId()))
      if (isIncludedAndEntitled(source, invoice.getSourceModule(), entitledModules))
        invoices.put(invoice.getId(), invoice);
    for (RestaurantOrder order : stayOrders) {
      bills
          .findByTenantIdAndOrderId(tenantId, order.getId())
          .ifPresent(
              bill -> {
                Invoice invoice = billing.getInvoice(bill.getInvoiceId());
                if (isIncludedAndEntitled(source, invoice.getSourceModule(), entitledModules))
                  invoices.put(invoice.getId(), invoice);
                orderIdsByInvoice
                    .computeIfAbsent(invoice.getId(), ignored -> new ArrayList<>())
                    .add(order.getId());
              });
    }

    BigDecimal invoicedTotal = BigDecimal.ZERO;
    BigDecimal paidTotal = BigDecimal.ZERO;
    BigDecimal creditTotal = BigDecimal.ZERO;
    BigDecimal amountDue = BigDecimal.ZERO;
    List<Map<String, Object>> invoiceHistory = new ArrayList<>();
    List<InvoiceLineItem> stayInvoiceLines = new ArrayList<>();

    for (Invoice invoice : invoices.values()) {
      List<InvoiceLineItem> lines =
          invoiceLines.findAllByTenantIdAndInvoiceId(tenantId, invoice.getId());
      List<Payment> invoicePayments =
          payments.findAllByTenantIdAndInvoiceIdOrderByPaidAt(tenantId, invoice.getId());
      List<CreditNote> invoiceCredits =
          creditNotes.findAllByTenantIdAndOriginalInvoiceIdOrderByCreatedAtDesc(
              tenantId, invoice.getId());
      BigDecimal paid =
          invoicePayments.stream()
              .map(Payment::getAmount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      BigDecimal credited =
          invoiceCredits.stream()
              .map(CreditNote::getAmount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      BigDecimal due = billing.amountDue(invoice);

      invoicedTotal = invoicedTotal.add(invoice.getTotalAmount());
      paidTotal = paidTotal.add(paid);
      creditTotal = creditTotal.add(credited);
      amountDue = amountDue.add(due);
      if ("stay".equals(invoice.getSourceModule())) stayInvoiceLines.addAll(lines);

      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("invoice", invoice);
      entry.put("line_items", lines);
      entry.put("payments", invoicePayments);
      entry.put("credit_notes", invoiceCredits);
      entry.put("restaurant_order_ids", orderIdsByInvoice.getOrDefault(invoice.getId(), List.of()));
      entry.put("paid_total", paid);
      entry.put("credit_total", credited);
      entry.put("amount_due", due);
      invoiceHistory.add(entry);
    }

    List<Map<String, Object>> chargeHistory =
        "restaurant".equals(source)
            ? List.of()
            : stayLookup.findCharges(tenantId, stayId).stream()
            .map(
                charge -> {
                  boolean invoiced =
                      stayInvoiceLines.stream()
                          .anyMatch(
                              line ->
                                  line.getDescription().equals(charge.description())
                                      && line.getUnitPrice().compareTo(charge.amount()) == 0);
                  return Map.<String, Object>of(
                      "id", charge.id(),
                      "description", charge.description(),
                      "amount", charge.amount(),
                      "included_in_stay_invoice", invoiced);
                })
                .toList();

    List<Map<String, Object>> openOrders =
        ("stay".equals(source) ? List.<RestaurantOrder>of() : stayOrders).stream()
            .filter(order -> "open".equals(order.getStatus()))
            .map(
                order ->
                    Map.<String, Object>of(
                        "order_id", order.getId(),
                        "order_type", order.getOrderType(),
                        "items", orderItems.findAllByTenantIdAndOrderId(tenantId, order.getId())))
            .toList();

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("source", source);
    result.put("stay_id", stay.id());
    result.put("stay_status", stay.status());
    result.put("customer", Map.of("id", customer.getId(), "name", customer.getName()));
    result.put("invoice_history", invoiceHistory);
    result.put("stay_charges", chargeHistory);
    result.put("open_restaurant_orders", openOrders);
    result.put("invoiced_total", invoicedTotal);
    result.put("paid_total", paidTotal);
    result.put("credit_total", creditTotal);
    result.put("amount_due", amountDue);
    return result;
  }

  public Map<String, Object> forCustomer(UUID customerId, String requestedSource) {
    String source = normalizeSource(requestedSource);
    UUID tenantId = tenant.tenantId();
    Set<String> entitledModules = Set.copyOf(moduleEntitlements.activeModules(tenantId));
    if ("restaurant".equals(source))
      requireRestaurant(entitledModules);
    Customer customer =
        customers
            .findByTenantIdAndId(tenantId, customerId)
            .orElseThrow(() -> new NoSuchElementException("Customer not found"));
    List<StaySummary> customerStays = stayLookup.findAllByCustomerId(tenantId, customerId);
    List<Map<String, Object>> histories =
        customerStays.stream().map(stay -> forStay(stay.id(), source)).toList();
    BigDecimal invoiced = total(histories, "invoiced_total");
    BigDecimal paid = total(histories, "paid_total");
    BigDecimal credits = total(histories, "credit_total");
    BigDecimal due = total(histories, "amount_due");

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("customer_id", customer.getId());
    result.put("source", source);
    result.put("customer_name", customer.getName());
    result.put("stays", histories);
    result.put("invoiced_total", invoiced);
    result.put("paid_total", paid);
    result.put("credit_total", credits);
    result.put("amount_due", due);
    return result;
  }

  private BigDecimal total(List<Map<String, Object>> histories, String field) {
    return histories.stream()
        .map(history -> (BigDecimal) history.get(field))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private String normalizeSource(String source) {
    if (source == null || source.isBlank() || "all".equalsIgnoreCase(source)) return "all";
    String normalized = source.trim().toLowerCase(Locale.ROOT);
    if (!Set.of("stay", "restaurant").contains(normalized))
      throw new IllegalArgumentException("source must be all, stay, or restaurant");
    return normalized;
  }

  private boolean includesSource(String requested, String actual) {
    return "all".equals(requested)
        || (actual != null && requested.equals(actual.trim().toLowerCase(Locale.ROOT)));
  }

  private boolean isIncludedAndEntitled(
      String requested, String actual, Set<String> entitledModules) {
    if (!includesSource(requested, actual)) return false;
    if ("stay".equals(actual)) return entitledModules.contains(ModuleType.STAY.key());
    if ("restaurant".equals(actual))
      return entitledModules.contains(ModuleType.RESTAURANT.key());
    return true;
  }

  private void requireRestaurant(Set<String> entitledModules) {
    if (!entitledModules.contains(ModuleType.RESTAURANT.key()))
      throw new ModuleEntitlementService.ModuleNotEntitledException(ModuleType.RESTAURANT);
  }
}
