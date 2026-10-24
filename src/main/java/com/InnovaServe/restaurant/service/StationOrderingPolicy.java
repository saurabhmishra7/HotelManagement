package com.InnovaServe.restaurant.service;

/** A serving station can request its own items and food from Kitchen. */
public final class StationOrderingPolicy {
  private StationOrderingPolicy() {}

  public static boolean allows(String serviceStation, String preparationStation) {
    return "kitchen".equals(preparationStation)
        || (serviceStation != null && serviceStation.equals(preparationStation));
  }

  public static void requireAllowed(String serviceStation, String preparationStation) {
    if (!allows(serviceStation, preparationStation))
      throw new IllegalArgumentException("This item is not available for the table's assigned station");
  }
}
