# Development Log

This is the chronological engineering log. Historical entries preserve the state
and verification recorded at the time; later audit notes explicitly correct stale
claims without rewriting valid history. Current status is in MILESTONES.md and
the latest entry below. Prompt/test/implementation/checkpoint links are indexed in
[PROMPTS.md](PROMPTS.md); fresh results and evidence limits are in
[TEST-RESULTS.md](docs/testing/TEST-RESULTS.md). A completed task is not necessarily
a completed milestone.

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

---

## 2026-08-26

### M5.1 - Format Detection Hardening

Actions:

- Bounded DICOM inspection to a 1 MiB detection budget.
- Strengthened NIfTI-1 and NIfTI-2 header invariants and endian handling.
- Validated gzip streams through EOF while retaining bounded decompressed inspection.
- Removed weak DICOM tag heuristics and used dcm4che validation for preamble-less input.
- Deferred NIfTI image-pair headers and added stable invalid-reference and oversized-input diagnostics.
- Added regression tests for each corrected behavior.

Verification:

```text
mvn -Dtest=FormatDetectionServiceTest test
```

Result:

```text
Tests run: 16
Failures: 0
Errors: 0
BUILD SUCCESS
```

Full verification:

```text
mvn clean test
```

Result:

```text
Tests run: 23
Failures: 0
Errors: 0
BUILD SUCCESS
```

Git: `1989589`

Status: COMPLETE

---

## 2026-10-01

### M6 - Common-Normal Projection and Final-Grid Residual Hardening

Starting checkpoint: `3c3d979` - feat: establish verified M6 reconstruction baseline.

Added four regression tests before changing production geometry logic. The refined
fixtures were verified with `mvn -Dtest=DicomSeriesServiceTest clean test`:
15 tests run, 3 failures, 0 errors, 0 skipped. They exposed reversed voxel order,
input-order-dependent rejection, and acceptance of cumulative grid drift. The
valid noisy-grid regression passed as intended.

Changes:

- Select reference orientation by lexical SOPInstanceUID order.
- Derive one reference normal and project all selected positions onto it for
  physical sorting, duplicate detection, and spacing validation.
- Retain first-gap spacing and adjacent-gap validation; additionally compare each
  projected position with the final regular grid using positionToleranceMm.
- Reject excessive whole-grid residuals with IRREGULAR_SPACING and pass the same
  validated directions, normal, and spacing into volume assembly.

Post-fix verification:

- `mvn -Dtest=DicomSeriesServiceTest test`: BUILD SUCCESS; 15 tests, 0 failures,
  0 errors, 0 skipped.
- `mvn clean test`: BUILD SUCCESS; 39 tests, 0 failures, 0 errors, 0 skipped.

Only the two geometry issues were addressed. Single-slice spacing policy,
overflow, FrameOfReference/BIPED, detection, provenance, and AR-1 remain deferred.
M6 overall completion is not claimed. No commit created, as requested.

### M6 - Voxel Allocation Overflow Hardening

Starting checkpoint: `32b4bc1`. Added nine allocation/dimension regression tests
using only tiny arrays or virtual voxel data.

Pre-fix verification: `mvn -Dtest=ImmutableVoxelDataTest clean test` failed with
9 tests, 3 failures, 0 errors, 0 skipped. The constructor accepted products that
wrapped to zero or one, including a shape whose mathematical count exceeds long
capacity. Existing nonpositive-dimension, array-length, valid-small-volume, and
service overflow behavior passed.

Replaced unchecked constructor multiplication with a private checkedVoxelCount
helper in ImmutableVoxelData. It validates positive dimensions and uses checked
int multiplication to reject counts above Integer.MAX_VALUE before cloning.
Arithmetic overflow is translated to IllegalArgumentException. The service,
reader, and NativeVolume required no production changes.

Post-fix verification:

- `mvn -Dtest=ImmutableVoxelDataTest test`: BUILD SUCCESS; 9 tests, 0 failures,
  0 errors, 0 skipped.
- `mvn clean test`: BUILD SUCCESS; 48 tests, 0 failures, 0 errors, 0 skipped.

