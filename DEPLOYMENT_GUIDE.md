# Backend deployment: GitHub Actions and Zoho Catalyst AppSail

## Current Development deployment (30 October 2026)

The `InnovaserveBE` Java 21 AppSail artifact was uploaded to the IN project `Innovaserve` at `https://innovaservebe-50047307449.development.catalystappsail.in`. Neon PostgreSQL and the signing keys are configured in Catalyst Development. The earlier artifact still returns HTTP 503 because Spring Boot finishes starting after AppSail's ten-second port-listening deadline. The updated application opens AppSail's public port first, then starts Spring on a private loopback port and forwards requests once it is ready. The updated artifact must be deployed before `/health` can return HTTP 200.

The remote Development secrets were entered in the Catalyst Console, so **do not use `catalyst deploy` with the current linked `app-config.json`**: its `env_variables` map can replace Console-only values. Build the ZIP below and create a new deployment through **AppSail → InnovaserveBE → Deployments → Create Deployment**. Choose Catalyst-managed Java 21, upload the ZIP, keep the existing 9000 port, 1024 MB memory, startup command and Development environment variables. Check that all five private keys are still present after deployment, then call `/health`. No secret values belong in the ZIP or repository.

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -DskipTests package
bash scripts/package-appsail.sh
(cd target/appsail && zip -q -X -FS ../innovaserve-appsail-console.zip innovaserve.jar)
```

Upload `target/innovaserve-appsail-console.zip`, which contains only `innovaserve.jar`. For the startup command, use `java -XX:+UseContainerSupport -XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError -jar innovaserve.jar`. After the Console deployment completes, the AppSail log should show `AppSail listener opened` promptly and `Spring Boot is ready` once initialization finishes. On a cold start, the first request may wait while Spring starts; if it is still warming after 25 seconds, the listener returns a retryable 503. The normal `/health` response remains the Spring endpoint's `{"status":"UP"}`.

The generated `appsail-java/` Java 8 demo remains in the checkout but is not an active `catalyst.json` deployment entry. The active entry points to the root backend. The CI deployment target has been aligned to `InnovaserveBE`.

## Release model

Java 21 builds `target/innovaserve.jar`. Only `target/appsail/innovaserve.jar` is uploaded; the repository, local configuration and test reports are not deployment content. `catalyst.json` names the service `InnovaserveBE`; `app-config.json` selects `javase`, `java21`, 1024 MB and a container-aware JVM heap capped at 70% of available memory. AppSail supplies `X_ZOHO_CATALYST_LISTEN_PORT`; local fallback is `PORT`, then 8080. On AppSail, a Java HTTP listener binds the supplied port immediately and waits up to 25 seconds for Spring Boot to finish startup before forwarding a request. Spring itself listens only on a random loopback port. Startup failures close the listener and exit; while warming, requests that exceed the wait return a retryable 503. This proxy supports the current HTTP API and file uploads; add WebSocket upgrade support if the backend later introduces WebSocket endpoints.

Pull requests to `main` run security and build jobs. The CLI deployment job is disabled by default while required runtime secrets live only in Catalyst Console; set `CATALYST_CLI_DEPLOY_ENABLED=true` only after the workflow is changed to supply every runtime variable securely on each CLI deployment. Manual workflow dispatch runs checks, not deployment. Deployments go to **Catalyst Development**. The CLI does not promote to Catalyst Production: validate Development and promote the release in the Catalyst Console. Keep separate database credentials and application secrets in each environment.

## Before the first release

1. Rotate credentials previously committed to Git. The local audit found a secret in historical `src/main/resources/application.properties`; the new history gate intentionally blocks release until the repository history is remediated. Coordinate history cleanup with collaborators and repository administrators; do not blindly force-push or add a blanket scanner exclusion. Rotation is necessary even after history cleanup.
2. Create/select your real Catalyst project and its AppSail service, named `InnovaserveBE`. Configure Java 21, 1024 MB, and the command from `app-config.json`. If retaining another service name, change it consistently in both JSON files and the workflow `--only` argument.
3. Configure the environment variables below in **Catalyst Console → AppSail → service → Configuration → Environment Variables**, separately for Development and Production. Do not commit secret values in `app-config.json` or GitHub variables.
4. Provision PostgreSQL reachable from AppSail, with TLS certificate verification, backups and a role allowed to run this application's Flyway migrations. Use a separate database for Development. Size connection limits for `DB_POOL_SIZE × maximum instances` plus operational headroom.
5. Configure verified email sending and a live payment provider. Production refuses demo payments and automatic platform-admin bootstrap. Provision the initial platform administrator through a controlled operator procedure before enabling access; do not turn production bootstrap back on.
6. Arrange durable receipt storage. The current expense receipt service uses files; AppSail instance-local storage is not a durable shared store. Do not rely on it across restarts or multiple instances. An object-storage integration or supported durable shared filesystem is a prerequisite for production receipt retention.

### Environment variables

`.env.example` is a template, not a file automatically loaded by Spring. Supply values through the host environment/Console. Never paste credentials into an issue, CI log or screenshot.

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `staging` in Catalyst Development while frontends are local; set `prod` for Production |
| `DB_URL` | PostgreSQL JDBC URL with `sslmode=verify-full` and appropriate trusted CA |
| `DB_USERNAME`, `DB_PASSWORD` | Database credentials; password required in prod |
| `JWT_SECRET` | Strong random tenant signing key, at least 32 characters |
| `PLATFORM_JWT_SECRET` | Different strong random platform key, at least 32 characters |
| `APP_ALLOWED_ORIGINS` | Comma-separated exact HTTPS tenant and guest frontend origins |
| `PLATFORM_ALLOWED_ORIGINS` | Exact HTTPS platform frontend origins |
| `APP_SIGNUP_VERIFICATION_BASE_URL` | Tenant frontend base URL for signup verification |
| `APP_PASSWORD_RESET_BASE_URL` | Tenant frontend base URL for password reset |
| `APP_STAFF_INVITATION_BASE_URL` | Tenant frontend invitation route, normally `/invite` |
| `APP_STAFF_APPROVAL_BASE_URL` | Tenant frontend base URL for owner approval |
| `APP_EMAIL_VERIFICATION_BASE_URL` | Tenant frontend base URL for email verification |
| `APP_MAIL_PROVIDER` | `resend` or `smtp` |
| `APP_MAIL_FROM` | Verified sender address/display name |
| `APP_MAIL_RESEND_API_KEY` | Required when using Resend |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | SMTP alternative; configure provider-required TLS/auth Spring mail properties too |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | Live payment credentials when using Razorpay |
| `TENANT_PROVISIONING_KEY` | Optional protected provisioning integration; leave disabled/empty unless required |
| `DB_POOL_SIZE` | Defaults to 10; tune for database and instance limits |
| `RECEIPT_STORAGE_DIR` | Durable storage path only if the hosting setup supports it; see warning above |

The `prod` configuration guard rejects blank signing keys/database password, shared tenant/platform keys, HTTP or wildcard origins, non-HTTPS email links, demo payments and bootstrap. CORS origins contain scheme/host/optional port, **no route or trailing slash**. Email links must target the tenant frontend, not the backend or platform portal. Confirm every route after frontend deployment.

Set frontend API and guest QR URLs in their own deployments; backend settings do not rebuild Vite apps. Ensure the guest QR host is publicly reachable HTTPS, never a local Wi-Fi address.

### Local configuration preservation

During this change, the existing local `application.properties` values were preserved in ignored `.env.properties` with owner-only permissions. The tracked configuration imports that external file locally. It is outside `src/main/resources` and is not shipped. Production disables the import. Keep local credentials in that file or environment variables. Dev/local resource files are excluded from the packaged JAR. Do not copy the local backup into an AppSail artifact.

## CLI and GitHub setup

```sh
npm install --global zcatalyst-cli@1.29.1
catalyst login
catalyst token:generate
```

Select the correct Catalyst data centre/project. Keep the generated token private. `catalyst init` is for initial project setup; this repository already has deployment configuration, so do not overwrite it without reviewing changes. Commit `catalyst.json` and `app-config.json`. Local project binding normally lives in `.catalystrc` (ignored here); CI explicitly passes the project ID and data centre and does not depend on your laptop's binding.

In GitHub **Settings → Environments**, create `catalyst-development` and restrict deployment branches to `main`. Add reviewers if desired. Under this environment's secrets/variables, configure:

| Type | Name | Value |
| --- | --- | --- |
| Secret | `CATALYST_TOKEN` | CI token generated for an authorized deployment account |
| Variable | `CATALYST_PROJECT_ID` | Actual project ID from the Console |
| Variable | `CATALYST_DC` | CLI data-centre code for that project, e.g. `in`, `us`, or `eu` |

Application DB/email/payment keys belong in Catalyst, not this workflow. GitHub receives only the Catalyst deployment credential. Enable branch protection/rulesets requiring the `security` and `build` checks and review before merging. Restrict who can edit workflows and deploy credentials.

Do not set `CATALYST_CLI_DEPLOY_ENABLED` while the linked CLI config has only public variables. A CLI redeploy can replace Console-only variables, leaving the service unable to start. The deployment job is intentionally skipped until a reviewed secret-safe release method is set up; the build artifact and Console upload path above remain available.

## Checks and security boundaries

- Secret scans cover the current checkout and full Git history, with redacted reports.
- Pinned Gitleaks and Trivy release downloads are checksum-verified. Trivy **rootfs** scans the packaged Java libraries; the script fails if no Java packages are detected. High/Critical findings block deployment, including those without fixes. No blanket ignore is configured.
- JUnit/Mockito tests and PostgreSQL-backed MockMvc/integration tests run on Java 21. CI uses disposable PostgreSQL 14 on port 55447, never the developer or production database.
- JaCoCo currently enforces **20% whole-backend line coverage**, plus **80%** for `TokenService` and `ProductionConfigurationGuard`. This is an initial regression baseline, not comprehensive coverage. Raise it as business-flow tests are added. The initial run passed 58 tests with about 21.25% overall line coverage.
- Request bodies invoke Bean Validation; billing DTOs have explicit boundaries and existing service validations remain. Existing map-based endpoints still rely on their own service validation. This is not a claim of complete OWASP compliance or a penetration test.
- Security chains enforce authentication/permissions, exact-origin CORS, nosniff, frame denial, restrictive API CSP and HTTPS HSTS. Unexpected errors and SQL constraints return sanitized messages; details stay in server logs.
- JSON bodies are capped at 1 MiB; uploaded files at 10 MiB and multipart requests at 11 MiB. The per-instance authentication limiter defaults to 30 requests/minute/IP, with bounded memory. It uses the actual peer address, not arbitrary forwarded headers. Shared NAT/proxies may share that budget. Use gateway/distributed rate limiting for multi-instance protection; the in-memory limiter resets on restart.
- TLS terminates at the hosting edge. Verify HTTPS/HSTS in the deployed response; only enable forwarded-header interpretation when the ingress strips untrusted client headers. Do not trust arbitrary `X-Forwarded-*` traffic.
- GET `/health` is public and returns only `{"status":"UP"}` after startup. It is a lightweight application liveness/startup signal, not a continuous database or third-party availability check. Configure platform health monitoring to use it and monitor DB errors separately.

## Run locally before pushing

Use Java 21. On this Mac, the default Maven JVM may still be Java 17 even though Java 21 is installed. Check `./mvnw -version`; if it reports Java 17, use:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -version
./mvnw clean package
```

