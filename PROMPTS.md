# AI Prompt Log

AI tools are engineering assistants; the human project owner retains architecture
and review authority. Important prompts are preserved for reproducibility and
CS5013 assessment. Structured prompts specify context, objective, constraints,
verification and explicit negative prompting. Generated changes remain subject to
human review and automated tests.

This index supplements, without rewriting, [ai/PROMPT_REGISTRY.md](ai/PROMPT_REGISTRY.md)
and immutable versioned prompts in `ai/`. IDs below are audit cross-references
assigned on 2026-10-01; they were not necessarily used when historical tasks ran.
They do not rename the original `M6-DICOM-PROCESSING` or `QWEN-ONBOARDING` IDs.
Dates for summaries are checkpoint dates, not invented prompt issuance dates.
Unknown original prompts/models/commands are labeled unavailable.

## Traceability

| Stage | Requirement / task | Prompt ID | Tests / evidence | Implementation or artifact | Commit | Status |
|---|---|---|---|---|---|---|
| M1-M4 | Environment, dependencies, initial boundaries | Historical prompts unavailable | MainTest, smoke tests, initial architecture tests | pom.xml, initial contracts | `63fff6d`, `8e1580b`, `cf9013d`, `7e50a9b` | Scoped milestones COMPLETE |
| M5 | Content-based recognition | M5-P01 | FormatDetectionServiceTest | FormatDetectionService, DicomFormatProbe, NiftiFormatProbe | `88da522` | Scoped task COMPLETE |
| M5.1 | Detection hardening | M5.1-P01 | rejectsGzipWithCorruptTrailer, oversizedDicomIsInspectedOnlyWithinTheDetectionBound | Detection probes | `1989589` | Scoped task COMPLETE; field-validation gap remains |
| Architecture | Preserve prompt provenance and approve contract | ARCH-P01 | Git/document review; no dedicated test | ai/, Architecture Revision 1.0 | `eb83cb1`, `f7e7ea2` | Documentation checkpoints COMPLETE |
| M6 baseline | Selected-series reconstruction | M6-P01 | DicomSeriesServiceTest (11), M6BoundaryTest; 35 total in fresh archive run | Dcm4cheInstanceReader, DefaultDicomSeriesService | `3c3d979` | Baseline task COMPLETE; M6 IN PROGRESS |
| M6 geometry | Common normal and final-grid residual | M6-P02 | Four named regressions below; 39 total in fresh archive run | DefaultDicomSeriesService.validateAndReconstruct | `32b4bc1` | Scoped fix COMPLETE |
| M6 allocation | Reject overflow before copying/reading voxels | M6-P03 | ImmutableVoxelDataTest | ImmutableVoxelData.checkedVoxelCount | `fc1ad46` | Scoped fix COMPLETE |
| Current governance | Document evidence through current M6 | M6-P04 | 48 tests pass; results in TEST-RESULTS.md | README, logs, testing docs, milestone status | Uncommitted by instruction | Governance task COMPLETE; M6 IN PROGRESS |

## Entry Convention

Use `<stage>-P<sequence>` for new entries. Record Date, Stage, Tool/Model only when
known, Repository baseline, Role, Context, Objective, Inputs / Relevant Files,
Requirements, Constraints, Negative Prompt, Verification, Result, Related Tests,
and Related Commit. Preserve verbatim prompts by reference where available; label
any extracted summary. Never imply an archived specification proves execution.

For future prompts, state task-specific DO NOT constraints: unrelated changes,
package moves, later milestones, dependency additions, suppressing failures,
weakening validation, deleting/disabling regressions, changing approved architecture,
and automatic commits without authorization. This convention is new governance,
not a claim that every historical prompt contained these words.

## M5-P01 — Content-based detection

**Evidence type:** Historical task summary — original verbatim prompt unavailable.
**Date:** 2026-08-26 (development log). **Stage:** M5.
**Repository baseline:** `270225e` (parent of implementation commit). **Tool/Model:** unavailable.

Git and the development log record DICOM, NIfTI and gzip recognition via
`FormatDetectionService` and infrastructure probes, plus extension/error fixtures.
Original role, detailed prompt constraints and requested commands are unavailable.
Recorded verification: `mvn clean test`, 16 tests, zero failures/errors; historical
skipped count not preserved in that entry. Related tests: `detectsDicomWithoutExtension`,
`detectsNifti1AndNifti2`, `handlesMissingPathAndDirectory` in `FormatDetectionServiceTest`.
Related commit: `88da522` — `feat: implement format detection`.

## M5.1-P01 — Detection hardening

**Evidence type:** Historical task summary — original verbatim prompt unavailable.
**Date:** 2026-08-26 (development log). **Stage:** M5.1.
**Repository baseline:** `d831192` (Git parent). **Tool/Model:** unavailable.

Recorded work includes bounded DICOM probing, gzip EOF/trailer checks, preamble-less
parsing, pair-header rejection and stable diagnostics. Original prompt constraints
are unavailable. Related tests include `rejectsGzipWithCorruptTrailer`,
`rejectsNiftiImagePairHeaders`, `detectsPreambleLessDicomThroughDcm4che` and
`oversizedDicomIsInspectedOnlyWithinTheDetectionBound`.
Recorded executions: `mvn -Dtest=FormatDetectionServiceTest test` (16) and
`mvn clean test` (23), zero failures/errors; skips unavailable in that entry.
Related commit: `1989589` — `fix: harden format detection`; log checkpoint `b1fb3d1`.
Audit correction: helpers `validNifti1Fields`/`validNifti2Fields` are uncalled;
the historical log's field-invariant claim is not evidence of active validation.

