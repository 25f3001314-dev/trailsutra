# TrailSutra Project Report

**Review scope:** Read-only review of project source, configuration, resources, tests, and documentation. The only file created for this task is this report. References below use project-relative `file:line` notation.

**Validation:** `node --check src/main/resources/static/js/app.js` passed. `mvn -q clean test` passed (10 tests, 0 failures, 0 errors); Maven logged a Flyway/H2 support-version warning. The application was started on free port `49485` with an in-memory H2 database and test-only environment credentials. It was stopped after HTTP checks. The final probe could not connect to the port, confirming shutdown.

## 1. Overview

TrailSutra is a Spring Boot / Thymeleaf investigation-support prototype for complaint-linked transaction review, account leads, audit-chain verification, detokenisation, and PDF reports. The README describes the intended system at `README.md:1-3`; dependency and Java configuration are in `pom.xml:1-8`. The tested runtime used Java 25; the project declares Java 21 in `pom.xml:2`.

### Package and file map

| File | Purpose |
|---|---|
| `src/main/java/com/trailsutra/TrailSutraApplication.java:7-9` | `TrailSutraApplication` starts Spring Boot. |
| `src/main/java/com/trailsutra/audit/AuditService.java:15-34` | `AuditService` appends and verifies a SHA-256-linked audit chain. |
| `src/main/java/com/trailsutra/config/SecurityConfig.java:15-37` | `SecurityConfig` creates in-memory users, role rules, form login, logout, CSRF, and response headers. |
| `src/main/java/com/trailsutra/evaluation/EvaluationService.java:8-14` | `EvaluationService` calculates precision, recall, F1, and false-positive rate from ground-truth records. |
| `src/main/java/com/trailsutra/features/AccountFeatureService.java:10-15` | `AccountFeatureService` queries transaction and endpoint activity features. |
| `src/main/java/com/trailsutra/graph/GraphProjectionService.java:8-14` | `GraphProjectionService` queries case edges and shared hubs. |
| `src/main/java/com/trailsutra/ingest/SourceIntegrityService.java:11-15` | `SourceIntegrityService` hashes an uploaded file and records its import metadata. |
| `src/main/java/com/trailsutra/matching/MatchingService.java:9-15` | `MatchingService` scores a complaint/transaction candidate and tests a score margin. |
| `src/main/java/com/trailsutra/report/ReportService.java:19-36` | `ReportService` builds PDF bytes, stores a manifest, and checks a report hash/signature. |
| `src/main/java/com/trailsutra/risk/RiskService.java:9-25` | `RiskService` calculates raw and calibrated scores from configured coefficients. |
| `src/main/java/com/trailsutra/synthetic/SyntheticDataInitializer.java:15-52` | `SyntheticDataInitializer` inserts deterministic demo banks, accounts, transactions, cases, leads, and related records. |
| `src/main/java/com/trailsutra/tokenisation/TokenisationService.java:14-44` | `TokenisationService` generates HMAC tokens and encrypts/decrypts vault values with AES-GCM. |
| `src/main/java/com/trailsutra/trail/AllocationService.java:7-14` | `AllocationService` provides FIFO/proportional taint allocation and lien bounds. |
| `src/main/java/com/trailsutra/trail/TemporalTrailService.java:16-24` | `TemporalTrailService` queries forward transactions and writes trail edges. |
| `src/main/java/com/trailsutra/triage/TriageService.java:8-13` | `TriageService` maps a score/evidence inputs to a role, recommendation, and lien. |
| `src/main/java/com/trailsutra/web/DashboardController.java:24-71` | `DashboardController` serves the dashboard and JSON/PDF endpoints. |
| `src/main/java/com/trailsutra/web/LoginController.java:6-10` | `LoginController` maps `/login` to the login template. |
| `src/main/resources/application.yml:1-63` | Port, database, Hibernate/Flyway, time zone, crypto, matching, trail, risk, and user configuration. |
| `src/main/resources/application.properties:1` | A second risk-calibration property value. |
| `src/main/resources/db/migration/V1__create_schema.sql:1-17` | Flyway schema for banks, accounts, transactions, complaints, cases, trails, scores, reviews, audit, reports, vault, ground truth, and imports. |
| `src/main/resources/messages.properties:1-7` | English dashboard labels; no template message lookup was found. |
| `src/main/resources/messages_hi.properties:1-7` | Contains the same English labels as `messages.properties`; it is not Hindi text. |
| `src/main/resources/static/css/app.css:1` | Single-line stylesheet, including the responsive breakpoints and table/graph layout. |
| `src/main/resources/static/js/app.js:1-41` | Cytoscape setup, UI handlers, review POST, audit GET, and report navigation. |
| `src/main/resources/templates/dashboard.html:1-8` | Single-page dashboard with six sections and settings/footer. |
| `src/main/resources/templates/login.html:1-78` | Role-picker login page with inline JavaScript. |
| `src/test/java/com/trailsutra/audit/AuditServiceTest.java:1-10` | Checks audit-chain verification and tamper detection. |
| `src/test/java/com/trailsutra/matching/MatchingServiceTest.java:1-8` | Checks candidate scoring and reference matching. |
| `src/test/java/com/trailsutra/tokenisation/TokenisationServiceTest.java:1-7` | Checks token determinism/domain separation and encryption round-trip. |
| `src/test/java/com/trailsutra/trail/AllocationServiceTest.java:1-10` | Checks lien bound, FIFO, and proportional allocation. |
| `src/test/java/com/trailsutra/triage/TriageServiceTest.java:1-8` | Checks merchant lien and structural-evidence triage behavior. |
| `pom.xml:1-8` | Maven coordinates, Spring Boot dependency management, dependencies, and build plugins. |
| `README.md:1-44` | Requirements, run/test instructions, routes, security notes, and configuration overview. |

