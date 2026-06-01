# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project Snapshot

- Kotlin/JVM HTTP API for converting HTML to accessible PDF/A-3a and PDF/UA output using Ktor and OpenHTMLToPDF.
- Also validates PDFs with veraPDF, identifies generated document UUIDs, and renders HTML to images through OpenHTMLToPDF Java2D.
- Java 24 is required. The Gradle wrapper and Foojay toolchain resolver are part of the repo.
- Single-module Gradle build: source lives at `src/main/kotlin/`, tests at `src/test/kotlin/`, the build script is the root `build.gradle.kts`.

## Important Paths

- `src/main/kotlin/bambamboole/pdfua/Application.kt` defines `main()`, `bootstrap()` (config + DI bindings), the `module()` test aggregator, and the `Route.expensiveRoute` helper (auth + rate-limit envelope).
- `src/main/kotlin/bambamboole/pdfua/Plugins.kt` exposes `Application.<feature>()` installers for cross-cutting plugins (`logging`, `serialization`, `statusPages`, `cors`, `rateLimit`, `auth`, `swagger`).
- Each `http/controller/<Feature>.kt` exports both `Route.<feature>Routes()` (Inspektor-annotated, drives the OpenAPI spec) and `Application.<feature>()` (the Ktor module that resolves DI bindings and wires the routes).
- `src/main/kotlin/bambamboole/pdfua/routes/` contains one route extension per endpoint.
- `src/main/kotlin/bambamboole/pdfua/services/` contains PDF conversion, validation, asset fetching, image optimization, and image rendering.
- `src/main/kotlin/bambamboole/pdfua/models/` contains kotlinx-serializable request/response DTOs.
- `src/main/resources/` contains Ktor config, logback config, bundled open-source fonts, ICC color profile, templates, and examples.
- `src/test/resources/fixtures/` and `src/test/resources/image-fixtures/` are regression baselines. Treat expected PDFs/images as test artifacts, not disposable output.

## Build And Test

- Run all tests: `./gradlew test`
- Run locally: `./gradlew run`
- Build install distribution: `./gradlew installDist`
- Suppress native-access warnings when needed: `export JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED"`
- Tests use JUnit Platform through `kotlin.test`; route tests use Ktor `testApplication`.
- Some conversion tests write generated PDFs or diff images beside fixtures when baselines differ. Review these files before keeping or deleting them.
- CI runs `./gradlew spotlessCheck detekt --no-daemon` then `./gradlew test --no-daemon`. Run `spotlessCheck` locally before pushing — it's not part of `test`, so it's easy to miss formatting drift otherwise.

## Benchmark

- `benchmark/` holds a one-command Docker benchmark against WeasyPrint and Gotenberg Chromium (`cd benchmark && make benchmark`). It runs the engines as HTTP services, drives load with `oha`, validates every output via the API's own veraPDF `/validate`, and writes `benchmark/results/latest.json`, which the docs `/benchmark` page renders at build time.
- The benchmark disables pdf-ua-api's rate limiter (`RATE_LIMIT_ENABLED=false`) so it measures raw engine throughput; this is disclosed in the harness README and on the docs page.

## Runtime Configuration

- Config lives in `src/main/resources/application.yaml`; env-var interpolation uses Ktor's `$ENV_VAR:default` syntax (empty default means "treat as missing"; `AppConfig.getOptional` filters blanks).
- The YAML's `ktor.application.modules` list is the production wiring — Ktor loads each per-feature `Application.<feature>()` in order. Tests call the `Application.module()` aggregator in `Application.kt` which runs the same sequence.
- Services are injected via `ktor-server-di`: `bootstrap()` registers `AppConfig`, `AssetResolver`, `DocumentUploader?`, `JwkProvider?`; later modules consume them with `val foo: T by dependencies`.
- Important variables: `PORT`, `API_KEY`, `JWT_ISSUER`, `JWT_JWKS_URL`, `JWT_AUDIENCE`, `PDF_PRODUCER`, `MAX_REQUEST_SIZE`, `LOG_LEVEL`, `LOG_FORMAT`, `ASSET_TIMEOUT`, `ASSET_MAX_SIZE`, `ASSET_ALLOWED_DOMAINS`, `UPLOAD_ENABLED`, `UPLOAD_TIMEOUT`, `UPLOAD_ALLOWED_DOMAINS`, `RATE_LIMIT_ENABLED`, `RATE_LIMIT_PER_IP`, `RATE_LIMIT_GLOBAL`, `RATE_LIMIT_WINDOW_SECONDS`, `RATE_LIMIT_TRUST_FORWARDED_FOR`, `CORS_ALLOWED_ORIGINS`.
- Docker builds a Gradle install distribution, runs on Eclipse Temurin 24 Alpine, and optionally attaches the OpenTelemetry Java agent from `entrypoint.sh`.

## Coding Conventions

- Check sibling files before creating or editing code so naming, structure, and Ktor/Gradle patterns stay consistent.
- Follow the existing direct Ktor style: small route extension functions, service objects for stateless/shared PDF operations, and kotlinx serialization DTOs.
- Use descriptive names for variables, functions, routes, and DTO fields; prefer clarity over abbreviation.
- Keep endpoint behavior explicit: validate inputs at the route/service boundary and return clear HTTP status codes.
- Preserve PDF/A and PDF/UA behavior unless the task explicitly changes compliance semantics.
- When touching conversion output, fonts, image rendering, or validation, expect fixture updates and run the relevant tests.
- Be careful with `AssetResolver`: it intentionally blocks localhost, private, link-local, wildcard addresses, and non-http schemes to reduce SSRF risk.
- Do not change dependencies, the Java toolchain, Gradle wrapper, or build conventions without explicit approval or a task that requires it.
- Avoid broad refactors. This project is small and benefits from straightforward, locally readable code.

## Comments

- Code should be self-explanatory through clear names, small functions, and types before comments.
- Do not add comments unless they explain why a non-obvious decision exists; never add comments that restate what the code does.
- Remove obsolete, redundant, or misleading comments when editing nearby code.

## Testing Expectations

- For route or model changes, add or update focused route tests under `src/test/kotlin/bambamboole/pdfua/routes/`.
- For PDF service behavior, add service tests or fixture coverage under `src/test/resources/fixtures/`.
- For image rendering, use `image-fixtures` and `RenderImageRoutesTest`.
- For security-sensitive URL fetching, update `AssetResolverTest`.
- Do not create one-off verification scripts when Gradle tests or focused fixtures can prove the behavior.
- Before claiming completion, run the narrowest relevant Gradle test command; run `./gradlew test` for broad behavior or public API changes.

## Git Hygiene

- Do not revert unrelated local changes. This repo may contain IDE metadata or generated fixture files from previous runs.
- Keep generated baselines only when they are intentional and reviewed.
- Never credit the agent in commits or PRs. Do not add `Co-Authored-By` trailers or "generated by" attribution.
- Make meaningful commits: one logical change per commit with a conventional-commit subject such as `feat:`, `fix:`, `docs:`, `test:`, `refactor:`, or `chore:`.
- Squash throwaway or WIP commits before opening a pull request.
- Keep PR descriptions compact: what changed and why, with screenshots or concrete before/after examples for visual output changes.
- Mention any test command that was not run and why.

## Documentation

- Only create new documentation files when explicitly requested.
- Keep agent guidance concise and repository-specific; do not import framework-specific rules from other projects unless they apply here.