## ARCH-P01 — Prompt provenance and architecture approval

**Evidence type:** Historical task summary — original verbatim approval prompt unavailable.
**Dates:** 2026-08-27 (`eb83cb1`); 2026-10-01 (`f7e7ea2`). **Stage:** Architecture.
**Repository baselines:** `b1fb3d1` before provenance; `eb83cb1` before approval.

`eb83cb1` preserves onboarding and M6 prompts naming Qwen 30B MoE as intended agent;
that does not identify the author of every later change. `f7e7ea2` adds the approved
Version 1.0.0 contract. Sections 22-24 distinguish AR-0 repair, AR-1 refactor and
M6 acceptance. No original approval dialogue or independent-audit transcript is
preserved in Git. Verification here is document/Git inspection, not a historical
test-run claim. Related commits: `docs: establish AI prompt provenance` and
`docs: approve architecture revision 1.0`. No dedicated executable approval test.

## M6-P01 — Reconstruction specification and verified baseline

**Evidence type:** Structured extraction from a preserved prompt; not a replacement quotation.
**Date:** 2026-08-27 (original prompt); baseline checkpoint 2026-10-01. **Stage:** M6.
**Tool/Model:** Qwen 30B MoE is the original prompt's named agent.
**Repository baseline:** `b1fb3d1` in the prompt; `f7e7ea2` is parent of `3c3d979`.

### Role

Implementation/testing agent per [onboarding](ai/onboarding/QWEN-ONBOARDING-v1.0.0.md).

### Context

Original [M6-DICOM-PROCESSING v1.0.0](ai/milestones/M6-DICOM-PROCESSING-v1.0.0.md)
predates approved Architecture 1.0. Its blanket infrastructure-only processing
constraint is historical; the approved contract assigns orchestration to application.

### Objective

Reconstruct one explicitly selected conventional single-frame MR series.

### Inputs / Relevant Files

Original versioned prompt; reader/service, voxel/geometry models and synthetic tests
introduced in `3c3d979`.

### Requirements

Physical IOP/IPP ordering, native coordinates, raw `long` values, encoding/rescale
semantics, approved transfer syntaxes, stable errors, privacy-safe provenance,
synthetic/adversarial fixtures and verified checkpoints.

### Constraints

Single selected series; limited MR profile. The approved architecture supersedes
conflicting historical package instructions.

### Negative Prompt

The preserved prompt explicitly forbids normalization, resampling, interpolation,
LPS/RAS conversion and additional DICOM libraries.

### Verification

The milestone prompt requires passing tests and geometry/pixel/error checks;
onboarding requires `mvn clean test`. Fresh isolated reproduction of `3c3d979` used
`mvn clean test -f <export>/pom.xml` on 2026-10-01.

### Result

Fresh reproduction: BUILD SUCCESS, 35 tests, zero failures/errors/skips. The commit
repairs the provenance constructor shape and adds a coherent baseline. This supports
the AR-0 build repair, not a claim that every AR-0 audit step or M6 criterion is done.

### Related Tests

`requiresExplicitSeriesSelectionAndReportsUnknownSeries`,
`ordersShuffledSlicesByProjectedPatientPositionAndPlacesVoxels`,
`preservesSignedStoredValuesAndBitsStoredSemantics`,
`supportsAllApprovedUncompressedTransferSyntaxes`, and `M6BoundaryTest`.

### Related Commit

`3c3d979` — `feat: establish verified M6 reconstruction baseline`.

## M6-P02 — Common-normal projection and whole-grid residual

**Evidence type:** Historical task summary — original verbatim prompt unavailable.
**Date:** 2026-10-01 (Git/log). **Stage:** M6. **Tool/Model:** unavailable.
**Repository baseline:** `3c3d979`.

### Role

Historical agent role unavailable; the evidenced task was geometry correctness.

### Context

The development log records reversed order, input-order-dependent rejection and
accepted cumulative grid drift before the fix.

### Objective

Use one deterministic reference normal for all projections and validate the final
regular grid, not only local gaps (architecture section 7).

### Inputs / Relevant Files

`DefaultDicomSeriesService.java`, `DicomSeriesServiceTest.java`, development log.

### Requirements

Choose reference orientation by lexical SOP UID; use the common normal for order,
duplicate/spacing checks and volume assembly; reject excess whole-grid residual.

### Constraints

Two geometry issues only, as recorded in the log; retain first-gap spacing.

### Negative Prompt

Exact historical wording unavailable. The log explicitly defers single-slice policy,
overflow, FrameOfReference/BIPED, detection, provenance and AR-1; it records no commit
being created during that task. The subsequent Git checkpoint is listed below.

### Verification

Log evidence: `mvn -Dtest=DicomSeriesServiceTest clean test` before fix: 15 tests,
3 failures, zero errors/skips. Post-fix focused run: 15 pass; `mvn clean test`: 39 pass.
Fresh archive rerun here independently confirms 39, zero failures/errors/skips.

### Result

Scoped geometry fix COMPLETE; M6 IN PROGRESS. `validateAndReconstruct` now passes
the validated normal and grid spacing into assembly.

### Related Tests

- `ordersSmallOrientationVariationsUsingOneCommonNormal`
- `geometryAndVoxelsAreInvariantUnderInputPermutation`
- `rejectsCumulativeDriftDespiteLocallyRegularGaps`
- `acceptsRegularGridWithPositionNoiseWithinTolerance`

### Related Commit

`32b4bc1` — `fix: harden M6 physical slice geometry`.

## M6-P03 — Voxel allocation overflow

