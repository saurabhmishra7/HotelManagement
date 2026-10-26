# InnovaServe working context

Read this file at the start of each user request and update it after every prompt. Keep it concise; current requests override older notes. The active checkout is `/Users/dheerajsharma/Documents/HMS-app`.

## Product decisions
- Preserve established hotel, restaurant, billing, inventory, notification, and guest workflows; avoid unnecessary API calls.
- Respect tenant role permissions and plan entitlements across navigation and actions.
- Table QR orders are confirmed by staff before sending to kitchen; additions append to the existing open order and final billing creates one invoice.
- Guest portal is a separate mobile Vite app; QR base URL is environment-configured.
- Required fields show an asterisk; modals lock background scrolling and keep modal content scrollable.

## Current implementation
- Printer & KOT setup is being implemented per `printer-kot-complete-prompt.md`: printer routes, WebUSB/network/Windows agent, shared durable queue, role-based operator permission, and uncertain-output review. Physical printer/Windows validation remains outstanding.
- The previous printer migration V46 had been applied to some databases, then removed during rollback. Current printer schema is V47; Flyway ignores missing versioned migrations so those databases can proceed without manual schema-history edits. This pattern can also hide other missing versioned migrations; restore the historical migration if recovered and reassess. The backend generated output was cleaned so stale V46 resources are gone.
- Station printer route dropdowns now use the shared `StyledSelect` and field-label styles rather than native selects. Staff frontend production build passed.

## Open work
- Restart backend to verify Flyway applies V47 against the configured database; this workspace did not modify the database.
- Validate physical printer delivery and the Windows agent on actual supported hardware.
