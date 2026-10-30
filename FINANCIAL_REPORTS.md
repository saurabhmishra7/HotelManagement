# Tenant financial reports

Open **Reports** beside the module selector, under **Finance** in sidebar view, or under **More → Finance** on mobile.

The initial period is today and the preceding 29 calendar days in the device's time zone. Use the shared date pickers, period shortcuts and business-area selector, then **Apply filters**. Draft changes do not affect the displayed report. **Reset** restores the last 30 days and all areas; **Refresh reports** reloads the applied selection. A request covers up to 366 days.

Report content is organised into **Overview**, **Compare periods**, **Area contribution**, **Payments**, **Records**, **Hotel**, **Menu**, **Guest ratings**, and **Unpaid bills** tabs. In Module view, these appear in the same secondary navigation row as other module tabs; Sidebar and mobile views show them on the Reports page. Period filters apply to period-based figures; Outstanding and Unpaid bills show current open balances across all invoice dates. The business-area filter applies throughout. Area contribution requires Expenses; Hotel, Menu, and Guest ratings appear only when the active subscription and selected business area make those reports relevant. Tabs switch without changing the applied filters and scroll horizontally on small screens.

## Access

Owners receive `FINANCIAL_REPORTS_READ` automatically, including existing owners. Hotel Admin, General Manager and wildcard permissions do not grant it automatically. No database migration is needed.

An Owner can open **Team & roles**, edit a role, and enable **View financial reports** in the **Financial reports** group. This permission exposes all available financial report areas. Only an Owner can change this permission. Non-owner assignment of a role carrying it uses the existing owner approval flow. Sign in again to refresh navigation after access changes; API permissions are checked against current roles on each request.

## Figures and their dates

| Figure | Definition |
| --- | --- |
| Billed sales | Final/credited invoice totals, including tax, dated by finalisation (creation date fallback for legacy invoices). Draft invoices excluded. |
| Net billed income | Billed sales less credit notes issued within the selected period. Credits can relate to earlier invoices. |
| Payments received | Cash, Card and UPI receipts plus unallocated check-in advances. Each advance is counted once: allocations plus the remaining unallocated amount. Recorded advance methods participate in the method breakdown; historical unknown methods remain unclassified. Records merge invoice receipts. Account transfers excluded. Collections are not added again to sales. |
| Recorded expenses | Approved and approval-exempt expenses dated by expense date. Pending expenses shown separately. |
| Outstanding | Current balance due across all final/credited invoices, after payments and credits, floored at zero per invoice. The date range does not restrict this current balance; the business-area filter still applies. This is not a historical closing balance. |
| Planning indicator | Net billed income less recorded expenses. Not net profit: tax is included and unrecorded business costs are absent. |

Reports group Hotel sales by stay invoices, Bar sales by the restaurant order's `bar` service station, and other restaurant sales under Restaurant. Bar orders can include kitchen food. Expense departments determine expense allocation; shared costs remain General. The current expense form has no Bar department, so existing Bar costs may be in Restaurant. Do not infer departmental profit from incomplete cost allocations.

Inventory purchases already entered as expenses are counted once. Price lists, average stock costs, petty cash transfers, and future recurring charges are not transaction expenses. Only active subscription modules contribute their associated data.

## Implementation and checks

`GET /api/v1/reports/financial?from=YYYY-MM-DD&to=YYYY-MM-DD&timezone=Asia/Kolkata&area=all` returns daily aggregates in one request. All source queries are tenant-scoped; income, payments and credits are aggregated independently to avoid join multiplication. Totals are independent of table pagination. Receipt/check-in timestamps follow the application's existing UTC interpretation; date-only expense fields retain their business dates.

UI charts include daily sales/expense lines, business-area sales donut, and expense-category bars, with accessible textual figures and tables. The page uses current theme colours and responsive layouts.

## Business insight sections

