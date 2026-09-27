-- V4: Expense module
-- ExpenseCategory, Expense, PettyCashLedger, RecurringExpense
-- Deliberately has NO link to Account or Invoice — only Tenant + User from Core.

-- ===================== EXPENSE CATEGORY =====================
CREATE TABLE expense.expense_category (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES core.tenant(id),
    name        VARCHAR(100) NOT NULL
);

-- ===================== EXPENSE =====================
CREATE TABLE expense.expense (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL REFERENCES core.tenant(id),
    category_id        UUID NOT NULL REFERENCES expense.expense_category(id),
    department         VARCHAR(15) NOT NULL CHECK (department IN ('rooms','restaurant','general')),
    vendor_name        VARCHAR(150),
    amount             NUMERIC(10,2) NOT NULL,
    payment_mode       VARCHAR(15) NOT NULL CHECK (payment_mode IN ('cash','card','upi','petty_cash')),
    receipt_file_ref   VARCHAR(300),
    entered_by         UUID NOT NULL REFERENCES core.users(id),
    approved_by        UUID REFERENCES core.users(id),
    approval_status    VARCHAR(15) NOT NULL CHECK (approval_status IN ('pending','approved','not_required')),
    expense_date       DATE NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT now()
);

-- ===================== PETTY CASH LEDGER =====================
CREATE TABLE expense.petty_cash_ledger (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL REFERENCES core.tenant(id),
    shift_date                  DATE NOT NULL,
    opening_float               NUMERIC(10,2) NOT NULL,
    top_up_amount               NUMERIC(10,2) NOT NULL DEFAULT 0,
    closing_balance_expected    NUMERIC(10,2) NOT NULL,
    closing_balance_actual      NUMERIC(10,2),
    reconciled_by               UUID REFERENCES core.users(id),
    reconciled_at               TIMESTAMP
);

-- ===================== RECURRING EXPENSE =====================
CREATE TABLE expense.recurring_expense (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES core.tenant(id),
    category_id     UUID NOT NULL REFERENCES expense.expense_category(id),
    description     VARCHAR(200) NOT NULL,
    amount          NUMERIC(10,2) NOT NULL,
    frequency       VARCHAR(10) NOT NULL CHECK (frequency IN ('weekly','monthly')),
    next_due_date   DATE NOT NULL,
    last_paid_date  DATE
);

-- ===================== INDEXES =====================
CREATE INDEX idx_expensecategory_tenant  ON expense.expense_category (tenant_id);
CREATE INDEX idx_expense_tenant          ON expense.expense (tenant_id);
CREATE INDEX idx_expense_category        ON expense.expense (category_id);
CREATE INDEX idx_pettycash_tenant_date   ON expense.petty_cash_ledger (tenant_id, shift_date);
CREATE INDEX idx_recurringexpense_tenant ON expense.recurring_expense (tenant_id);