**Evidence type:** Historical task summary — original verbatim prompt unavailable.
**Date:** 2026-10-01 (Git/log). **Stage:** M6. **Tool/Model:** unavailable.
**Repository baseline:** `32b4bc1`.

The task corrected unchecked dimension multiplication in `ImmutableVoxelData` with
checked int arithmetic before array cloning. Original role/negative-prompt wording
is unavailable; the log limits work to allocation and defers detection, provenance,
FrameOfReference/BIPED and AR-1. Nine tests use tiny arrays/virtual voxels.
`rejectsCountThatWrapsToZero`, `rejectsCountThatWrapsToSmallPositiveLength` and
`rejectsCountBeyondEvenLongCapacity` expose the corrected wraparound cases.
Recorded pre-fix `mvn -Dtest=ImmutableVoxelDataTest clean test`: 9 tests, 3 failures,
zero errors/skips. Post-fix focused run: 9 pass; full `mvn clean test`: 48 pass,
zero failures/errors/skips. See TEST-RESULTS.md for fresh current verification.
Related commit: `fc1ad46` — `fix: harden voxel allocation validation`. Scoped task COMPLETE.

## M6-P04 — Documentation, test governance and AI provenance

**Evidence type:** Structured summary of the current user-supplied request.
**Date:** 2026-10-01. **Stage:** M6. **Tool:** Codex; model not recorded here.
**Repository baseline:** `fc1ad46`.

### Role

Documentation, testing-governance and AI-provenance engineer for CS5013.

### Context

The supplied request asks for an auditable account through current M6. Initial
status has only three pre-existing untracked files: `.roomodes` and architecture
RC2/RC3. No unfinished tracked source/test edits were present.

### Objective

Establish the seven requested artifacts, preserve valid history and make current
scope, test evidence, missing prompts and remaining acceptance gaps explicit.

### Inputs / Relevant Files

Git history/diffs, pom.xml, PROJECT.md, MILESTONES.md, README, DEVELOPMENT_LOG,
approved architecture, ai/ records, complete test tree and production source.
The request arrived as `Pasted text.txt`; this entry preserves its structured
engineering summary, not an asserted verbatim copy.

### Requirements

Inventory actual tests; distinguish historical evidence from fresh runs; review
JavaDoc read-only; retain M6 IN PROGRESS unless every acceptance criterion is met;
return an artifact/verification/traceability report and suggested commit message.

### Constraints

Keep approved architecture technical content unchanged and preserve all user work.
Correct stale milestone metadata using verified evidence. No invented history.

### Negative Prompt

DO NOT fabricate tests/results/commits/dates/prompts; start AR-1; move packages;
introduce ImageVolume; redesign encoding/provenance; implement conversion or GUI;
add dependencies; remove/disable tests; weaken assertions/tolerances; overwrite
unfinished work; stage; commit; reset/clean/restore user work; delete the three
pre-existing untracked files.

### Verification

Requested: `mvn clean test`, `git status --short`, `git diff --stat`, `git diff`,
`git diff --check`. Also reproduce the 35/39-test checkpoints from isolated Git archives.

### Result

See the dated governance entry in [DEVELOPMENT_LOG.md](DEVELOPMENT_LOG.md) and
[TEST-RESULTS.md](docs/testing/TEST-RESULTS.md) for executed outcomes. Production,
tests and approved architecture are preserved; M6 remains IN PROGRESS.
Fresh `mvn clean test`: BUILD SUCCESS, 48 tests, 0 failures, 0 errors, 0 skipped.
Diff/whitespace and local documentation link checks passed. Governance task COMPLETE.

### Related Tests

All existing classes, inventoried by class and method in TEST-RESULTS.md.
No speculative or count-inflating tests added.

### Related Commit

None: this task explicitly prohibits staging and committing.

## M6-P05 — Format-detection evidence-budget hardening

**Evidence type:** Structured summary of the user-supplied hardening request.
**Date:** 2026-10-01 (task and verification); entry recorded 2026-10-02.
**Stage:** M5/M6. **Tool:** Codex.
**Repository baseline:** `ba13b10`; the supplied prompt named the earlier `fc1ad46`.

### Role

Perform focused format-detection correctness hardening under approved Architecture 1.0.

### Context

DICOM probing treated its 1 MiB evidence budget as a total-file size limit.
An inconclusive probe could suppress later probes. Gzip NIfTI probing drained
the payload and classified exceeding its 16 MiB decompressed cap as corruption.

### Objective

Recognize large supported inputs from bounded identifying evidence, continue
after inconclusive probes, and distinguish budget exhaustion from corruption.

### Inputs / Relevant Files

FormatDetectionService, DicomFormatProbe, NiftiFormatProbe, DetectionDiagnostic,
FormatDetectionServiceTest and the approved architecture contract.

### Requirements

Add regressions before production fixes; preserve preamble-less DICOM and small
valid input recognition; read only the required NIfTI header; inspect existing
datatype helpers and change them only if needed by the active detection path.

### Constraints

Use deterministic synthetic temporary fixtures and bounded reads. Reuse existing
result types and keep changes local. Recognition does not certify payload integrity.

### Negative Prompt

DO NOT start conversion or AR-1; move packages; introduce ImageVolume; redesign
PixelEncoding; modify reconstruction geometry, allocation, provenance, GUI or
FrameOfReferenceUID/BIPED logic; add dependencies; weaken or disable tests;
overwrite user work; stage or commit. Leave unused datatype helpers deferred.

### Verification

- Pre-fix: `mvn "-Dtest=FormatDetectionServiceTest" clean test` — 24 tests,
  6 failures, 0 errors, 0 skipped.
