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
