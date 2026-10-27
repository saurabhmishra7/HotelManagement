# Tenant financial reports

Open **Reports** beside the module selector, under **Finance** in sidebar view, or under **More → Finance** on mobile.

The initial period is today and the preceding 29 calendar days in the device's time zone. Use the shared date pickers, period shortcuts and business-area selector, then **Apply filters**. Draft changes do not affect the displayed report. **Reset** restores the last 30 days and all areas; **Refresh reports** reloads the applied selection. A request covers up to 366 days.

## Access

Owners receive `FINANCIAL_REPORTS_READ` automatically, including existing owners. Hotel Admin, General Manager and wildcard permissions do not grant it automatically. No database migration is needed.

An Owner can open **Team & roles**, edit a role, and enable **View financial reports** in the **Financial reports** group. This permission exposes all available financial report areas. Only an Owner can change this permission. Non-owner assignment of a role carrying it uses the existing owner approval flow. Sign in again to refresh navigation after access changes; API permissions are checked against current roles on each request.

## Figures and their dates

| Figure | Definition |
| --- | --- |
| Billed sales | Final/credited invoice totals, including tax, dated by finalisation (creation date fallback for legacy invoices). Draft invoices excluded. |
| Net billed income | Billed sales less credit notes issued within the selected period. Credits can relate to earlier invoices. |
| Payments received | Cash, card and UPI payments dated by payment time, including payments for older invoices. Account transfers excluded. These are not added again to sales. |
| Recorded expenses | Approved and approval-exempt expenses dated by expense date. Pending expenses shown separately. |
| Outstanding | Current invoice total less all payments and credits, floored at zero per invoice, for invoices finalised in the selected period. This is not a historical closing balance. |
| Planning indicator | Net billed income less recorded expenses. Not net profit: tax is included and unrecorded business costs are absent. |

Reports group Hotel sales by stay invoices, Bar sales by the restaurant order's `bar` service station, and other restaurant sales under Restaurant. Bar orders can include kitchen food. Expense departments determine expense allocation; shared costs remain General. The current expense form has no Bar department, so existing Bar costs may be in Restaurant. Do not infer departmental profit from incomplete cost allocations.

Inventory purchases already entered as expenses are counted once. Price lists, average stock costs, petty cash transfers, and future recurring charges are not transaction expenses. Only active subscription modules contribute their associated data.

## Implementation and checks

`GET /api/v1/reports/financial?from=YYYY-MM-DD&to=YYYY-MM-DD&timezone=Asia/Kolkata&area=all` returns daily aggregates in one request. All source queries are tenant-scoped; income, payments and credits are aggregated independently to avoid join multiplication. Totals are independent of table pagination. Stored timestamps follow the application's UTC interpretation; date-only expenses retain their business date.

UI charts include daily sales/expense lines, business-area sales donut, and expense-category bars, with accessible textual figures and tables. The page uses current theme colours and responsive layouts.

Validated with frontend production build, Java 21 compilation and focused access tests. An isolated PostgreSQL fixture checked tenant isolation, local date boundaries, draft exclusion, multiple payments/credits without duplicate income, earlier-invoice collections, outstanding balances, and area/module filtering. No application data was modified for these checks.