- Post-fix: `mvn "-Dtest=FormatDetectionServiceTest" test` — BUILD SUCCESS;
  24 tests, 0 failures, 0 errors, 0 skipped.
- Full: `mvn clean test` — BUILD SUCCESS; 56 tests, 0 failures, 0 errors, 0 skipped.
- `git diff --check` passed.

### Result

Scoped hardening COMPLETE; M6 remains IN PROGRESS. DICOM uses bounded metadata
parsing, UNKNOWN outcomes allow later probes, and NIfTI reads at most its 348/540
header bytes. The directly conflicting trailer-integrity expectation was explicitly
updated for header-only detection. Unused NIfTI validators remain deferred.
Detailed failure evidence and limitations are in [DEVELOPMENT_LOG.md](DEVELOPMENT_LOG.md).

### Related Tests

- `detectsLargePart10DicomFromEarlyMetadata`
- `continuesToLaterProbeAfterInsufficientEvidence`
- `detectsLargeNiftiAfterDicomProbe`
- `detectsLargeGzipNiftiWithoutDrainingPayload`
- `largeUnrelatedFileIsUnknownRatherThanSizeFailure`
- `rejectsTruncatedGzipNiftiHeader`
- `distinguishesTruncatedDicomMetadataFromProbeExhaustion`

### Related Commit

Pending. The hardening changes are uncommitted; the recorded verification is for
the working tree based on `ba13b10`.

## M6-P06 - Selected-series frame and BIPED profile hardening

**Evidence type:** Structured summary of the current user-supplied task, not a
verbatim transcript. **Date:** 2026-10-02. **Tool:** Codex.
**Repository baseline:** `cacec3a`.

### Role

Perform focused M6 DICOM reconstruction-profile hardening under Architecture 1.0.

### Context

The reader/model omitted frame identity and anatomical orientation; reconstruction
could combine different frames or interpret QUADRUPED data as BIPED.
The preceding detection work was committed and tracked files were clean.

### Objective

Require one nonblank FrameOfReferenceUID across selected slices and effective
BIPED orientation, treating absent/blank anatomy as BIPED.

### Relevant Files

DicomInstance, Dcm4cheInstanceReader, DefaultDicomSeriesService,
DicomSeriesServiceTest, ImmutableVoxelDataTest (constructor fixture only),
DEVELOPMENT_LOG.md and docs/testing/TEST-RESULTS.md.

### Requirements

Add regressions before production edits; carry both nullable attributes through
the current model; use dcm4che Tag constants; validate after series selection and
before service geometry; reject missing/mixed frames and unsupported anatomy;
preserve unrelated candidate-series isolation and use existing error categories.

### Constraints

Use tiny synthetic temporary DICOM fixtures and the existing reader/test framework.
Preserve null attributes in the reader; apply the BIPED default in the application.
Keep case-sensitive BIPED handling and exact nonblank frame UID comparison.

### Negative Prompt

Do not implement full MR IOD validation, QUADRUPED conversion, synthetic frame UIDs,
AR-1, NIfTI conversion, provenance redesign, GUI changes, dependencies, package moves,
or changes to geometry, allocation or detection algorithms. Do not weaken tests,
edit approved architecture, stage files, or commit.

### Verification

- Pre-fix `mvn -Dtest=DicomSeriesServiceTest test`: BUILD FAILURE;
  25 tests, 6 failures, 0 errors, 0 skipped.
- Final focused same command: BUILD SUCCESS; 25/0/0/0.
- `mvn clean test`: BUILD SUCCESS; 66/0/0/0.

### Result

Scoped frame/profile hardening COMPLETE; M6 remains IN PROGRESS. Nullable attributes
are preserved, selected-series validation rejects unsupported profiles, and real
reader extraction is covered. Results expose existing categories; explicit exception
messages are not surfaced by the unchanged result API.

### Related Tests

- acceptsConsistentFrameOfReferenceUid
- rejectsMissingFrameOfReferenceUid
- rejectsBlankFrameOfReferenceUid
- rejectsMixedFrameOfReferenceUids
- acceptsExplicitBipedOrientation
- acceptsMissingAnatomicalOrientationTypeAsBiped
- acceptsBlankAnatomicalOrientationTypeAsBiped
- rejectsQuadrupedOrientation
- rejectsMixedBipedAndQuadrupedOrientations
- rejectsUnknownAnatomicalOrientationType
- acceptsMultipleSuppliedSeriesOnlyWhenRequestedSeriesIsExplicit (strengthened)
- rejectsOversizedReconstructionBeforeReadingVoxels (fixture signature only)

### Related Commit

Pending at verification time.

## M6-P07 - Single-Slice Spacing Policy Hardening

**Evidence type:** Structured summary of the current user task, not a verbatim
historical transcript. **Date:** 2026-10-02. **Baseline:** `5c9b3ee`.

### Role

Perform focused M6 geometry/profile hardening under approved Architecture 1.0.

### Context

One-slice reconstruction published 0.0 spacing; neither spacing metadata field
was available in the current reader/model. Multi-slice spacing was IPP-derived.

### Objective

Use positive finite SpacingBetweenSlices, else SliceThickness only when the
primary is absent, else fail. Thickness is a one-slice surrogate, not measured spacing.

### Inputs

DicomInstance, Dcm4cheInstanceReader, DefaultDicomSeriesService, VolumeGeometry,
DicomSeriesServiceTest, ImmutableVoxelDataTest, architecture and engineering logs.

### Requirements

