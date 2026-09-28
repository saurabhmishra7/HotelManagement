package com.InnovaServe.stay.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Common room and accommodation categories used by hotels, resorts, and serviced stays. */
public enum RoomType {
  // Occupancy and bed configuration
  SINGLE,
  DOUBLE,
  TWIN,
  DOUBLE_DOUBLE,
  HOLLYWOOD_TWIN,
  TRIPLE,
  QUAD,
  QUEEN,
  KING,
  TWIN_KING,
  BUNK,

  // Standard hotel classifications
  ECONOMY,
  STANDARD,
  SUPERIOR,
  DELUXE,
  PREMIUM,
  EXECUTIVE,
  CLUB,
  BUSINESS,
  ACCESSIBLE,

  // Suites and multi-room layouts
  STUDIO,
  JUNIOR_SUITE,
  SUITE,
  EXECUTIVE_SUITE,
  PRESIDENTIAL_SUITE,
  PENTHOUSE,
  FAMILY,
  CONNECTING,
  ADJOINING,
  LOFT,
  DUPLEX,

  // Resort, extended-stay, and specialty accommodation
  SERVICED_APARTMENT,
  APARTMENT,
  VILLA,
  POOL_VILLA,
  OVERWATER_VILLA,
  COTTAGE,
  BUNGALOW,
  CHALET,
  CABANA,
  LODGE,
  TENT,
  TREEHOUSE,
  DORMITORY,
  HONEYMOON;

  public String label() {
    return Arrays.stream(name().split("_"))
        .map(word -> word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT))
        .collect(Collectors.joining(" "));
  }

  @JsonCreator
  public static RoomType fromJson(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return RoomType.valueOf(
        value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT));
  }
}
