package com.InnovaServe.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.InnovaServe.core.entity.*;
import com.InnovaServe.core.repository.*;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TenantProfileServiceTest {
  private final UUID tenantId = UUID.randomUUID();
  private final TenantRepository tenants = mock(TenantRepository.class);
  private final StayChargePresetRepository presets = mock(StayChargePresetRepository.class);
  private final TenantContext context = mock(TenantContext.class);
  private final Tenant tenant = new Tenant("Demo", null, "Address");
  private TenantProfileService service;

  @BeforeEach
  void setup() {
    when(context.tenantId()).thenReturn(tenantId);
    when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenants.lockById(tenantId)).thenReturn(Optional.of(tenant));
    when(presets.findAllByTenantIdOrderByDescriptionAsc(tenantId)).thenReturn(List.of());
    service = new TenantProfileService(tenants, presets, context);
  }

  @Test
  void newPropertiesOfferRoomOnlyAndRejectMealPlansUntilEnabled() {
    assertEquals(List.of("EP"), service.profile().mealPlans());
    assertDoesNotThrow(() -> service.requireMealPlan("EP"));
    assertThrows(IllegalArgumentException.class, () -> service.requireMealPlan("CP"));
  }

  @Test
  void enablingAndDisablingMealsRetainsRoomOnlyOption() {
    var result = service.updateRules(new TenantProfileService.Rules(LocalTime.of(10, 30), List.of("CP")));
    assertEquals(List.of("EP", "CP"), result.mealPlans());
    assertEquals(LocalTime.of(10, 30), result.checkoutTime());
    assertDoesNotThrow(() -> service.requireMealPlan("CP"));
    service.updateRules(new TenantProfileService.Rules(LocalTime.of(11, 0), List.of("EP")));
    assertThrows(IllegalArgumentException.class, () -> service.requireMealPlan("CP"));
  }

  @Test
  void rejectsInvalidRulesWithoutChangingTenant() {
    assertThrows(IllegalArgumentException.class, () -> service.updateRules(
        new TenantProfileService.Rules(LocalTime.NOON, List.of("INVALID"))));
    assertEquals(LocalTime.of(11, 0), tenant.getCheckoutTime());
  }

  @Test
  void presetEditsAndDeletesAreScopedToAuthenticatedTenant() {
    UUID otherPreset = UUID.randomUUID();
    when(presets.findByTenantIdAndId(tenantId, otherPreset)).thenReturn(Optional.empty());
    assertThrows(NoSuchElementException.class, () -> service.savePreset(otherPreset,
        new TenantProfileService.PresetInput("Laundry", new BigDecimal("100.00"))));
    assertThrows(NoSuchElementException.class, () -> service.deletePreset(otherPreset));
    verify(presets, never()).delete(any());
  }

  @Test
  void invalidPricesNeverSavePreset() {
    for (String amount : List.of("0", "-1", "1.001", "100000000")) {
      assertThrows(IllegalArgumentException.class, () -> service.savePreset(null,
          new TenantProfileService.PresetInput("Laundry", new BigDecimal(amount))));
    }
    verify(presets, never()).save(any());
  }

  @Test
  void profileUpdatesValidateNameAndGstin() {
    assertThrows(IllegalArgumentException.class, () -> service.updateDetails(
        new TenantProfileService.Details(" ", null, null)));
    assertThrows(IllegalArgumentException.class, () -> service.updateDetails(
        new TenantProfileService.Details("Hotel", "bad", null)));
    var result = service.updateDetails(new TenantProfileService.Details(" Hotel ", "", " Mumbai "));
    assertEquals("Hotel", result.name());
    assertNull(result.gstin());
    assertEquals("Mumbai", result.address());
  }
}