The project root also contains a zero-byte `[Help` file and a file-backed H2 database under `data/`. These are not application source files; the existing database contents were not inspected. `target/` contains generated build output and was recreated by the requested clean test.

## 2. The six dashboards

All six sections are in one Thymeleaf page, rather than separate routes or templates (`src/main/resources/templates/dashboard.html:2-8`). “Real” below distinguishes database-backed server rendering from literal/template and JavaScript demo content.

| Dashboard | UI / data source | JavaScript or backing API/service | Assessment |
|---|---|---|---|
| **Case Overview** | The case ID and officer are Thymeleaf values. The monetary totals, counts, victim, status, and stepper are literals in the page (`dashboard.html:2`). The controller queries one fixed case for the complaint, trail, accounts, and audit rows (`DashboardController.java:28-34`). | Only the root `GET /` controller backs the page; there is no overview-specific JS action (`DashboardController.java:28-35`). | **Partial** — some case and officer data are database-backed, while the headline values/status are static. |
| **Intake and Matching** | Inputs and three candidate rows/scores are prefilled literals, not bound to the controller model (`dashboard.html:3`). | The form handler prevents submit and displays an alert; “Select” only disables its button (`app.js:32-34`). `MatchingService` can calculate scores (`MatchingService.java:11-14`) but no controller injects or calls it (`DashboardController.java:24-71`). | **Broken** — submit does not run matching or store complaint input; displayed results are static. |
| **Money Trail** | The transaction table is populated from `${trail}` and `DashboardController` queries transactions (`dashboard.html:4`, `DashboardController.java:31-32`). The graph nodes and edges are literal arrays (`app.js:4-17`), not built from `${trail}`. | Cytoscape renders the hardcoded graph and node clicks update the reason card (`app.js:18-30`). `TemporalTrailService` writes edges (`TemporalTrailService.java:18-21`) but is not called from a controller. | **Partial** — the table uses database rows, but the graph is a fixed example; there is no UI/API action to generate the trail. |
| **Account Triage** | The account table iterates over controller-provided account rows; the role/risk and selected recommendations are bound, while the key-reason text is fixed (`dashboard.html:5`, `DashboardController.java:33`). | `TriageService` exists (`TriageService.java:10-12`) but is not injected or called by a controller (`DashboardController.java:24-71`). | **Partial** — account/role/risk values are from a DB query with fallback expressions, but reasons and the triage calculation are not live in this path. |
| **Review and Lien** | Review subject, balance, risk details, tainted amount, reasoning, default reason, and lien are hardcoded; only the account token is resolved from the account model (`dashboard.html:6`). | The buttons POST JSON to `/api/reviews` with a CSRF header (`app.js:35-38`). The endpoint checks reviewer role, reason length, account balance, caps the lien, inserts a review, and audits it (`DashboardController.java:53-60`). Valid reviewer request returned **200** in testing. | **Partial** — the seeded Ramesh case can be reviewed, but the displayed recommendation is not dynamically generated by `TriageService`/`RiskService`; invalid account token causes 500. |
| **Reports** | Case ID is bound; model version, officer, report-contents checklist, and report button destination are fixed (`dashboard.html:7`, `app.js:40`). Audit rows are rendered from the database (`dashboard.html:7`, `DashboardController.java:34`). | Audit verification uses `/api/audit/verify` and worked for officer/auditor (`app.js:39`, `DashboardController.java:37`). The PDF controller calls `ReportService` (`DashboardController.java:66-67`), but runtime calls to both report routes returned **500** because of unnamed path variables. | **Broken** for PDF generation/verification; the audit portion works for authorized roles. |

