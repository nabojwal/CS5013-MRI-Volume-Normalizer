# Test Strategy

## Current integrated verification scope — 2026-11-07

**Audited source:** `main` merge commit
[`b032a51cd369b428f201c6b0dd8d23a49f195eec`](https://github.com/nabojwal/CS5013-MRI-Volume-Normalizer/commit/b032a51cd369b428f201c6b0dd8d23a49f195eec),
corresponding to PR [#1](https://github.com/nabojwal/CS5013-MRI-Volume-Normalizer/pull/1).
The tested feature head `454f8b58275583b0137d03a88d025c13523013eb`
has the same tracked source tree. **M8 reverse conversion and M10 GUI
integration are implemented and have associated executable tests**;
the historic M6/M7-only strategy is no longer the complete test inventory.

| Implemented area | Representative executable test coverage | Evidence boundary |
| --- | --- | --- |
| M6 / M7 forward imaging | `DicomSeriesServiceTest`, `ImageVolumeTest`, `NiftiAffineMapperTest`, `DicomToNiftiServiceTest`, `Nifti1VolumeWriterTest` | Supported conventional MR profile; historic independent NiBabel and selected real-MRI checks |
| F1–F8 foundations and verification | `JsonManifestCodecTest`, `ManifestStoreTest`, `VerifiedConversionEvidenceServiceTest`, `NiftiOutputComparatorTest` | Restricted manifest, source/output identity, reopened comparison and publication semantics; F8 formal acceptance separately pending |
| F9 / FBO DICOM → NIfTI | `F9HeadlessConversionServiceTest`, `F9HeadlessIntegrationTest`, `FboCommandIntegrationTest`, `FboDirectoryPreflightTest` | Selected supported series and qualified sequential batch; F9 formal acceptance separately pending |
| **M8 NIfTI → derived MR DICOM** | `M8ConversionIntegrationTest`, `M8DicomEncodingTest`, `M8GeometryTest`, `M8RecoveryTest`, `M8CommandTest` | Implemented independent 3D integer NIfTI-1 conversion; separate formal M8 acceptance is not inferred |
| EBO-1 R2 reverse evaluation batch | `EboCommandTest`, `EboBatchControllerTest`, `EboAcceptanceClosureTest` | Accepted under the bounded non-clinical EBO-1 R2 evaluation contract |
| **M10 / GUI-I1 desktop** | `GuiI1ScannerTest`, `GuiI1ExecutionTest`, `GuiI1AutomaticDiscoveryTest`, `GuiI1DicomPreparationTest`, `GuiI1WorkflowE1Test` | Implemented Swing/FlatLaf workflow with backend-authoritative dispatch; separate F10/F11 foundation acceptance is not implied |
| Destination and recovery guards | `DestinationQualificationTest`, `JsonManifestStoreD1DestinationTest`, `M8WorkspaceTest`, `FboDirectoryPreflightTest` | Windows/local filesystem and failure controls are conditional on platform, permissions and capabilities |

**Current reported integrated regression:** PR #1 records **961/961 tests
passing (zero failures, errors or skips)** on an **elevated native Windows
workstation**, with provisioned filesystem fixtures and
`java.io.tmpdir=C:\PT`, `d1.test.destination=C:\PD`. A targeted mixed-series
GUI conversion test also passed. This is **user-provided workstation
evidence**, not a newly executed test, a GitHub-hosted CI run, or proof of
ordinary-user Windows/native Linux GUI qualification.

The M7 **129-test** (2026-10-03), F8 **795-test** and EBO-1 R2 **888-test**
results are **historical checkpoint-specific evidence**; they must not be
presented as today's full integrated regression count. The separate F8/F9/M8
formal acceptance obligations, F10/F11 foundation workstreams, comprehensive
bidirectional round-trip acceptance and the global Pre-M8 gate remain
governed by the [canonical tracker](../planning/PRE-M8-FOUNDATION-TRACKER.md).
**M10 (the desktop GUI milestone) is not F10 (foundation boundary
reconciliation).** This documentation update does not execute Maven or
change acceptance decisions.

## 1. Purpose

Correctness spans both implemented conversion directions: supported DICOM
reconstruction and NIfTI output, independent bounded NIfTI-to-derived-DICOM
generation, provenance-backed verification/publication, sequential batch
orchestration and the Swing desktop workflow. In each route, tests must
establish supported voxel and geometry semantics, explicit rejection of
unsupported inputs, and non-verified handling of uncertain publication.
[Architecture 1.0](../architecture/ARCHITECTURE-REVISION-1.0.md) and
later approved scope-specific contracts govern their respective obligations.

Executable JUnit tests establish only the cases actually asserted. This
document began as an M6-focused strategy; sections 4–6 retain dated M6
fixture details, not a current exhaustive gap inventory. The current
integrated test mapping appears above and in §8; [test results](TEST-RESULTS.md)
separate executed evidence by exact source checkpoint and environment.

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
`Dcm4cheInstanceReader`: selection, compatibility, assembly, raw values,
rescale, ordering, geometry rejection and stable errors.
`FormatDetectionServiceTest` exercises format probing.
Additional implemented routes have separate suites for F9 headless
verification, FBO sequential batch, M8 conversion/verification, EBO
evaluation batch and GUI execution/discovery; see the current mapping
and §8. These suites are not interchangeable with an end-to-end
cross-direction round-trip acceptance test.

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

`DependencyDirectionTest`, `PackageStructureTest`,
`PresentationBoundaryTest`, `M6BoundaryTest`,
`FoundationContractBoundaryTest`, `F9HeadlessBoundaryTest`,
`M8BoundaryTest` and `VerifiedConversionBoundaryTest` enforce
selected dependency and contract boundaries. These executable checks
are not a proof that every class, method body, nested generic argument
or Architecture Revision 1.0 rule is statically enforced.
Preserve earlier invariant tests when updating the boundary architecture.

### Regression tests

Geometry (`32b4bc1`) and allocation (`fc1ad46`) regressions have recorded pre-fix
failures in [DEVELOPMENT_LOG.md](../../DEVELOPMENT_LOG.md). The [results audit](TEST-RESULTS.md)
separates historical records from fresh runs. Do not infer failures from test names.

## 4. Historical M6 DICOM Geometry Test Strategy

At the historical M6 fixture checkpoint, tests covered mandatory/unknown series, mixed studies, duplicate SOPs,
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

The original M6 audit flagged dedicated nonfinite/unit/orthogonality,
in-plane displacement, conflicting `InstanceNumber`,
`FrameOfReferenceUID`/anatomical orientation and single-slice-policy
tests for further review. Those are **historical audit observations**,
not a verified claim that every gap remains in the merged suite.
Shuffled-file ordering alone is not an explicit `InstanceNumber` test.

## 5. Historical M6 Pixel / Voxel Test Strategy

At the M6 checkpoint, DICOM assertions included unsigned 16-bit 65535, signed 12-bit -2048/-1,
BitsStored/HighBit metadata, rescale declaration/slope, slice placement and three
approved syntaxes. RGB/multiple samples and deflated syntax are rejected.
The original M6 audit listed dedicated 8-bit boundaries, missing Pixel
Data, payload-length failures, invalid HighBit, rescale mismatch/partial
declarations and multiframe fixtures as gap candidates. Their **present**
coverage needs a source-level assertion inventory, not a blanket
unimplemented claim. The supported M6 reconstruction path does not apply
rescale metadata to stored samples.

Allocation regressions cover int wraparound, counts above int/long capacity,
nonpositive dimensions and length mismatch using tiny arrays. Checked counts do
not guarantee sufficient heap for every theoretically valid volume.

## 6. Historical Format Detection Fixture Strategy

Tests cover DICOM correct/missing/wrong extensions and preamble-less input;
NIfTI-1/2 magic/header length and big-endian recognition; gzip NIfTI versus unrelated
gzip; corrupt/truncated input; image-pair rejection; invalid/missing paths,
directories, empty/unknown content and the 1 MiB DICOM detection budget.

The budget test asserts UNKNOWN/INPUT_TOO_LARGE, not rejection by the full reader.
The original format-detection audit separately flagged a valid >1 MiB
reconstruction case and uncalled field-validation helpers. Those notes
apply to the **detection subsystem at that checkpoint** and must not be
misread as saying that later `Nifti1VolumeReader` field, geometry,
bounds and gzip tests are absent. The 16 MiB decompression budget and
`INVALID_GZIP` mapping cited above were recorded for the historical
detector; verify the relevant implementation before claiming the same
limit for another reader or workflow.

## 7. Test Data

Synthetic unit and integration suites use `@TempDir`, generated DICOM
Attributes, byte buffers and gzip streams; they do not require real
patient data or an external imaging installation. Ordinary fixture UIDs
may use clock/random generation when assertions do not depend on their
exact values; geometry-reference regressions fix relevant UIDs.

Separately, historic bounded **real-MRI** and independent NIfTI-parser
checks have been reported, including a selected 15-image slab and
NiBabel interoperability. These are **not** the synthetic JUnit
fixtures and are not equivalent to unrestricted real-volume or
clinical validation. Preserve input identities and private evidence
outside the repository. Never commit DICOM studies, imaging outputs,
patient data, local execution logs or secrets.

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
  selected-15-image real-MRI evidence (at the relevant M6/M7/F7/F9
  checkpoints) must not be presented as validation of arbitrary DICOM
  series, an entire study or unrestricted round-trip equivalence.

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

## 9. Commands (Java 21; execute on the target workstation)

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

The commands above are **reproduction examples**, not assertions that
they were run during this documentation update. Run a full clean suite
under the exact environment required by the relevant acceptance gate.
On native Windows, prepare supported NTFS test destinations and
privileges; the reported PR #1 full run was elevated and used the
short-path fixture properties listed above. Do not equate a passing
subset, or an environment-dependent skip, with the qualified full run.

For historical reproduction, use `git archive` in a temporary
directory and `mvn clean test -f <export>/pom.xml`; do not reset a user's
working tree. Read Surefire XML only after fresh execution, recording
the exact Git tree, Java/Maven versions, operating system, command,
fixture paths, privileges, counts and any skips. Stale reports do not
prove success.

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
Record development-log changes only after the corresponding activity
has actually occurred. Documentation-only PR changes may be committed
for review, but such commits are **not** executable test evidence or
a formal acceptance authorization.