Test first; preserve absent versus invalid metadata; reject invalid explicit
primary values without fallback; retain IPP-derived multi-slice spacing and
single-slice origin/directions; exercise actual reader extraction and non-finite
values at the service boundary. Update verified traceability.

### Constraints

Use nullable Double fields, existing error categories and tiny temporary fixtures.
Keep the policy in the service and changes local. Preserve existing assertions.

### Negative Prompt

Do not invent spacing, alter multi-slice geometry, frame/BIPED checks, allocation,
detection, provenance, dependencies or packages. Do not start AR-1, conversion,
affine work or full IOD validation. Do not modify approved architecture, stage or commit.

### Verification

- Before production edits: focused 33 tests, 7 failures, 0 errors, 0 skipped.
- After model-only wiring, before extraction/policy: focused 36/10/0/0.
- Final `mvn -Dtest=DicomSeriesServiceTest test`: BUILD SUCCESS, 36/0/0/0.
- `mvn clean test`: BUILD SUCCESS, 77/0/0/0.

### Result

Scoped hardening COMPLETE; M6 remains IN PROGRESS. Single-slice successes have
positive finite spacing; multi-slice IPP spacing remains authoritative.
Absent tags remain null; present-empty/unparsable tags remain invalid as NaN.

### Related Tests

- `acceptsSingleSliceWithSpacingBetweenSlices`
- `prefersSpacingBetweenSlicesOverSliceThickness`
- `acceptsSingleSliceWithSliceThicknessFallback`
- `rejectsSingleSliceWithoutSpacingMetadata`
- `rejectsNonPositiveSingleSliceSpacingBetweenSlices`
- `rejectsInvalidSliceThicknessFallback`
- `multiSliceSpacingStillComesFromIpp`
- `rejectsEmptyOrMalformedExplicitSingleSliceSpacing`
- `rejectsNonFiniteSingleSliceSpacingBetweenSlices`
- `rejectsNonFiniteSliceThicknessFallback`
- `readerExtractsSingleSliceSpacingMetadata`
- `rejectsOversizedReconstructionBeforeReadingVoxels`: fixture signature only.

### Related Commit

Pending at verification time.

## M6-P08 - Provenance Source Identity Hardening

**Evidence type:** Structured summary of the current user request, not a verbatim
historical transcript. **Date:** 2026-10-02. **Baseline:** `06c9b69`.

### Role

Perform focused M6 provenance-identity hardening under approved Architecture 1.0.

### Context

inputFingerprint hashed every candidate path in request order, not file content.
Relocation and permutations changed identity; selected byte changes did not.

### Objective

Identify selected DICOM sources by logical SOP UID and raw source content,
independent of paths, candidate order and unselected candidate inputs.

### Relevant Files

DefaultDicomSeriesService, application/provenance/SelectedSourceFingerprint,
DicomSeriesServiceTest, DEVELOPMENT_LOG.md, docs/testing/TEST-RESULTS.md, README.md.

### Requirements

Test first; stream SHA-256 source bytes; sort selected sources lexically by SOP UID;
aggregate records framed as 4-byte big-endian UTF-8 UID length, UID bytes and
32-byte content digest; emit lowercase SHA-256 hex. Preserve duplicate rejection.
Fail if any selected source cannot be read; never publish a subset digest.

### Constraints

Reuse existing InputSource associations; keep hashing application-owned. Use JDK
APIs and bounded buffers. Preserve all other provenance fields and medical-image
processing behavior. Failure identity is unavailable (null).

### Negative Prompt

Do not hash paths/order/timestamps, load entire files for hashing, include
unselected candidates, redesign provenance, inject Clock, add dependencies,
alter geometry/frame/BIPED/spacing/detection/allocation, start AR-1/conversion,
change GUI or approved architecture, weaken tests, stage or commit.

### Verification

- Pre-fix `mvn -Dtest=DicomSeriesServiceTest test`: BUILD FAILURE;
  43 tests, 6 failures, 0 errors, 0 skipped.
- Final focused same command: BUILD SUCCESS; 43/0/0/0.
- `mvn clean test`: BUILD SUCCESS; 84/0/0/0.

### Results

Scoped identity hardening COMPLETE; M6 remains IN PROGRESS. The old source-path
hash is replaced by canonical selected UID/content identity; source I/O failures
return a structured failure with no fingerprint. Sources must remain stable
during the existing parse/hash workflow; atomic snapshots are outside this task.

### Related Tests

- `fingerprintIsIndependentOfInputOrder`
- `fingerprintIsIndependentOfSourcePath`
- `fingerprintChangesWhenSelectedSourceContentChanges`
- `unselectedCandidateContentDoesNotAffectSelectedFingerprint`
- `fingerprintHasCanonicalSha256Encoding`
- `knownCanonicalAggregationFixture`
- `failsWithoutPartialFingerprintWhenSelectedSourceCannotBeRead`

### Related Commit

Pending at verification time.

## M7-P01 - Format-Neutral ImageVolume Migration

**Evidence type:** Current structured implementation task. **Date:** 2026-10-02. **Baseline:** `cb7769e` on `feature/m7-dicom-to-nifti`.

### Role

Perform the minimum architecture migration required before DICOM-to-NIfTI conversion.

### Context

The existing `NativeVolume` was close to a generic image aggregate, but `VoxelData.encoding()` exposed DICOM `PixelEncoding`. This made the future NIfTI writer depend on DICOM storage semantics.

### Objective

Create a format-neutral `ImageVolume` boundary with generic scalar semantics while preserving all verified M6 reconstruction behavior.

### Requirements