- **Previous period:** The same length immediately before the selected range, using the same business-area filter. Sales, collections and expenses follow their event dates. Current outstanding is shown in Overview and Unpaid bills, not compared with a previous period because historical balance snapshots are not stored.
- **Hotel performance:** Available room nights use the current room count for each day. Occupied room nights use recorded stays and their check-in/check-out dates. Estimated room revenue multiplies each occupied night by the stay's current tariff. Occupancy = occupied / available nights; ADR = estimated room revenue / occupied nights; RevPAR = estimated room revenue / available nights. The app does not retain historical room closures, moves, or tariff changes, so these are operational estimates rather than audited STR metrics. Tax and extras are excluded.
- **Menu performance:** Served dish quantity is dated by service time (placement time fallback for historical records). Popularity is the dish's share of served units in its category. Estimated contribution per serving is current menu price minus the recipe's ingredient quantities at current weighted average inventory costs. Dishes without a recipe or priced ingredients show “Cost unavailable”. This excludes tax, labour, waste and overheads and does not reconstruct historical costs or prices.
- **Unpaid bills by age:** Groups all current unpaid balances into 0–7, 8–30, 31–60, and 61+ days since finalisation, regardless of the selected date range. The business-area filter applies. There is no invoice due date in the app, so these are age bands, not contractual overdue status. Each entry links to Billing when the user also has `BILLING_READ`.
- **Area contribution:** Billed income less credits issued and approved/approval-exempt expenses recorded in the selected period, grouped by service area. The balance includes billed tax, can combine events from different invoices, and excludes unrecorded labour/overheads. It is a planning indicator, not departmental profit or a formal P&L. Bar sales follow the order's service station, while expenses use their entered department.
- **Payment methods:** Cash, Card and UPI include advances with a recorded method. Unknown historical methods remain in the collection total, outside the known-method chart. Account transfers and bill-later decisions are not new receipts.
- **Guest ratings:** Average dish stars and number of ratings submitted in the selected dates and area, displayed beside served quantity in the same dates and area. Ratings are per guest submission, not per serving quantity. Written comments expire after a month; dish ratings remain available. A rating may refer to a dish served in a different period.
- **Records:** Payments and Expenses sub-tabs show individual transactions using the app's filterable, paginated table. The shared date and business-area filters apply first; table conditions then narrow the displayed records. The amount above the table and **Download Excel** include every matching record across all table pages. Expenses includes pending approvals, with their status visible, so its sum can differ from Overview's approved-expense figure. Expenses appears only with an active Expenses subscription.

Table conditions retain choice types: payment method offers Cash/Card/UPI (plus Petty cash for expenses), approval status offers its valid states, and category/area are selected from dropdowns. The same choice-aware filter control is available to other app tables; their known form options can be passed to it so users do not need to type enum values.

`GET /api/v1/reports/insights` uses the same date, time zone, area, tenant and report-permission checks as the financial endpoint. Hotel and menu sections only appear with their active modules. No database migration is required.

`GET /api/v1/reports/financial/records` groups receipts by real invoice number. Before allocation, the unallocated amount appears as Pending invoice with a room reference. Once allocated it moves into its invoice row, without duplication. Remaining excess stays visible for reconciliation; there is no refund workflow yet. Advance/Paid is a separate status based on the current balance after payments and credits, never a fourth payment method. Received in period sums only receipts within the selected dates; Total paid to date and Outstanding describe the current invoice. The row date is the latest receipt within the selected period. Multiple actual methods are listed together; a method filter selects whole invoices containing that method in the period, including their other receipts. For exact method totals, use the Payments tab. The date/area selection applies before table filters and Excel export. Tenant/module/report permissions remain enforced. The mobile table renders invoice cards. Excel includes every filtered row, not only the visible page; unknown outstanding values remain blank. Historical advances are not assigned an invented method; new positive check-in advances require Cash, Card or UPI. V57 adds this nullable method field.

The new views use the existing theme-aware SVG and CSS charts, with consistent area colours and a short takeaway. No new chart dependency or data-capture schema is required. A physical stocktake is not currently captured, so recipe deductions cannot establish real food-cost variance or detect shrinkage by themselves; that proposed report is deferred. Tax breakdown by rule is also deferred rather than presenting the current billed tax total as tax collected or as a filing-ready GST statement.

Validated with frontend production build, Java 21 compilation and focused access tests. An isolated PostgreSQL fixture checked tenant isolation, local date boundaries, draft exclusion, multiple payments/credits without duplicate income, earlier-invoice collections, outstanding balances, and area/module filtering. No application data was modified for these checks.

Latest advance update: code review and build results are recorded in CHAT_CONTEXT.md; previous validation above predates V57. Live receipt, checkout and report reconciliation must still be checked after migration.

### Pending-invoice outstanding estimate

Records shows a current estimate for active stays whose checkout invoice has not been prepared: agreed nightly tariff × accrued nights (minimum one, using the same server date/date-difference convention as checkout), plus quantity × unit price for recorded unbilled stay charges, less the unused advance. The result is floored at zero. Tax, future nights, unrecorded extras and future discounts are excluded. Already invoiced charges remain on their own invoices and are not counted again here. Balance basis is exported and displayed as Estimated · before tax; a zero estimate still has Advance status until invoicing. Once checkout invoicing occurs, the real invoice balance replaces the estimate; an excess unallocated receipt has zero outstanding and Unused advance basis. This estimate is current, not a historical closing balance at the report end date. The Overview outstanding and Unpaid bills reports remain finalized-invoice balances.
