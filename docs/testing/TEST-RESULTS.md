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

## 2026-10-02 - M7-P01 Format-Neutral ImageVolume Verification

Baseline: `cb7769e` on `feature/m7-dicom-to-nifti`.
Related commit: **`1dd31fc` - `refactor: introduce format-neutral image volume`**.

This verification covers the minimum architecture migration required before
DICOM-to-NIfTI conversion.

The generic volume boundary was migrated from `NativeVolume` with
DICOM-coupled voxel encoding to `ImageVolume` with generic scalar semantics.

Changes verified include:

- `ImageVolume` as the authoritative generic 3D image aggregate;
- `ScalarType` with `UINT8`, `INT8`, `UINT16`, and `INT16`;
- `VoxelData.scalarType()` replacing generic exposure of DICOM `PixelEncoding`;
- DICOM `PixelEncoding` retained at the DICOM metadata boundary;
- `IntensityTransform` as the generic linear intensity representation;
- `DicomProcessingResult` returning `ImageVolume`;
- removal of the parallel `NativeVolume` aggregate;
- preservation of decoded stored voxel values and geometry;
- architecture regression coverage preventing DICOM encoding leakage.

| Command / suite | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| `DicomSeriesServiceTest` | 47 | 0 | 0 | 0 | PASS |
| `ImageVolumeTest` | 3 | 0 | 0 | 0 | PASS |
| `ImmutableVoxelDataTest` | 9 | 0 | 0 | 0 | PASS |
| `FormatDetectionServiceTest` | 24 | 0 | 0 | 0 | PASS |
| Architecture tests | 6 | 0 | 0 | 0 | PASS |
| Smoke / main tests | 3 | 0 | 0 | 0 | PASS |
| `mvn clean test` | **92** | **0** | **0** | **0** | **BUILD SUCCESS** |

`git diff --check` also completed without errors.

AR-1A / M7 preparation is COMPLETE. The generic volume contract is now suitable
for the next conversion stage. NIfTI affine construction, LPS-to-RAS conversion,
qform/sform handling, and NIfTI serialization are not part of this verification.


## 2026-10-02 - M7-P02 DICOM LPS to NIfTI RAS Affine Verification

Baseline: `1dd31fc` on `feature/m7-dicom-to-nifti`.
Related commit: **8fb2831 - feat: add nifti ras affine mapping**.

This verification establishes the explicit spatial transform required before
NIfTI serialization.

Verified behavior:

- immutable finite 4x4 affine representation;
- column-vector voxel-to-world convention;
- x=column, y=row, z=slice indexing;
- column spacing applied to x;
- row spacing applied to y;
- validated slice spacing applied to z;
- voxel `(0,0,0)` maps to the recorded physical origin;
- nonzero origins and unequal spacing are preserved;
- oblique orientations are represented without resampling;
- DICOM LPS to NIfTI RAS is performed by left multiplication with
  `diag(-1,-1,1,1)`;
- voxel arrays are not flipped, reordered, interpolated, or resampled;
- unsupported source coordinate systems are rejected by the conversion mapper.

| Command / suite | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| `AffineMatrix4Test` | 5 | 0 | 0 | 0 | PASS |
| `NiftiAffineMapperTest` | 5 | 0 | 0 | 0 | PASS |
| Focused affine command | 10 | 0 | 0 | 0 | BUILD SUCCESS |
| `mvn clean test` | **102** | **0** | **0** | **0** | **BUILD SUCCESS** |

The previous 92-test M7-P01 baseline remains green. NIfTI serialization,
qform quaternion encoding and independent interoperability validation are outside
this checkpoint.

## 2026-10-02 - M7-P03 NIfTI-1 Writer Verification

Baseline: `8fb2831` on `feature/m7-dicom-to-nifti`.
Related commit: **`6924dd5` - `feat: add nifti-1 volume writer`**.

This verification covers project-owned NIfTI-1 serialization of the generic
image-volume model.

Verified behavior includes:

- NIfTI-1 single-file serialization;
- `.nii` output;
- gzip-wrapped `.nii.gz` output;
- UINT8, INT8, UINT16 and INT16 datatype mapping;
- matching datatype and bit-depth metadata;
- dimensions and voxel spacing;
- x-fastest raw voxel serialization;
- preservation of supported integer raw values;
- preservation of declared linear intensity scaling metadata;
- millimetre spatial units;
- supplied RAS affine serialized into sform;
- equivalent qform generation when representable;
- exact sform retention and `qform_code = 0` for affine geometry that cannot be
  represented by the quaternion model;