The scoped voxel allocation hardening is complete. Detection, provenance,
FrameOfReference/BIPED, and AR-1 remain deferred. M6 overall completion is not
claimed. No commit created, as requested.

---

## 2026-10-01 — Documentation and Test-Governance Audit

Starting checkpoint: `fc1ad46` — fix: harden voxel allocation validation.
Prompt: M6-P04 in PROMPTS.md. Objective: establish evaluator-facing documentation,
test methodology/inventory and AI traceability through the current M6 stage.

### Historical checkpoint reconciliation

- `eb83cb1` (2026-08-27) preserves versioned AI prompts; `f7e7ea2` (2026-10-01)
  approves Architecture 1.0. Exact original approval/geometry/allocation prompts
  are unavailable; PROMPTS.md labels summaries rather than inventing quotations.
- M6 baseline `3c3d979` (2026-10-01), parent `f7e7ea2`, adds the reader, service,
  models, 11 DICOM service tests and M6BoundaryTest. Its ProvenanceRecord change
  resolves the constructor shape used by the service, supporting the AR-0 build
  repair. No preserved pre-repair failing run or complete AR-0 audit transcript
  was found; full AR-0 procedural completion is not asserted.
- Fresh reproduction of the `3c3d979` Git archive with
  `mvn clean test -f <export>/3c3d979/pom.xml`: BUILD SUCCESS, 35 tests,
  0 failures, 0 errors, 0 skipped. Status: baseline task COMPLETE; M6 IN PROGRESS.
- The geometry entry above was subsequently checkpointed as `32b4bc1` —
  fix: harden M6 physical slice geometry. Fresh archive reproduction with
  `mvn clean test -f <export>/32b4bc1/pom.xml`: BUILD SUCCESS, 39 tests,
  0 failures, 0 errors, 0 skipped. Status: scoped geometry task COMPLETE.
- The allocation entry was subsequently checkpointed as `fc1ad46` —
  fix: harden voxel allocation validation. Status: scoped allocation task COMPLETE.
  Earlier "No commit created" statements describe their task-time state, not the
  subsequent Git history. No later M6 correctness commit exists at this audit HEAD.
- Correction to M5.1 wording: NiftiFormatProbe contains field validators but
  detectHeader does not call them. The historical field-invariant claim must not
  be read as active dimension/datatype validation. Architecture section 15.4 and
  TEST-RESULTS.md retain this as outstanding work.

### Changes and review

Replaced the initialization README; created PROMPTS.md, TEST-STRATEGY.md and
TEST-RESULTS.md; added this evidence reconciliation and corrected stale milestone
status/roadmap labels. Preserved original ai/ prompts and historical log entries.
Reviewed every executable test class/method; naming is behavior-oriented and
fixture comments already explain the difficult cases. Production Java review
was read-only; recommended API documentation is recorded in TEST-RESULTS.md.
No production or test behavior changed, no assertions changed, no tests added.

The approved architecture is unchanged, with Version 1.0.0 and authoritative
approval metadata. Its duplicate title and draft-era narrative remain editorial
issues; current implementation status is documented separately.

### Verification and status

Environment checked with `mvn --version`: Java 21.0.12.1 (Eclipse Adoptium),
Maven 3.9.16, Windows 11. Fresh current `mvn clean test`: BUILD SUCCESS;
48 tests, 0 failures, 0 errors, 0 skipped. Counts/method inventory come from that
run's Surefire XML. Historical 35/39 results above were independently reproduced
from isolated Git archives without switching the checkout.

`git status --short`, `git diff --stat` and `git diff` were reviewed.
`git diff --check` passed; new documents also passed whitespace checks and all
local Markdown link targets resolved. Source, tests, pom.xml, original ai/ records
and the approved architecture match HEAD; the index is unchanged.
Status: documentation/governance task COMPLETE; no software milestone advanced.

M6 IN PROGRESS: section 24 item 19 still requires format-neutral ImageVolume.
AR-1, explicit single-slice policy before output, provenance identity/privacy
coverage and additional detection/geometry/profile hardening remain pending.
Overflow-safe allocation is already fixed and must not be listed as unresolved.

This task has no related commit: staging and committing are explicitly prohibited.
The pre-existing `.roomodes`, architecture RC2 and RC3 are preserved.
