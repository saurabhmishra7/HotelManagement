package com.InnovaServe.core.controller;

import com.InnovaServe.core.service.ModuleEntitlementService;
import com.InnovaServe.core.service.TenantContext;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Aggregates tenant financial events without loading invoice lines or paginated lists. */
@RestController
@RequestMapping("/api/v1/reports/financial")
public class FinancialReportsController {
  private final NamedParameterJdbcTemplate jdbc;
  private final TenantContext tenant;
  private final ModuleEntitlementService entitlements;

  public FinancialReportsController(NamedParameterJdbcTemplate jdbc, TenantContext tenant,
      ModuleEntitlementService entitlements) {
    this.jdbc = jdbc;
    this.tenant = tenant;
    this.entitlements = entitlements;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_FINANCIAL_REPORTS_READ')")
  public Map<String, Object> report(@RequestParam LocalDate from, @RequestParam LocalDate to,
      @RequestParam(defaultValue = "UTC") String timezone,
      @RequestParam(defaultValue = "all") String area) {
    ZoneId zone;
    try { zone = ZoneId.of(timezone); }
    catch (DateTimeException ex) { throw new IllegalArgumentException("Choose a valid time zone"); }
    if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 365)
      throw new IllegalArgumentException("Choose a date range of up to 366 days");
    if (!Set.of("all", "Hotel", "Restaurant", "Bar", "General", "Other").contains(area))
      throw new IllegalArgumentException("Unknown report area");
    var modules = entitlements.activeModules(tenant.tenantId());
    if (modules.isEmpty()) throw new org.springframework.security.access.AccessDeniedException("An active subscription is required");
    var params = new HashMap<String, Object>();
    params.put("tenant", tenant.tenantId());
    params.put("from", from); params.put("to", to); params.put("zone", zone.getId()); params.put("area", area);
    params.put("stay", modules.contains("stay")); params.put("restaurant", modules.contains("restaurant"));
    params.put("expense", modules.contains("expense"));
    // Each source is aggregated independently, preventing payment/credit joins multiplying sales.
    var rows = jdbc.queryForList("""
        WITH invoices AS (
          SELECT i.*,
            CASE WHEN i.source_module = 'stay' THEN 'Hotel'
              WHEN i.source_module = 'restaurant' THEN
                CASE WHEN o.service_station = 'bar' THEN 'Bar' ELSE 'Restaurant' END
              ELSE 'Other' END AS area,
            ((COALESCE(i.locked_at, i.created_at) AT TIME ZONE 'UTC') AT TIME ZONE :zone)::date AS day
          FROM core.invoice i
          LEFT JOIN restaurant.restaurant_bill b ON b.invoice_id = i.id AND b.tenant_id = i.tenant_id
          LEFT JOIN restaurant.restaurant_order o ON o.id = b.order_id AND o.tenant_id = i.tenant_id
          WHERE i.tenant_id = :tenant AND i.status IN ('final', 'credited')
            AND ((i.source_module = 'stay' AND :stay) OR (i.source_module = 'restaurant' AND :restaurant)
              OR i.source_module = 'other')
        ), events AS (
          SELECT day, area, 'sales' AS metric, '' AS category, total_amount AS amount, tax_amount AS tax
          FROM invoices WHERE day BETWEEN :from AND :to
          UNION ALL
          SELECT ((p.paid_at AT TIME ZONE 'UTC') AT TIME ZONE :zone)::date, i.area,
            'payments', p.mode, p.amount, 0
          FROM core.payment p JOIN invoices i ON i.id = p.invoice_id
          WHERE p.tenant_id = :tenant AND p.mode <> 'account'
          UNION ALL
          SELECT ((c.created_at AT TIME ZONE 'UTC') AT TIME ZONE :zone)::date, i.area,
            'credits', '', c.amount, 0
          FROM core.credit_note c JOIN invoices i ON i.id = c.original_invoice_id
          WHERE c.tenant_id = :tenant
          UNION ALL
          SELECT e.expense_date,
            CASE e.department WHEN 'rooms' THEN 'Hotel' WHEN 'restaurant' THEN 'Restaurant'
              WHEN 'bar' THEN 'Bar' ELSE 'General' END,
            CASE WHEN e.approval_status = 'pending' THEN 'pending_expenses' ELSE 'expenses' END,
            COALESCE(c.name, 'Uncategorised'), e.amount, 0
          FROM expense.expense e LEFT JOIN expense.expense_category c ON c.id = e.category_id AND c.tenant_id = e.tenant_id
          WHERE e.tenant_id = :tenant AND :expense
            AND e.approval_status IN ('approved', 'not_required', 'pending')
          UNION ALL
          SELECT i.day, i.area, 'outstanding', '', GREATEST(0, i.total_amount
            - COALESCE((SELECT SUM(p.amount) FROM core.payment p WHERE p.invoice_id = i.id AND p.tenant_id = :tenant), 0)
            - COALESCE((SELECT SUM(c.amount) FROM core.credit_note c WHERE c.original_invoice_id = i.id AND c.tenant_id = :tenant), 0)), 0
          FROM invoices i WHERE i.day BETWEEN :from AND :to
        )
        SELECT day::text AS day, area, metric, category, SUM(amount) AS amount, SUM(tax) AS tax, COUNT(*) AS count
        FROM events WHERE day BETWEEN :from AND :to AND (:area = 'all' OR area = :area)
        GROUP BY day, area, metric, category ORDER BY day, area, metric, category
        """, params);
    return Map.of("rows", rows, "from", from, "to", to, "timezone", zone.getId(),
        "expensesEnabled", modules.contains("expense"));
  }
}
