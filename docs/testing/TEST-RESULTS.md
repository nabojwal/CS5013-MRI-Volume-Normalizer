# Verified Test Results

## Verification Policy

Add fresh-result entries only after execution. Stale `target/surefire-reports`
cannot establish success. Record the exact baseline, command, counts and scope;
working-tree verification is not a new Git checkpoint. Keep generated reports
out of Git. Historical log evidence is explicitly distinguished below.

## Verified Checkpoints

All dates below are 2026-10-01 (Asia/Calcutta). Fresh runs used Java 21.0.12.1
(Eclipse Adoptium), Maven 3.9.16 on Windows 11. Versions were checked with
`mvn --version` in this audit.

| Verification date | Commit / tree | Stage | Command | Tests | Failures | Errors | Skipped | Result |
|---|---|---|---|---:|---:|---:|---:|---|
| 2026-10-01 | `3c3d979` Git archive | M6 reconstruction baseline | `mvn clean test -f <export>/3c3d979/pom.xml` | 35 | 0 | 0 | 0 | BUILD SUCCESS |
| 2026-10-01 | `32b4bc1` Git archive | M6 geometry hardening | `mvn clean test -f <export>/32b4bc1/pom.xml` | 39 | 0 | 0 | 0 | BUILD SUCCESS |
| 2026-10-01 | `fc1ad46` + documentation working tree | Current M6 governance | `mvn clean test` | 48 | 0 | 0 | 0 | BUILD SUCCESS |
| 2026-10-01 | Pending | Format-detection budget hardening | `mvn clean test` | 56 | 0 | 0 | 0 | BUILD SUCCESS |

