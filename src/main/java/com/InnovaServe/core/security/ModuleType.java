package com.InnovaServe.core.security;

import java.util.Locale;

public enum ModuleType {
  STAY("stay"),
  RESTAURANT("restaurant"),
  EXPENSE("expense");

  private final String key;

  ModuleType(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }

  public static ModuleType from(String value) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Module names cannot be blank");
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    for (ModuleType module : values()) {
      if (module.key.equals(normalized)) return module;
    }
    throw new IllegalArgumentException("Unknown module: " + value);
  }
}
