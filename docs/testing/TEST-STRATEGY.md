# Test Strategy

## 1. Purpose

Correctness means reconstructing the selected supported MR series without silent
changes to stored pixels or physical geometry, with stable failures for inconsistent
input. [Architecture 1.0](../architecture/ARCHITECTURE-REVISION-1.0.md) governs acceptance.
Executable JUnit tests are evidence of covered behavior, not proof of all requirements.

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
look-and-feel installation, not a working GUI.

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

## 8. Commands

```shell
mvn --version
mvn clean test
mvn "-Dtest=DicomSeriesServiceTest" test
mvn "-Dtest=ImmutableVoxelDataTest" test
mvn "-Dtest=FormatDetectionServiceTest" test
mvn "-Dtest=M6BoundaryTest" test
git diff --check
```

For historical reproduction, use `git archive` in a temporary directory and
`mvn clean test -f <export>/pom.xml`; do not reset the user's tree. Read Surefire XML
for counts/names only after fresh execution. Stale reports do not prove success.

## 9. Acceptance Discipline

M6 is IN PROGRESS until architecture section 24 is satisfied. `NativeVolume` and
`VoxelData.encoding()` do not satisfy the format-neutral ImageVolume requirement.
AR-1, single-slice policy before output and later conversion tests remain separate
work. Future affine/conversion/dataset modules have no tests because they are not
implemented. Record baseline, prompt ID, affected tests, command/results, reviewed
diff and checkpoint; do not manufacture history. This documentation task explicitly
forbids staging and committing.