The “Settings” section is only a heading, with no settings controls or content (`dashboard.html:8`).

## 3. Role matrix

Security rules are declared in `SecurityConfig.java:30-36`; method-level checks also appear in `DashboardController.java:44-45` and `DashboardController.java:53-55`. All four users successfully authenticated and received `GET /` **200** with the synthetic-data badge. The HTTP tests used a fresh in-memory DB and test-only passwords.

| Page / endpoint | Officer | Reviewer | Auditor | Bank analyst | Notes |
|---|---:|---:|---:|---:|---|
| `GET /login` | 200 | 200 | 200 | 200 | Public login page; tested before each user login. |
| `GET /` | 200 | 200 | 200 | 200 | Any authenticated account; same dashboard for every role. |
| `GET /css/**`, `GET /js/**` | Configured public | Configured public | Configured public | Configured public | `permitAll` is configured (`SecurityConfig.java:30`); HTTP status for individual static assets was **Not verified**. |
| `GET /api/audit/verify` | 200 | 403 | 200 | 403 | Auditor or officer only. |
| `POST /api/cases/{caseId}/confirm` | 500 | 403 | 403 | 403 | Officer is allowed by rule but fails while resolving the unnamed path variable. |
| `POST /api/detokenise` | 200 | 403 | 403 | 403 | Officer only; valid account token/reason were used for the officer request. |
| `POST /api/reviews` | 403 | 200 | 403 | 403 | Reviewer only; valid token saved the review. Reviewer with invalid token returned 500. |
| `GET /api/cases/{caseId}/transactions` | 500 | 500 | 500 | 500 | All are authenticated; each request fails path-variable resolution. |
| `GET /api/reports/{caseId}.pdf` | 500 | 500 | 500 | 500 | Any authenticated user by configured rules; each request fails path-variable resolution. |
| `POST /api/reports/{caseId}/verify` | 500 | 500 | 500 | 500 | Any authenticated user by configured rules; each request fails path-variable resolution. |
| `POST /logout` | 404* | Not tested | Not tested | Not tested | Officer test returned 404; security config matches GET logout, not POST. |
| `GET /logout` | 200* | Not tested | Not tested | Not tested | Officer test returned the logged-out page; other roles were not separately exercised. |

The `*` logout statuses were tested with an officer session. `/css/**`, `/js/**`, and `/login` are configured `permitAll` (`SecurityConfig.java:30`); individual static-asset HTTP statuses were **Not verified**. The POST API tests included CSRF: a reviewer POST without the CSRF token returned **403**, while the request with the token reached the review handler.

## 4. API table

