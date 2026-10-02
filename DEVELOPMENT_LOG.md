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

---

## 2026-10-01 — M5/M6 Format Detection Evidence Budgets

Starting checkpoint: `ba13b10` — docs: establish project documentation and test
governance. The supplied task named `fc1ad46`; live Git showed the subsequent
documentation checkpoint and no tracked modifications before this task.

Objective: make probe limits evidence budgets rather than total-file validity
limits, continue after inconclusive probes, and recognize gzip NIfTI from bounded
header reads. Reconstruction, allocation, provenance and architecture are out of scope.

### Test-first evidence

Before production edits, added eight tests in FormatDetectionServiceTest and
updated two directly affected existing fixtures/expectations. Command:
`mvn "-Dtest=FormatDetectionServiceTest" clean test`.
Result: BUILD FAILURE; 24 tests, 6 failures, 0 errors, 0 skipped.

| Test | Observed pre-fix result |
|---|---|
| detectsLargePart10DicomFromEarlyMetadata | Expected DICOM, got UNKNOWN for a streamed 2 MiB Pixel Data fixture |
| continuesToLaterProbeAfterInsufficientEvidence | Expected NIFTI, got UNKNOWN after a budget-limited first probe |
| detectsLargeNiftiAfterDicomProbe | Expected NIFTI, got UNKNOWN for a >1 MiB uncompressed fixture |
| detectsLargeGzipNiftiWithoutDrainingPayload | Expected NIFTI_GZ, got CORRUPT after exceeding the old 16 MiB decompressed cap |
| largeUnrelatedFileIsUnknownRatherThanSizeFailure | Expected UNSUPPORTED_FORMAT, got INPUT_TOO_LARGE for seeded unrelated bytes |
| detectsCompleteGzipHeaderWithoutRequiringTrailerValidation | Expected NIFTI_GZ, got CORRUPT because the old implementation checked the trailer |

New rejectsTruncatedGzipNiftiHeader, distinguishesTruncatedDicomMetadataFromProbeExhaustion
and detectsBigEndianGzipNifti2Header passed before the fix. Existing small valid
files and preamble-less DICOM also passed; no extra production changes were made
to force failures in those cases.

The old rejectsGzipWithCorruptTrailer expectation was explicitly replaced to
match the requested header-only detection contract. Payload/trailer validation
cannot be promised without reading past the header. The existing oversized DICOM
test now uses incomplete-within-budget File Meta Information rather than a DICM
marker plus arbitrary padding; its UNKNOWN/INPUT_TOO_LARGE assertions are retained.
Fixtures use temporary files and 8 KiB streaming buffers; the gzip fixture expands
to 17 MiB to exceed the old cap without allocating that payload in memory.

### Implementation

- DicomFormatProbe uses dcm4che's existing createWithLimit with a 1 MiB budget.
  It checks the preamble, parses File Meta Information and stops on identifying
  SOP class/instance and transfer-syntax metadata. Preamble-less input retains
  parser-based fallback through the early dataset SOP identifiers. It does not
  parse Pixel Data merely to classify a file.
- Parser EOF while more file bytes exist beyond the budget returns
  UNKNOWN/INPUT_TOO_LARGE. Actual short recognizable metadata remains
  CORRUPT/INVALID_DICOM; unrelated bytes remain UNKNOWN/UNSUPPORTED_FORMAT.
  INPUT_TOO_LARGE is retained for API compatibility and documented as inconclusive
  evidence, not invalid total file size. Recognition is not full DICOM validation.
- FormatDetectionService continues through UNKNOWN outcomes and preserves a
  nontrivial fallback diagnostic if later probes only report non-matches. A
  positive match or actual CORRUPT result still terminates probing.
- NiftiFormatProbe reads four size bytes, then only the remaining header bytes:
  348 total for NIfTI-1 or 540 for NIfTI-2, for both plain and gzip input. Unknown
  sizes stop after four bytes. GZIPInputStream may buffer compressed bytes, but
  the detector does not drain decompressed payload or require trailer validation.
  Short recognizable headers remain INVALID_NIFTI; gzip decoding failures while
  obtaining the header remain INVALID_GZIP.

### NIfTI helper review and limits

