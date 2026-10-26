# TrailSutra

TrailSutra is an investigation-support application for complaint-linked transaction review. It provides case-scoped account leads, time-ordered money movement, human review records, an append-only audit chain, and a PDF report with a signed manifest.

## Requirements

- Java 21
- Maven 3.9+
- Network access on first build to resolve Maven dependencies and the pinned Cytoscape.js CDN asset

## Run

```bash
export TRAILSUTRA_HMAC_KEY='replace-with-a-long-random-hmac-secret'
export TRAILSUTRA_ENCRYPTION_KEY='replace-with-a-separate-long-random-encryption-secret'
export OFFICER_PASSWORD='set-a-unique-officer-password'
export REVIEWER_PASSWORD='set-a-unique-reviewer-password'
export BANK_ANALYST_PASSWORD='set-a-unique-bank-password'
export AUDITOR_PASSWORD='set-a-unique-auditor-password'
mvn spring-boot:run
```

Open `http://localhost:8080`. Initial user names are `officer`, `reviewer`, `bank-analyst`, and `auditor`; passwords are supplied through the environment variables above. The local fallback values in `application.yml` are for local development only and must be replaced before deployment.

## Build and tests

```bash
mvn -q verify
```

The first start seeds deterministic records from seed 42 into a file-backed H2 database. All displayed financial amounts are stored as integer paise. Event timestamps are stored in UTC. The interface marks all generated records as `SYNTHETIC DATA`.

## Key routes

- `/` — redirects to `/overview`
- `/overview` — case overview
- `/intake` — complaint intake and transaction matching
- `/trail` — observed transaction graph and transaction table
- `/triage` — account leads and review recommendations
- `/review` — reviewer decision and lien recording
- `/reports` — PDF report and audit log
- `/settings` — application settings information
- `/api/cases/{caseId}/match` — officer-only ranked transaction candidates
- `/api/cases/{caseId}/confirm` — officer confirmation of a transaction
- `/api/cases/{caseId}/transactions` — officer and reviewer transaction list
- `/api/audit/verify` — append-only audit chain verification
- `POST /api/detokenise` — officer-only vault lookup, with case-trail membership and written reason checks
- `POST /api/reviews` — reviewer decision recording
- `/api/reports/{caseId}.pdf` — generate a report with a signed manifest
- `POST /api/reports/{caseId}/verify` — compare supplied report bytes with the stored report hash and verify the manifest signature

Page access is role-based: officers can open overview, intake, trail, triage, reports, and settings; reviewers can open overview, trail, triage, review, and settings; auditors can open overview, reports, and settings; bank analysts can open overview, triage, and settings. Sidebar links follow the signed-in user's role.

## Security notes

Set independent high-entropy HMAC and encryption keys. The app uses BCrypt user passwords, CSRF protection, response security headers, encrypted vault values, case-scoped detokenisation checks, and an audit hash chain. The report manifest is labelled `signed manifest`; it is not represented as a legal digital signature. Review recommendations are advisory and require a human decision.

## Configuration

Matching weights, score bands, temporal trail limits, triage thresholds, risk coefficients, and calibration values are in `src/main/resources/application.yml`. H2 database files are stored in `./data`.