- introduce `ImageVolume`;
- introduce `ScalarType` = UINT8, INT8, UINT16, INT16;
- expose `VoxelData.scalarType()` rather than `PixelEncoding`;
- retain DICOM `PixelEncoding` separately on DICOM metadata;
- preserve raw decoded values, geometry, x-fastest indexing and linear intensity semantics;
- migrate `DicomProcessingResult` to `ImageVolume`;
- remove the parallel `NativeVolume` aggregate;
- add architecture regression coverage preventing DICOM encoding leakage.

### Negative Prompt

Do not implement the NIfTI writer, affine conversion, qform/sform, package-wide AR-1 cleanup, reverse conversion, GUI work, new dependencies, value resampling, voxel reordering, or changes to validated M6 geometry/profile/provenance behavior.

### Verification

- `git diff --check`: clean.
- `mvn clean test`: BUILD SUCCESS; 92 tests, 0 failures, 0 errors, 0 skipped.
- `DicomSeriesServiceTest`: 47/0/0/0.
- `ImageVolumeTest`: 3/0/0/0.
- `ImmutableVoxelDataTest`: 9/0/0/0.
- Architecture tests: 6/0/0/0.

### Related Commit

`1dd31fc` - `refactor: introduce format-neutral image volume`

## M7-P02 - DICOM LPS to NIfTI RAS Affine

**Evidence type:** Current structured implementation task. **Date:** 2026-10-02. **Baseline:** `1dd31fc` on `feature/m7-dicom-to-nifti`.

### Role

Implement and verify the explicit spatial-affine boundary required before NIfTI serialization.

### Context

M7-P01 established a format-neutral `ImageVolume`. The approved architecture requires an immutable 4x4 voxel-to-world affine and an explicit DICOM patient LPS to NIfTI RAS conversion before writing NIfTI output.

### Objective

Represent voxel-to-world geometry explicitly and convert the validated DICOM LPS geometry to NIfTI RAS without changing voxel indexing, ordering, or values.

### Requirements

- introduce immutable `AffineMatrix4`;
- use column-vector affine semantics;
- preserve x=column index, y=row index, z=slice index;
- derive the LPS affine from `VolumeGeometry`;
- affine column 0 = column direction × column spacing;
- affine column 1 = row direction × row spacing;
- affine column 2 = slice direction × slice spacing;
- affine column 3 = voxel-(0,0,0) origin;
- convert LPS to RAS by left multiplication with `diag(-1,-1,1,1)`;
- do not flip or reorder voxel data;
- cover nonzero origins, unequal spacing, oblique geometry and representative voxel/world mappings;
- reject use of the DICOM-to-NIfTI mapper with unsupported source coordinate systems;
- record the affine policy in ADR-011.

### Negative Prompt

Do not implement NIfTI byte serialization, qform quaternion encoding, resampling, interpolation, voxel-axis permutation, reverse conversion, GUI work, or change validated DICOM reconstruction geometry.

### Verification

- `git apply --check`: clean for the corrected patch.
- `git diff --check`: clean after application.
- `mvn "-Dtest=AffineMatrix4Test,NiftiAffineMapperTest" test`: BUILD SUCCESS; 10 tests, 0 failures, 0 errors, 0 skipped.
- `mvn clean test`: BUILD SUCCESS; 102 tests, 0 failures, 0 errors, 0 skipped.
- `AffineMatrix4Test`: 5/0/0/0.
- `NiftiAffineMapperTest`: 5/0/0/0.
- Existing `DicomSeriesServiceTest`: 47/0/0/0.

### Results

M7-P02 affine mapping COMPLETE. DICOM reconstruction remains in patient LPS coordinates. NIfTI RAS conversion occurs explicitly at the conversion boundary, with no voxel-array reordering or resampling. NIfTI serialization remains pending.

### Related Commit

8fb2831 - feat: add nifti ras affine mapping

## M7-P03 - NIfTI-1 Volume Writer

**Evidence type:** Current structured implementation task. **Date:** 2026-10-02. **Baseline:** `8fb2831` on `feature/m7-dicom-to-nifti`.

### Role

Implement the project-owned NIfTI-1 serialization adapter required for the
DICOM-to-NIfTI vertical slice.

### Context

M7-P01 established a format-neutral `ImageVolume`. M7-P02 established explicit
voxel-to-world affine semantics and DICOM patient LPS to NIfTI RAS conversion.
The next boundary is deterministic serialization of a generic image volume and
an already-computed RAS affine into NIfTI-1 single-file output.

### Objective

Write supported integer `ImageVolume` data to `.nii` and `.nii.gz` while
preserving dimensions, raw voxel values, scalar representation, intensity
metadata and spatial geometry.

### Requirements

- introduce an application output port for NIfTI volume writing;
- implement a project-owned NIfTI-1 writer without adding a new dependency;
- accept an already-computed voxel-to-world RAS affine rather than performing
  DICOM coordinate conversion inside infrastructure;
- support `UINT8`, `INT8`, `UINT16`, and `INT16`;
- write the NIfTI-1 348-byte header plus extension indicator;
- use single-file `n+1` format with voxel data beginning at offset 352;
- serialize voxel data in x-fastest order without interpolation, resampling,
  flipping or permutation;
- preserve explicitly declared linear intensity transforms;
- preserve identity/no-declared-transform semantics;
- write millimetre spatial units;
- write scanner-anatomical sform metadata;
- write equivalent qform metadata where representable;
- use `qform_code = 0` rather than silently approximating an affine that cannot
  be represented by the NIfTI quaternion model;
- support `.nii` and gzip-wrapped `.nii.gz`;
- reject raw values outside the selected scalar type's legal range;
- verify generated `.nii` output with the existing NIfTI format detector.

