package com.InnovaServe.core.service;

import java.util.regex.Pattern;

/** Validation for new account credentials and contact details. */
public final class AccountValidation {
  private static final Pattern MOBILE = Pattern.compile("[0-9]{10}");
  private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+");
  private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
  private static final Pattern NUMBER = Pattern.compile("[0-9]");
  private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9\\s]");

  private AccountValidation() {}

  public static void requireMobile(String value) {
    if (value == null || !MOBILE.matcher(value).matches())
      throw new IllegalArgumentException("Enter a 10-digit mobile number");
  }

  public static void requireEmail(String value) {
    if (value == null || !EMAIL.matcher(value.trim()).matches())
      throw new IllegalArgumentException("Enter a valid email address");
  }

  public static void requirePassword(String value, int minLength) {
    if (value == null || value.length() < minLength
        || !UPPERCASE.matcher(value).find()
        || !NUMBER.matcher(value).find()
        || !SPECIAL.matcher(value).find())
      throw new IllegalArgumentException("Password must have at least " + minLength
          + " characters, one uppercase letter, one number, and one special character");
  }
}