- no voxel flip, resampling, interpolation or permutation;
- generated `.nii` recognition by the existing NIfTI format detector;
- scalar-range rejection.

The writer receives an already-computed RAS affine. DICOM LPS-to-RAS conversion
remains an application conversion responsibility rather than an infrastructure
serialization responsibility.

| Command / suite | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| `Nifti1VolumeWriterTest` | 11 | 0 | 0 | 0 | PASS |
| `mvn clean test` | **113** | **0** | **0** | **0** | **BUILD SUCCESS** |

An earlier test compile failure was caused only by use of the nonexistent
`DetectionResult.isRecognized()` test API. The assertion was corrected to use
`DetectionResult.outcome()` and expect `DetectionOutcome.NIFTI`; production
writer behavior was unchanged by that correction.

M7-P03 serialization is verified. End-to-end DICOM reconstruction, affine
conversion and NIfTI writing as one use case remain pending.


## 2026-10-02 - M7-P04 End-to-End DICOM to NIfTI Verification

Baseline: `6924dd5` on `feature/m7-dicom-to-nifti`.
Related commit: **`a495699` - `feat: add end-to-end dicom to nifti conversion`; follow-up governance cleanup `c38ff6d`**.

This verification exercises the complete implemented DICOM-to-NIfTI path using
real synthetic DICOM files and the production adapters.

| Command / suite | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| `DicomToNiftiServiceTest` | 5 | 0 | 0 | 0 | PASS |
| `mvn clean test` | **118** | **0** | **0** | **0** | **BUILD SUCCESS** |

Verified end-to-end properties include DICOM reconstruction, physical slice
ordering, generic `ImageVolume` construction, LPS-to-RAS affine mapping,
NIfTI-1 serialization, `.nii.gz` wrapping, dimensions, datatype, voxel spacing,
complete raw voxel preservation, signed-value preservation, scaling metadata,
sform geometry, deterministic input-order behavior and failure-without-output.

No interpolation, resampling, normalization or voxel-array reordering occurs.

This establishes the first executable DICOM-to-NIfTI vertical slice for the
supported profile. Output-write failure normalization and broader independent
interoperability validation remain follow-up hardening work.

## 2026-10-02 - M7-P05 Acceptance Hardening and Independent Interoperability Verification

Baseline: `c38ff6d` on `feature/m7-dicom-to-nifti`.

M7-P05A related commit: **`2bf1927` - `fix: harden nifti output handling`**.

M7-P05B is validation-only acceptance evidence and did not modify tracked
production source code.

### M7-P05A - Output Hardening Verification

Verified behavior:

- existing NIfTI output is not overwritten;
- output collision is represented as `OUTPUT_ALREADY_EXISTS`;
- invalid output targets are represented as `INVALID_OUTPUT`;
- general output write failures are represented as `OUTPUT_WRITE_FAILED`;
- output-side failures are distinct from DICOM reconstruction errors;
- failed output conversion produces unsuccessful overall provenance;
- partial newly created output is cleaned up when serialization fails;
- successful output behavior remains unchanged.

| Command / suite | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| Hardening-focused tests | 5 | 0 | 0 | 0 | PASS |
| Combined NIfTI writer/conversion regression | 21 | 0 | 0 | 0 | PASS |
| `mvn clean test` | **123** | **0** | **0** | **0** | **BUILD SUCCESS** |

### M7-P05B - Independent NIfTI Interoperability Verification

The production `Nifti1VolumeWriter` generated temporary `.nii` and `.nii.gz`
files from a synthetic `2 x 2 x 2` `UINT16` volume.

Independent validation was performed with NiBabel 5.4.2. NiBabel was used only
as an external acceptance validator and was not added to the Java runtime or
Maven dependency graph.

Validation input:

- dimensions: `2 x 2 x 2`;
- raw stored voxels: `1..8`;
- column spacing: `0.5 mm`;
- row spacing: `0.75 mm`;
- slice spacing: `2.0 mm`;
- DICOM-LPS origin: `[10, 20, 30]`;
- declared intensity slope/intercept: `2.5 / -100`.

Independent results:

| Acceptance property | Result |
|---|---|
| `.nii` parsed by NiBabel | PASS |
| `.nii.gz` parsed by NiBabel | PASS |
| Shape `(2, 2, 2)` | PASS |
| Datatype `uint16` | PASS |
| Spacing `(0.5, 0.75, 2.0)` | PASS |
| Raw voxel values `1..8` preserved exactly | PASS |
| `.nii` and `.nii.gz` raw arrays identical | PASS |
| `.nii` and `.nii.gz` spatial affines equivalent | PASS |
| `qform_code = 1` | PASS |
| `sform_code = 1` | PASS |
| Declared slope/intercept preserved | PASS |
| Expected DICOM-LPS to NIfTI-RAS mapping | PASS |
| Affine comparison tolerance `1e-5` | PASS |

