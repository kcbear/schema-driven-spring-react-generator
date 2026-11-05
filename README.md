# Schema-driven Spring + React Generator

Generate an editable Spring Boot 3 / Java 21 REST + JPA backend and a React + TypeScript CRUD frontend from one JSON resource schema.

The generator is a **build-time code generator**: it reads a schema, validates it, and writes ordinary Java and TypeScript source files. The generated application does not need this generator package at runtime.

## 1. Requirements

- JDK 21
- Gradle 8.8 or later (the repository uses the Gradle wrapper where available; otherwise use an installed Gradle)
- Node.js 20+ and npm
- PostgreSQL, if you want to run the generated backend against a database
- Git

The CI workflow currently uses JDK 21, Node.js 22 and Ubuntu 24.04.

## 2. Repository layout

```text
.
├── generator/                 # Java 21 schema-to-project generator
│   └── src/
│       ├── main/java/dev/schemacodegen/
│       └── test/java/dev/schemacodegen/
├── examples/
│   └── global-instruments.json # Demonstration schema (stocks + ETFs)
├── e2e/
│   ├── tests/                  # Playwright browser tests
│   ├── package.json
│   └── playwright.config.ts
├── build/                      # Generated output (created locally/CI)
└── .github/workflows/ci.yml
```

## 3. What is in the generator package?

The Gradle subproject `generator` is a small Java 21 application. Its main dependency is Jackson Databind for reading JSON; JUnit 5 is used for unit tests.

| Class | Responsibility |
|---|---|
| `Main` | CLI entry point. Parses arguments, loads the schema, then invokes generation. |
| `CliOptions` | Parses `--schema`, `--output` and `--force`. |
| `SchemaLoader` | Reads JSON with Jackson and validates the resource metadata, field definitions, supported types and demo-record count. |
| `ResourceModel` | Immutable record representing the resource name, API plural, Java package, ID field, fields and demo-record count. |
| `FieldModel` | Immutable record for each field: name, type, label, required, editable and list-display metadata. |
| `ProjectGenerator` | Emits the generated Spring Boot backend and React/Vite frontend source files. |

The generation flow is:

1. Parse CLI options.
2. Load and validate the JSON schema into a `ResourceModel` and `FieldModel` list.
3. Create the output directory structure.
4. Generate the Spring Boot entity, Spring Data repository, REST controller and (when requested) demo-data initializer.
5. Generate the React/TypeScript types, API client, CRUD page, Vite configuration and package files.

The generated backend uses Spring Boot 3.4.3, Java 21, Spring Web, Spring Data JPA and PostgreSQL. The generated frontend uses React, TypeScript, Vite, TanStack Query and TanStack Table. Generated code is intended to be edited and extended by the application team.

## 4. Quick start: build and generate the example

Run these commands from the **repository root**:

```bash
# Compile and run the generator's unit tests
gradle test

# Generate the example application
gradle :generator:run --args="--schema ../examples/global-instruments.json --output ../build/generated"
```

The paths in the generator arguments are resolved from the generator project's working directory, so the example schema is referenced as `../examples/...`.

Generated files are written to:

```text
build/generated/
├── backend/    # Spring Boot application
└── frontend/   # React + TypeScript application
```

The output directory must be empty or not exist. If it already contains files, generation stops unless you pass `--force`:

```bash
gradle :generator:run --args="--schema ../examples/global-instruments.json --output ../build/generated --force"
```

Use `--force` to allow writing into a non-empty output directory. It overwrites files that the generator emits; it does not clean unrelated or obsolete files already in that directory. For a clean regeneration, remove `build/generated` first.

## 5. The schema: use the global instruments example

Start with [examples/global-instruments.json](examples/global-instruments.json). Its resource metadata is:

- `name: "Instrument"` — generated Java entity and UI resource name.
- `plural: "instruments"` — REST collection path, `/api/instruments`.
- `package: "com.example.globaltrading"` — generated Java package.
- `id: "id"` — the field used as the entity identifier.
- `demoRecords: 10000` — asks the generator to create a deterministic 10,000-row demo-data initializer.

