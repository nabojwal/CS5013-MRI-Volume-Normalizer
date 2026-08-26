# Development Log

## 2026-08-26

### M1 - Java/Maven/JUnit Baseline

Actions:

- Configured Temurin Java 21.0.12.1.
- Configured Maven 3.9.16.
- Created the Maven project.
- Added JUnit 5.
- Created `Main.java`.
- Created `MainTest.java`.

Verification:

```text
mvn clean test
```

Result:

```text
Tests run: 1
Failures: 0
Errors: 0
BUILD SUCCESS
```

Git: `63fff6d`

Status: COMPLETE

---

## 2026-08-26

### M2 - dcm4che Integration

Actions:

- Added `dcm4che-core` 5.33.0.
- Added the dcm4che Maven repository.
- Created `Dcm4cheSmokeTest`.

Verification:

```text
mvn clean test
```

Result:

```text
Tests run: 2
Failures: 0
Errors: 0
BUILD SUCCESS
```

Git: `8e1580b`

Status: COMPLETE

---

## 2026-08-26

### M3 - FlatLaf Integration

Actions:

- Added FlatLaf 3.6.1.
- Created `FlatLafSmokeTest`.

Verification:

```text
mvn clean test
```

Result:

```text
Tests run: 3
Failures: 0
Errors: 0
BUILD SUCCESS
```

Git: `cf9013d`

Status: COMPLETE

---

## 2026-08-26

### M4 - Core Architecture

Actions:

- Established domain, application, infrastructure, and presentation package boundaries.
- Added conceptual domain and synchronous application contracts.
- Kept `Volume` storage, precision, memory layout, and coordinate convention unresolved.
- Added architecture documentation under `docs/architecture/`.
- Added lightweight JUnit package and boundary tests without new dependencies.

Verification:

```text
mvn clean test
```

Result:

```text
Tests run: 7
Failures: 0
Errors: 0
BUILD SUCCESS
```

Git: `7e50a9b`

Status: COMPLETE

---

Current milestone: M5 - Format Detection.

---

## Post-M4 Architecture Review

An independent review was performed after M4 completion.

The architecture test suite contains 7 total tests, including 4 architecture-focused tests. The tests meaningfully protect the currently established domain contracts and presentation boundary.

Known limitation:
the architecture tests are not repository-wide dependency analysis. They inspect the currently established contract types rather than automatically discovering all future classes.

This limitation is intentional for M4. No architecture-testing dependency or custom bytecode analyzer is introduced.

Future milestones must add boundary-specific tests as new infrastructure, processing, and presentation components are introduced.

M4 remains accepted and complete.

---

## 2026-08-26

### M5 - Format Detection

Actions:

- Added domain detection outcomes, diagnostics, and immutable results.
- Added the synchronous application `FormatDetectionService` boundary.
- Added dcm4che-backed DICOM structural probing.
- Added standard-library NIfTI-1, NIfTI-2, and bounded gzip header probing.
- Added synthetic temporary-file tests for supported, corrupt, unknown, empty, missing, directory, and extension-hint cases.

Verification:

```text
mvn clean test
```

Result:

```text
Tests run: 16
Failures: 0
Errors: 0
BUILD SUCCESS
```

Git: `88da522`

Status: COMPLETE

---

Current milestone: M6 - DICOM Processing.