### Architecture Correction

The initial writer draft performed `NiftiAffineMapper` conversion inside the
infrastructure adapter. This was corrected before commit.

The final boundary is:

`application geometry conversion -> RAS affine -> NiftiVolumeWriter port -> infrastructure serializer`

Therefore the infrastructure writer serializes a supplied RAS affine and does
not depend on the application conversion implementation.

### Verification

- Initial test compilation exposed a test-only API mismatch:
  `DetectionResult.isRecognized()` does not exist.
- The test was corrected to assert
  `DetectionOutcome.NIFTI` through `DetectionResult.outcome()`.
- `mvn "-Dtest=Nifti1VolumeWriterTest" test`: BUILD SUCCESS;
  11 tests, 0 failures, 0 errors, 0 skipped.
- `mvn clean test`: BUILD SUCCESS;
  113 tests, 0 failures, 0 errors, 0 skipped.
- The non-qform-representable affine regression verifies exact sform retention
  with `qform_code = 0`.

### Results

M7-P03 NIfTI-1 serialization COMPLETE. The writer is ready to be composed with
the verified DICOM reconstruction service and M7-P02 affine mapper. End-to-end
DICOM-to-NIfTI orchestration remains pending.

### Related Commit

6924dd5 - feat: add nifti-1 volume writer


## M7-P04 - End-to-End DICOM to NIfTI Conversion

**Evidence type:** Current structured implementation task. **Date:** 2026-10-02. **Baseline:** `6924dd5` on `feature/m7-dicom-to-nifti`.

### Role

Compose the verified DICOM reconstruction, spatial conversion and NIfTI writer
into one application-level DICOM-to-NIfTI use case.

### Objective

Convert an explicitly selected supported DICOM MR series directly to NIfTI-1
without resampling, interpolation, voxel reordering or unintended numerical change.

### Implementation

The implemented pipeline is:

`DicomToNiftiRequest -> DicomSeriesService -> ImageVolume -> NiftiAffineMapper -> NiftiVolumeWriter`

Added:

- `DicomToNiftiRequest`;
- `DicomToNiftiResult`;
- `DicomToNiftiService`;
- `DefaultDicomToNiftiService`;
- end-to-end `DicomToNiftiServiceTest`.

The application service delegates DICOM parsing/reconstruction to the existing
verified DICOM service, converts the resulting DICOM-LPS geometry to a NIfTI-RAS
affine and supplies both the generic image volume and the RAS affine to the NIfTI
writer.

### Verified Behaviors

- multi-slice DICOM to `.nii`;
- single-slice DICOM to `.nii.gz`;
- dimensions preserved;
- supported datatype preserved;
- complete raw voxel values preserved;
- voxel spacing preserved;
- declared linear scaling metadata preserved;
- DICOM LPS origin/orientation mapped to NIfTI RAS sform;
- input-file ordering does not alter the resulting NIfTI bytes;
- signed INT16 values remain numerically unchanged;
- DICOM reconstruction failures propagate as structured errors;
- failed reconstruction does not create an output NIfTI file.

### Negative Prompt

Do not add resampling, interpolation, normalization, GUI integration, reverse
NIfTI-to-DICOM conversion, dataset scanning, broad architecture refactoring or
new dependencies.

### Verification

- `mvn "-Dtest=DicomToNiftiServiceTest" test`: BUILD SUCCESS;
  5 tests, 0 failures, 0 errors, 0 skipped.
- `mvn clean test`: BUILD SUCCESS;
  118 tests, 0 failures, 0 errors, 0 skipped.

### Results

The first executable DICOM-to-NIfTI vertical slice is COMPLETE for the supported
profile. Output-I/O failure normalization, broader independent interoperability
validation and final M7 exit-criteria review remain separate hardening work.

### Related Commit

a495699 - feat: add end-to-end dicom to nifti conversion; c38ff6d - follow-up governance cleanup

## M7-P05 - Acceptance Hardening and Independent Interoperability

**Evidence type:** Hardening and independent acceptance validation. **Date:** 2026-10-02. **Baseline:** `c38ff6d` on `feature/m7-dicom-to-nifti`.

### M7-P05A - Fail-Safe Output Handling

The DICOM-to-NIfTI path was hardened so output-side failures are represented
separately from DICOM reconstruction failures and existing output files are never
silently overwritten.

Implemented:

- introduced `DicomToNiftiError` with `INVALID_OUTPUT`,
  `OUTPUT_ALREADY_EXISTS`, and `OUTPUT_WRITE_FAILED`;
- extended `DicomToNiftiResult` with structured conversion errors;
- conversion success now requires both DICOM and conversion error lists to be empty;
- output failures produce unsuccessful overall provenance;
- the NIfTI writer uses create-new semantics rather than overwrite semantics;
- an existing destination remains unchanged;
- a newly created partial output is deleted when serialization subsequently fails;
- invalid output targets and general write failures are normalized into structured
  application-level conversion errors.

### M7-P05A Verification

- focused hardening tests: 5 tests, 0 failures, 0 errors, 0 skipped;
- combined NIfTI writer/conversion regression: 21 tests, 0 failures, 0 errors, 0 skipped;
- `mvn clean test`: 123 tests, 0 failures, 0 errors, 0 skipped;
- BUILD SUCCESS.

### M7-P05B - Independent NIfTI Interoperability Validation

A temporary acceptance harness generated `.nii` and `.nii.gz` files through the
production `Nifti1VolumeWriter`. The generated files were then read independently
with NiBabel 5.4.2. NiBabel is not a Maven dependency and is not part of the
deployed Java application.

