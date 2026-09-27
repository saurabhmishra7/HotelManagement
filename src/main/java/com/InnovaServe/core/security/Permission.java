package com.InnovaServe.core.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

public enum Permission {
  STAFF_READ,
  STAFF_MANAGE,
  ROLE_READ,
  ROLE_MANAGE,
  CUSTOMER_READ,
  CUSTOMER_WRITE,
  TAX_READ,
  TAX_MANAGE,
  ACCOUNT_READ,
  ACCOUNT_MANAGE,
  BILLING_READ,
  BILLING_CREATE,
  PAYMENT_RECORD,
  ROOM_READ,
  HOUSEKEEPING_UPDATE,
  STAY_READ,
  STAY_CHECKIN,
  STAY_CHARGE,
  STAY_CHECKOUT,
  FORM_C_READ,
  FORM_C_SUBMIT,
  MENU_READ,
  MENU_MANAGE,
  TABLE_READ,
  ORDER_READ,
  ORDER_CREATE,
  ORDER_CONFIRM,
  KITCHEN_READ,
  KITCHEN_MANAGE,
  BILL_CREATE,
  BILL_SETTLE,
  EXPENSE_READ,
  EXPENSE_CREATE,
  EXPENSE_APPROVE,
  EXPENSE_REPORT,
  PETTY_CASH_MANAGE,
  RECURRING_EXPENSE_MANAGE,
  AUDIT_READ,
  APPROVAL_PIN_VERIFY;

  @JsonCreator
  public static Permission fromJson(String value) {
    if (value == null) return null;
    return Permission.valueOf(
        value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT));
  }
}