The Pending row records the executed 2026-10-01 working-tree verification based
on `ba13b10`; no new commit is implied. See [M6-P05](../../PROMPTS.md#m6-p05--format-detection-evidence-budget-hardening)
and the development log for its regressions and changed detection semantics.
The 48-method inventory below remains the earlier governance-audit snapshot.

These are fresh reproductions, not reconstructed original build transcripts.
Both commits were exported with `git archive --format=zip` and extracted into an
isolated temporary directory; the user's checkout/index was not changed. The
first run had 11 DICOM service tests; the second had 15. The original geometry
log independently records the 39-test full-suite success. The baseline commit
contains no original 35-test execution log; its result is established here by rerun.

### Earlier historical records

[DEVELOPMENT_LOG.md](../../DEVELOPMENT_LOG.md) records `mvn clean test` successes:
M1 `63fff6d` (1), M2 `8e1580b` (2), M3 `cf9013d` (3), M4 `7e50a9b` (7),
M5 `88da522` (16), M5.1 `1989589` (23), all with zero failures/errors.
Those earlier skipped counts are unavailable; those checkpoints were not rerun
in this audit. The log at `fc1ad46` records 48 tests with zero failures/errors/skips.
These historical records are supporting evidence, not fresh runs in the table.

## Regression Evidence

The following pre-fix runs are preserved in the development log committed with
each fix; this audit did not recreate their failing trees.

| Fix / requirement | Regression methods | Recorded pre-fix evidence | Post-fix evidence |
|---|---|---|---|
| `32b4bc1`: one common normal, invariant reference geometry | `ordersSmallOrientationVariationsUsingOneCommonNormal`; `geometryAndVoxelsAreInvariantUnderInputPermutation` | Geometry log records reversed order and input-order-dependent rejection | Focused suite 15/0/0/0 in log; fresh archive full suite 39/0/0/0 |
| `32b4bc1`: validate final grid | `rejectsCumulativeDriftDespiteLocallyRegularGaps`; `acceptsRegularGridWithPositionNoiseWithinTolerance` | Drift accepted incorrectly; valid noisy control already passed | Same focused/full results |
| `fc1ad46`: overflow-safe dimensions | `rejectsCountThatWrapsToZero`; `rejectsCountThatWrapsToSmallPositiveLength`; `rejectsCountBeyondEvenLongCapacity` | Allocation log records these wraparound defects | Focused suite 9/0/0/0 and full 48/0/0/0 in log |

Geometry pre-fix command: `mvn -Dtest=DicomSeriesServiceTest clean test`,
15 tests / 3 failures / 0 errors / 0 skipped. Allocation pre-fix command:
`mvn -Dtest=ImmutableVoxelDataTest clean test`, 9 / 3 / 0 / 0.
Post-fix focused commands used `test` without `clean`, followed by `mvn clean test`.
No historical red-run claim is made for the initial baseline or M5/M5.1.

## Outstanding Test and Acceptance Gaps

| Area | Current evidence | Remaining work / scope |
|---|---|---|
| Format-neutral volume | `NativeVolume`, `VoxelData.encoding()` expose DICOM-shaped metadata | M6 section 24 item 19 unmet; AR-1 generic ImageVolume/scalar model and updated tests pending |
| Architecture boundaries | Reflection checks selected types; ports still in `domain.port`; monolithic service remains | AR-1 package responsibilities and broader boundary coverage pending |
| Provenance | Ordered path-reference SHA-256; no selected series/content identity; one assertion excludes raw path from fingerprint | Identity policy, patient-field exclusion and success/failure regression coverage need expansion; API constructor mismatch itself is repaired |
| Single slice | Service publishes spacing `0.0` | Explicit unavailable/metadata-derived policy and tests required before NIfTI output (section 7.8) |
| Large DICOM inputs | Detector returns INPUT_TOO_LARGE above 1 MiB; reader parses directly without that cap | Valid >1 MiB reader/service regression absent; do not report an inherited reader limit |
| Frame/anatomical orientation | Reader/model do not retain/check FrameOfReferenceUID or AnatomicalOrientationType | Deferred hardening/policy and tests; not separately enumerated in section 24 |
| NIfTI detection invariants | `validNifti1Fields`/`validNifti2Fields` exist but are not called | Section 15.4 requires integrating or removing dead validation paths; malformed dimensions/datatype regressions absent |
| Geometry adversarial coverage | Ordering, duplicates, drift, noisy control tested | Dedicated nonfinite/unit/orthogonality, in-plane displacement and conflicting InstanceNumber fixtures missing |
| Encoding/object rejection coverage | Signed 12-bit, unsigned 16-bit, supported syntaxes, RGB and deflated rejection tested | Dedicated 8-bit bounds, missing Pixel Data, bad HighBit/payload length, multiframe, partial/mismatched rescale and file-meta mismatch fixtures missing |
| Voxel allocation | Checked multiplication and nine tests at `fc1ad46` | Scoped wraparound defect resolved; no heap-capacity guarantee or stress-test claim |
| NIfTI conversion / round trip | No reader/writer/conversion implementation | Planned M7/M8 affine, voxel fidelity and interoperability tests; not current functionality |

**M6 IN PROGRESS.** The format-neutral volume requirement alone prevents completion.
Other documented gaps distinguish missing tests from absent implementation and
later-stage policy decisions; not every future test is an immediate M6 blocker.

## JavaDoc / Comments Review

Production review was read-only. `VoxelData.rawValueAt` and `ImmutableVoxelData`
would benefit from documenting x=column, y=row, z=slice, x-fastest indexing,
raw-versus-rescaled semantics, defensive copies and allocation exceptions.
`SliceGeometry`/`VolumeGeometry` need explicit units, direction conventions and
current validation limits: vector length/copying is not full finite/unit validation.
`ProvenanceRecord.inputFingerprint` needs its path-based, order-sensitive semantics
documented. These are recommendations, not new guarantees or source changes.

Existing service comments explain deterministic reference selection and final-grid
use; test comments explain tilted-stack projections, fixed reference UIDs, OW endian
conversion and arithmetic wraparound. No additional fixture comments were necessary.
The approved architecture was reviewed and retained byte-for-byte; duplicate title
and draft-era status prose remain as editorial/history issues. Version 1.0.0 and
APPROVED/authoritative metadata are present; no broken explicit internal Markdown
links were found. This audit does not reinterpret its technical contracts.

## Current Working-Tree Verification and Inventory

Fresh current execution and its Surefire-derived class/method inventory are recorded below.

Fresh current run on 2026-10-01: `mvn clean test`, **BUILD SUCCESS**,
48 tests, 0 failures, 0 errors, 0 skipped. Baseline `fc1ad46` plus this documentation
task; production and test files match HEAD. No new commit was created.

| Class | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `architecture.DependencyDirectionTest` | 2 | 0 | 0 | 0 |
| `architecture.M6BoundaryTest` | 1 | 0 | 0 | 0 |
| `architecture.PackageStructureTest` | 1 | 0 | 0 | 0 |
| `architecture.PresentationBoundaryTest` | 1 | 0 | 0 | 0 |
| `Dcm4cheSmokeTest` | 1 | 0 | 0 | 0 |
| `DicomSeriesServiceTest` | 15 | 0 | 0 | 0 |
| `FlatLafSmokeTest` | 1 | 0 | 0 | 0 |
| `FormatDetectionServiceTest` | 16 | 0 | 0 | 0 |
| `ImmutableVoxelDataTest` | 9 | 0 | 0 | 0 |
| `MainTest` | 1 | 0 | 0 | 0 |

### Executed methods

All 48 methods below were obtained from fresh Surefire XML and matched against
the complete test tree. Existing names and executable tests were retained.
Source links are authoritative; loops within one method do not count as extra tests.

#### [architecture.DependencyDirectionTest](../../src/test/java/org/cbihi/mrinormalizer/architecture/DependencyDirectionTest.java)

- `domainContractsShouldDependOnlyOnDomainOrJavaTypes`
- `domainContractsShouldNotDependOnOuterLayersOrInfrastructure`

#### [architecture.M6BoundaryTest](../../src/test/java/org/cbihi/mrinormalizer/architecture/M6BoundaryTest.java)

- `m6DomainAndApplicationApisShouldNotExposeOuterFrameworkTypes`

#### [architecture.PackageStructureTest](../../src/test/java/org/cbihi/mrinormalizer/architecture/PackageStructureTest.java)

- `requiredArchitecturalPackagesShouldExist`

#### [architecture.PresentationBoundaryTest](../../src/test/java/org/cbihi/mrinormalizer/architecture/PresentationBoundaryTest.java)

- `presentationBoundaryShouldAcceptApplicationResults`

#### [Dcm4cheSmokeTest](../../src/test/java/org/cbihi/mrinormalizer/Dcm4cheSmokeTest.java)

- `dcm4cheAttributesShouldWork`

#### [DicomSeriesServiceTest](../../src/test/java/org/cbihi/mrinormalizer/DicomSeriesServiceTest.java)

- `acceptsMultipleSuppliedSeriesOnlyWhenRequestedSeriesIsExplicit`
- `acceptsRegularGridWithPositionNoiseWithinTolerance`
- `geometryAndVoxelsAreInvariantUnderInputPermutation`
- `mapsEmptyMissingNonDicomAndCorruptInputsToStableErrors`
- `ordersShuffledSlicesByProjectedPatientPositionAndPlacesVoxels`
- `ordersSmallOrientationVariationsUsingOneCommonNormal`
- `preservesSignedStoredValuesAndBitsStoredSemantics`
- `reconstructsSingleSliceWithRawUnsignedValuesAndRescaleMetadata`
- `rejectsCumulativeDriftDespiteLocallyRegularGaps`
- `rejectsIncompatibleSpacingOrientationDuplicateAndIrregularGeometry`
- `rejectsMissingSpatialMetadataUnsupportedObjectsAndUnsupportedPixels`
- `rejectsMixedStudyDuplicateSopAndIncompatibleDimensions`
- `rejectsUnsupportedDeflatedTransferSyntax`
- `requiresExplicitSeriesSelectionAndReportsUnknownSeries`
- `supportsAllApprovedUncompressedTransferSyntaxes`

#### [FlatLafSmokeTest](../../src/test/java/org/cbihi/mrinormalizer/FlatLafSmokeTest.java)

- `flatLafShouldInstall`

#### [FormatDetectionServiceTest](../../src/test/java/org/cbihi/mrinormalizer/FormatDetectionServiceTest.java)

- `detectsBigEndianNifti`
- `detectsCorruptRecognizableInputs`
- `detectsDicomWithExpectedExtension`
- `detectsDicomWithoutExtension`
- `detectsDicomWithWrongExtension`
- `detectsGzipWrappedNiftiOnlyWhenPayloadIsNifti`
- `detectsNifti1AndNifti2`
- `detectsNiftiWithoutExtensionAndIgnoresWrongExtension`
- `detectsPreambleLessDicomThroughDcm4che`
- `handlesEmptyAndUnsupportedInputs`
- `handlesMissingPathAndDirectory`
- `invalidInputReferenceGetsStableDiagnostic`
- `oversizedDicomIsInspectedOnlyWithinTheDetectionBound`
- `rejectsGzipWithCorruptTrailer`
- `rejectsNiftiImagePairHeaders`
- `weakDicomLikeBytesRemainUnknown`

#### [ImmutableVoxelDataTest](../../src/test/java/org/cbihi/mrinormalizer/ImmutableVoxelDataTest.java)

- `preservesSmallVolumeCountAndIndexing`
- `rejectsCountBeyondEvenLongCapacity`
- `rejectsCountThatWrapsToSmallPositiveLength`
- `rejectsCountThatWrapsToZero`
- `rejectsMismatchedArrayLength`
- `rejectsNegativeInEachDimension`
- `rejectsOversizedReconstructionBeforeReadingVoxels`
- `rejectsPositiveLongCountAboveArrayCapacity`
- `rejectsZeroInEachDimension`

#### [MainTest](../../src/test/java/org/cbihi/mrinormalizer/MainTest.java)

- `javaVersionShouldBe21`

## 2026-10-02 - Frame/Profile Working-Tree Verification

Baseline: `cacec3a`. Related commit: **Pending at verification time**.
This is a fresh working-tree result, not a new Git checkpoint.
The historical inventories above remain snapshots of their recorded checkpoints.

| Command / phase | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| `mvn -Dtest=DicomSeriesServiceTest test` pre-fix | 25 | 6 | 0 | 0 | BUILD FAILURE |
| Same command, final post-fix | 25 | 0 | 0 | 0 | BUILD SUCCESS |
| `mvn clean test` | 66 | 0 | 0 | 0 | BUILD SUCCESS |

Six negative profile regressions demonstrated acceptance of missing/blank/mixed
frames and QUADRUPED/mixed/unknown anatomy before the fix. Four positive new tests
and the strengthened selected-series isolation test already passed.
See [M6-P06](../../PROMPTS.md#m6-p06---selected-series-frame-and-biped-profile-hardening)
and [development log](../../DEVELOPMENT_LOG.md) for exact method names and evidence.

Real dcm4che extraction is exercised end-to-end and asserted directly in the
profile fixtures, including null preservation. The direct accessor assertions were
added after model extension; the pre-fix failures were service behavior assertions.

Final class counts: DicomSeriesServiceTest 25, FormatDetectionServiceTest 24,
ImmutableVoxelDataTest 9, architecture tests 5, smoke/main tests 3 = 66.
All failures/errors/skips are zero. M6 remains IN PROGRESS; single-slice spacing,
provenance identity, unused NIfTI validators, AR-1 and conversion are still deferred.
No staging or commit performed.

## 2026-10-02 - Single-Slice Spacing Working-Tree Verification

Baseline: `5c9b3ee`. Related commit: **Pending at verification time**.
These are executed working-tree results, not a new committed checkpoint.

| Phase / command | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| Before production edits: `mvn -Dtest=DicomSeriesServiceTest test` | 33 | 7 | 0 | 0 | BUILD FAILURE |
| After model-only wiring, before extraction/policy: same command | 36 | 10 | 0 | 0 | BUILD FAILURE |
| Final focused: same command | 36 | 0 | 0 | 0 | BUILD SUCCESS |
| `mvn clean test` | 77 | 0 | 0 | 0 | BUILD SUCCESS |

New tests cover primary spacing, precedence, thickness fallback, missing/invalid
metadata, non-finite boundary values, reader extraction/null preservation and
multi-slice non-regression. Exact names and old behavior are recorded in
[development log](../../DEVELOPMENT_LOG.md) and
[M6-P07](../../PROMPTS.md#m6-p07---single-slice-spacing-policy-hardening).

The 1.5 mm multi-slice control passed before and after despite 9.0/7.0 metadata.
One-slice geometry retains its original origin and validated directions.
Final class totals: DicomSeriesServiceTest 36, FormatDetectionServiceTest 24,
ImmutableVoxelDataTest 9, architecture 5, smoke/main 3 = 77.

M6 remains IN PROGRESS. This resolves the reconstruction single-slice spacing
gap recorded in earlier snapshots; provenance identity, unused NIfTI validators,
AR-1 and conversion remain deferred. No staging or commit performed.

## 2026-10-02 - Provenance Identity Working-Tree Verification

Baseline: `06c9b69`. Related commit: **Pending at verification time**.
Fresh execution results for the working tree, not a new committed checkpoint.

| Phase / command | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| Pre-fix: `mvn -Dtest=DicomSeriesServiceTest test` | 43 | 6 | 0 | 0 | BUILD FAILURE |
| Final focused: same command | 43 | 0 | 0 | 0 | BUILD SUCCESS |
| `mvn clean test` | 84 | 0 | 0 | 0 | BUILD SUCCESS |

Seven added tests cover order/path independence, selected-byte sensitivity,
unselected-candidate isolation, lowercase encoding/repeatability, canonical
UID framing/order and read failure without a partial digest. Format/repeatability
already passed before the fix. Unselected content changes alone also passed
previously because no content was hashed; removing the candidate exposed scope.
See [development log](../../DEVELOPMENT_LOG.md) for exact pre-fix evidence and
[M6-P08](../../PROMPTS.md#m6-p08---provenance-source-identity-hardening).

Final totals: DicomSeriesServiceTest 43, FormatDetectionServiceTest 24,
ImmutableVoxelDataTest 9, architecture 5, smoke/main 3 = 84.
M6 remains IN PROGRESS. Richer provenance, Clock injection, unused NIfTI validators,
AR-1 and conversion remain deferred. No staging or commit performed.
