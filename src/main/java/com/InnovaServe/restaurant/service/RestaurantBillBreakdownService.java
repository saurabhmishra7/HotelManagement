package com.InnovaServe.restaurant.service;

import com.InnovaServe.core.entity.Invoice;
import com.InnovaServe.core.entity.InvoiceLineItem;
import com.InnovaServe.core.entity.TaxRule;
import com.InnovaServe.core.repository.CreditNoteRepository;
import com.InnovaServe.core.repository.InvoiceLineItemRepository;
import com.InnovaServe.core.repository.InvoiceRepository;
import com.InnovaServe.core.repository.PaymentRepository;
import com.InnovaServe.core.repository.TaxRuleRepository;
import com.InnovaServe.restaurant.entity.MenuItem;
import com.InnovaServe.restaurant.entity.OrderItem;
import com.InnovaServe.restaurant.entity.RestaurantBill;
import com.InnovaServe.restaurant.repository.MenuItemRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RestaurantBillBreakdownService {
  private final MenuItemRepository menuItems;
  private final TaxRuleRepository taxRules;
  private final InvoiceRepository invoices;
  private final InvoiceLineItemRepository invoiceLines;
  private final PaymentRepository payments;
  private final CreditNoteRepository creditNotes;

  public RestaurantBillBreakdownService(MenuItemRepository menuItems, TaxRuleRepository taxRules,
      InvoiceRepository invoices, InvoiceLineItemRepository invoiceLines,
      PaymentRepository payments, CreditNoteRepository creditNotes) {
    this.menuItems = menuItems;
    this.taxRules = taxRules;
    this.invoices = invoices;
    this.invoiceLines = invoiceLines;
    this.payments = payments;
    this.creditNotes = creditNotes;
  }

  public Map<String, Object> forGuest(UUID tenantId, List<OrderItem> items, RestaurantBill bill) {
    if (bill == null) {
      Map<String, Object> estimate = new LinkedHashMap<>(preview(tenantId, items));
      estimate.put("kind", "estimate");
      return estimate;
    }

    Invoice invoice = invoices.findByTenantIdAndId(tenantId, bill.getInvoiceId())
        .orElseThrow(() -> new NoSuchElementException("Invoice not found"));
    BigDecimal paid = payments.sumPaid(tenantId, invoice.getId());
    BigDecimal credits = creditNotes.totalForInvoice(tenantId, invoice.getId());
    List<Map<String, Object>> lines = invoiceLines.findAllByTenantIdAndInvoiceId(tenantId, invoice.getId())
        .stream().map(this::invoiceLine).toList();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("kind", "invoice");
    result.put("invoice_number", invoice.getInvoiceNumber());
    result.put("line_items", lines);
    result.put("subtotal", invoice.getSubtotal());
    result.put("tax_amount", invoice.getTaxAmount());
    result.put("total_amount", invoice.getTotalAmount());
    result.put("paid_total", paid);
    result.put("credit_total", credits);
    result.put("amount_due", invoice.getTotalAmount().subtract(paid).subtract(credits).max(BigDecimal.ZERO));
    result.put("settlement_mode", bill.getSettlementMode());
    return result;
  }

  private Map<String, Object> invoiceLine(InvoiceLineItem line) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("description", line.getDescription());
    result.put("quantity", line.getQuantity());
    result.put("unitPrice", line.getUnitPrice());
    result.put("taxAmount", line.getTaxAmount());
    result.put("lineTotal", line.getLineTotal());
    return result;
  }

  public Map<String, Object> preview(UUID tenantId, List<OrderItem> orderItems) {
    BigDecimal subtotal = BigDecimal.ZERO;
    BigDecimal taxTotal = BigDecimal.ZERO;
    List<Map<String, Object>> lines = new ArrayList<>();
    LocalDate today = LocalDate.now();
    Map<UUID, MenuItem> menuItemsById = menuItems.findAllByTenantIdOrderByName(tenantId)
        .stream().collect(Collectors.toMap(MenuItem::getId, item -> item));
    Map<UUID, TaxRule> taxRulesById = taxRules.findAllByTenantId(tenantId)
        .stream().collect(Collectors.toMap(TaxRule::getId, rule -> rule));

    for (OrderItem orderItem : orderItems) {
      if ("cancelled".equals(orderItem.getStatus())) continue;
      MenuItem menuItem = java.util.Optional.ofNullable(menuItemsById.get(orderItem.getMenuItemId()))
          .orElseThrow(() -> new NoSuchElementException("Menu item not found"));
      BigDecimal quantity = BigDecimal.valueOf(orderItem.getQuantity());
      BigDecimal unitPrice = menuItem.getPrice();
      BigDecimal base = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
      BigDecimal rate = BigDecimal.ZERO;

      if (menuItem.getTaxRuleId() != null) {
        TaxRule taxRule = java.util.Optional.ofNullable(taxRulesById.get(menuItem.getTaxRuleId()))
            .orElseThrow(() -> new NoSuchElementException("Tax rule not found"));
        if (!taxRule.getEffectiveFrom().isAfter(today)
            && (taxRule.getEffectiveTo() == null || !taxRule.getEffectiveTo().isBefore(today))) {
          rate = taxRule.getRatePercent();
        }
      }

      BigDecimal tax = base.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
      BigDecimal lineTotal = base.add(tax);
      subtotal = subtotal.add(base);
      taxTotal = taxTotal.add(tax);
      Map<String, Object> line = new LinkedHashMap<>();
      line.put("description", menuItem.getName());
      line.put("quantity", quantity);
      line.put("unitPrice", unitPrice);
      line.put("taxAmount", tax);
      line.put("lineTotal", lineTotal);
      lines.add(line);
    }

    return Map.of(
        "line_items", lines,
        "subtotal", subtotal,
        "tax_amount", taxTotal,
        "total_amount", subtotal.add(taxTotal),
        "paid_total", BigDecimal.ZERO,
        "credit_total", BigDecimal.ZERO,
        "amount_due", subtotal.add(taxTotal));
  }
}
