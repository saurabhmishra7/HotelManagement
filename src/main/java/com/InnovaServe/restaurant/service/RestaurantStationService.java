package com.InnovaServe.restaurant.service;

import com.InnovaServe.core.service.TenantContext;
import com.InnovaServe.restaurant.entity.RestaurantStation;
import com.InnovaServe.restaurant.repository.KotBatchRepository;
import com.InnovaServe.restaurant.repository.MenuItemRepository;
import com.InnovaServe.restaurant.repository.RestaurantStationRepository;
import com.InnovaServe.restaurant.repository.DiningTableRepository;
import com.InnovaServe.platform.service.BarFeatureService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RestaurantStationService {
  private final TenantContext tenant;
  private final RestaurantStationRepository stations;
  private final MenuItemRepository items;
  private final KotBatchRepository batches;
  private final BarFeatureService barFeature;
  private final DiningTableRepository tables;

  public RestaurantStationService(TenantContext tenant, RestaurantStationRepository stations,
      MenuItemRepository items, KotBatchRepository batches, BarFeatureService barFeature,
      DiningTableRepository tables) {
    this.tenant = tenant;
    this.stations = stations;
    this.items = items;
    this.batches = batches;
    this.barFeature = barFeature;
    this.tables = tables;
  }

  public record Station(String code, String name, boolean active) {}

  public List<Station> list() {
    return list(tenant.tenantId());
  }

  public List<Station> list(UUID tenantId) {
    Map<String, Station> result = new LinkedHashMap<>();
    result.put("kitchen", new Station("kitchen", "Kitchen", true));
    result.put("bar", new Station("bar", "Bar", true));
    for (RestaurantStation station : stations.findAllByTenantIdOrderByNameAsc(tenantId))
      result.put(station.getCode(), new Station(station.getCode(), station.getName(), station.isActive()));
    return List.copyOf(result.values());
  }

  public void requireActive(String code) {
    requireActive(tenant.tenantId(), code);
  }

  public void requireActive(UUID tenantId, String code) {
    if (code != null && !"kitchen".equals(code)) barFeature.requireEnabled(tenantId);
    if (code == null || list(tenantId).stream().noneMatch(s -> s.code().equals(code) && s.active()))
      throw new IllegalArgumentException("Choose an active restaurant station");
  }

  public void requireKnown(String code) {
    if (code == null || list().stream().noneMatch(s -> s.code().equals(code)))
      throw new IllegalArgumentException("Restaurant station not found");
  }

  public boolean barAvailable() { return barFeature.enabled(tenant.tenantId()); }

  public boolean barAvailable(UUID tenantId) { return barFeature.enabled(tenantId); }

  public void requireBarAccess() { barFeature.requireEnabled(tenant.tenantId()); }

  @Transactional
  public void add(String name) {
    String clean = cleanName(name);
    String code = clean.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    if (!code.matches("[a-z][a-z0-9_]{0,31}"))
      throw new IllegalArgumentException("Station name must start with a letter and create a code of at most 32 characters");
    if (list().stream().anyMatch(s -> s.code().equals(code) || s.name().equalsIgnoreCase(clean)))
      throw new IllegalArgumentException("A station with this name already exists");
    if (!"kitchen".equals(code)) barFeature.requireEnabled(tenant.tenantId());
    stations.save(new RestaurantStation(tenant.tenantId(), code, clean));
  }

  @Transactional
  public void update(String code, String name, boolean active) {
    requireKnown(code);
    if (!"kitchen".equals(code) && active) barFeature.requireEnabled(tenant.tenantId());
    String clean = cleanName(name);
    if (list().stream().anyMatch(s -> !s.code().equals(code) && s.name().equalsIgnoreCase(clean)))
      throw new IllegalArgumentException("A station with this name already exists");
    if (!active && items.existsByTenantIdAndStationAndActiveTrue(tenant.tenantId(), code))
      throw new IllegalStateException("Move or hide active menu items before disabling this station");
    if (!active && tables.existsByTenantIdAndStationAndStatusNot(tenant.tenantId(), code, "free"))
      throw new IllegalStateException("Finish service and clean assigned tables before disabling this station");
    if (!active && !batches.findPendingByTenantIdAndStation(tenant.tenantId(), code).isEmpty())
      throw new IllegalStateException("Finish pending tickets before disabling this station");
    RestaurantStation station = stations.findByTenantIdAndCode(tenant.tenantId(), code)
        .orElseGet(() -> new RestaurantStation(tenant.tenantId(), code, clean));
    station.update(clean, active);
    stations.save(station);
  }

  private String cleanName(String name) {
    String clean = name == null ? "" : name.trim();
    if (clean.isEmpty() || clean.length() > 50)
      throw new IllegalArgumentException("Station name is required (maximum 50 characters)");
    return clean;
  }
}
