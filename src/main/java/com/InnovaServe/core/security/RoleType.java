package com.InnovaServe.core.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.*;

public enum RoleType {
  OWNER,
  HOTEL_ADMIN,
  GENERAL_MANAGER,
  FRONT_DESK,
  HOUSEKEEPING,
  RESTAURANT_MANAGER,
  WAITER,
  KITCHEN_STAFF,
  ACCOUNTANT,
  EXPENSE_MANAGER;

  @JsonCreator
  public static RoleType fromJson(String value) {
    if (value == null) return null;
    return RoleType.valueOf(
        value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT));
  }

  public Set<Permission> defaultPermissions() {
    return switch (this) {
      case OWNER, HOTEL_ADMIN -> EnumSet.allOf(Permission.class);
      case GENERAL_MANAGER -> EnumSet.complementOf(EnumSet.of(Permission.ROLE_MANAGE));
      case FRONT_DESK ->
          EnumSet.of(
              Permission.CUSTOMER_READ,
              Permission.CUSTOMER_WRITE,
              Permission.ROOM_READ,
              Permission.STAY_READ,
              Permission.STAY_CHECKIN,
              Permission.STAY_CHARGE,
              Permission.STAY_CHECKOUT,
              Permission.FORM_C_READ,
              Permission.FORM_C_SUBMIT,
              Permission.ACCOUNT_READ,
              Permission.BILLING_READ,
              Permission.BILLING_CREATE,
              Permission.PAYMENT_RECORD,
              Permission.MENU_READ,
              Permission.TABLE_READ,
              Permission.QR_CODE_MANAGE,
              Permission.ORDER_READ,
              Permission.ORDER_CREATE,
              Permission.BILL_CREATE,
              Permission.BILL_SETTLE,
              Permission.APPROVAL_PIN_VERIFY);
      case HOUSEKEEPING -> EnumSet.of(Permission.ROOM_READ, Permission.HOUSEKEEPING_UPDATE);
      case RESTAURANT_MANAGER ->
          EnumSet.of(
              Permission.MENU_READ,
              Permission.MENU_MANAGE,
              Permission.TABLE_READ,
              Permission.TABLE_MANAGE,
              Permission.QR_CODE_MANAGE,
              Permission.ORDER_READ,
              Permission.ORDER_CREATE,
              Permission.ORDER_CONFIRM,
              Permission.KITCHEN_READ,
              Permission.KITCHEN_MANAGE,
              Permission.BILL_CREATE,
              Permission.BILL_SETTLE,
              Permission.STAY_READ,
              Permission.BILLING_READ,
              Permission.APPROVAL_PIN_VERIFY);
      case WAITER ->
          EnumSet.of(
              Permission.MENU_READ,
              Permission.TABLE_READ,
              Permission.ORDER_READ,
              Permission.ORDER_CREATE,
              Permission.BILL_CREATE,
              Permission.BILL_SETTLE,
              Permission.STAY_READ);
      case KITCHEN_STAFF ->
          EnumSet.of(Permission.KITCHEN_READ, Permission.KITCHEN_MANAGE, Permission.ORDER_READ);
      case ACCOUNTANT ->
          EnumSet.of(
              Permission.CUSTOMER_READ,
              Permission.TAX_READ,
              Permission.TAX_MANAGE,
              Permission.CREDIT_NOTE_CREATE,
              Permission.ACCOUNT_READ,
              Permission.ACCOUNT_MANAGE,
              Permission.BILLING_READ,
              Permission.BILLING_CREATE,
              Permission.PAYMENT_RECORD,
              Permission.EXPENSE_READ,
              Permission.EXPENSE_REPORT,
              Permission.RECURRING_EXPENSE_MANAGE,
              Permission.AUDIT_READ);
      case EXPENSE_MANAGER ->
          EnumSet.of(
              Permission.EXPENSE_READ,
              Permission.EXPENSE_CREATE,
              Permission.EXPENSE_APPROVE,
              Permission.EXPENSE_REPORT,
              Permission.PETTY_CASH_MANAGE,
              Permission.RECURRING_EXPENSE_MANAGE,
              Permission.APPROVAL_PIN_VERIFY);
    };
  }
}
