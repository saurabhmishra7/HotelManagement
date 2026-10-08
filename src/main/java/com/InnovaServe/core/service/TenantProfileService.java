package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.*;
import com.InnovaServe.core.repository.*;
import java.math.*;
import java.time.LocalTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TenantProfileService {
  private final TenantRepository tenants;
  private final StayChargePresetRepository presets;
  private final TenantContext context;

  public TenantProfileService(TenantRepository tenants, StayChargePresetRepository presets,
      TenantContext context) {
    this.tenants = tenants;
    this.presets = presets;
    this.context = context;
  }

  private Tenant tenant() {
    return tenants.findById(context.tenantId())
        .orElseThrow(() -> new NoSuchElementException("Tenant not found"));
  }

  private Tenant lockedTenant() {
    return tenants.lockById(context.tenantId())
        .orElseThrow(() -> new NoSuchElementException("Tenant not found"));
  }

  public Profile profile() {
    Tenant t = tenant();
    return new Profile(t.getName(), t.getTenantCode(), t.getGstin(), t.getAddress(),
        t.getCheckoutTime(), t.getRestaurantServiceMode(), t.getUiPalette(), List.of("EP", "CP", "MAP", "AP").stream()
            .filter(t.getMealPlans()::contains).toList(), t.getLogoVersion(),
        presets.findAllByTenantIdOrderByDescriptionAsc(context.tenantId()));
  }

  @Transactional
  public Profile updateDetails(Details request) {
    String name = request.name() == null ? "" : request.name().trim();
    if (name.isEmpty() || name.length() > 200)
      throw new IllegalArgumentException("Property name is required (maximum 200 characters)");
    String gstin = request.gstin() == null ? "" : request.gstin().trim().toUpperCase(Locale.ROOT);
    if (!gstin.isEmpty() && !gstin.matches("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9A-Z]Z[0-9A-Z]"))
      throw new IllegalArgumentException("Enter a valid 15-character GSTIN or leave it blank");
    String address = request.address() == null ? "" : request.address().trim();
    if (address.length() > 2000) throw new IllegalArgumentException("Address must be at most 2000 characters");
    lockedTenant().updateProfile(name, gstin.isEmpty() ? null : gstin, address);
    return profile();
  }

  @Transactional
  public Profile updateRules(Rules request) {
    if (request.checkoutTime() == null)
      throw new IllegalArgumentException("Checkout time is required");
    if (request.mealPlans() == null || request.mealPlans().isEmpty()
        || request.mealPlans().stream().anyMatch(p -> p == null || !Set.of("EP", "CP", "MAP", "AP").contains(p)))
      throw new IllegalArgumentException("Choose at least one valid meal plan");
    Set<String> plans = new LinkedHashSet<>(request.mealPlans());
    plans.add("EP");
    lockedTenant().updateRules(request.checkoutTime().withSecond(0).withNano(0), plans);
    return profile();
  }

  public void requireMealPlan(String plan) {
    if (!tenant().getMealPlans().contains(plan))
      throw new IllegalArgumentException("This meal plan is not offered by the property");
  }

  @Transactional
  public Profile updateRestaurantSettings(RestaurantSettings request) {
    String mode = request.serviceMode() == null ? "" : request.serviceMode().trim().toLowerCase(Locale.ROOT);
    if (!Set.of("kitchen_display", "thermal_printer", "both").contains(mode))
      throw new IllegalArgumentException("Choose Kitchen display, Thermal printer, or Both");
    lockedTenant().updateRestaurantServiceMode(mode);
    return profile();
  }

  @Transactional
  public Profile updateAppearance(Appearance request) {
    String palette = request.palette() == null ? "" : request.palette().trim().toLowerCase(Locale.ROOT);
    if (!Set.of("earth", "sage", "ocean", "berry", "lavender", "forest", "terracotta", "slate")
        .contains(palette))
      throw new IllegalArgumentException("Choose one of the available color palettes");
    lockedTenant().updateUiPalette(palette);
    return profile();
  }

  @Transactional
  public Profile savePreset(UUID id, PresetInput request) {
    lockedTenant();
    String description = request.description() == null ? "" : request.description().trim();
    if (description.isEmpty() || description.length() > 200)
      throw new IllegalArgumentException("Charge description is required (maximum 200 characters)");
    BigDecimal amount = request.amount();
    if (amount == null || amount.signum() <= 0 || amount.compareTo(new BigDecimal("99999999.99")) > 0
        || amount.scale() > 2)
      throw new IllegalArgumentException("Charge amount must be positive with at most two decimal places");
    var existing = presets.findAllByTenantIdOrderByDescriptionAsc(context.tenantId());
    if (existing.stream().anyMatch(p -> p.getDescription().equalsIgnoreCase(description) && !p.getId().equals(id)))
      throw new IllegalArgumentException("A charge with this name already exists");
    if (id == null) presets.save(new StayChargePreset(context.tenantId(), description, amount));
    else presets.findByTenantIdAndId(context.tenantId(), id)
        .orElseThrow(() -> new NoSuchElementException("Charge option not found"))
        .update(description, amount);
    return profile();
  }

  @Transactional
  public Profile deletePreset(UUID id) {
    lockedTenant();
    presets.delete(presets.findByTenantIdAndId(context.tenantId(), id)
        .orElseThrow(() -> new NoSuchElementException("Charge option not found")));
    return profile();
  }

  @Transactional
  public Profile saveLogo(byte[] png) {
    lockedTenant().setLogo(png);
    return profile();
  }

  public byte[] logo() {
    byte[] logo = tenant().getLogo();
    if (logo == null) throw new NoSuchElementException("Property logo not set");
    return logo;
  }

  public record Details(String name, String gstin, String address) {}
  public record Rules(LocalTime checkoutTime, List<String> mealPlans) {}
  public record RestaurantSettings(String serviceMode) {}
  public record Appearance(String palette) {}
  public record PresetInput(String description, BigDecimal amount) {}
  public record Profile(
      String name, String tenantCode, String gstin, String address,
      LocalTime checkoutTime, String restaurantServiceMode, String uiPalette, List<String> mealPlans,
      UUID logoVersion, List<StayChargePreset> chargePresets) {}
}
