# Hotel-Management

## Backend release pipeline

The [GitHub Actions workflow](.github/workflows/ci-cd.yml) builds the Spring Boot JAR with Java 21 and runs the existing Maven checks against a temporary PostgreSQL 16 database. Pull requests to `develop` or `main` run CI only. Pushes to `develop` or `main` deploy the tested JAR to the **Development** environment of the configured Zoho Catalyst project. The `main` run also records a production handoff in the workflow summary.

### One-time setup

1. Create a GitHub environment named `test` in the backend repository. Add environment secret `CATALYST_TOKEN` and environment variables `CATALYST_PROJECT_ID`, `CATALYST_APP_NAME`, and `CATALYST_DC` (for example, `in`). Use a token with permission to deploy the chosen Catalyst project.
2. Configure the Catalyst AppSail service's **Development and Production** environment variables separately: `CATALYST_DB_URL`, `CATALYST_DB_USERNAME`, `CATALYST_DB_PASSWORD`, `CATALYST_APP_JWT_SECRET`, and `CATALYST_TENANT_PROVISIONING_KEY`. Configure other runtime integrations, such as email, with environment variables as needed. Use different database credentials and signing secrets for test and production.
3. Keep `src/main/resources/application.properties` free of literal credentials. The CI guard requires environment placeholders for the database, tenant JWT secret, and provisioning key. Keep local-only values outside tracked source.
4. Enable Catalyst Production and set its payment method and deployment permissions in the Catalyst console before the first live release.

The workflow runs on `main` even if a `develop` branch has not been created yet. If your team uses `develop`, merge into it for test deployments, then merge to `main` for a production candidate.

### Production release

After a successful `main` run, review the test deployment and its workflow summary. In **Catalyst Console → Settings → Environments → Deployments**, promote the tested Development release to Production. Check the live AppSail URL and Flyway migration result afterward. Do not treat the green `production-ready` job as proof of a live deployment: Catalyst's AppSail CLI deploys only to Development, so Production promotion is a separate console action.

If a Catalyst promotion is delayed, avoid merging another backend change to `main` until the intended release is promoted; the Catalyst Development environment may otherwise contain a newer build.