In IntelliJ, set **Project SDK**, **Maven runner JRE**, and **Maven importer JDK** to 21. The error `release version 21 not supported` means Maven is running under an older JDK. Start a disposable PostgreSQL instance for the full verification suite (Docker example):

```sh
docker run --rm --name innovaserve-ci-postgres \
  -e POSTGRES_USER=postgres -e POSTGRES_HOST_AUTH_METHOD=trust \
  -e POSTGRES_DB=innovaserve_test -p 127.0.0.1:55447:5432 -d postgres:14
export TEST_DB_URL=jdbc:postgresql://127.0.0.1:55447/innovaserve_test
export PRINTER_TEST_DB_URL="$TEST_DB_URL"
./mvnw -B -ntp clean verify
bash scripts/package-appsail.sh
bash scripts/install-security-tools.sh /tmp/innovaserve-security-bin
/tmp/innovaserve-security-bin/gitleaks git . --redact
bash scripts/scan-dependencies.sh /tmp/innovaserve-security-bin/trivy
docker stop innovaserve-ci-postgres
```

Do not point these tests at a real property database: integration tests migrate and write records. Existing migrations include a compatibility exception for a historically removed V46 versioned migration; missing-version ignore is retained for existing installs. Review that exception before tightening Flyway settings. Never casually repair checksums or disable migration validation to force deployment.

