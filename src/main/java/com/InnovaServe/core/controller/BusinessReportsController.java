package com.InnovaServe.core.controller;

import com.InnovaServe.core.service.ModuleEntitlementService;
import com.InnovaServe.core.service.TenantContext;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Operational context for financial reports. All queries are scoped to the signed-in tenant. */
@RestController
@RequestMapping("/api/v1/reports/insights")
public class BusinessReportsController {
  private final NamedParameterJdbcTemplate jdbc;
  private final TenantContext tenant;
  private final ModuleEntitlementService entitlements;

  public BusinessReportsController(NamedParameterJdbcTemplate jdbc, TenantContext tenant,
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
    params.put("from", from);
    params.put("to", to);
    params.put("zone", zone.getId());
    params.put("area", area);
    params.put("stay", modules.contains("stay"));
    params.put("restaurant", modules.contains("restaurant"));
    params.put("inventory", modules.contains("inventory"));

    // Room nights use the stay interval and the current room inventory. The app has no
    // historic room availability or tariff-change ledger, so these are clearly estimates.
    var hotel = modules.contains("stay") && (area.equals("all") || area.equals("Hotel"))
        ? jdbc.queryForMap("""
            WITH days AS (SELECT generate_series(CAST(:from AS date), CAST(:to AS date), interval '1 day')::date AS day),
            supply AS (SELECT COUNT(*) AS rooms FROM stay.room WHERE tenant_id = :tenant),
            occupied AS (
              SELECT d.day, s.room_id, MAX(s.tariff) AS tariff
              FROM days d JOIN stay.stay s ON s.tenant_id = :tenant
                AND s.check_in_at::date <= d.day
                AND COALESCE(GREATEST(s.actual_check_out_at::date, s.check_in_at::date + 1),
                  (CURRENT_TIMESTAMP AT TIME ZONE :zone)::date + 1) > d.day
              GROUP BY d.day, s.room_id
            )
            SELECT (SELECT COUNT(*) FROM days) * (SELECT rooms FROM supply) AS available_room_nights,
              COUNT(*) AS occupied_room_nights, COALESCE(SUM(tariff), 0) AS estimated_room_revenue
            FROM occupied
            """, params) : Map.<String, Object>of();

    // Popularity is the share of served units in each menu category. Recipe cost uses
    // today's weighted average inventory cost, not an historic cost at the sale date.
    var menu = modules.contains("restaurant") && Set.of("all", "Restaurant", "Bar").contains(area)
        ? jdbc.queryForList("""
            WITH sold AS (
              SELECT oi.menu_item_id, SUM(oi.quantity) AS units
              FROM restaurant.order_item oi
              JOIN restaurant.restaurant_order o ON o.id = oi.order_id AND o.tenant_id = :tenant
              WHERE oi.tenant_id = :tenant AND oi.status = 'served'
                AND ((COALESCE(oi.served_at, oi.placed_at) AT TIME ZONE 'UTC') AT TIME ZONE :zone)::date BETWEEN :from AND :to
                AND (:area = 'all' OR CASE WHEN o.service_station = 'bar' THEN 'Bar' ELSE 'Restaurant' END = :area)
              GROUP BY oi.menu_item_id
            ), recipe_cost AS (
              SELECT r.menu_item_id, COUNT(ri.id) AS ingredients,
                COUNT(*) FILTER (WHERE inv.average_unit_cost <= 0) AS unpriced_ingredients,
                SUM(ri.quantity_required * inv.average_unit_cost) AS cost
              FROM restaurant.recipe r
              JOIN restaurant.recipe_ingredient ri ON ri.recipe_id = r.id
              JOIN inventory.item inv ON inv.id = ri.inventory_item_id AND inv.tenant_id = :tenant
              WHERE r.tenant_id = :tenant AND :inventory GROUP BY r.menu_item_id
            )
            SELECT m.id::text AS id, m.name, c.name AS category, m.price,
              sold.units, recipe_cost.ingredients, recipe_cost.unpriced_ingredients,
              recipe_cost.cost AS estimated_recipe_cost
            FROM sold JOIN restaurant.menu_item m ON m.id = sold.menu_item_id AND m.tenant_id = :tenant
            JOIN restaurant.menu_category c ON c.id = m.category_id AND c.tenant_id = :tenant
            LEFT JOIN recipe_cost ON recipe_cost.menu_item_id = m.id
            ORDER BY sold.units DESC, m.name
            """, params) : List.<Map<String, Object>>of();

    // Ratings are dated by submission, while menu popularity is dated by service.
    // Keep both within the selected period without joining feedback to order items,
    // which would multiply ratings for orders with repeated dish lines.
    var ratings = modules.contains("restaurant") && Set.of("all", "Restaurant", "Bar").contains(area)
        ? jdbc.queryForList("""
            SELECT m.id::text AS id, m.name, c.name AS category,
              COUNT(*) AS rating_count, ROUND(AVG(f.stars), 2) AS average_stars
            FROM restaurant.dish_feedback f
            JOIN restaurant.menu_item m ON m.id = f.menu_item_id AND m.tenant_id = :tenant
            JOIN restaurant.menu_category c ON c.id = m.category_id AND c.tenant_id = :tenant
            JOIN restaurant.restaurant_order o ON o.id = f.order_id AND o.tenant_id = :tenant
            WHERE f.tenant_id = :tenant
              AND ((f.created_at AT TIME ZONE 'UTC') AT TIME ZONE :zone)::date BETWEEN :from AND :to
              AND (:area = 'all' OR CASE WHEN o.service_station = 'bar' THEN 'Bar' ELSE 'Restaurant' END = :area)
            GROUP BY m.id, m.name, c.name
            ORDER BY rating_count DESC, m.name
            """, params) : List.<Map<String, Object>>of();

    // No contractual due date is stored. Age is measured from finalisation; balances
    // reflect current payments and credits, including those recorded after this period.
    var unpaid = jdbc.queryForList("""
        WITH invoice_balance AS (
          SELECT i.id, i.invoice_number, i.source_module, i.locked_at, i.created_at,
            i.total_amount - COALESCE((SELECT SUM(p.amount) FROM core.payment p
              WHERE p.invoice_id = i.id AND p.tenant_id = :tenant), 0)
              - COALESCE((SELECT SUM(c.amount) FROM core.credit_note c
              WHERE c.original_invoice_id = i.id AND c.tenant_id = :tenant), 0) AS due,
            CASE WHEN i.source_module = 'stay' THEN 'Hotel'
              WHEN i.source_module = 'restaurant' AND o.service_station = 'bar' THEN 'Bar'
              WHEN i.source_module = 'restaurant' THEN 'Restaurant' ELSE 'Other' END AS area
          FROM core.invoice i
          LEFT JOIN restaurant.restaurant_bill b ON b.invoice_id = i.id AND b.tenant_id = :tenant
          LEFT JOIN restaurant.restaurant_order o ON o.id = b.order_id AND o.tenant_id = :tenant
          WHERE i.tenant_id = :tenant AND i.status IN ('final', 'credited')
            AND ((i.source_module = 'stay' AND :stay) OR (i.source_module = 'restaurant' AND :restaurant)
              OR i.source_module = 'other')
        )
        SELECT id::text AS id, invoice_number, source_module, area,
          ((COALESCE(locked_at, created_at) AT TIME ZONE 'UTC') AT TIME ZONE :zone)::date::text AS finalized_on,
          GREATEST(0, (CURRENT_TIMESTAMP AT TIME ZONE :zone)::date
            - ((COALESCE(locked_at, created_at) AT TIME ZONE 'UTC') AT TIME ZONE :zone)::date) AS age_days, due
        FROM invoice_balance WHERE due > 0 AND (:area = 'all' OR area = :area)
        ORDER BY age_days DESC, invoice_number
        """, params);
    return Map.of("hotel", hotel, "menu", menu, "ratings", ratings, "unpaid", unpaid);
  }
}
