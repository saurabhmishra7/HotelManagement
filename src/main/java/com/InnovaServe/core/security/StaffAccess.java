package com.InnovaServe.core.security;

import com.InnovaServe.core.entity.StaffRole;
import com.InnovaServe.core.entity.StaffUser;
import java.util.*;

/** One permission union shared by authentication, approvals and notification targeting. */
public final class StaffAccess {
  private StaffAccess() {}

  public static Set<String> permissions(Collection<StaffRole> roles) {
    Set<String> result = new TreeSet<>();
    for (StaffRole role : roles) {
      if (role.getPermissions() != null) result.addAll(role.getPermissions());
      if (Set.of("OWNER", "HOTEL_ADMIN").contains(role.getName().toUpperCase(Locale.ROOT)) || result.contains("*"))
        Arrays.stream(Permission.values()).map(Enum::name).forEach(result::add);
    }
    result.remove("*");
    return result;
  }

  public static List<StaffRole> roles(StaffUser user, Map<UUID, StaffRole> byId) {
    return user.getRoleIds().stream().map(byId::get).filter(Objects::nonNull)
        .filter(role -> user.getTenantId().equals(role.getTenantId())).toList();
  }
}
