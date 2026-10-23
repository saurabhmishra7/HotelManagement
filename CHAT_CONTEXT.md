# InnovaServe working context

Read this brief at the start of a task. Update it after each user prompt; keep current facts and unresolved work, rather than a chronological transcript. The latest user request takes precedence.

## Project layout

- `HotelManagement`: Spring Boot backend, Flyway migrations, and backend documentation.
- `InnovaServe-Frontend`: staff and tenant React/Vite app, generally on port 3000 locally.
- `InnovaServe-Guest-Portal`: mobile-friendly QR guest React/Vite app, generally on port 3002 locally.
- `InnovaServe-Platform-Portal`: platform administration React/Vite app.
- These are sibling Git repositories under `/Users/dheerajsharma/Documents/HMS-app`. Backend guidance in `HotelManagement/AGENTS.md` requires `docs/DATA_MODEL.md` updates alongside schema/model changes.

## Product decisions and user preferences

- Preserve established hotel, restaurant, billing, inventory, notification, and guest flows when making changes. Favor clear, consistent UI and reusable components. Avoid unnecessary API calls.
- Staff can use either sidebar or module workspace navigation. Access must respect roles and plan entitlements.
- Restaurant tables support adding dishes to an existing open order; billing should create one final invoice for that order. Bill Now and Bill Later mark an order billed when chosen. Table status reflects service and cleaning workflow, rather than payment status.
- Guest QR links open the separate guest portal, where table guests can browse the menu, order, add items, and request a detailed bill once food is served. Room guests can request room service and order food; room service completion requires both attendant and guest confirmation.
- Relevant staff receive category-based notifications, including guest orders, room service, cleaning, bill requests, and inventory alerts. Inventory notifications are important and remain until dismissed.
- QR deployment may use a shared domain or separate domains. Guest portal URL is configured through environment settings; local development should use a reachable LAN address for phone scanning without editing source code.
- Show times in the device's timezone. Modal dialogs should keep the background from scrolling while allowing modal content to scroll. Required fields use an asterisk beside the label and field-level validation.

## Latest completed request

- Shared staff-app modals now keep the header and close icon outside a dedicated scrollable content area, preventing the header background from covering scrolled content. The separate QR preview/print modal retains its sticky close icon. Responsive spacing stays consistent.
- Notification record links now close both the notification popup and bell panel, then open the relevant order detail or focus the matching room, inventory item, or room-service request. Links respect visible sections. The inbox API exposes the existing `source_key`, and new kitchen-ready notices include their order ID for direct order navigation. Older notices without an order ID open the Orders section. Opening a notice does not dismiss its durable inbox entry.
- Moved the waiter’s `Mark served` action into the dish description cell of the existing Restaurant order bill preview. Removed the separate ready-items section and its styling. Preview lines now carry `order_item_id` so the action targets the exact item even when the same dish appears more than once. The order popup's bill table no longer renders filter or pagination controls, reducing its height. The action still calls the existing served endpoint and waits for its response.
- Kitchen Display now marks prepared items `ready` instead of directly `served`. Flyway V34 adds `ready` and `ready_at`; marking ready creates an IMPORTANT notification for staff with `ORDER_CREATE`, preferring WAITER, including the dish and table/room/takeaway location. The notice is dismissed once a waiter marks the item served from the Restaurant table or order popup. Printer-only workflow still lets staff serve directly. Kitchen tickets clear when all items are ready, while billing and inventory deduction still wait for `served`. Service performance uses ready time for kitchen duration and served time for total duration. Staff notifications continue using the existing 20-second inbox polling.
- Added cancellation for empty open restaurant orders. Staff with `ORDER_CREATE` can cancel from the order builder, the order detail popup, or an occupied table popup. The backend locks the order, rejects cancellation if any item or bill exists, marks it `cancelled`, and frees its table when no other open order remains. It also closes active QR sessions for the freed table. Cancelled orders remain in history. No migration is needed because `cancelled` was already allowed by the order status constraint.
- Fixed a table QR guest remaining on “Billing has started” after payment. The payment event now closes each fully paid guest session independently, and session reads reconcile payment status if an event did not close it. The guest portal checks every 5 seconds while table payment is pending (15 seconds otherwise), then shows the green paid screen. Rescanning after payment may start a fresh session on a billed table even when another QR session is active; dirty tables still require staff to mark them ready.
- Guest portal shows order and bill details without the Menu tab once table billing starts. After payment, it shows a green paid check and order breakdown and clears the stored QR session token. Room QR ordering can order again after its restaurant order is billed.
- Previously, Restaurant order popup gained Bill Later for served table QR orders, with active room selection and phone verification. A transactional `bill-to-room` backend endpoint creates and settles the bill in one request, rolling back on verification failure.

## Open work

- No task remains from the latest request. Automated tests were not run because they were not requested.

## Worktree note

- At creation, the backend repository already had an unrelated modification to `src/main/resources/application.properties`; preserve it.
