# Schema-driven Spring + React Generator

Generate editable Spring Boot 3 / Java 21 REST + JPA backend source and a React + TypeScript CRUD frontend from one JSON resource schema.

## Requirements
JDK 21, Gradle 8.8+, Node.js 20+, PostgreSQL.

## Build and generate
```bash
gradle test
gradle :generator:run --args="--schema examples/purchase-order.json --output build/generated"
```
Use `--force` to replace a non-empty output directory.

Generated output contains a Spring Boot backend and a Vite React frontend using TanStack Query and TanStack Table. Run backend with DB_URL, DB_USER, DB_PASSWORD environment variables; run frontend using `npm install && npm run dev`.

## Schema
See examples/purchase-order.json. Scalar field types: string, integer, number, boolean, date, datetime. Generated REST endpoints provide paged list, get, create, update and delete operations.

## Architecture
SchemaLoader validates and normalizes input to ResourceModel; ProjectGenerator emits editable Java and TypeScript source. No runtime schema engine is required.

## Scope
This is an extensible CRUD starter, not a production-certified framework. Add DTOs, Bean Validation, authorization, audit/locking, robust error handling, relationships, richer filtering/sorting, migration evolution, and integration/e2e tests before production. Review generated code and secure database credentials.

## End-to-end tests

CI generates the sample application, builds the generated React frontend, and runs Playwright tests in Chromium. The tests cover create, edit, delete, checkbox state, and accessible API-error display using mocked API responses. Each CI run uploads a `playwright-results-*` artifact containing the HTML report and JUnit XML; traces, screenshots, and videos are retained for failed tests. Download the artifact from the workflow run's **Artifacts** section (retained for 30 days).

Apache-2.0.