### M7 Acceptance State

The focused M7 verification now satisfies:

- [x] independent NIfTI parsing;
- [x] output shape preservation;
- [x] supported integer voxel-array preservation;
- [x] voxel spacing preservation;
- [x] world-coordinate mapping within tolerance;
- [x] qform/sform consistency;
- [ ] explicit conversion provenance/validation report.

M7 therefore remains **IN PROGRESS**.

Reconstruction provenance is already carried through `DicomToNiftiResult`, but
the dedicated conversion validation-report model remains the final M7 acceptance
item.
## 2026-10-03 - M7-P05C Conversion Validation Report and Final M7 Verification

Architectural checkpoint: **`b6bab40` - `docs: define m7 conversion validation report`**.

Implementation checkpoint: **`0976788` - `feat: add conversion validation report`**.

This verification closes the final Architecture Revision 1.0 M7 requirement that
DICOM-to-NIfTI conversion produce explicit provenance/validation output.

### M7-P05C - Validation-Report Verification

The application now returns a dedicated `ConversionValidationReport` for
successful DICOM-to-NIfTI conversion.

The report is created only after `NiftiVolumeWriter.write(...)` completes
successfully.

It records factual conversion invariants derived from the validated in-memory
`ImageVolume`, the explicit NIfTI-RAS affine supplied to the writer and the
supported conversion path.

The report does not claim that the written NIfTI file was reopened, reparsed or
independently validated at runtime.

Verified report contents include:

- output target;
- width, height and depth;
- scalar type;
- voxel count;
- row spacing;
- column spacing;
- slice spacing;
- NIfTI-RAS voxel-to-world affine;
- intensity transform;
- stored-voxel preservation state;
- resampling state;
- interpolation state;
- voxel-order-change state.

For the supported M7 integer-preserving path, the verified invariants are:

- stored voxel values preserved: `true`;
- resampled: `false`;
- interpolated: `false`;
- voxel order changed: `false`.

Failure-path verification confirms:

- DICOM reconstruction failure produces no validation report;
- output-write failure produces no validation report;
- failed conversion cannot carry a validation report;
- successful output requires a validation report;
- result output and validation-report output must match.

### Focused and Combined Test Results

| Command / suite | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| `ConversionValidationReportTest` | 6 | 0 | 0 | 0 | PASS |
| `ConversionValidationReportTest` + `DicomToNiftiServiceTest` + `DicomToNiftiServiceHardeningTest` | 14 | 0 | 0 | 0 | PASS |
| `mvn clean test` | **129** | **0** | **0** | **0** | **BUILD SUCCESS** |

The previous 123-test regression baseline remains green. P05C adds six focused
tests, increasing the complete Java suite to 129 tests.

### Independent Interoperability Evidence

Independent serialized-file validation remains the M7-P05B NiBabel 5.4.2
acceptance evidence.

That validation independently confirmed:

- `.nii` parsing;
- `.nii.gz` parsing;
- shape;
- datatype;
- voxel spacing;
- exact supported integer voxel-array preservation;
- equivalent `.nii` and `.nii.gz` spatial affines;
- qform/sform consistency;
- declared intensity scaling;
- expected DICOM-LPS to NIfTI-RAS world mapping;
- affine agreement within absolute tolerance `1e-5`.

NiBabel remains external acceptance tooling only. It is not a Maven or runtime
dependency.

### Final M7 Acceptance State

Architecture Revision 1.0 M7 exit criteria:

- [x] output parsed by an independent NIfTI implementation;
- [x] output shape matches;
- [x] supported integer voxel array matches exactly;
- [x] spacing matches within tolerance;
- [x] world-coordinate mapping matches within tolerance;
- [x] qform/sform are internally consistent;
- [x] conversion produces explicit provenance/validation output.

All seven M7 exit criteria are satisfied.

**M7 DICOM-to-NIfTI is COMPLETE for the supported profile as of 2026-10-03.**

GUI integration was not required for M7 completion.

ADR-014 records that M7 uses structured application-level provenance and
validation models without adding a persisted sidecar. Future persisted
provenance/validation manifests use UTF-8 JSON as their canonical serialization
format.