| Method and path | Auth rule | Request / response from code | Observed HTTP result |
|---|---|---|---|
| `GET /api/audit/verify` | Officer or auditor (`SecurityConfig.java:33`) | No body; returns `{valid: boolean}` (`DashboardController.java:37`). | Officer **200**, auditor **200**, reviewer/bank analyst **403**. |
| `POST /api/cases/{caseId}/confirm` | Officer (`SecurityConfig.java:31`); explicit authority check in handler | JSON record `{txnId, score, basis}`; intended response `{confirmed, caseId, txnId}` (`DashboardController.java:38-41`). | Officer **500**; other roles **403**. |
| `POST /api/detokenise` | Officer (`SecurityConfig.java:31`, `DashboardController.java:44-45`) | JSON `{caseId, token, reason}`; response is either `{value}` or `{masked, status}` (`DashboardController.java:44-51`). | Officer **200** for a valid token/reason; all other roles **403**. |
| `POST /api/reviews` | Reviewer (`SecurityConfig.java:32`); explicit authority check in handler | JSON `{caseId, token, decision, reason, lienPaise}`; response `{saved, lienPaise, decision}` (`DashboardController.java:53-60`). | Reviewer **200** with valid token, **500** with invalid token; others **403**. Missing CSRF token: **403**. |
| `GET /api/cases/{caseId}/transactions` | Any authenticated user (`SecurityConfig.java:34`) | No body; intended to return a list of transaction maps (`DashboardController.java:62-64`). | **500** for all four roles. |
| `GET /api/reports/{caseId}.pdf` | Any authenticated user (`SecurityConfig.java:34`) | No body; intended `application/pdf` bytes (`DashboardController.java:66`). | **500** for all four roles. |
| `POST /api/reports/{caseId}/verify` | Any authenticated user (`SecurityConfig.java:34`) | `application/octet-stream` PDF bytes; intended JSON verification map (`DashboardController.java:67`). | **500** for all four roles. |
| `GET /login` | Public (`SecurityConfig.java:30`) | Returns Thymeleaf login page (`LoginController.java:6-9`). | **200**. |
| `GET /` | Any authenticated user (`SecurityConfig.java:34`) | Returns dashboard HTML and queries the fixed case (`DashboardController.java:28-35`). | **200** for all four roles. |
| `POST /login` | Public form-login flow (`SecurityConfig.java:35`) | Form-encoded `username`/`password`; login redirects to authenticated dashboard. | Final response **200** for all four configured test users. |
| `GET /logout` | Explicit GET matcher (`SecurityConfig.java:35`) | Invalidates the officer session and redirects to `/login?logout`. | **200** after redirect for officer. |
| `POST /logout` | No matching logout handler; CSRF enabled (`SecurityConfig.java:35-36`) | Dashboard contains a POST form (`dashboard.html:1`). | **404** for officer. |

## 5. End-to-end flow check

1. **Intake:** The form has prefilled example fields and no `name` attributes for its complaint inputs (`dashboard.html:3`). The submit handler prevents default submission and only displays an alert (`app.js:33`). No intake controller mapping exists (`DashboardController.java:28-71`).
2. **Matching:** Candidate rows are static HTML (`dashboard.html:3`). `MatchingService` has scoring logic, but no controller calls it; only unit tests instantiate it (`MatchingService.java:9-15`, `MatchingServiceTest.java:5-7`).
3. **Officer confirmation:** An API exists and a seeded case link is prepopulated (`DashboardController.java:38-41`, `SyntheticDataInitializer.java:37-38`), but runtime call by officer returned 500 because the path variable is unnamed.
4. **Trail generation:** The observed table is populated from the fixed case’s transaction query (`DashboardController.java:31-32`). There is no controller action invoking `TemporalTrailService.trace`; the graph is static JavaScript data (`app.js:4-20`). The generated trail service is not reached by the page.
5. **Triage:** The page receives database account rows (`DashboardController.java:33`, `dashboard.html:5`), but its reasons are static and the `TriageService` is not invoked from a controller.
6. **Review/lien:** A reviewer can submit a review for a valid token and the endpoint saves it; this was verified with HTTP **200** (`DashboardController.java:53-60`). This endpoint is not connected to a live intake/matching/triage pipeline; an unknown account token produces HTTP **500**.
7. **Report:** The report route is present but fails with HTTP **500** due to path-variable binding (`DashboardController.java:66-67`). Even after correcting that binding, the report’s transaction query is restricted to fixed transaction IDs (`ReportService.java:24`), and its signing key is newly generated at service construction (`ReportService.java:20`).

**Conclusion:** End-to-end workflow is **not complete**. It breaks at intake submission first, and the separate officer-confirmation and report APIs also fail at runtime. Review and audit verification work independently for their allowed roles.

## 6. Bugs and exact minimal fixes (not applied)