`gitleaks dir .` locally includes ignored files, so it can flag your local secrets too. CI's clean checkout has no ignored credentials. For a source-only local directory scan use a temporary snapshot of `git ls-files --cached --others --exclude-standard`, preserving paths, instead of suppressing findings globally. Scanner reports can contain file paths and commit metadata even when secrets are redacted; limit artifact access and retention.

Reports: `target/surefire-reports`, `target/failsafe-reports`, `target/site/jacoco/index.html`, and `security-reports/`. CI uploads these separately from the release JAR.

## Release, monitoring and rollback

1. Open a PR; resolve failing checks. Never bypass a vulnerability or historical-secret failure simply to deploy.
2. Merge to `main`. The workflow deploys the tested JAR to Development using explicit project/DC and `--only appsail:InnovaserveBE`.
3. In Catalyst inspect AppSail deployment status, instance logs, startup failures, memory/CPU and request/error metrics. Confirm `/health`, authentication, tenant separation, CORS from all three frontends, email links, real payment configuration and database migration status.
4. Back up the production database and verify restoration procedures. Configure Production secrets separately. Promote the validated AppSail release using the Console's Production deployment flow; review the selected service/version and environment before confirming.
5. Smoke-test Production immediately. Alert on repeated 5xx responses, failed starts, high resource usage, email/payment failures and exhausted database connections. Log retention/access must protect customer information and internal exception details.
6. If application code fails, restore the previous known-good AppSail release through available Console release controls, or redeploy its retained artifact to Development and promote it. Do not assume every account exposes a one-click rollback. Keep release artifacts and commit IDs together.
7. A code rollback **does not undo Flyway migrations**. Roll back only to schema-compatible code; use forward corrective migrations or a deliberately planned database restore with the associated data-loss implications. Never auto-drop tables or auto-repair migration history during deployment.

