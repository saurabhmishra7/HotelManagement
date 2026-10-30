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
            'payments', CASE WHEN p.mode = 'advance' THEN COALESCE(s.advance_payment_mode, '') ELSE p.mode END, p.amount, 0
          FROM core.payment p JOIN invoices i ON i.id = p.invoice_id
          LEFT JOIN stay.stay s ON s.account_id = i.account_id AND s.tenant_id = i.tenant_id
          WHERE p.tenant_id = :tenant AND p.mode <> 'account'
          UNION ALL
          SELECT ((s.check_in_at AT TIME ZONE 'UTC') AT TIME ZONE :zone)::date,
            'Hotel', 'payments', COALESCE(s.advance_payment_mode, ''),
            GREATEST(0, s.advance_paid - COALESCE((SELECT SUM(p.amount)
              FROM core.payment p JOIN core.invoice i ON i.id = p.invoice_id AND i.tenant_id = p.tenant_id
              WHERE p.tenant_id = s.tenant_id AND i.account_id = s.account_id AND p.mode = 'advance'), 0)), 0
          FROM stay.stay s WHERE s.tenant_id = :tenant AND :stay AND s.advance_paid > 0
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
          SELECT CAST(:to AS date), i.area, 'outstanding', '', GREATEST(0, i.total_amount
            - COALESCE((SELECT SUM(p.amount) FROM core.payment p WHERE p.invoice_id = i.id AND p.tenant_id = :tenant), 0)
            - COALESCE((SELECT SUM(c.amount) FROM core.credit_note c WHERE c.original_invoice_id = i.id AND c.tenant_id = :tenant), 0)), 0
          FROM invoices i
          WHERE i.total_amount
            - COALESCE((SELECT SUM(p.amount) FROM core.payment p WHERE p.invoice_id = i.id AND p.tenant_id = :tenant), 0)
            - COALESCE((SELECT SUM(c.amount) FROM core.credit_note c WHERE c.original_invoice_id = i.id AND c.tenant_id = :tenant), 0) > 0
        )
        SELECT day::text AS day, area, metric, category, SUM(amount) AS amount, SUM(tax) AS tax, COUNT(*) AS count
        FROM events WHERE day BETWEEN :from AND :to AND (:area = 'all' OR area = :area)
          AND (metric <> 'payments' OR amount > 0)
        GROUP BY day, area, metric, category ORDER BY day, area, metric, category
        """, params);
    return Map.of("rows", rows, "from", from, "to", to, "timezone", zone.getId(),
        "expensesEnabled", modules.contains("expense"));
  }

  /** Full transaction rows for the Reports record table and its filtered export. */
  @GetMapping("/records")
  @PreAuthorize("hasAuthority('PERM_FINANCIAL_REPORTS_READ')")
  public Map<String, Object> records(@RequestParam LocalDate from, @RequestParam LocalDate to,
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
    params.put("today", LocalDate.now());
    params.put("stay", modules.contains("stay")); params.put("restaurant", modules.contains("restaurant"));

    var payments = jdbc.queryForList("""
        WITH receipts AS (
          SELECT i.id::text AS id, i.invoice_number,
            CASE WHEN i.source_module = 'stay' THEN 'Hotel'
              WHEN i.source_module = 'restaurant' AND o.service_station = 'bar' THEN 'Bar'
              WHEN i.source_module = 'restaurant' THEN 'Restaurant' ELSE 'Other' END AS area,
            CASE WHEN p.mode = 'advance' THEN s.advance_payment_mode ELSE p.mode END AS mode,
            p.amount, p.paid_at,
            ((p.paid_at AT TIME ZONE 'UTC') AT TIME ZONE :zone)::date AS day,
            CASE WHEN r.room_number IS NOT NULL THEN 'Room ' || r.room_number ELSE p.reference END AS reference,
            GREATEST(0, i.total_amount
              - COALESCE((SELECT SUM(ap.amount) FROM core.payment ap WHERE ap.tenant_id = i.tenant_id AND ap.invoice_id = i.id), 0)
              - COALESCE((SELECT SUM(c.amount) FROM core.credit_note c WHERE c.tenant_id = i.tenant_id AND c.original_invoice_id = i.id), 0)) AS balance, 'Invoice balance'::text AS balance_basis
          FROM core.payment p
          JOIN core.invoice i ON i.id = p.invoice_id AND i.tenant_id = p.tenant_id
          LEFT JOIN stay.stay s ON s.account_id = i.account_id AND s.tenant_id = i.tenant_id
          LEFT JOIN stay.room r ON r.id = s.room_id AND r.tenant_id = s.tenant_id
          LEFT JOIN restaurant.restaurant_bill b ON b.invoice_id = i.id AND b.tenant_id = i.tenant_id
          LEFT JOIN restaurant.restaurant_order o ON o.id = b.order_id AND o.tenant_id = i.tenant_id
          WHERE p.tenant_id = :tenant AND p.mode <> 'account' AND i.status IN ('final', 'credited')
            AND ((i.source_module = 'stay' AND :stay) OR (i.source_module = 'restaurant' AND :restaurant)
              OR i.source_module = 'other')
          UNION ALL
          SELECT 'advance:' || s.id::text, 'Pending invoice', 'Hotel', s.advance_payment_mode,
            GREATEST(0, s.advance_paid - COALESCE((SELECT SUM(p.amount)
              FROM core.payment p JOIN core.invoice i ON i.id = p.invoice_id AND i.tenant_id = p.tenant_id
              WHERE p.tenant_id = s.tenant_id AND i.account_id = s.account_id AND p.mode = 'advance'), 0)),
            s.check_in_at, ((s.check_in_at AT TIME ZONE 'UTC') AT TIME ZONE :zone)::date,
            'Room ' || r.room_number,
            CASE WHEN s.status = 'active' AND s.checkout_invoice_id IS NULL THEN
              GREATEST(0, s.tariff * GREATEST(1, CAST(:today AS date) - s.check_in_at::date)
                + COALESCE((SELECT SUM(c.quantity * c.unit_price) FROM stay.stay_charge c
                    WHERE c.tenant_id = s.tenant_id AND c.stay_id = s.id AND c.invoice_id IS NULL), 0)
                - GREATEST(0, s.advance_paid - COALESCE((SELECT SUM(p.amount)
                    FROM core.payment p JOIN core.invoice i ON i.id = p.invoice_id AND i.tenant_id = p.tenant_id
                    WHERE p.tenant_id = s.tenant_id AND i.account_id = s.account_id AND p.mode = 'advance'), 0)))
              ELSE 0 END,
            CASE WHEN s.status = 'active' AND s.checkout_invoice_id IS NULL
              THEN 'Estimated · before tax' ELSE 'Unused advance' END
          FROM stay.stay s JOIN stay.room r ON r.id = s.room_id AND r.tenant_id = s.tenant_id
          WHERE s.tenant_id = :tenant AND :stay AND s.advance_paid > 0
        )
        SELECT id, MAX(day) FILTER (WHERE day BETWEEN :from AND :to)::text AS day,
          area, invoice_number,
          STRING_AGG(DISTINCT COALESCE(mode, 'not_recorded'), ', ' ORDER BY COALESCE(mode, 'not_recorded'))
            FILTER (WHERE day BETWEEN :from AND :to) AS mode,
          MAX(reference) AS reference,
          SUM(amount) FILTER (WHERE day BETWEEN :from AND :to) AS amount,
          SUM(amount) AS total_paid, MAX(balance) AS balance, MAX(balance_basis) AS balance_basis,
          CASE WHEN MAX(balance_basis) = 'Invoice balance' AND MAX(balance) = 0 THEN 'paid' ELSE 'advance' END AS payment_status
        FROM receipts WHERE amount > 0 AND (:area = 'all' OR area = :area)
        GROUP BY id, area, invoice_number
        HAVING SUM(amount) FILTER (WHERE day BETWEEN :from AND :to) > 0
        ORDER BY MAX(paid_at) FILTER (WHERE day BETWEEN :from AND :to) DESC, id DESC
        """, params);

    var expenses = modules.contains("expense") ? jdbc.queryForList("""
        SELECT e.id::text AS id, e.expense_date::text AS day,
          CASE e.department WHEN 'rooms' THEN 'Hotel' WHEN 'restaurant' THEN 'Restaurant'
            WHEN 'bar' THEN 'Bar' ELSE 'General' END AS area,
          COALESCE(c.name, 'Uncategorised') AS category, e.vendor_name, e.description,
          e.payment_mode AS mode, e.approval_status, e.amount
        FROM expense.expense e
        LEFT JOIN expense.expense_category c ON c.id = e.category_id AND c.tenant_id = :tenant
        WHERE e.tenant_id = :tenant AND e.approval_status IN ('approved', 'not_required', 'pending')
          AND e.expense_date BETWEEN :from AND :to
          AND (:area = 'all' OR CASE e.department WHEN 'rooms' THEN 'Hotel'
            WHEN 'restaurant' THEN 'Restaurant' WHEN 'bar' THEN 'Bar' ELSE 'General' END = :area)
        ORDER BY e.expense_date DESC, e.id DESC
        """, params) : List.<Map<String, Object>>of();
    return Map.of("payments", payments, "expenses", expenses);
  }
}