Each field has a camelCase `name`, a supported `type`, and an optional display `label`. The schema also supports `required`, `editable` and `list` field metadata. Supported scalar types are:

| Schema type | Generated Java type | Generated TypeScript type |
|---|---|---|
| `string` | `String` | `string` |
| `integer` | `Long` | `number` |
| `number` | `BigDecimal` | `number` |
| `boolean` | `Boolean` | `boolean` |
| `date` | `LocalDate` | `string` |
| `datetime` | `OffsetDateTime` | `string` |

The loader checks that the resource name is PascalCase, the plural is kebab-case, the Java package is valid, field names are valid and unique, field types are supported, and the ID field exists. `demoRecords` is optional and defaults to zero; it must be non-negative.

### How the JSON model maps to the UI view and table columns

There is currently **one resource schema that drives both the data model and the generated CRUD screen**. You do not write a second column definition or a separate React view configuration. Instead, each entry in `fields` describes a model property and carries a small amount of presentation metadata used by the generated screen.

For example, these fields from `examples/global-instruments.json`:

```json
{
  "name": "symbol",
  "type": "string",
  "label": "Ticker",
  "required": true,
  "editable": true,
  "list": true
},
{
  "name": "isin",
  "type": "string",
  "label": "ISIN",
  "editable": true,
  "list": false
},
{
  "name": "id",
  "type": "integer",
  "label": "ID",
  "editable": false,
  "list": false
}
```

produce the following behaviour:

| Field metadata | Generated model / API | Table view (TanStack Table) | Create / edit form |
|---|---|---|---|
| `name` | Property on the Java entity and TypeScript interface; included in API payloads | Identifies the data accessor | Identifies the value bound to the input |
| `type` | Determines the Java and TypeScript scalar types | Values are rendered using the current generic cell rendering | Determines the basic HTML input type (text, number, checkbox or date) |
| `label` | No effect on the stored property name | Becomes the column header | Becomes the input label |
| `list` | Does not remove the property from the model or API | `true`: include a column; `false`: omit the column | No effect |
| `editable` | Does not make the property read-only in the REST API | No effect | `true`: include an input, except that the resource ID is always omitted; `false`: omit the input |
| `required` | Retained as schema metadata | No effect | Currently not enforced: generated HTML inputs do not receive `required`, and the backend does not generate Bean Validation constraints |

The defaults are `editable: true`, `list: true`, and `required: false`. The resource ID is special: it identifies the entity and is always excluded from the generated form, even if its `editable` value is omitted or set to `true`.

**Column selection and ordering:** the generator filters the schema's `fields` array to entries where `list` is true, then emits a TanStack Table column for each one. The column accessor is the field's `name`, its header is the field's `label`, and its position follows the field's position in the JSON array. For example, setting `"list": false` on `isin` hides ISIN from the table while the property remains available to the backend, API and form (if editable).

**Important scope boundary:** these flags provide a basic list-view and form definition, not a full view-descriptor language. The current schema does not define multiple screens, per-column widths, alignment, formatting, sorting/filtering controls, nested views or role-based visibility. The generated table currently displays values generically. Those richer presentation requirements would need additional schema metadata and corresponding generator templates.


### What the example demonstrates

The generated Instrument catalogue includes both **STOCK** and **ETF** records across 12 exchanges: NASDAQ, NYSE, LSE, XETRA, Euronext Paris, SIX, HKEX, Tokyo Stock Exchange, ASX, TSX, BSE and SGX. Fields include ticker, instrument name, instrument type, exchange, country, trading currency, ISIN, sector, last price, market capitalisation, daily volume and active status.

The 10,000 instruments are deterministic synthetic demo data, **not live market data**. The generated initializer seeds the database only when the table is empty.

### Server-side paging

The generated REST collection endpoint uses Spring Data paging. It defaults to 50 records per page and caps the requested page size at 100. The React application requests one page at a time and displays the current range, total record count and page count. It does not download all 10,000 rows into the browser.

