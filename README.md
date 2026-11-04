# Schema-driven Spring + React Generator

Generate editable Spring Boot 3 / Java 21 REST + JPA backend source and a React + TypeScript CRUD frontend from one JSON resource schema.

## Requirements
JDK 21, Gradle 8.8+, Node.js 20+, PostgreSQL.

## Build and generate
```bash
gradle test
gradle :generator:run --args="--schema ../examples/global-instruments.json --output ../build/generated"
``
Use `--force` to replace a non-empty output directory.

Generated output contains a Spring Boot backend and a Vite React frontend using TanStack Query and TanStack Table. Run backend with DB_URL, DB_USER and DB_PASSWORD environment variables; run frontend using `npm install && npm run dev`.

## Example: global stock-trading instruments

The example schema models a global instrument catalogue with both **STOCK** and **ETF** instrument types across NASDAQ, NYSE, LSE, XETRA, Euronext Paris, SIX, HKEX, Tokyo Stock Exchange, ASX, TSX, BSE and SGX. It includes ticker, instrument name, type, exchange, country, trading currency, ISIN, sector, last price, market capitalisation, daily volume and active status.

The schema's `demoRecords: 10000` setting generates 10,000 deterministic synthetic instruments on first backend startup. These are demonstration values, not live market quotes. The initializer only seeds an empty table.

The generated REST list endpoint uses Spring Data paging (default 50 records, maximum 100 per request). The React UI requests one page at a time and displays the current page, total pages and total instrument count. It does not fetch all 10,000 records into the browser.

## Schema
See examples/global-instruments.json. Scalar field types: string, integer, number, boolean, date, datetime. Set `demoRecords` to a non-negative integer to generate deterministic sample rows for the example resource.

## Architecture
SchemaLoader validates and normalizes input to ResourceModel; ProjectGenerator emits editable Java and TypeScript source. No runtime schema engine is required.

## Scope
This is an extensible CRUD starter, not a production-certified trading platform. Add DTOs, Bean Validation, authorization, audit/locking, robust error handling, relationships, richer filtering/sorting, migration evolution, and integration/e2e tests before production. Prices and market capitalisation in the example are synthetic. Do not use this generated CRUD API for live order execution or regulated trading without a separate security, risk, compliance and market-data architecture.

## End-to-end tests

CI generates the sample application, builds the generated React frontend, and runs Playwright tests in Chromium. Tests verify paging through a mocked 10,000-record API response and accessible API-error display. Each CI run uploads a `playwright-results-*` artifact containing the HTML report and JUnit XML; screenshots are captured for every test. Artifacts are retained for 1 day.

Apache-2.0.