| Severity | Finding and evidence | Minimal proposed fix |
|---|---|---|
| **High** | Unnamed `@PathVariable String caseId` is used in confirm, transaction, report, and report-verification handlers (`DashboardController.java:38`, `62`, `66-67`). Runtime returned 500 with Spring’s “Name for argument ... not specified” message. The Maven compiler configuration sets `release` but not parameter metadata (`pom.xml:8`). | Name the variable explicitly in all four places: `@PathVariable("caseId") String caseId`. |
| **High** | The intake submit handler prevents the form submission and displays a success-shaped candidate-results alert instead of running matching (`app.js:33`); the candidate rows are hardcoded (`dashboard.html:3`). | Replace the alert-only handler with an actual request after validating/binding the form, and render returned candidates. Example target call: `fetch('/api/matches', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf }, body: JSON.stringify(complaint) })`. Add a real matching endpoint and response; none exists now. |
| **High** | Four demo usernames and passwords are embedded in browser-served JavaScript (`login.html:54-58`); fixed fallback crypto keys and passwords are configured in YAML (`application.yml:28-29`, `60-63`). The README asks operators to set secrets through environment variables (`README.md:15-22`), but the fallbacks remain active. | Remove password values from `USERS`; use user-entered credentials, e.g. `<input name="username">` and `<input name="password" type="password">`. Require deployment secrets by removing defaults, e.g. `hmac-key: ${TRAILSUTRA_HMAC_KEY}` and `officer-password: ${OFFICER_PASSWORD}` (apply the same pattern to every role/key). Rotate any values used outside a disposable local environment. |
| **High** | The POST logout form returns 404 because security is configured with a GET-only matcher (`SecurityConfig.java:35`), despite the dashboard POST form (`dashboard.html:1`). GET logout was HTTP 200; POST logout was HTTP 404. | Restore Spring Security’s default CSRF-protected POST logout: `.logout(l -> l.logoutSuccessUrl("/login?logout").permitAll())`. Replace the header’s GET anchor with the same POST form pattern as the sidebar. |
| **Medium** | A reviewer’s request with an unknown token returns 500 because `queryForObject` throws when no account row exists (`DashboardController.java:57`). This was reproduced over HTTP. | Query for an optional row and return 404 before dereferencing, for example: `Long balance = jdbc.query("SELECT balance_paise FROM accounts WHERE token=?", rs -> rs.next() ? rs.getLong(1) : null, req.token()); if (balance == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);`. |
| **High** | `ReportService` generates a new RSA key pair in its constructor on every start (`ReportService.java:20`), and uses it both to sign and verify manifests (`ReportService.java:29-31`). A stored signature from a prior process cannot be verified with the new public key. | Load a stable key pair from a managed secret/key store rather than generating one at startup. A minimal Java loading pattern is: `var factory = KeyFactory.getInstance("RSA"); var privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64))); var publicKey = factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64))); this.keyPair = new KeyPair(publicKey, privateKey);`. Configure and preserve those key values outside the database. |
| **High** | `POST /api/reviews` checks that the token exists, but does not verify that `req.caseId()` exists or that the account belongs to that case; it also accepts any decision string (`DashboardController.java:53-60`). The schema’s review table has no foreign keys (`V1__create_schema.sql:12`). | Validate that the case and account are associated before insert, and constrain decision to an enum/allowlist; add schema constraints/foreign keys in a reviewed migration. |
| **Medium** | Report and transaction APIs are reachable by any authenticated role because they fall through to `.anyRequest().authenticated()` (`SecurityConfig.java:34-35`). | Add explicit role and case-scope matchers for transaction/report reads and verify the caller’s access to that case inside the handlers. |
| **Medium** | The report’s transaction selection is hardcoded to seven seeded IDs, not filtered by `caseId` (`ReportService.java:24`). | Select report transactions by the supplied case’s confirmed links/trail rows and bind the case ID as a query parameter. |
| **Medium** | Service logic for matching, trail tracing, triage, feature extraction, risk scoring, graph projections, source import integrity, and evaluation is not called from a controller. There are no main-source consumers for `MatchingService.java:9`, `TemporalTrailService.java:16`, `TriageService.java:8`, `AccountFeatureService.java:11`, `RiskService.java:9`, `GraphProjectionService.java:9`, `SourceIntegrityService.java:11`, or `EvaluationService.java:8`. `AllocationService` is only used by `TriageService` (`TriageService.java:3`, `10`). | Wire the intended services into validated, role-checked workflow endpoints, or remove unused paths after confirming they are not planned features. |
| **Medium** | The initial data is synthesized and only seeded when the banks table is empty (`SyntheticDataInitializer.java:18-19`); a persistent H2 file is the default (`application.yml:7-11`). | Keep synthetic and production datasets/configuration strictly separated; use a managed production database and explicit import/audit controls before treating output as operational evidence. |

## 7. UI text audit