Header-size values, endian handling and NIfTI-1 four-byte magic placement agree
with the official nifti_clib header definitions. Existing NIfTI-2 recognition
checks only the first four magic bytes, not its additional signature bytes.
Dimension/datatype helpers are uncalled; validTypeAndBitpix groups independent
codes incorrectly (e.g. code 512 is UINT16 but the helper rejects bitpix 16;
code 128 is RGB24 but bitpix 24 is absent). They were not activated, changed or
given speculative tests, per the focused request. vox_offset, complete datatype
validation, full NIfTI-2 signature validation and full-file integrity remain deferred.
Reference definitions reviewed:
[nifti1.h](https://github.com/NIFTI-Imaging/nifti_clib/blob/master/niftilib/nifti1.h),
[nifti2.h](https://github.com/NIFTI-Imaging/nifti_clib/blob/master/nifti2/nifti2.h).

### Verification and status

- Focused: `mvn "-Dtest=FormatDetectionServiceTest" test` — BUILD SUCCESS;
  24 tests, 0 failures, 0 errors, 0 skipped.
- Full: `mvn clean test` — BUILD SUCCESS; 56 tests, 0 failures, 0 errors, 0 skipped.
- Focused Maven required execution outside the sandbox because its sandbox-local
  repository path was inaccessible; no dependencies or versions were changed.
- Reviewed `git status --short`, `git diff --stat` and the complete diff;
  `git diff --check` passed. The index remains empty.

Status: FORMAT DETECTION BUDGET HARDENING COMPLETE. M6 remains IN PROGRESS.
Provenance identity, FrameOfReferenceUID/BIPED, single-slice spacing, AR-1 and
NIfTI conversion remain deferred. Earlier documentation/test inventories describe
their recorded checkpoint; this entry records the new detection semantics and count.
No staging or commit performed. The three intentional untracked files are untouched.

## 2026-10-02 - M6 Frame of Reference / BIPED Profile Hardening

Baseline: `cacec3a` (format-detection hardening checkpoint). Tracked files were
clean; the three intentional untracked files were untouched. Objective: enforce
frame identity and the supported BIPED profile only for selected-series slices.

### Test-first evidence

Added ten methods in DicomSeriesServiceTest using tiny synthetic DICOM files
and the real dcm4che reader; strengthened the existing selected-series isolation
test with unrelated QUADRUPED/different-frame and missing-frame candidates.
Before production changes, `mvn -Dtest=DicomSeriesServiceTest test` returned
BUILD FAILURE: 25 tests, 6 failures, 0 errors, 0 skipped.

Failing regressions (all invalid stacks were incorrectly accepted):

- rejectsMissingFrameOfReferenceUid
- rejectsBlankFrameOfReferenceUid
- rejectsMixedFrameOfReferenceUids
- rejectsQuadrupedOrientation
- rejectsMixedBipedAndQuadrupedOrientations
- rejectsUnknownAnatomicalOrientationType

Positive controls already passed: acceptsConsistentFrameOfReferenceUid,
acceptsExplicitBipedOrientation, acceptsMissingAnatomicalOrientationTypeAsBiped,
acceptsBlankAnatomicalOrientationTypeAsBiped, and the strengthened
acceptsMultipleSuppliedSeriesOnlyWhenRequestedSeriesIsExplicit.
The unknown-value loop stopped at UNKNOWN in the pre-fix run; lowercase biped
was also verified rejected after the fix.

### Implementation

DicomInstance carries nullable frameOfReferenceUid and anatomicalOrientationType.
Dcm4cheInstanceReader extracts Tag.FrameOfReferenceUID and
Tag.AnatomicalOrientationType without generating defaults. After selection and
study/series/duplicate-SOP checks, DefaultDicomSeriesService validates frame
presence, exact UID equality, then effective BIPED before pixel compatibility
and service geometry validation. Null/blank orientation means BIPED; trimmed
uppercase BIPED is accepted; other nonblank values are rejected case-sensitively.

Existing categories are reused: MISSING_REQUIRED_METADATA for absent/blank frame,
INCOMPATIBLE_INSTANCE for mixed frames, UNSUPPORTED_OBJECT_TYPE for unsupported
anatomy. Exceptions carry explicit messages; the existing result API exposes
only error categories, not those messages. No result/provenance redesign was made.
The reader still derives per-instance geometry before selection, as before;
new semantic validation precedes service orientation/common-normal reconstruction.

After adding the model accessors, direct reader assertions were added to the
same fixtures to verify exact extraction and preserved nulls. The existing
allocation regression received a valid frame UID constructor argument so it
continues reaching the allocation guard; no allocation behavior/assertion changed.
Geometry algorithms, dependencies, detection and approved architecture are unchanged.

### Verified results

- `mvn -Dtest=DicomSeriesServiceTest test`: BUILD SUCCESS; 25 tests,
  0 failures, 0 errors, 0 skipped (including final reader assertions).
- `mvn clean test`: BUILD SUCCESS; 66 tests, 0 failures, 0 errors, 0 skipped.

M6 frame/profile hardening COMPLETE. M6 remains IN PROGRESS. Single-slice spacing,
provenance identity, unused NIfTI validators, AR-1 and conversion remain deferred.
No staging or commit performed. Related commit: Pending at verification time.

## 2026-10-02 - M6 Single-Slice Spacing Policy Hardening

Baseline: `5c9b3ee`. Tracked files were clean; only the three intentional
untracked files existed. Objective: replace successful zero-spacing one-slice
reconstruction with the explicitly requested supported-project metadata policy.

### Test-first evidence

Before any production edits, `mvn -Dtest=DicomSeriesServiceTest test`:
BUILD FAILURE; 33 tests, 7 failures, 0 errors, 0 skipped.
Then added nullable model fields and constructor wiring only (reader values
still null; service behavior unchanged) to enable direct boundary tests.
Before extraction/policy implementation, the same command returned BUILD FAILURE;
36 tests, 10 failures, 0 errors, 0 skipped.

| Test | Scenario | Pre-fix result |
|---|---|---|
| acceptsSingleSliceWithSpacingBetweenSlices | 2.5 mm primary, unchanged origin/directions | Failed: spacing 0.0 |
| prefersSpacingBetweenSlicesOverSliceThickness | 2.5 primary wins over 4.0 thickness | Failed: spacing 0.0 |
| acceptsSingleSliceWithSliceThicknessFallback | Absent primary, 3.2 thickness | Failed: spacing 0.0 |
| rejectsSingleSliceWithoutSpacingMetadata | Both absent | Failed: accepted |
| rejectsNonPositiveSingleSliceSpacingBetweenSlices | Zero/negative primary despite valid thickness | Failed at zero: accepted |
| rejectsInvalidSliceThicknessFallback | Absent primary, zero/negative thickness | Failed at zero: accepted |
| multiSliceSpacingStillComesFromIpp | 1.5 IPP spacing vs 9.0/7.0 metadata | Passed |
| rejectsEmptyOrMalformedExplicitSingleSliceSpacing | Empty/blank/malformed primary, valid thickness | Failed at empty: accepted |
| rejectsNonFiniteSingleSliceSpacingBetweenSlices | NaN and both infinities at service boundary | Second run failed at NaN: accepted |
| rejectsNonFiniteSliceThicknessFallback | Non-finite fallback at service boundary | Second run failed at NaN: accepted |
| readerExtractsSingleSliceSpacingMetadata | Positive values, null absence, invalid presence | Second run failed: 2.5 read as null |

Loops stop on their first failed assertion in pre-fix runs. All loop cases ran
successfully after the fix. Non-finite tests use the service/domain boundary
because NaN/infinities are not valid DICOM DS values; no raw-byte hacks were used.
Reader tests use existing synthetic temporary DICOM fixtures.

### Implementation and semantics

- DicomInstance carries nullable Double spacingBetweenSlices and sliceThickness.
- Reader uses Tag.SpacingBetweenSlices and Tag.SliceThickness. Tag absence maps
  to null; present-empty or unparsable values map to NaN as invalid metadata,
  never as absence. Numeric zero, negative and non-finite values remain invalid
  values for application-level policy; no reader fallback is performed.
- After existing geometry validation/sorting, one slice uses SpacingBetweenSlices
  when present, otherwise SliceThickness, otherwise MISSING_REQUIRED_METADATA.
  The chosen value must be finite and > 0; invalid values produce
  INCOMPATIBLE_INSTANCE with an attribute-specific exception message.
  Invalid primary metadata never falls back to thickness.
- SliceThickness is a one-slice grid-spacing surrogate, not measured spacing.
  Multi-slice IPP spacing, common normal, residual validation and origin are unchanged.
- Successful reconstruction now supplies positive finite spacing. VolumeGeometry
  itself was not changed: this scoped invariant is enforced in reconstruction.
- Existing fixture defaults now declare 2.0 mm explicitly. The existing
  rejectsOversizedReconstructionBeforeReadingVoxels fixture supplies 2.0 in its
  new constructor argument, preserving its allocation-path assertion.
  No original assertions were weakened. Error results still expose categories
  only; the existing result API does not surface exception diagnostic messages.

### Verification

- `mvn -Dtest=DicomSeriesServiceTest test`: BUILD SUCCESS; 36 tests,
  0 failures, 0 errors, 0 skipped.
- `mvn clean test`: BUILD SUCCESS; 77 tests, 0 failures, 0 errors, 0 skipped.

M6 single-slice spacing hardening COMPLETE. M6 remains IN PROGRESS.
Provenance identity, unused NIfTI validators, AR-1 and conversion remain deferred.
Related commit: Pending at verification time. No staging or commit performed.