## References

- [Catalyst CLI deployment](https://docs.catalyst.zoho.com/en/cli/v1/deploy-resources/introduction/)
- [Catalyst project initialization](https://docs.catalyst.zoho.com/en/cli/v1/initialize-resources/initialize-new-project/)
- [AppSail runtime concepts](https://docs.catalyst.zoho.com/en/serverless/help/appsail/catalyst-managed-runtimes/key-concepts/)
- [Trivy Java coverage](https://trivy.dev/docs/latest/coverage/language/java/)
- [JaCoCo coverage checks](https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html)

## Implementation validation (30 October 2026)

- Java 21 `clean verify`: 58 tests, no failures/errors, 21.25% line coverage; both coverage gates passed.
- Packaged JAR excludes local/dev configuration; JSON and workflow YAML parse; shell syntax and Git whitespace checks passed.
- Trivy inspected 92 Java packages with no High/Critical findings after updating Tomcat to 11.0.26 and Jackson BOMs to 2.21.7/3.1.7. These are time-specific scan results, not a promise about future advisories.
- Source snapshot Gitleaks: no findings. Full Git history: one finding; release remains blocked pending credential rotation and coordinated history remediation.
- No GitHub workflow or Catalyst deployment was executed remotely. Real project binding, environment secrets, durable receipt storage and hosted smoke checks remain operator setup tasks.
