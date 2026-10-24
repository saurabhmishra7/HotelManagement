# Production email setup (Resend)

InnovaServe sends password-reset links, optional email-verification links, and
Owner approval messages through the backend's `EmailSender`. The `dev` profile
uses Resend's test sender and redirects every message to one development inbox.
Production must use a domain you control and a sender address on that domain.

## 1. Verify a sending domain

1. Obtain a domain whose DNS records you can edit. A dedicated subdomain such
   as `mail.example.com` is also suitable.
2. Add that domain or subdomain under **Domains** in Resend and enable sending.
3. Copy the DNS records Resend gives you to your DNS provider. Use the exact
   names and values shown in your Resend account; do not copy records from an
   example or another domain.
4. Wait until Resend reports that sending is verified. Resend's verification
   screen shows which records are still pending.

See [Resend's domain verification guidance](https://resend.com/changelog/domain-verification-events).

## 2. Create a production API key

Create a separate key in **Resend → API Keys** with **Sending access**. Restrict
it to the production sending domain if that option is available. Save the key
in the backend deployment platform's secret store; it belongs only on the
server. See [Resend's API key permissions](https://resend.com/changelog/new-api-key-permissions).

## 3. Set backend deployment variables

Set these on the **backend service**, replacing the example domain and URLs:

```text
APP_MAIL_PROVIDER=resend
APP_MAIL_FROM=InnovaServe <no-reply@mail.example.com>
APP_MAIL_RESEND_API_KEY=<production sending key>
APP_PASSWORD_RESET_BASE_URL=https://staff.example.com/reset-password
APP_EMAIL_VERIFICATION_BASE_URL=https://staff.example.com/
APP_STAFF_APPROVAL_BASE_URL=https://staff.example.com/app/team
```

The domain in `APP_MAIL_FROM` must be the one verified for sending in Resend.
The three URLs must point to the deployed staff application, not `localhost`.
Restart or redeploy the backend after changing environment variables.

Do **not** activate the `dev` Spring profile in production: leave
`SPRING_PROFILES_ACTIVE` unset unless another production profile is needed.
Do not set `APP_MAIL_DEV_RECIPIENT`; it is only for the `dev` profile. Resend
uses its HTTP API here, so `spring.mail.host` is not needed.

## 4. Remove local secrets before deploying

`src/main/resources/application.properties` must not contain an API-key
fallback or another real credential. Keep
`app.mail.resend-api-key=${APP_MAIL_RESEND_API_KEY:}` and supply the key through
the backend secret store. Never put the key in a frontend `VITE_*` variable,
repository, deployment log, or container image. Rotate any key that was
committed or shared.

## 5. Check delivery

After deployment, use **Forgot password** with a real property code and the
email address of an active staff account. That address must belong to the
staff account; the development inbox address is not a substitute. Confirm the
message appears in the recipient's inbox and in Resend's **Emails** activity.
Also confirm the reset link opens the deployed staff application and a new
password can be set. The UI intentionally returns the same request message
for unknown accounts, so use a known account for this check. If delivery
fails, inspect backend logs and the Resend activity/error details.

Production sends to the actual staff recipient. The `[DEV]` prefix and
development-inbox redirection apply only while the `dev` profile is active.
