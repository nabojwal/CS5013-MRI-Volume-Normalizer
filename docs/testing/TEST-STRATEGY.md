# Test Strategy

## 1. Purpose

Correctness means reconstructing the selected supported MR series without silent
changes to stored pixels or physical geometry, with stable failures for inconsistent
input. [Architecture 1.0](../architecture/ARCHITECTURE-REVISION-1.0.md) governs acceptance.
Executable JUnit tests are evidence of covered behavior, not proof of all requirements.
This file originated as an M6-focused test strategy. Sections 4–6 preserve
the M6-era fixture inventory and historically identified gaps; they are not
a fresh exhaustive coverage audit of the 2026-11-07 integrated `main` tree.
The newer F1–F9, M8, EBO/FBO and GUI suites require their own bounded mapping.

## 2. Testing Principles

- Assert deterministic geometry and voxel placement for supported input permutations.
- Confirmed defect -> failing regression -> minimal fix -> focused run -> full suite.
- Include rejection paths, numeric boundaries and valid near-boundary controls.
- Preserve tolerances, assertions and regressions; never disable tests to pass.
- Execute Maven before recording success or creating a checkpoint.
- Keep fixtures small; overflow tests must not allocate huge arrays.

## 3. Test Levels

### Unit and domain invariant tests

`ImmutableVoxelDataTest` checks positive dimensions, checked counts, array-length
agreement and x-fastest indexing. Its service overflow test supplies virtual pixels
whose accessor throws if allocation validation reads them. `PixelEncoding`,
`RescaleTransform`, `SliceGeometry` and `VolumeGeometry` have production invariants,
but exhaustive isolated constructor/boundary tests do not exist. Selected geometry
and rescale behaviors are covered through service tests.

### Application / service tests

`DicomSeriesServiceTest` runs `DefaultDicomSeriesService` with the real
`Dcm4cheInstanceReader`: selection, compatibility, assembly, raw values, rescale,
ordering, geometry rejection and stable errors. `FormatDetectionServiceTest`
exercises the detection service with real probes.

### Infrastructure tests

Service suites create synthetic DICOM/NIfTI bytes to exercise parsing, endian
handling and gzip integrity. There is no separate `Dcm4cheInstanceReaderTest`.
`Dcm4cheSmokeTest` only proves Attributes API use; `FlatLafSmokeTest` proves
look-and-feel installation by itself, not a working GUI. The later
`GuiI1ScannerTest`, `GuiI1ExecutionTest`,
`GuiI1AutomaticDiscoveryTest`, `GuiI1DicomPart10IntegrationTest`,
`GuiI1DicomPreparationTest` and `GuiI1WorkflowE1Test` exercise
separate implemented GUI workflows.

### Architecture / boundary tests

`DependencyDirectionTest`, `PackageStructureTest`, `PresentationBoundaryTest` and
`M6BoundaryTest` inspect selected types/packages using reflection. They do not scan
all classes, method bodies or nested generic arguments, or enforce all AR-1 rules.
AR-1 must preserve behavior while updating these boundary expectations.

### Regression tests

Geometry (`32b4bc1`) and allocation (`fc1ad46`) regressions have recorded pre-fix
failures in [DEVELOPMENT_LOG.md](../../DEVELOPMENT_LOG.md). The [results audit](TEST-RESULTS.md)
separates historical records from fresh runs. Do not infer failures from test names.

## 4. DICOM Geometry Test Strategy

Existing tests cover mandatory/unknown series, mixed studies, duplicate SOPs,
incompatible dimensions/spacing/orientation, missing IOP/IPP, duplicate positions,
irregular spacing and ordering shuffled slices by physical position.
The common-normal fixture varies orientations at x=200 mm: separate normals can
reverse a 0.005 mm stack. Fixed SOP UIDs make reference selection reproducible.
The permutation test exercises all six orders of three slices, comparing voxel
order, origin, directions and spacing.

Whole-grid fixtures distinguish locally acceptable gaps with cumulative drift
(`0, 1, 2.0009, 3.0018`) from valid noise (`0, 1, 2.0004, 2.9996`). Defaults are
1e-4 for direction cosines and 1e-3 mm for position, pixel spacing, slice spacing
and duplicate position. Do not change these merely to pass tests.

Dedicated nonfinite/unit/orthogonality, in-plane displacement, conflicting
InstanceNumber, FrameOfReferenceUID/anatomical orientation and single-slice-policy
tests remain gaps. Shuffled-file coverage is not an explicit InstanceNumber test.

## 5. Pixel / Voxel Test Strategy

Existing DICOM assertions include unsigned 16-bit 65535, signed 12-bit -2048/-1,
BitsStored/HighBit metadata, rescale declaration/slope, slice placement and three
approved syntaxes. RGB/multiple samples and deflated syntax are rejected.
Dedicated 8-bit boundaries, missing Pixel Data, payload-length failures, invalid HighBit, rescale
mismatch/partial declarations and explicit multiframe fixtures remain gaps.
M6 never applies rescale metadata to stored samples.

Allocation regressions cover int wraparound, counts above int/long capacity,
nonpositive dimensions and length mismatch using tiny arrays. Checked counts do
not guarantee sufficient heap for every theoretically valid volume.

## 6. Format Detection Test Strategy

Tests cover DICOM correct/missing/wrong extensions and preamble-less input;
NIfTI-1/2 magic/header length and big-endian recognition; gzip NIfTI versus unrelated
gzip; corrupt/truncated input; image-pair rejection; invalid/missing paths,
directories, empty/unknown content and the 1 MiB DICOM detection budget.