## 6. Run the generated application locally

### 6.1 Start PostgreSQL

Create a PostgreSQL database (for example, `generated`) and ensure it is reachable. The generated backend defaults to:

- URL: `jdbc:postgresql://localhost:5432/generated`
- User: `postgres`
- Password: `postgres`

Override these defaults with environment variables when appropriate:

```bash
export DB_URL='jdbc:postgresql://localhost:5432/generated'
export DB_USER='your_user'
export DB_PASSWORD='your_password'
```

### 6.2 Start the Spring Boot backend

From the repository root:

```bash
gradle -p build/generated/backend bootRun
```

The backend listens on port 8080. On first startup with an empty database, the generated initializer inserts the 10,000 demo instruments.

### 6.3 Start the React frontend

In a second terminal:

```bash
npm --prefix build/generated/frontend install
npm --prefix build/generated/frontend run dev
```

Open the Vite URL printed in the terminal (normally http://localhost:5173). The Vite development server proxies `/api` requests to the backend on port 8080.

## 7. Run end-to-end (E2E) tests with Playwright

The E2E suite lives in `e2e/`. It tests the generated React application in Chromium. **The tests mock the REST API using Playwright route interception; they do not require a running PostgreSQL database or Spring Boot backend.** They do require the generated frontend to exist because Playwright starts its Vite development server from that directory.

### 7.1 Generate and install dependencies

Run from the repository root:

```bash
gradle :generator:run --args="--schema ../examples/global-instruments.json --output ../build/generated"
npm --prefix build/generated/frontend install
cd e2e
npm install
npx playwright install chromium
```

On Linux CI or a fresh Linux machine, install the browser's operating-system dependencies as well:

```bash
npx playwright install --with-deps chromium
```

### 7.2 Execute the tests

From the `e2e/` directory:

```bash
npm test
```

The Playwright configuration starts the generated Vite frontend automatically at `http://127.0.0.1:4173`, runs the Chromium project, and writes:

- `e2e/playwright-report/` — HTML report
- `e2e/test-results/junit.xml` — JUnit results
- `e2e/test-results/` — test diagnostics, including screenshots and failure traces where applicable

To open the HTML report after a run:

```bash
npx playwright show-report playwright-report
```

### 7.3 What the current E2E tests cover

The tests in `e2e/tests/global-instruments.spec.ts`:

1. Intercept `/api/instruments` and return a mocked paged response representing 10,000 instruments.
2. Verify the first page displays 50 rows (plus the table header), the correct total count and page information, and does not render row 51.
3. Click **Next**, verify the second page is displayed, and assert that the UI requested page indexes 0 and 1.
4. Simulate an HTTP 500 response and verify the error is exposed through an accessible alert.

These tests validate frontend paging and error-state behaviour against a controlled API contract. They do not test database persistence, the real Spring REST controller, authentication or production deployment.

## 8. CI workflow

The GitHub Actions workflow at `.github/workflows/ci.yml` runs the full verification sequence:

1. Run generator unit tests.
2. Generate the global-instruments sample application.
3. Compile/build the generated Spring Boot backend.
4. Install dependencies and build the generated React frontend.
5. Install Playwright and Chromium.
6. Run the Playwright E2E suite.
7. Upload the HTML report and test diagnostics as a `playwright-results-*` artifact.

CI uses Ubuntu 24.04, JDK 21 and Node.js 22. Playwright artifacts are retained for **1 day**. The E2E suite uses a mocked API; backend compilation and frontend build are separate CI checks.

## 9. Scope and production considerations

This repository provides an extensible CRUD starter, not a production-certified trading platform. Before production use, assess and implement appropriate DTOs, Bean Validation, authentication and authorization, audit trails, concurrency controls, robust error handling, relationships, richer filtering and sorting, database migrations, and integration tests. The example prices and market capitalisation are synthetic. Do not use this generated CRUD API for live order execution or regulated trading without a separate security, risk, compliance and market-data architecture.

## License

Apache-2.0.