- The grep of templates, message properties, and seed data found one remaining Hinglish/Roman Hindi string: `"4 min me 100% aage gaya"` in `SyntheticDataInitializer.java:40`. It is stored as an account-score reason. The current triage template uses generic hardcoded reason text rather than `${a.reasons}` (`dashboard.html:5`), so this seeded text is not displayed in the current triage row.
- `messages_hi.properties:1-7` contains English labels identical to `messages.properties:1-7`; Hindi localization is not present in that file.
- Remaining explanatory or static copy worth reviewing: the “From Complaint to Clarity” tagline (`dashboard.html:1`); the matching weights and officer-confirmation explanation (`dashboard.html:3`); the generic risk reason and recommendation disclaimer (`dashboard.html:5`); the fixed “Model Reasoning” bullets, “View source rows,” and fixed lien explanation (`dashboard.html:6`); and the report-contents list/model version/officer labels (`dashboard.html:7`). Some are useful explanation, but they are not dynamically tied to the case/model. Their accuracy is therefore **Not verified** beyond reading the source.
- The `Settings` section is an empty heading (`dashboard.html:8`). The login template contains an empty `.note` element (`login.html:51`).
- The visible form workflow alert says candidate results are displayed although it does not run a matching request (`app.js:33`).

## 8. Mobile check at approximately 380px

This is a CSS/source inspection, not a browser viewport screenshot; visual behavior at 380px is **Not verified**.

- Breakpoints are at `1100px`, `700px`, and `380px` (`app.css:1`). At `<=700px`, the sidebar becomes a horizontally scrolling top navigation, the content margins shrink, metric cards become two columns, and review/report grids become one column.
- At `<=380px`, the fields become one column and content margins shrink to 6px. The data badge is hidden. The settings are all in a single stylesheet line (`app.css:1`).
- Tables are wrapped in `.table-wrap { overflow-x: auto; }` (`app.css:1`), so table overflow should scroll horizontally rather than force every column to fit. Actual scrolling is **Not verified** at runtime.
- The graph has `height:300px` generally and `height:270px` at `<=700px` (`app.css:1`); no dedicated narrower-phone graph behavior was tested. The graph data itself remains fixed (`app.js:4-17`).
- At narrow widths the sidebar’s logout form is hidden (`app.css:1`), leaving the header’s GET logout link (`dashboard.html:1`), which conflicts with the intended POST logout flow described above.

## 9. Production readiness checklist

- [ ] **Credentials/secrets:** Remove client-side demo passwords and fixed fallback passwords/crypto keys; require secret injection and rotation (`login.html:54-58`, `application.yml:28-29`, `60-63`).
- [ ] **Authentication/session:** Align logout UI and server behavior; verify session invalidation and CSRF behavior for every role (`SecurityConfig.java:35-36`, `dashboard.html:1`).
- [ ] **Authorization:** Add case-scoped checks for transaction/report endpoints, and verify ownership/membership on review requests (`SecurityConfig.java:31-35`, `DashboardController.java:53-67`).
- [ ] **Input validation:** Validate case ID, transaction, decision, token, lien range, and request body; turn unknown records into deliberate 4xx responses (`DashboardController.java:38-67`).
- [ ] **Workflow:** Implement and test intake, matching, confirmation, trail generation, triage, review, and reporting as one authenticated case flow (`app.js:32-40`, `DashboardController.java:28-71`).
- [ ] **Data integrity:** Replace demo seed behavior/default file-backed database for production and define migration constraints/relationships (`SyntheticDataInitializer.java:18-52`, `application.yml:7-21`, `V1__create_schema.sql:1-17`).
- [ ] **Cryptographic durability:** Persist signing keys; assess key rotation and backup; avoid fixed key fallbacks (`ReportService.java:20`, `application.yml:28-29`).
- [ ] **Audit/report semantics:** Make report content case-specific and preserve a verifiable signing key/manifest across restarts (`ReportService.java:20-31`).
- [ ] **Frontend quality:** Replace hardcoded candidate/graph/review data with API-derived state and test browser console/network behavior. Browser console errors are **Not verified**; `node --check` passed.
- [ ] **Responsive/accessibility:** Validate the page in a real browser at about 380px, including graph legibility, table scrolling, keyboard behavior, labels, and mobile logout (`app.css:1`, `dashboard.html:1-8`).
- [ ] **Automated coverage:** Add controller/security/CSRF and end-to-end tests; current suite has 10 service-focused tests and no controller tests (`src/test/java/com/trailsutra`).
- [ ] **Runtime compatibility:** Resolve the Flyway warning that bundled H2 2.2.224 exceeds the version Flyway reports as tested (2.2.220); warning appeared during `mvn -q clean test`.
- [ ] **Operational readiness:** Add production profiles, monitoring, backups, data-retention policy, deployment hardening, and incident/recovery procedures. These operational controls were **Not verified** in this codebase.