The budget test asserts UNKNOWN/INPUT_TOO_LARGE, not rejection by the full reader.
A valid >1 MiB reconstruction test remains needed. NIfTI field-validation helpers
are uncalled; dimension/datatype/bitpix validation is an implementation/test gap.
Gzip is consumed through EOF up to a 16 MiB decompressed cap, retaining at most
4096 header bytes. Exceeding the cap currently maps to INVALID_GZIP.

## 7. Test Data

Fixtures use `@TempDir`, generated DICOM Attributes, byte buffers and gzip streams.
No real patient data or external imaging installation is required. Ordinary fixture
UIDs may use clock/random generation; assertions do not depend on those exact values.
Geometry-reference regressions fix relevant UIDs. Never commit studies or generated data.

## 8. Integrated prototype test design (2026-11-07)

The merged feature checkpoint `454f8b58275583b0137d03a88d025c13523013eb`
and `main` merge commit `b032a51cd369b428f201c6b0dd8d23a49f195eec`
have identical source trees. Test obligations must be traced to the actual
implemented routes, not inferred from the presence of a Swing class or a
single successful imaging conversion:

- **F1/F8/F9:** restricted plan/checkpoint serialization, private storage
  qualification, safe no-replace publication, reopened NIfTI voxel/type/geometry/
  scaling comparison, acknowledgment and fail-closed uncertain outcomes.
  Representative suites include `JsonManifestCodecTest`,
  `F9HeadlessConversionServiceTest`, `F9HeadlessIntegrationTest` and
  `F9HeadlessBoundaryTest`.
- **Forward batch (FBO):** qualified prepared-series roots, bounded sequential
  dispatch, failure aggregation and report/publication behavior.
  `FboDirectoryPreflightTest`, `FboCommandIntegrationTest`,
  `FboBatchControllerTest` and `FboReportWriterTest` provide separate
  executable coverage.
- **Reverse conversion (M8/EBO):** restricted 3D integer NIfTI-1 inputs,
  geometry and intensity semantics, derived DICOM verification, journal/
  completion inventory, nonclinical evaluation metadata and sequential
  batching. Relevant suites include `M8ConversionIntegrationTest`,
  `M8RecoveryTest`, `M8JournalTest`, `EboCommandTest` and
  `EboAcceptanceClosureTest`.
- **GUI-I1:** discovery and inspection separated from authoritative backend
  qualification, single-versus-batch route selection, destination admission,
  re-run ledger protection, nonverified recovery states and proper user
  acknowledgment for reverse evaluation. Use the `GuiI1*Test` suites;
  a FlatLaf smoke test alone is not a GUI acceptance test.
- **Real MRI and independent interchange:** maintain separately documented
  source identity, selected-series completeness, voxel-level comparisons,
  spatial transforms and profiles of the external parser. Historical
  15-slice M6/M7/F7 evidence must not be presented as a general 155-slice
  or arbitrary DICOM-series acceptance claim.

**Environment and negative cases:** Native Windows regression qualification
may require appropriately provisioned NTFS fixtures, user privileges and
sufficiently short staging paths. PR #1's 961/961 local run used an **elevated**
session, `java.io.tmpdir=C:\PT`, `d1.test.destination=C:\PD`.
Keep unsupported network/cloud, long-path, symlink/reparse, privilege/ACL,
removable-media and uncertain-publication cases separate from successful
destination demonstrations. A valid `RECOVERY_REQUIRED` result is not success,
and deleting private evidence to make tests pass is prohibited.

**Evidence boundary:** PR #1 records user-provided workstation results and
a passing targeted mixed-series GUI test; there was **no GitHub CI run** in
that record. The date of this document update is not a new Maven execution
date. Respect the unchanged formal F8/F9/M8 and global Pre-M8 gates.

## 9. Commands

```shell
mvn --version
mvn clean test
mvn "-Dtest=DicomSeriesServiceTest" test
mvn "-Dtest=ImmutableVoxelDataTest" test
mvn "-Dtest=FormatDetectionServiceTest" test
mvn "-Dtest=M6BoundaryTest" test
mvn "-Dtest=F9HeadlessIntegrationTest,FboCommandIntegrationTest" test
mvn "-Dtest=M8ConversionIntegrationTest,EboCommandTest" test
mvn "-Dtest=GuiI1ExecutionTest,GuiI1ScannerTest,GuiI1DicomPart10IntegrationTest" test
git diff --check
```

For historical reproduction, use `git archive` in a temporary directory and
`mvn clean test -f <export>/pom.xml`; do not reset the user's tree. Read Surefire XML
for counts/names only after fresh execution. Stale reports do not prove success.

## 10. Acceptance Discipline

M6 and M7, and the F0–F7 foundation workstreams, have recorded bounded
acceptance evidence; the older M6 `IN PROGRESS` statement is a historical
snapshot, **not** today's status. The format-neutral `ImageVolume`, DICOM
LPS-to-NIfTI-RAS affine, forward conversion, later dataset services, M8/EBO,
FBO and GUI all have implemented source and corresponding tests. The existence
of these components does not establish full profile coverage or every contract.

Retain frozen test data, tolerances, requirement-to-assertion mapping and
historical run records. Before changing an acceptance state, identify its
exact contract, source commit, executed command, environment, test count,
negative cases, and independent review/owner approval where required.
**Do not** promote pending F8/F9/M8 obligations, F10/F11 or the global
Pre-M8 gate based solely on the 961-test GUI integration run. Documentation
updates do not constitute an executable regression or acceptance decision.
Record changes in the development log only after the relevant activity
has actually occurred. This documentation task explicitly
forbids staging and committing.