The synthetic validation volume used:

- dimensions: `2 x 2 x 2`;
- scalar type: `UINT16`;
- raw stored voxel values: `1..8`;
- column spacing: `0.5 mm`;
- row spacing: `0.75 mm`;
- slice spacing: `2.0 mm`;
- DICOM-LPS origin: `[10, 20, 30]`;
- declared intensity slope/intercept: `2.5 / -100`.

Independent validation confirmed:

- `.nii` parsed successfully;
- `.nii.gz` parsed successfully;
- shape `(2, 2, 2)`;
- datatype `uint16`;
- spacing `(0.5, 0.75, 2.0)`;
- exact raw voxel-array preservation;
- identical raw arrays between `.nii` and `.nii.gz`;
- equivalent spatial affines between `.nii` and `.nii.gz`;
- `qform_code = 1`;
- `sform_code = 1`;
- declared slope/intercept preserved;
- expected DICOM-LPS to NIfTI-RAS world mapping;
- affine comparisons within absolute tolerance `1e-5`.

### M7 Exit-Criteria State

The focused M7 acceptance evidence now verifies independent parsing, shape,
supported integer voxel preservation, spacing, world-coordinate mapping and
qform/sform consistency.

M7 remains IN PROGRESS because the architecture also requires explicit
conversion provenance/validation output. Reconstruction provenance is already
carried through `DicomToNiftiResult`; a dedicated conversion validation-report
model remains the final M7 acceptance item.

### Related Commit

M7-P05A: `2bf1927` - `fix: harden nifti output handling`

M7-P05B: validation-only acceptance evidence; no production-code change.
## M7-P05C - Conversion Validation Report and Final M7 Acceptance

**Evidence type:** Structured implementation and acceptance closure. **Date:** 2026-10-03. **Baseline:** `b6bab40` on `feature/m7-dicom-to-nifti`.

### Architectural Decision

ADR-014 established that M7 exposes provenance and conversion validation as
structured application-level models.

For M7:

- a successful DICOM-to-NIfTI conversion exposes a dedicated
  `ConversionValidationReport`;
- the report is created only after the NIfTI writer completes successfully;
- the report records factual conversion invariants known from the validated
  in-memory volume, explicit NIfTI-RAS affine and conversion path;
- the report does not claim that the written NIfTI file was reopened, reparsed
  or independently validated at runtime;
- M7 does not create an additional persisted provenance/validation sidecar;
- future persisted provenance/validation manifests use UTF-8 JSON as their
  canonical serialization format.

### Objective

Close the final Architecture Revision 1.0 M7 exit criterion:

`conversion produces provenance/validation output`

without adding runtime NIfTI rereading, a JSON dependency, a persisted sidecar,
resampling, interpolation, GUI integration or unrelated dataset-workflow code.

### Implementation Sequence

The implementation followed the approved sequence:

1. M7-P05C.0 - record ADR-014;
2. M7-P05C.1 - add `ConversionValidationReport`;
3. M7-P05C.2 - add the report to `DicomToNiftiResult`;
4. M7-P05C.3 - populate the report only after successful NIfTI writing;
5. M7-P05C.4 - add focused report and failure-path tests;
6. M7-P05C.5 - run the full regression;
7. M7-P05C.6 - perform the final M7 acceptance review.

### Implementation

Added the application-level
`org.cbihi.mrinormalizer.application.validation.ConversionValidationReport`.

The report records:

- output target;
- width, height and depth;
- scalar type;
- voxel count;
- row, column and slice spacing;
- NIfTI-RAS voxel-to-world affine;
- intensity transform;
- stored-voxel preservation state;
- resampling state;
- interpolation state;
- voxel-order-change state.

For the supported M7 integer-preserving conversion path:

- stored voxel values are preserved;
- resampling is false;
- interpolation is false;
- voxel ordering is unchanged.

`DicomToNiftiResult` now carries the validation report on successful conversion.
Its result contract rejects a successful output without a report, rejects a
report on a failed conversion and requires the result output target to match the
report output target.

`DefaultDicomToNiftiService` constructs the report only after
`NiftiVolumeWriter.write(...)` returns successfully.

Reconstruction failures and output-write failures do not produce a validation
report.

### Verification

Focused report suite:

`mvn "-Dtest=ConversionValidationReportTest" test`

Result: BUILD SUCCESS; 6 tests, 0 failures, 0 errors, 0 skipped.

Combined report/conversion regression:

`mvn "-Dtest=ConversionValidationReportTest,DicomToNiftiServiceTest,DicomToNiftiServiceHardeningTest" test`

Result: BUILD SUCCESS; 14 tests, 0 failures, 0 errors, 0 skipped.

Full regression:

`mvn clean test`

Result: BUILD SUCCESS; 129 tests, 0 failures, 0 errors, 0 skipped.

### Final M7 Acceptance

All Architecture Revision 1.0 M7 exit criteria are satisfied:

- independent NIfTI parsing: PASS;
- output shape preservation: PASS;
- supported integer voxel-array preservation: PASS;
- spacing preservation within tolerance: PASS;
- world-coordinate mapping within tolerance: PASS;
- qform/sform consistency: PASS;
- explicit provenance/validation output: PASS.

M7 DICOM-to-NIfTI acceptance is COMPLETE for the supported profile.

GUI integration was not required for M7 completion. NiBabel remains an external
acceptance-validation tool only and is not a Maven or runtime dependency.

### Related Commits

ADR-014: `b6bab40` - `docs: define m7 conversion validation report`

M7-P05C: `0976788` - `feat: add conversion validation report`
