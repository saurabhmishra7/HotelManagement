# Hotel-Management

## Transactional email

All backend email flows use `EmailSender`. Password reset, optional email
verification, and Owner approval notices share the configured provider. In-app
Owner approval notifications remain available even when email is not configured.

### Development without a domain

Set `SPRING_PROFILES_ACTIVE=dev` and set `APP_MAIL_DEV_RECIPIENT` to the email
address used for your Resend account. Keep the Resend API key in the backend
environment. `application-dev.properties` selects Resend's test sender
(`onboarding@resend.dev`), and the backend routes all transactional emails to
that one inbox with a `[DEV]` subject and the intended recipient in the body.
Resend permits this test sender to deliver only to the account's own email.
Use this profile with development data only: password-reset and approval links
for test accounts will arrive in the configured inbox. The app fails startup
with a clear message if the dev recipient is missing.

### Sending from your own domain

Follow [PRODUCTION_EMAIL_SETUP.md](PRODUCTION_EMAIL_SETUP.md) for domain
verification, deployment variables, secret handling, and a delivery check.
Email defaults and environment mappings live in
`src/main/resources/application.properties`. Approval emails link to the Team
page; the Owner signs in and enters their PIN there. An email delivery failure
is logged and does not cancel the in-app approval request.

Resend is the current provider default. SMTP remains available through
`APP_MAIL_PROVIDER=smtp` and Spring's `spring.mail.*` settings. To switch
providers later, implement `EmailSender` and select it in
`EmailSenderConfiguration`; the email flows and message content need no
provider-specific changes.
