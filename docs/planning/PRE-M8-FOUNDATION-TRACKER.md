# Pre-M8 Foundation Completion Tracker

> **A workstream is not complete because its classes exist. It is complete only when its behavioral contracts are implemented, negative cases are tested, integration evidence passes, and the tracker records the verified checkpoint.**

## Program control

- Date: 2026-10-04 (Asia/Calcutta).
- Branch: feature/pre-m8-foundation.
- Canonical baseline: 0b91666c03d6247a6882c164aa6b033d9b761101.
- M6 protected checkpoint: cb7769e; protected tag: pre-demo-m6-2026-10-02.
- M7 protected checkpoint: d9ad7ea; protected tag: pre-demo-m7-2026-10-03.
- Current authorization: tracker-only reconciliation of accepted S3/F0 checkpoints and closure evidence. No Java changes or later implementation is authorized. F1-F11, GUI and M8 remain blocked.
- F0: S1 ACCEPTED at 76b676d; S2 ACCEPTED — ea40616 using user-supplied Java 21/Maven results. S2 was committed and pushed; the accepted remote checkpoint is ea4061672a293745ac5f5fc1b0d8d240d217e64e. S3 ACCEPTED — 8b6e714; F0 IMPLEMENTATION COMPLETE; F0 CLOSURE EVIDENCE PASSED; F0 ACCEPTED.
- F1-F11: NOT STARTED / BLOCKED by sequential review and acceptance.
- M8: BLOCKED until the complete Pre-M8 acceptance freeze.
- GUI: BLOCKED; interface remains frozen and no GUI implementation is authorized.

## Authority and baseline evidence

Authority order: original proposal at docs/project/PROJECT_PROPOSAL.pdf; approved docs/architecture/ARCHITECTURE-REVISION-1.0.md; accepted DECISIONS.md ADRs; actual source/tests; historical engineering records.

Fresh remote-ref verification on 2026-10-04: git ls-remote resolved feature/pre-m8-foundation to the exact canonical baseline. A clean local checkout was created from that remote ref. This does not verify future branch state.

The attached ZIP has HEAD d9e32566a446dfbadc84ef64b558e0f33e6f9f39 on docs/project-rebaseline, working-tree differences and three intentionally untracked local files. It was extracted separately for inspection; none of its tracked or protected untracked files was edited or staged. The clean checkout is the review/change target.

Fresh Git comparison: source, tests and pom.xml at the canonical baseline are byte-identical to M7 closure d9ad7ea. Only proposal documents differ. This is Git-tree evidence, not executed correctness evidence.

Historical evidence only: docs/testing/TEST-RESULTS.md records 129 tests, zero failures/errors/skips and NiBabel 5.4.2 acceptance. Those tests and the external acceptance checks were NOT rerun in this task.

Historical assistant runtime check: OpenJDK 17.0.20; mvn is unavailable. The project requires Java 21. The attempted mvn clean test command failed before execution because Maven was not found. Those attempts generated no test-pass evidence. This historical pre-execution limitation is superseded by the user-reported S1/S2/S3 verification and passed full F0 closure regression in the established Java 21/Maven environment, recorded below.

## Workstream ledger

| ID | Contract/outcome | Dependency | Status | Verified checkpoint |
| --- | --- | --- | --- | --- |
| F0 | Architectural contracts and policies | Baseline audit and design approval | ACCEPTED; IMPLEMENTATION COMPLETE; CLOSURE EVIDENCE PASSED; S1/S2/S3 ACCEPTED | S1: 76b676d; S2: ea40616; S3/tested implementation: 8b6e714; closure evidence: a2554ab |
| F1 | Persistent provenance/reporting | Accepted F0 | NOT STARTED | None |
| F2 | Deterministic recursive inventory | F0/F1 | NOT STARTED | None |
| F3 | Metadata-only DICOM inspection | F0/F1 | NOT STARTED | None |
| F4 | Candidate series discovery | F2/F3 | NOT STARTED | None |
| F5 | Immutable deterministic organization plan | F1/F2/F4 | NOT STARTED | None |
| F6 | Verified collision-safe COPY execution | F1/F5 | NOT STARTED | None |
| F7 | Supported project-owned NIfTI-1 reading | F0 and protected generic volume | NOT STARTED | None |
| F8 | Reopen, compare and persist conversion evidence | F1/F7/M7 | NOT STARTED | None |
| F9 | Headless orchestration through existing M6/M7 | F2-F8 | NOT STARTED | None |
| F10 | Minimal boundary reconciliation | Working F9 | NOT STARTED | None |
| F11 | Synthetic then stakeholder acceptance | F0-F10 | NOT STARTED | None |

The matrix records dependencies; it does not authorize parallel work or skipping the user's F0-then-F1 sequence.

## Global invariants

1. Source bytes remain unchanged; COPY is the initial operation.
2. Never silently overwrite a destination or delete a source.
3. Plan completely before execution. Revalidate execution-time assumptions.
4. Discovery does not decode cohort pixels; grouping never depends on filenames.
5. Recognition, validity, support and readiness remain separate.
6. M6 is the definitive selected-series validator; M7 is the only forward converter.
7. Stored voxel values, spatial geometry and intensity interpretation remain explicit.
8. Use deterministic technical identifiers and idempotent organization.
9. Default provenance excludes unnecessary identifying metadata and unsafe free text.
10. No traversal outside canonical configured roots, default following of discovered symlink entries, or generated-output ingestion; fail closed when safe containment cannot be established.
11. Failed post-write verification cannot produce a validated successful output/job.
12. No existing test is weakened and no scope expands without review.

## Deferred work and documentation queue

MOVE, GUI, M8, enhanced/multiframe objects, multidimensional automatic splitting, 4D/float/NIfTI-2 conversion, parallelism, PACS and BIDS remain deferred.

Record without editing now: README.md and MILESTONES.md still identify M8 as next; architecture historical sections describe an obsolete M6 state; DECISIONS.md ADR-008 has stale large-file wording relative to current probe behavior, and ADR-007's reader milestone wording is historical. ADR-014 already fixes UTF-8 JSON, superseding the architecture's unresolved serialization wording. Coordinate documentation reconciliation only after Pre-M8 acceptance, unless an explicit earlier ADR is justified and approved.

## Pre-M8 acceptance gate (all unchecked)

- [ ] F0-F10 accepted with verified coherent checkpoints and no architectural blockers.
- [ ] Safe deterministic recursive content-based discovery; separate recognition/validity/support/readiness.
- [ ] Metadata-only DICOM discovery; conservative unambiguous grouping; definitive M6 validation reused.
- [ ] Complete immutable organization plan; verified COPY; no overwrite/source mutation; deterministic idempotent rerun.
- [ ] Versioned UTF-8 JSON provenance and reports; mandatory deterministic source-to-destination mapping for every relocated file in the restricted job manifest; privacy-safe exports; recoverable factual job state.
- [ ] Approved bounded 3D integer NIfTI-1 reading; explicit units, affine and scaling behavior.
- [ ] Every eligible dataset conversion uses M7 and reopens output; complete voxels/type/dimensions/spacing/world mapping/scaling compared.
- [ ] Validation failure prevents validated publication/success; report persistence failures remain visible.
- [ ] Synthetic acceptance including negative/failure-injection cases passes.
- [ ] De-identified stakeholder acceptance passes; no data committed.
- [ ] Actual mvn clean test passes, zero failures/errors/skips; actual count recorded.
- [ ] git diff --check passes; final documentation reconciled.
- [ ] Protected accepted Pre-M8 commit/tag recorded; M8 branches only from that checkpoint.

## Checkpoint and approval discipline

Historical tracker/setup record (superseded by S1/S2 acceptance): 1147129, docs: establish Pre-M8 foundation tracker and F0 review gate. It changes only this tracker. Local commit only; no remote push. The F0 specification below is a subsequent uncommitted documentation change for review. No implementation or workstream closure is represented by the setup commit.

Corrected specification approved; S1 accepted at 76b676d; S2 ACCEPTED — ea40616 after user-supplied executable verification. S3 is accepted at 8b6e714 after executable verification; F0 implementation is complete, closure evidence passed and F0 is accepted. The closure evidence record is a2554ab. This update records only that bookkeeping and does not authorize F1. After approval, implement one coherent slice at a time, tests first where feasible; focused tests, failure inspection, subsystem regression, displayed diff and responsibility review after each slice. F0 closure requirements for full regression, whitespace checks, criterion-to-test mapping, updated tracker and a coherent tested implementation checkpoint are satisfied by the accepted records below. Stop before F1.

## F0 repository-grounded implementation review

The approved specification below is normative. S1 is accepted at 76b676d with user-reported executable evidence. S2 factory and tests are accepted using the user-reported executable evidence below; the accepted S2 checkpoint is ea40616. S3 is accepted using the executable results below; the exact applied S3 tree passed full F0 closure regression. Later work remains unimplemented and unauthorized. All project Java symbols below carry one of the exact classifications EXISTING — verified or PROPOSED — new. Standard Java language/library syntax in proposed signatures does not imply a new project API. A verified symbol means its declaration, implementation and relevant callers/tests were inspected; it does not mean those tests were executed here. The proposed positive readiness state records evidence supplied by a later owner; it is never recognition-derived conversion authorization.

### Grounding findings and limitations

1. EXISTING — verified: FormatDetectionService.detect(InputSource) checks the referenced file and invokes configured probes in order. A later positive match can follow an inconclusive result. Its production consumers are the probes/model boundary; direct use-case callers are currently tests, not an executable dataset workflow.
2. EXISTING — verified: DetectionResult contains only outcome, diagnostic and extensionMismatch. It exposes no NIfTI version, profile-validity evidence or readiness evidence. Do not infer such getters.
3. EXISTING — verified: NiftiFormatProbe accepts complete identifying NIfTI-1/2 single-file headers, including gzip, without payload/trailer validation. Its private field-validation helpers are unused. EXISTING — verified: FormatDetectionServiceTest explicitly accepts NIfTI-2 and a gzip header with a truncated trailer. Changing these tests to imply conversion support would be incorrect.
4. EXISTING — verified: DicomFormatProbe can identify a large Part 10 file from early metadata. Exhausting the 1 MiB inspection budget without identification yields an inconclusive result. Large total size alone is not rejection. ADR-008's older prose is stale; no change to that accepted policy or source is proposed in F0.
5. EXISTING — verified: Dcm4cheInstanceReader.read(InputSource) ultimately decodes pixels. EXISTING — verified: DefaultDicomSeriesService.process(DicomSeriesRequest) reads every supplied input before filtering by selected series. Later orchestration must supply only the candidate's members, not an entire cohort. No M6 change is proposed.
6. EXISTING — verified: SelectedSourceFingerprint.sha256(List<Source>) is a selected-series aggregate, ordered by SOP identity and including content digests. It is not a generic one-file digest or a hash of filenames. Do not call it speculatively from F0 or claim its aggregate equals a per-file SHA-256.
7. EXISTING — verified: DefaultDicomToNiftiService.convert(DicomToNiftiRequest) composes reconstruction, coordinate mapping and writing. EXISTING — verified: ConversionValidationReport records known in-memory invariants after writing, not a reopening check. Preserve both meanings.
8. EXISTING — verified: Nifti1VolumeWriter.write(ImageVolume, AffineMatrix4, OutputTarget) uses create-new output and attempts cleanup of partial new output. That is protected serializer behavior, not an already implemented dataset executor.
9. EXISTING — verified: DependencyDirectionTest and M6BoundaryTest inspect selected types, not all future packages. Add dedicated F0 coverage; do not claim current tests already protect new contracts.

### 1. Behavioral contract

F0 establishes explicit reviewed policies and a small application-owned, immutable assessment boundary above M5. Its executable behavior is limited to preserving the supplied recognition evidence, conservatively mapping it, rejecting contradictory assessment states and preventing mutable diagnostic collections. It performs no filesystem access, metadata parsing, hashing, pixel decoding, conversion, serialization or orchestration.

F0's assessment is a statement of supplied evidence, not a validator. Recognition alone never establishes full validity, supported-profile status or positive readiness. PROPOSED — new: ConversionReadiness.READY may be supplied only by a later owning validator/reader after successful evidence. PROPOSED — new: FormatAssessment.fromDetection(DetectionResult) never emits READY. A READY assessment records eligibility within that owner's processing profile, not completed conversion or permission to bypass definitive M6/M7 processing. The record checks coherence, not the truth or origin of evidence; enforcing owner-only production of READY belongs to the later owner's contract and integration tests. No evidence token, validator or authorization mechanism is added to F0.

Forward-looking policies below are contracts for their owning workstreams. F0 review approves those constraints; executable filesystem/parser/report behavior and its acceptance remain F1-F11 obligations. F0 closure must not claim those future algorithms have been implemented.

### 2. Supported cases

| Case | F0 behavior |
| --- | --- |
| M5 identifies DICOM | Preserve recognition evidence; DICOM family, unspecified object variant; validity/support unassessed; validation required |
| M5 identifies plain/gzip NIfTI | Preserve wrapper distinction in raw evidence; NIfTI family; version/variant unspecified; validity/support unassessed; validation required |
| M5 returns corrupt | Preserve diagnostic; validity invalid; support unassessed; blocked |
| M5 returns unknown after budget exhaustion | Inconclusive validity; support unassessed; blocked until new evidence |
| M5 unknown because input/access/I/O prevents detection | Validity/support unassessed; blocked with DETECTION_NOT_COMPLETED; preserve exact raw diagnostic |
| M5 unknown after completed non-match | Validity/support unassessed; blocked with FORMAT_NOT_RECOGNIZED for raw UNSUPPORTED_FORMAT only |
| Missing/wrong extension | Preserve hint; it cannot change format, validity, support or readiness |
| Supplied refined assessment | Accept coherent explicit evidence under constructor invariants; factory does not obtain that evidence itself |
| Valid recognized unsupported variant | Can represent valid and unsupported simultaneously, with blocked readiness |
| Later owner supplies successful validation/read evidence | Known family, valid, supported and READY; empty reasons accepted; protected M6/M7 processing still required |
| Pending, blocked, unsupported, invalid or inconclusive assessment | Requires an applicable reason; empty reasons rejected |
| Caller mutates supplied reason list | Stored reasons remain unchanged, immutable and canonically ordered |

The constructor can describe refined evidence supplied by a later owning service. That is not a new parsing API. The initial raw recognition result remains available for provenance and cannot be silently rewritten.

### 3. Rejected/unsupported cases

- Null required fields, null entries in reasons, duplicate reasons, and incompatible format/variant/status combinations are programming-contract violations: reject with a fixed non-identifying argument error. Do not silently coerce them.
- Header recognition cannot be promoted to valid/supported automatically; extension cannot supply a missing version.
- A DICOM recognition result cannot acquire a NIfTI variant, or vice versa. A corrupt recognition cannot be rehabilitated by constructor assertions; require a fresh independent recognition result after corrective investigation.
- PROPOSED — new: READY cannot coexist with unknown family, unspecified NIfTI variant, unassessed/inconclusive/invalid validity, unassessed/unsupported support or pending/blocking reasons. Recognition alone cannot supply READY; even coherent READY does not bypass definitive M6/M7 processing.
- F0 does not accept or reject imaging bytes itself. Unsupported MRI objects, transfer syntaxes, dimensionality, scalar types, gzip payloads and geometry are adjudicated by their later owning components or existing M6/M7, not a second F0 validator.
- NIfTI-2, paired files, 4D, floating/complex/color voxels, compressed/enhanced/multiframe DICOM, automatic multidimensional splitting, MOVE and reverse conversion remain outside the initial processing profile. Format recognition can still identify some of them.

### 4. Invariants and policy decisions for review

All PM8 decisions in this table were PROPOSED — new relative to the original audit and are now approved, except explicitly inherited ADR constraints. Approval makes these program constraints; it does not start their later owning implementations.

| Decision | Exact policy | Owner of executable evidence |
| --- | --- | --- |
| PM8-D01 assessment | Preserve M5 result; separate family/variant, validity, support and readiness; READY requires successful evidence supplied by a later owning validator/reader, never fromDetection(); READY cannot bypass definitive M6/M7 processing | F0 coherence/factory; later owner evidence |
| PM8-D02 roots | Resolve configured roots to real/canonical paths for containment and overlap checks; reject equal/nested input/output roots; do not follow discovered symlink entries by default; reject special files and path escapes; fail closed when safe containment cannot be established. No blanket rejection of every symlink ancestor; any stricter rule requires F2/F6 platform evidence | F2/F5/F6 |
| PM8-D03 generated data | All generated artifacts stay within the configured output root; a nested output configuration is rejected rather than filtered heuristically | F2/F5/F9 |
| PM8-D04 execution | COPY only; never delete sources; full plan before writes; verify source identity/size/content at execution; verify destination bytes; existing identical destination is an explicit idempotent outcome; differing bytes are a conflict | F5/F6 |
| PM8-D05 publication | No silent replacement, including concurrent destination creation; write private temporary artifact, verify, then publish through provider-proven no-replace behavior; if that cannot be guaranteed, fail closed | F6/F8 |
| PM8-D06 naming | Versioned layout; framed technical study/series keys and full content digests; no filename/order counters; same bytes and identities produce same semantic target; no lossy gzip recompression during COPY | F5 |
| PM8-D07 grouping | Group by study+series identity; record all members; reject/flag missing identity, duplicates or mixed acquisition dimensions; no automatic splitting/merging; candidate screening is not M6 validation | F3/F4 |
| PM8-D08 provenance | UTF-8 JSON is inherited from ADR-014; version field required; fixed controlled fields/reasons; plan and actual results separate; no success inferred from plan; checkpoint after completed operations; persistence failure stops further mutation | F1/F6/F9 |
| PM8-D09 privacy and traceability | Restricted local job manifest MUST trace every relocated source file to its destination using deterministic normalized paths relative to their canonical source/output roots plus per-file content hashes and actual operation status. Preserve distinct source entries even when bytes match. No absolute paths by default. Relative paths may contain sensitive identifiers: restrict manifest access and exclude unnecessary patient fields, raw UIDs, free-text metadata and exception text. Public/export reports may omit path-like identifiers and sensitive metadata, but must not replace or remove the mandatory restricted mapping | F1/F3/F5/F6/F9 |
| PM8-D10 reader profile | Project-owned NIfTI-1 single-file plain/gzip, true 3D including depth one, four existing integer scalar types, little/big endian; no paired/NIfTI-2/4D/float support; output existing generic volume model in RAS/mm | F7 |
| PM8-D11 world geometry | Retain valid sform preference and valid qform fallback. If both exist and disagree, record the disagreement explicitly; preference does not silently settle reverse-conversion semantics. No affine-element rejection tolerance is fixed in F0. Exact comparison/tolerance, validity and disagreement consequences require the F7 reader contract and executable geometry tests; reverse-conversion semantics remain for authorized M8 review. Neither reliable transform means not ready; do not invent identity geometry | F7/F8; future M8 semantics |
| PM8-D12 scaling/units | Preserve stored values; undeclared/no-scaling maps to existing identity semantics; finite declared nonzero scaling remains explicit; unit normalization must update spacing and affine consistently; invalid/unknown units cannot silently mean mm | F7 |
| PM8-D13 bounds | Checked dimensions/offset/byte-count arithmetic before allocation; explicit finite configured limits on decoded bytes, retained voxels, offset/header skips and discovery metadata; bounded gzip with full supported-payload/trailer verification | F3/F7 |
| PM8-D14 verified output | Stage through existing M7; reopen; compare complete voxels, type, dimensions, spacing, affine/world mapping and scaling; only pass permits verified publication; failed staging is removed; cleanup failure recorded; no claim of validation on failure | F8 |
| PM8-D15 recovery | Initially one sequential operation/series; atomically checkpoint factual job-state JSON after operation; on restart reconcile actual filesystem, hashes and recorded status; never trust journal success alone | F1/F6/F9 |

Filesystem qualification: normalized lexical paths alone do not prove containment under symlink races. An atomic rename alone does not prove no replacement of a concurrently created destination. F6 must test its actual provider strategy and enforce the policy; these guarantees are not inherited from M7. Hostile concurrent root/symlink mutation remains fail-closed where detected, with platform limitations stated in F6's review.

Privacy qualification: hashing technical identifiers reduces direct exposure but is not anonymization. COPY preserves the original DICOM contents, including any identifying fields. F0 does not create a de-identification product. Paths can themselves contain names; relative paths are not automatically privacy-safe. The restricted operational source-to-destination mapping is mandatory and separate from public/export reports. Root references use stable manifest-local logical keys; absolute filesystem locations are not persisted by default. Each relocated source remains traceable, including identical-content sources sharing a destination where the later approved collision policy permits it. F1 must define the schema/access/export contracts, F5 the planned mapping, and F6/F9 the factual completed-operation mapping. These are future evidence obligations, not F0 implementations.

F7 design must resolve exact memory-limit defaults from the existing long-array backing and its defensive copies, finite-float precision/tolerance behavior, sform/qform comparison semantics and disagreement reporting, qform numerical edge cases, sform/header spacing consistency, extension/trailing-member policy and scalar/header cross-checks before parser implementation. F1 design must resolve concrete JSON schema, serializer selection and atomic checkpoint replacement mechanics before persistence implementation. These are explicit future review gates, not authorization to implement them during F0.

### 5. Failure taxonomy

Keep EXISTING — verified: DetectionDiagnostic, DicomProcessingError and DicomToNiftiError unchanged. Preserve their original phase and codes at future boundaries; do not flatten geometry, input and output failures into a generic failure. Their actual enum members were inspected. M5's size-budget diagnostic is inconclusive, not an unsupported-profile verdict.

F0 reasons are assessment explanations, not infrastructure exceptions. PROPOSED — new: AssessmentReason contains only FORMAT_NOT_RECOGNIZED, DETECTION_NOT_COMPLETED, DETECTION_INCONCLUSIVE, INPUT_RECOGNIZED_AS_CORRUPT, VALIDATION_NOT_PERFORMED, UNSUPPORTED_FORMAT_VARIANT, UNSUPPORTED_PROFILE and VALIDATION_FAILED. Every listed member is PROPOSED — new. Preserve the raw existing diagnostic instead of remapping all old errors into new enum members.

Future stable codes below are PROPOSED — new specification entries, not F0 Java enums or currently executable failure handling:

| Category | Proposed codes | Owning review |
| --- | --- | --- |
| Input/root/policy | INPUT_ROOT_INVALID, OUTPUT_ROOT_INVALID, ROOTS_OVERLAP, PATH_OUTSIDE_ROOT, SYMLINK_DISALLOWED, SPECIAL_FILE_UNSUPPORTED | F2/F5/F6 |
| Resource/parse | RESOURCE_LIMIT_EXCEEDED, METADATA_READ_FAILED, NIFTI_PARSE_FAILED, GZIP_INTEGRITY_FAILED | F3/F7 |
| Discovery/profile | INCOMPLETE_METADATA, AMBIGUOUS_SERIES, MULTIDIMENSIONAL_SERIES, UNSUPPORTED_FORMAT_VARIANT, UNSUPPORTED_PROFILE | F3/F4/F7 |
| Execution | SOURCE_CHANGED, DESTINATION_CONFLICT, COPY_FAILED, COPY_VERIFICATION_FAILED, SAFE_PUBLICATION_UNAVAILABLE, CLEANUP_FAILED | F6 |
| Persistence | MANIFEST_WRITE_FAILED, REPORT_WRITE_FAILED, CHECKPOINT_FAILED | F1/F8/F9 |
| Reopening/validation | OUTPUT_REOPEN_FAILED, OUTPUT_VALIDATION_FAILED | F8 |
| Unexpected internal defect | INTERNAL_ERROR, with controlled diagnostic context only | Owning service |

Identical-existing, skipped-policy, validation-required and planned states are statuses, not errors. Missing/unsupported inputs remain represented rather than silently dropped. Future failure records may carry more than one fact: a primary failure and a cleanup/checkpoint failure. Neither may erase the other. Application boundaries must not expose raw stack traces or external-library exception text.

### 6. Security/privacy implications

The proposed F0 object contains enums and the existing bounded-detection result, not paths, UIDs, DICOM attributes, patient fields, arbitrary messages, throwables or bytes. Constructor error messages use fixed field/state descriptions. Reason-list copying prevents callers from changing recorded evidence. F0 performs no I/O and cannot mutate source/output data.

This narrow object cannot itself secure discovery/execution, authenticate a dataset or enforce process memory budgets. Those guarantees require their owning executable tests. No new framework, network access, telemetry, runtime dependency, filesystem abstraction or serializer is part of F0.

### 7. Exact existing components to reuse

Paths below are relative to the package root src/main/java/org/cbihi/mrinormalizer. Each project symbol is EXISTING — verified. Reuse means preserve and call through its real contract when its owning phase starts, not modify it now.

| Verified symbol and actual contract | Source | Verified caller/test evidence | F0 treatment |
| --- | --- | --- | --- |
| EXISTING — verified: FormatDetectionService.detect(InputSource) | application/service/FormatDetectionService.java | EXISTING — verified: FormatDetectionServiceTest; writer recognition test | New tests wrap its actual returned evidence |
| EXISTING — verified: DetectionResult(outcome, diagnostic, extensionMismatch), DetectionOutcome, DetectionDiagnostic, ImagingFormat | domain/model, corresponding files | Detection service/probes and detection tests | Direct type reuse; no replacement/migration |
| EXISTING — verified: InputSource(String reference), OutputTarget(String reference) | domain/model, corresponding files | Detection/reconstruction/conversion requests/tests | No inferred path validation in these records |
| EXISTING — verified: FormatProbe.probe(InputSource) | domain/port/FormatProbe.java | Detection service and two probes | No new implementation or relocation |
| EXISTING — verified: DicomFormatProbe, NiftiFormatProbe | infrastructure/detection, corresponding files | Detection tests; writer detection test | Unchanged bounded recognition |
| EXISTING — verified: DicomSeriesService.process(DicomSeriesRequest), DefaultDicomSeriesService, DicomSeriesRequest | application/service and application/request | EXISTING — verified: DicomSeriesServiceTest and conversion service | Definitive reconstruction stays here |
| EXISTING — verified: Dcm4cheInstanceReader.read(InputSource) | infrastructure/dicom/Dcm4cheInstanceReader.java | Reconstruction/conversion integration fixtures | No metadata-discovery reuse because it decodes pixels |
| EXISTING — verified: DicomToNiftiService.convert(DicomToNiftiRequest), DefaultDicomToNiftiService, DicomToNiftiRequest, DicomToNiftiResult | application/service, request, result | EXISTING — verified: DicomToNiftiServiceTest, DicomToNiftiServiceHardeningTest, ConversionValidationReportTest | Only future converter path; unchanged |
| EXISTING — verified: NiftiVolumeWriter.write(ImageVolume, AffineMatrix4, OutputTarget), Nifti1VolumeWriter | application/port/out; infrastructure/nifti | EXISTING — verified: Nifti1VolumeWriterTest, Nifti1VolumeWriterHardeningTest | Preserve serializer/create-new behavior |
| EXISTING — verified: ImageVolume(VolumeGeometry, VoxelData, IntensityTransform), ScalarType, ImmutableVoxelData | domain/model, corresponding files | Image/voxel tests, reconstruction and writer callers | No parallel volume or scalar model |
| EXISTING — verified: VolumeGeometry.voxelToWorldAffine(), AffineMatrix4, CoordinateSystem, GeometryValidationPolicy.defaults() | domain/model, corresponding files | Geometry/affine/reconstruction tests and mapper | No duplicate geometry algorithms |
| EXISTING — verified: NiftiAffineMapper.toNiftiRas(VolumeGeometry) | application/conversion/NiftiAffineMapper.java | Forward service; affine/writer tests | Existing LPS-to-RAS operation untouched |
| EXISTING — verified: IntensityTransform.identityNotDeclared(), ProvenanceRecord, SelectedSourceFingerprint.sha256(List<Source>) and its nested Source | domain/model; application/provenance | Reconstruction, conversion, report and fingerprint fixtures | Preserve semantics; no new hashing/persistence |
| EXISTING — verified: ConversionValidationReport.losslessIntegerPreserving(OutputTarget, ImageVolume, AffineMatrix4) | application/validation/ConversionValidationReport.java | Forward service and report tests | No reopening claim added |
| EXISTING — verified: DependencyDirectionTest, M6BoundaryTest, PackageStructureTest | src/test/java/org/cbihi/mrinormalizer/architecture | Existing selected-type checks | Leave untouched; add dedicated F0 test |

The existing standard-library imports used by these verified declarations include List, Path and the primitive/string types shown in signatures. Proposed API signatures below use the same language/runtime conventions, without assuming any uninspected project method.

### 8. Proposed Java types and exact responsibilities

All six production files are under the new package org.cbihi.mrinormalizer.application.dataset.model. No new port or adapter is needed for pure values. Names and member sets below retain PROPOSED — new classification relative to the inspected baseline. S1 types are accepted at 76b676d. The listed factory was implemented and accepted in S2; its accepted checkpoint is ea40616.

| New type | Responsibility and complete proposed members |
| --- | --- |
| PROPOSED — new: FormatAssessment | Immutable recognition/refinement evidence, constructor coherence checks, and pure conservative factory; seven components: initialDetection, format, variant, validity, support, readiness and reasons |
| PROPOSED — new: FormatVariant | UNDETERMINED, DICOM_UNSPECIFIED, NIFTI_UNSPECIFIED, NIFTI_1_SINGLE_FILE, NIFTI_2_SINGLE_FILE, NIFTI_PAIR; all PROPOSED — new |
| PROPOSED — new: ValidityStatus | NOT_ASSESSED, INCONCLUSIVE, VALID, INVALID; all PROPOSED — new; full-load validity cannot come from M5 alone |
| PROPOSED — new: SupportStatus | NOT_ASSESSED, SUPPORTED, UNSUPPORTED; all PROPOSED — new; scoped to the documented processing profile |
| PROPOSED — new: ConversionReadiness | REQUIRES_VALIDATION, BLOCKED, READY; all PROPOSED — new; READY records later-owner evidence and never bypasses definitive processing |
| PROPOSED — new: AssessmentReason | Fixed safe reason codes from section 5; no arbitrary text or exception payload |

The exact PROPOSED — new: FormatAssessment component declaration below is authoritative. No nullable placeholder is proposed.

No inventory, scanner, metadata DTO, series discovery service, organization plan/executor, manifest/report writer, reader, reopen validator or processing service is added in F0. Their contracts are constrained by the reviewed policies, but their exact APIs await the corresponding repository-grounded reviews.

### 9. Proposed public-method contracts

The following is specification text only, not a source file or compiled implementation. Every declared project member is PROPOSED — new. Referenced EXISTING — verified types: DetectionResult and ImagingFormat from domain.model. All other project types in the signature are PROPOSED — new as classified above.

    // PROPOSED — new public record and canonical constructor
    public record FormatAssessment(
        DetectionResult initialDetection,
        ImagingFormat format,
        FormatVariant variant,
        ValidityStatus validity,
        SupportStatus support,
        ConversionReadiness readiness,
        List<AssessmentReason> reasons
    ) { ... }

    // PROPOSED — new S2-only factory specification; absent from S1 source, no stub
    public static FormatAssessment fromDetection(DetectionResult detection);

PROPOSED — new canonical constructor contract:

1. All components, raw outcome/diagnostic and reason entries are required; the reasons list may be empty only under rule 6. Reject null, duplicate reasons and contradictions with fixed IllegalArgumentException messages. Copy and sort reasons lexically by stable member name; no caller-owned mutable list retained. Raw positive outcomes require EXISTING — verified: NONE; raw UNKNOWN permits only EXISTING — verified: INPUT_NOT_FOUND, INPUT_IS_DIRECTORY, INPUT_NOT_READABLE, INVALID_INPUT_REFERENCE, IO_ERROR, EMPTY_INPUT, INPUT_TOO_LARGE or UNSUPPORTED_FORMAT; UNKNOWN + NONE and UNKNOWN + INVALID_DICOM/INVALID_NIFTI/INVALID_GZIP are contradictory evidence and must be rejected by both constructor and factory; raw CORRUPT requires EXISTING — verified: INVALID_DICOM, INVALID_NIFTI or INVALID_GZIP. Preserve the original raw object and extensionMismatch. Do not guess the meaning of contradictory raw combinations.
2. DICOM family requires DICOM_UNSPECIFIED. NIfTI family requires one of its NIfTI variants. Unknown family requires UNDETERMINED, NOT_ASSESSED support and non-valid validity. A raw positive family cannot conflict with the assessed family. These assessment variant/status members are PROPOSED — new; family members are EXISTING — verified.
3. Raw CORRUPT forces INVALID validity and BLOCKED readiness; later correction requires fresh recognition evidence. Raw UNKNOWN may be explicitly refined to a known family only by a later owner with new evidence; fromDetection() never does so. While the assessed family remains UNKNOWN, raw UNKNOWN must retain the factory's corresponding validity, blocked readiness and applicable detection reason from the mapping below. Refinement to a known family removes reasons that apply only to an unresolved family but preserves raw evidence unchanged.
4. INVALID validity, UNSUPPORTED support or UNKNOWN assessed family always requires BLOCKED. Each blocked assessment needs a cause applicable to its actual state. REQUIRES_VALIDATION requires a recognized family, non-invalid validity, non-unsupported support and VALIDATION_NOT_PERFORMED; it also covers recognized INCONCLUSIVE validity awaiting further evidence. Recognized but not definitively validated evidence deterministically uses REQUIRES_VALIDATION, including recognized family with NOT_ASSESSED validity/support. It does not authorize bypass of definitive processing. Detection unavailable/inconclusive/non-match/corrupt, invalid or unsupported input, and failed validation use BLOCKED; later successful owning-validator/reader evidence uses READY. The readiness enum has exactly these three states.
5. A known NIFTI_2_SINGLE_FILE or NIFTI_PAIR variant requires UNSUPPORTED, BLOCKED and UNSUPPORTED_FORMAT_VARIANT, even when VALID. NIFTI_UNSPECIFIED cannot be SUPPORTED or READY. SUPPORTED for DICOM_UNSPECIFIED or NIFTI_1_SINGLE_FILE records a later owner's explicit supported-profile evidence, not a check performed by this record.
6. READY requires a recognized family, VALID validity, SUPPORTED support and a compatible supported variant under rule 5. Only a later owning validator/reader may supply READY after successful evidence; the constructor checks coherence, not evidence authenticity. Empty reasons are permitted only for this fully assessed successful combination. All current reason codes describe pending or unsuccessful assessment, so a coherent READY state has an empty list; stale pending/blocking reasons are rejected. Every pending, blocked, unsupported, invalid or inconclusive state must contain an applicable reason. No global nonempty-reasons invariant remains.
7. INVALID states require INPUT_RECOGNIZED_AS_CORRUPT when raw CORRUPT, or VALIDATION_FAILED; raw CORRUPT always requires its corrupt reason. UNSUPPORTED states require UNSUPPORTED_FORMAT_VARIANT or UNSUPPORTED_PROFILE. Every supplied reason must satisfy the applicability table below, independently of list nonemptiness.
8. Never infer validity, support, version, transfer syntax, modality or READY from recognition or filename. A READY assessment does not claim conversion completion, successful post-write verification or an exemption from EXISTING — verified: DicomSeriesService.process(DicomSeriesRequest) / DicomToNiftiService.convert(DicomToNiftiRequest). Future owners must still use the definitive M6/M7 contracts.

All readiness members and state/reason members in the following tables are PROPOSED — new. Raw detection and family members explicitly identified below are EXISTING — verified.

| ConversionReadiness member | Coherent assessment and reason requirement |
| --- | --- |
| REQUIRES_VALIDATION | Known family; validity NOT_ASSESSED, INCONCLUSIVE or VALID; support NOT_ASSESSED or SUPPORTED; VALIDATION_NOT_PERFORMED required |
| BLOCKED | Unknown family, invalid validity or unsupported support with applicable causal reason(s); never an unexplained veto on an otherwise successful assessment |
| READY | Known family; VALID; SUPPORTED; supported variant; empty reasons; successful later-owner evidence required; definitive processing remains mandatory |

| AssessmentReason member | Exact permitted state |
| --- | --- |
| FORMAT_NOT_RECOGNIZED | Assessed family UNKNOWN; raw UNKNOWN + UNSUPPORTED_FORMAT; validity NOT_ASSESSED; readiness BLOCKED; completed non-match only |
| DETECTION_NOT_COMPLETED | Assessed family UNKNOWN; raw UNKNOWN with INPUT_NOT_FOUND, INPUT_IS_DIRECTORY, INPUT_NOT_READABLE, INVALID_INPUT_REFERENCE, IO_ERROR or EMPTY_INPUT; validity NOT_ASSESSED; readiness BLOCKED; no completed non-match claim |
| DETECTION_INCONCLUSIVE | Assessed family UNKNOWN; raw UNKNOWN + INPUT_TOO_LARGE; validity INCONCLUSIVE; readiness BLOCKED |
| INPUT_RECOGNIZED_AS_CORRUPT | Raw CORRUPT; validity INVALID; readiness BLOCKED |
| VALIDATION_NOT_PERFORMED | Recognized assessed family; readiness REQUIRES_VALIDATION; validity non-invalid; support non-unsupported; pending owning-operation validation, even if file validity was established |
| UNSUPPORTED_FORMAT_VARIANT | NIFTI_2_SINGLE_FILE or NIFTI_PAIR; support UNSUPPORTED; readiness BLOCKED |
| UNSUPPORTED_PROFILE | Support UNSUPPORTED; readiness BLOCKED |
| VALIDATION_FAILED | Validity INVALID; readiness BLOCKED |

Raw UNKNOWN + NONE and UNKNOWN + INVALID_DICOM/INVALID_NIFTI/INVALID_GZIP are constructor/factory contract violations. The inspected built-in probes associate invalid diagnostics with CORRUPT. Reject contradictory supplied evidence with a fixed argument error; do not map it to a reason, rewrite the raw result or fabricate a blocked assessment. New raw members would require an explicit mapping review.

PROPOSED — new factory mapping contract for FormatAssessment.fromDetection(DetectionResult):

| EXISTING — verified raw outcome/diagnostic | Family/variant | Validity/support/readiness (PROPOSED — new) | Reason (PROPOSED — new) |
| --- | --- | --- | --- |
| DICOM + NONE | DICOM / DICOM_UNSPECIFIED | NOT_ASSESSED / NOT_ASSESSED / REQUIRES_VALIDATION | VALIDATION_NOT_PERFORMED |
| NIFTI or NIFTI_GZ + NONE | NIFTI / NIFTI_UNSPECIFIED | NOT_ASSESSED / NOT_ASSESSED / REQUIRES_VALIDATION | VALIDATION_NOT_PERFORMED |
| CORRUPT + INVALID_DICOM, INVALID_NIFTI or INVALID_GZIP | UNKNOWN / UNDETERMINED | INVALID / NOT_ASSESSED / BLOCKED | INPUT_RECOGNIZED_AS_CORRUPT |
| UNKNOWN + INPUT_NOT_FOUND | UNKNOWN / UNDETERMINED | NOT_ASSESSED / NOT_ASSESSED / BLOCKED | DETECTION_NOT_COMPLETED |
| UNKNOWN + INPUT_IS_DIRECTORY | UNKNOWN / UNDETERMINED | NOT_ASSESSED / NOT_ASSESSED / BLOCKED | DETECTION_NOT_COMPLETED |
| UNKNOWN + INPUT_NOT_READABLE | UNKNOWN / UNDETERMINED | NOT_ASSESSED / NOT_ASSESSED / BLOCKED | DETECTION_NOT_COMPLETED |
| UNKNOWN + INVALID_INPUT_REFERENCE | UNKNOWN / UNDETERMINED | NOT_ASSESSED / NOT_ASSESSED / BLOCKED | DETECTION_NOT_COMPLETED |
| UNKNOWN + IO_ERROR | UNKNOWN / UNDETERMINED | NOT_ASSESSED / NOT_ASSESSED / BLOCKED | DETECTION_NOT_COMPLETED |
| UNKNOWN + EMPTY_INPUT | UNKNOWN / UNDETERMINED | NOT_ASSESSED / NOT_ASSESSED / BLOCKED | DETECTION_NOT_COMPLETED |
| UNKNOWN + UNSUPPORTED_FORMAT | UNKNOWN / UNDETERMINED | NOT_ASSESSED / NOT_ASSESSED / BLOCKED | FORMAT_NOT_RECOGNIZED |
| UNKNOWN + INPUT_TOO_LARGE | UNKNOWN / UNDETERMINED | INCONCLUSIVE / NOT_ASSESSED / BLOCKED | DETECTION_INCONCLUSIVE |
| UNKNOWN + NONE, INVALID_DICOM, INVALID_NIFTI or INVALID_GZIP | Contract violation: reject | No assessment produced | No reason mapping |

The factory preserves the complete EXISTING — verified: DetectionResult, including DetectionDiagnostic and extensionMismatch, unchanged. Every accepted combination has nonempty applicable reasons and readiness exactly REQUIRES_VALIDATION or BLOCKED, never READY. The factory neither validates bytes nor invents a version, supported profile or success state. Null/contradictory raw components produce a fixed argument error. Recognition of an unsupported format is not a definitive unsupported MRI-profile verdict.

PROPOSED — new record accessors initialDetection(), format(), variant(), validity(), support(), readiness() and reasons() expose only stored values. Generated value equality/hash semantics are sufficient. Public declaration/signature remains as shown above. No setter, convenience upgrade method, ready boolean, I/O method, evidence token, exception mapper, serializer or converter is added.

### 10. Test matrix and slice order after approval

New test symbols are PROPOSED — new: FormatAssessmentTest (unit), FormatAssessmentIntegrationTest (real M5 adapter integration), FoundationContractBoundaryTest (architecture). Put the first two in src/test/java/org/cbihi/mrinormalizer; the architecture test in its existing architecture subpackage. Do not modify existing tests or duplicate their algorithms.

| Criterion / test ID | Executable test contract proposed | Kind | Current evidence |
| --- | --- | --- | --- |
| F0-A01 / U01 | All five raw outcomes map conservatively; preserve raw outcome/diagnostic and both extensionMismatch values; all three coherent corrupt diagnostics covered | Unit, new assessment test | PASS / ACCEPTED: user-reported focused S2 run, 40 tests, zero failures/errors/skips |
| F0-A02 / U02 | Exhaust exactly the eight allowed UNKNOWN diagnostics: five input/access/I/O failures and EMPTY_INPUT map to DETECTION_NOT_COMPLETED; UNSUPPORTED_FORMAT alone to FORMAT_NOT_RECOGNIZED; INPUT_TOO_LARGE to INCONCLUSIVE/DETECTION_INCONCLUSIVE; preserve raw evidence; no fabricated INVALID/SUPPORTED/READY | Parameterized unit table | PASS / ACCEPTED: user-reported focused S2 run, 40 tests, zero failures/errors/skips |
| F0-A03 / U03-S1 | Constructor rejects null components/raw enums/reason entries, duplicate reasons and contradictory raw combinations, including UNKNOWN + NONE and UNKNOWN + each INVALID_DICOM/INVALID_NIFTI/INVALID_GZIP, even with otherwise coherent refined state | S1 constructor unit table | S1 ACCEPTED at 76b676d; user-reported 26/26 focused pass |
| F0-A03 / U03-S2 | fromDetection() rejects the same malformed raw combinations; factory contract is unchanged and belongs exclusively to S2 | S2 factory unit table | PASS / ACCEPTED: user-reported focused S2 run, 40 tests, zero failures/errors/skips |
| F0-A04 / U04 | Reject incompatible family/variant, invalid/unsupported/unknown nonblocked states, contradictory reasons and unexplained BLOCKED; recognized family with NOT_ASSESSED validity/support deterministically uses REQUIRES_VALIDATION; include coherent known-family INCONCLUSIVE with applicable reason; exact readiness members are REQUIRES_VALIDATION, BLOCKED and READY | Unit positive/negative table | S1 ACCEPTED at 76b676d; user-reported 26/26 focused pass |
| F0-A05 / U05 | Valid unsupported NIfTI-2/pair represented as blocked with applicable reason; reject READY for either; unspecified NIfTI cannot be SUPPORTED/READY | Unit positive/negative pairs | S1 ACCEPTED at 76b676d; user-reported 26/26 focused pass |
| F0-A06 / U06 | Source-list mutation cannot change reasons; returned empty/nonempty lists unmodifiable; input permutation yields canonical reason order | Unit mutation/determinism | S1 ACCEPTED at 76b676d; user-reported 26/26 focused pass |
| F0-A07 / U07 | Raw UNKNOWN budget/input-failure/non-match permits explicit later known-family evidence without rewriting raw diagnostic; remove unresolved-detection reason after refinement; raw CORRUPT cannot be rehabilitated | Unit positive/negative pairs | S1 ACCEPTED at 76b676d; user-reported 26/26 focused pass |
| F0-A14 / U08 | Coherent owner-supplied DICOM and NIfTI-1 VALID/SUPPORTED/READY states accept empty reasons; test raw positive and refined UNKNOWN evidence; synthetic constructor fixtures make no claim of implemented later validator | Unit positive table | S1 ACCEPTED at 76b676d; user-reported 26/26 focused pass |
| F0-A14 / U09 | Reject READY with UNKNOWN family, unspecified/unsupported variant, NOT_ASSESSED/INCONCLUSIVE/INVALID validity, NOT_ASSESSED/UNSUPPORTED support, or any pending/blocking reason | Unit negative table | S1 ACCEPTED at 76b676d; user-reported 26/26 focused pass |
| F0-A15 / U10 | Reject empty reasons for each pending, BLOCKED, UNSUPPORTED, INVALID and INCONCLUSIVE case; require applicable causes, not any arbitrary reason; reject FORMAT_NOT_RECOGNIZED for unavailable input, EMPTY_INPUT and budget exhaustion | Unit reason-applicability table | S1 ACCEPTED at 76b676d; user-reported 26/26 focused pass |
| F0-A14 / U11 | Exhaust every accepted raw outcome/diagnostic and both extension hints through fromDetection(): exactly REQUIRES_VALIDATION or BLOCKED, never READY; no VALID/SUPPORTED claim; raw evidence unchanged; applicable nonempty reasons; UNKNOWN + NONE/each INVALID_* rejected without producing any assessment or mapped reason | Unit exhaustive factory table | PASS / ACCEPTED: user-reported focused S2 run, 40 tests, zero failures/errors/skips |
| F0-A08 / I01 | Actual complete NIfTI-1/2 headers through real M5 remain unspecified-version/unvalidated; never READY | Real temporary-file integration | PASS / ACCEPTED: user-reported focused S2 run, 40 tests, zero failures/errors/skips |
| F0-A08 / I02 | Recognizable gzip header with truncated trailer never becomes VALID/SUPPORTED/READY | Real temporary-file negative integration | PASS / ACCEPTED: user-reported focused S2 run, 40 tests, zero failures/errors/skips |
| F0-A08 / I03 | Wrong/extensionless DICOM identifying metadata produces validation-required assessment, never READY; F0 does not load pixels | Real M5 fixture integration; no cohort discovery claim | PASS / ACCEPTED: user-reported focused S2 run, 40 tests, zero failures/errors/skips |
| F0-A02 / I04 | Actual missing file, directory, invalid reference, empty file and random non-match through existing service preserve distinct diagnostics and revised reasons | Real M5 temporary-file/input integration | PASS / ACCEPTED: user-reported focused S2 run, 40 tests, zero failures/errors/skips |
| F0-A02 / I05 | Controlled existing probe boundary returns IO_ERROR or INPUT_TOO_LARGE: service result preserved; factory yields DETECTION_NOT_COMPLETED or DETECTION_INCONCLUSIVE respectively; no permission-dependent unreadability claim | Existing service with test probe; raw INPUT_NOT_READABLE covered by U02 | PASS / ACCEPTED: user-reported focused S2 run, 40 tests, zero failures/errors/skips |
| F0-A09 / B01 | Inspect new fields/constructors/methods including generic signatures; allow Java/new model/existing detection-domain types only; reject infrastructure/UI/dcm4che/path/byte/throwable/free-text exposure | Dedicated architecture test and diff review | PASS / ACCEPTED: S3 focused 5 tests; combined F0/M5/architecture 75 tests; zero failures/errors/skips |
| F0-A10 / B02 | Approved readiness enum set is exactly REQUIRES_VALIDATION, BLOCKED and READY; reason set includes DETECTION_NOT_COMPLETED; pure factory emits only REQUIRES_VALIDATION or BLOCKED; record exposes no converter/validator/bypass API; no production M6/M7 caller changes; package placement preserved | Architecture, U11 and source/diff review | PASS / ACCEPTED: S3 focused 5 tests; combined F0/M5/architecture 75 tests; zero failures/errors/skips |
| F0-A11 | Existing M5 and relevant M6/M7/architecture regressions pass without weakening or modifying tests | Existing regression commands | PASS: S3 combined F0/M5/architecture gate 75 tests; protected M6/M7 79 tests; both BUILD SUCCESS, zero failures/errors/skips |
| F0-A12 | Actual Java 21 mvn clean test with zero failures/errors/skips; git diff --check; protected paths unchanged | Closure commands and Git comparison | PASS: final exact-tree mvn clean test after complete S3 patch, 174 tests, zero failures/errors/skips, BUILD SUCCESS; production/tests compiled under release 21; diff check passed with known tracker LF→CRLF informational warning only |
| F0-A13 | Revised decisions approved; criterion-to-executed-test mapping and coherent checkpoint recorded; no later implementation | Review/tracker/Git evidence | SPECIFICATION APPROVED; S1/S2/S3 ACCEPTED; F0 IMPLEMENTATION COMPLETE; F0 CLOSURE EVIDENCE PASSED; F0 ACCEPTED; tested implementation: 8b6e714; closure evidence: a2554ab |

Policy evidence remains assigned to its owning workstream. F0 reviews the specification; it must not claim the following future runtime checks pass:

| Revised policy | Required later test matrix | Owner/status |
| --- | --- | --- |
| PM8-D02 roots | Canonical aliases to equal/nested roots detected; configured roots with safely resolvable symlink ancestors can pass; discovered symlink entries not followed; escapes and unresolvable/unsafe containment fail closed; provider race/containment behavior documented by platform tests | F2/F6 review obligation; NOT IMPLEMENTED / NOT RUN |
| PM8-D09 traceability/privacy | Every executed relocation maps normalized source-relative path + source content hash to destination-relative path + verified content hash/status; identical bytes at distinct source paths remain individually traceable; default manifest omits absolute paths/unnecessary sensitive metadata; public/export redaction leaves restricted mapping intact; planned mappings never imply completed operations | F1 schema/export, F5 plan, F6/F9 integration; NOT IMPLEMENTED / NOT RUN |
| PM8-D11 geometry | Valid sform preference; valid qform fallback; both-present agreement/disagreement and invalid/missing-transform cases; disagreement recorded explicitly; F7 chooses comparison/tolerance from geometry tests including units/precision; no silent reverse-conversion semantics | F7/F8; M8 semantics deferred; NOT IMPLEMENTED / NOT RUN |
| PM8-D01 owner-supplied READY | Later owner emits READY only after successful validator/reader evidence; unsuccessful/inconclusive evidence never emits READY; READY path still exercises definitive M6/M7 processing contracts | Later owning validator/reader and F9 integration; NOT IMPLEMENTED / NOT RUN |

These tests exercise missing behavior and guard externally meaningful boundaries. No counts are predeclared. State tables should test both coherent and conflicting examples, not merely constructor invocation or enum existence.

Historical slice plan and remaining gates (S1/S2 instructions superseded by recorded acceptance):

1. Slice F0-S1 (accepted at 76b676d): tests first for U03-S1 and U04-U10, then six immutable contract types and only constructor invariants. Run the unit test, inspect each failure, run M5+architecture subsystem, display diff and confirm no adapters/algorithms appeared. Do not declare, stub or implement the conservative factory until S2. For this environment perform static/source review only; no compilation/test claim. Stop after S1 and wait for actual Java 21/Maven results before any accepted checkpoint.
2. Slice F0-S2 (ACCEPTED at ea40616; executable results recorded below): tests first for U01-U02/U03-S2/U11/I01-I05, then only the conservative factory in the existing new record. Run focused unit/integration tests; rerun M5 and M6/M7 relevant regression; show diff and inspect readiness/privacy responsibility.
3. Slice F0-S3 (ACCEPTED at 8b6e714; executable results recorded below): add B01/B02, review all public/generic references and constant messages. Run focused architecture tests and relevant subsystem; show diff. No extra production abstraction.
4. Close only with actual full regression, diff check, criterion mapping, protected-file proof and coherent proposed F0 commit. Stop before F1.

Historical command plan (superseded by user-reported S1/S2/S3 and F0 closure results below; commands were not executed in the assistant environment):

    mvn -Dtest=FormatAssessmentTest test
    mvn -Dtest=FormatAssessmentTest,FormatAssessmentIntegrationTest test
    mvn -Dtest=FoundationContractBoundaryTest test
    mvn -Dtest=FormatDetectionServiceTest,DependencyDirectionTest,M6BoundaryTest,PackageStructureTest,PresentationBoundaryTest test
    mvn -Dtest=DicomSeriesServiceTest,DicomToNiftiServiceTest,DicomToNiftiServiceHardeningTest,ConversionValidationReportTest,Nifti1VolumeWriterTest,Nifti1VolumeWriterHardeningTest,NiftiAffineMapperTest test
    mvn clean test
    git diff --check

All existing test classes named in these commands are EXISTING — verified; PresentationBoundaryTest and NiftiAffineMapperTest declarations are present in the inspected test tree. New test names are PROPOSED — new. Actual commands/results, executed test names and counts replace these intentions in the closure ledger.

### 11. Expected file-change budget

S1 accepted checkpoint 76b676d contains the eight budgeted paths: this tracker, six approved production files and one test file. S2 changes exactly four paths relative to that checkpoint: modify this tracker, FormatAssessment.java and FormatAssessmentTest.java; add FormatAssessmentIntegrationTest.java. S2 is ACCEPTED at ea40616; the S2 checkpoint was committed and pushed. This tracker-only update creates no additional commit. The standalone tracker contains identical bytes.

Approved full F0 budget is at most ten paths, all explicitly named; S1 uses eight and S2 uses the already-budgeted integration test. S3 adds only the budgeted architecture test and modifies this tracker: exactly two paths relative to 14207bc. The table below retains the original full-F0 additions relative to the audit baseline:

| Action | Exact path | Classification |
| --- | --- | --- |
| Update | docs/planning/PRE-M8-FOUNDATION-TRACKER.md | Existing tracker created by authorized setup |
| Add | src/main/java/org/cbihi/mrinormalizer/application/dataset/model/FormatAssessment.java | PROPOSED — new |
| Add | src/main/java/org/cbihi/mrinormalizer/application/dataset/model/FormatVariant.java | PROPOSED — new |
| Add | src/main/java/org/cbihi/mrinormalizer/application/dataset/model/ValidityStatus.java | PROPOSED — new |
| Add | src/main/java/org/cbihi/mrinormalizer/application/dataset/model/SupportStatus.java | PROPOSED — new |
| Add | src/main/java/org/cbihi/mrinormalizer/application/dataset/model/ConversionReadiness.java | PROPOSED — new |
| Add | src/main/java/org/cbihi/mrinormalizer/application/dataset/model/AssessmentReason.java | PROPOSED — new |
| Add | src/test/java/org/cbihi/mrinormalizer/FormatAssessmentTest.java | PROPOSED — new |
| Add | src/test/java/org/cbihi/mrinormalizer/FormatAssessmentIntegrationTest.java | PROPOSED — new |
| Add | src/test/java/org/cbihi/mrinormalizer/architecture/FoundationContractBoundaryTest.java | PROPOSED — new |

Budget unchanged: six production files, three test files and this tracker (ten repository paths). READY adds one member to the already budgeted ConversionReadiness enum; DETECTION_NOT_COMPLETED adds one reason to the already budgeted AssessmentReason enum. No file or dependency beyond this approved budget is required. S2 adds only the already-approved public factory method in the accepted record.

No dependency, version, packaging, broad formatter, documentation sweep or module migration. If implementation needs an extra path, altered public contract, early ADR or different responsibility, explain and review that scope adjustment first.

### 12. Explicit protected files/components

- All baseline files under src/main and src/test are protected from modification in F0. New files are limited to section 11. This includes all EXISTING — verified components listed in section 7 and their callers/tests.
- In particular, preserve EXISTING — verified: DefaultDicomSeriesService, Dcm4cheInstanceReader, SelectedSourceFingerprint, DicomSeriesService, DicomToNiftiService, DefaultDicomToNiftiService, NiftiAffineMapper, NiftiVolumeWriter, Nifti1VolumeWriter, ConversionValidationReport and all generic volume/geometry/scalar/intensity types. No algorithm extraction, duplication or port relocation.
- Preserve pom.xml, AGENTS.md, PROJECT.md, README.md, MILESTONES.md, DECISIONS.md, DEVELOPMENT_LOG.md, PROMPTS.md, ai/, docs/testing/ and all docs/architecture/ files. Preserve the canonical proposal PDF and project README. Only the tracker is an allowed documentation mutation.
- Preserve the uploaded archive and attached source text/PDF. In its separate extracted working copy, do not stage, delete, rename or edit .roomodes, docs/architecture/ARCHITECTURE-REVISION-1.0-RC2.md or docs/architecture/ARCHITECTURE-REVISION-1.0-RC3.md. Do not copy them into the canonical change set.
- Preserve M6/M7 tags/history; no force push, reset, rebase, squash or M8 branch work. The ZIP's existing M8 branch name is historical, not implementation authorization.

## Historical F0-S1 source review (superseded by acceptance evidence below)

Historical S1 authorization: S1 only, granted on 2026-10-04. The following records the original source review before the user supplied successful executable results and accepted 76b676d. U03 responsibilities were split before Java edits: U03-S1 constructor rejection; U03-S2 factory rejection. This is bookkeeping only, with no semantic or budget change. Tests were authored before the six production files, then refined during static review. No red/green test cycle was executed.

S1 implemented source: five enum declarations and the seven-component record with canonical-constructor validation, private pure validation helpers, defensive copying and lexical reason ordering. At the S1 checkpoint, no fromDetection declaration, stub or implementation exists in production source. No adapters, ports, paths, byte parsing, hashing, persistence, metadata reader, convenience upgrade method, evidence token, conversion algorithm or dependency was added. At that checkpoint only S1 test source is present; S2 factory/integration and S3 architecture tests were deferred.

Every newly added type/test method in this section is PROPOSED — new relative to the audited baseline; source existence and static inspection do not mean compilation or runtime verification. Referenced existing raw detection types remain EXISTING — verified.

| Approved constructor invariant | S1 test methods in FormatAssessmentTest | Evidence status |
| --- | --- | --- |
| C01 / rule 1: required components/raw enums, non-null unique reasons, coherent raw outcome/diagnostic; UNKNOWN + NONE/INVALID_* rejected before refinement | rejectsMissingComponentsAndRawEnums; rejectsNullAndDuplicateReasonEntries; rejectsMalformedUnknownEvidenceBeforeRefinement; positiveRawOutcomesRequireNoneDiagnostic; corruptRawOutcomeRequiresCorruptDiagnostic | Source reviewed; NOT COMPILED / NOT RUN |
| C02 / rule 2: family/variant compatibility, unknown-family restrictions, positive recognition cannot change family | rejectsIncompatibleFamiliesAndVariants; rawPositiveFamilyCannotBeRewritten; unresolvedUnknownEvidenceRequiresExactValidityAndReason | Source reviewed; NOT COMPILED / NOT RUN |
| C03 / rule 3: raw corruption cannot be rehabilitated; unresolved unknown keeps exact state/reason; explicit later refinement retains raw evidence and drops unresolved reasons | corruptRawEvidenceCannotBeRehabilitated; unresolvedUnknownEvidenceRequiresExactValidityAndReason; laterRefinementPreservesRawUnknownEvidenceAndExtensionHint; refinedKnownFamilyMustDropUnresolvedDetectionReasons | Source reviewed; NOT COMPILED / NOT RUN |
| C04 / rule 4: exact enum sets, deterministic pending state, invalid/unsupported/unknown blocked, no unexplained veto | approvedEnumMemberSetsAreExact; recognizedPendingStatesRequireValidationReason; invalidUnsupportedAndUnknownStatesMustBeBlocked; blockedStateNeedsAnApplicableCause | Source reviewed; NOT COMPILED / NOT RUN |
| C05 / rule 5: known unsupported variants require blocked/unsupported status and variant reason; unspecified NIfTI cannot be supported/ready | unsupportedVariantsRemainBlockedEvenWhenValid; unspecifiedNiftiCannotBeSupportedOrReady | Source reviewed; NOT COMPILED / NOT RUN |
| C06 / rule 6: READY requires valid supported known variant and empty reasons; every unsuccessful state needs applicable reasons; immutable canonical reason list | readyKnownSupportedInputsAcceptImmutableEmptyReasons; readyRejectsUnsuccessfulStatusesAndEveryPendingOrBlockingReason; emptyReasonsRejectedForEveryUnsuccessfulState; recognizedPendingStatesRequireValidationReason; defensivelyCopiesReasonsAndOrdersThemLexically | Source reviewed; NOT COMPILED / NOT RUN |
| C07 / rule 7: each reason applies independently; invalid/unsupported facts need their respective reasons and cannot erase one another | eachReasonMustApplyToTheSuppliedState; requiredCausesDoNotMakeOtherReasonsApplicable; invalidAndUnsupportedFactsCannotEraseEachOther; failedValidationBlocksRecognizedInputWithItsOwnReason; unsupportedProfileCanBeValidButRemainsBlocked; corruptRawEvidenceCannotBeRehabilitated | Source reviewed; NOT COMPILED / NOT RUN |
| C08 / rule 8: preserve complete raw result and extension hint; do not infer or perform processing | laterRefinementPreservesRawUnknownEvidenceAndExtensionHint; readyKnownSupportedInputsAcceptImmutableEmptyReasons; scope/import/API and protected-tree source review | Source reviewed; NOT COMPILED / NOT RUN; later owning-validator/reader and M6/M7 integration enforcement deferred |

Reason applicability coverage: unresolvedUnknownEvidenceRequiresExactValidityAndReason exercises each of the eight allowed raw UNKNOWN diagnostics; corruptRawEvidenceCannotBeRehabilitated exercises the raw-corrupt reason; recognizedPendingStatesRequireValidationReason exercises pending validation; unsupportedVariantsRemainBlockedEvenWhenValid exercises variant rejection; unsupportedProfileCanBeValidButRemainsBlocked exercises profile rejection; failedValidationBlocksRecognizedInputWithItsOwnReason exercises failed validation. eachReasonMustApplyToTheSuppliedState and requiredCausesDoNotMakeOtherReasonsApplicable exercise additional inapplicable reasons despite otherwise sufficient causes.

Static evidence executed: protected tracked-file SHA-256 comparison against the S1 starting tree; exactly six new production files plus one test; only tracker modified among previously tracked files; source imports/public API reviewed; no production factory symbol; no staged changes; HEAD remains 1147129. git diff --check and baseline whitespace checks passed. The full S1 diff includes untracked Java files and tracker changes from the approved pre-S1 specification; it is not limited to Git's tracked-file diff.

At that original S1 review, Java 21/Maven verification was still required (commands below were proposed, NOT executed here):

    mvn -Dtest=FormatAssessmentTest test
    mvn -Dtest=FormatAssessmentTest,FormatDetectionServiceTest,DependencyDirectionTest,M6BoundaryTest,PackageStructureTest,PresentationBoundaryTest test
    mvn -Dtest=DicomSeriesServiceTest,DicomToNiftiServiceTest,DicomToNiftiServiceHardeningTest,ConversionValidationReportTest,Nifti1VolumeWriterTest,Nifti1VolumeWriterHardeningTest,NiftiAffineMapperTest test

The focused run must establish compilation/test discovery and every constructor case; subsystem runs must establish no regressions. Record actual results, executed counts, failures/errors/skips and the tested tree before S1 acceptance. F0 closure still requires actual mvn clean test after the remaining authorized slices. That original source review supplied no Java compiler output, test-pass result or accepted checkpoint. It did not authorize S2 or later work. The subsequent user acceptance and S2 authorization are recorded below.

## F0-S1 acceptance and F0-S2 accepted evidence

S1 is accepted by the user at remote checkpoint 76b676dfb2bfa7376839e99fb390c2579f9cc783. User-reported evidence: Java 21.0.12.1 / Maven 3.9.16; FormatAssessmentTest 26/26; M5 plus architecture gate 56/56; protected M6/M7 regression 79/79; zero failures/errors/skips; git diff --check passed. These executions were not performed in this environment. Fresh Git fetch verified the remote feature/pre-m8-foundation ref at that exact checkpoint; a clean isolated S2 worktree was created there.

Historical S2 source preparation (pre-execution, superseded by the accepted evidence below): authorization was F0-S2 only. Tests were authored first, followed by the pure factory. No compilation or red/green test execution occurred here. The only production API addition is public static FormatAssessment fromDetection(DetectionResult detection) in the existing FormatAssessment.java. It rejects null/malformed raw evidence with fixed argument errors, reuses the existing raw-coherence helper and canonical constructor, preserves the original DetectionResult object and extensionMismatch, and emits only REQUIRES_VALIDATION or BLOCKED. It never infers NIfTI version, VALID, SUPPORTED or READY. There is no filename inspection, filesystem access, probe invocation, parsing or pixel decoding in the factory.

EXISTING — verified at 76b676d: the S1 record components, constructor, all private validation helpers, five enum member sets and all 26 S1 test methods. PROPOSED — new relative to that checkpoint: the factory, appended S2 unit methods and FormatAssessmentIntegrationTest. All accepted S1 production source and test bodies outside the factory/test additions are unchanged.

| ID | Authored test methods | Executable evidence / status |
| --- | --- | --- |
| U01 | FormatAssessmentTest.factoryMapsAllFiveRawOutcomesConservatively | Written; all five raw outcomes; PASS / ACCEPTED: user-reported focused run, 40 tests, zero failures/errors/skips |
| U02 | FormatAssessmentTest.factoryMapsExactlyEightAcceptedUnknownDiagnostics | Written; exactly eight UNKNOWN diagnostics and distinct mappings; PASS / ACCEPTED: user-reported focused run, 40 tests, zero failures/errors/skips |
| U03-S2 | FormatAssessmentTest.factoryRejectsMalformedUnknownEvidence; factoryRejectsNullDetectionAndRawComponents; factoryRejectsPositiveOutcomesWithNonNoneDiagnostic; factoryRejectsCorruptWithNonCorruptDiagnostic | Written; all malformed UNKNOWN combinations, fixed null error, raw component nulls, positive/corrupt contradictions; both extension hints for all non-null invalid pairs; PASS / ACCEPTED: user-reported focused run, 40 tests, zero failures/errors/skips |
| U11 | FormatAssessmentTest.factoryExhaustsAcceptedCombinationsAndPreservesRawEvidence | Written; all 14 accepted raw pairs with both extension hints (28 fixtures); exact state/reason plus raw identity/fields; never READY/VALID/SUPPORTED; PASS / ACCEPTED: user-reported focused run, 40 tests, zero failures/errors/skips |
| I01 | FormatAssessmentIntegrationTest.nifti1RecognitionRemainsUnspecifiedAndUnvalidated; nifti2RecognitionDoesNotInferVersionSupportOrReadiness | Written; real M5 recognition of both header versions remains unspecified/unassessed/requires validation; PASS / ACCEPTED: user-reported focused run, 40 tests, zero failures/errors/skips |
| I02 | FormatAssessmentIntegrationTest.truncatedGzipTrailerRecognitionDoesNotBecomeValid | Written; real M5 recognition of truncated trailer never establishes validity/support/readiness; PASS / ACCEPTED: user-reported focused run, 40 tests, zero failures/errors/skips |
| I03 | FormatAssessmentIntegrationTest.wrongAndExtensionlessDicomRequireValidation | Written; real M5 synthetic identifying metadata with no Pixel Data, both mismatching extension cases retained; PASS / ACCEPTED: user-reported focused run, 40 tests, zero failures/errors/skips |
| I04 | FormatAssessmentIntegrationTest.missingDirectoryAndInvalidReferenceKeepDistinctDiagnostics; emptyAndRandomNonmatchKeepDistinctReasons | Written; actual missing file, directory, NUL reference, empty input and random non-match preserve distinct diagnostics/reasons; PASS / ACCEPTED: user-reported focused run, 40 tests, zero failures/errors/skips |
| I05 | FormatAssessmentIntegrationTest.controlledIoAndBudgetDiagnosticsRemainBlockedAndDistinct | Written; real M5 service with controlled test probe and existing NIfTI probe preserves IO_ERROR/INPUT_TOO_LARGE; no permission assumptions; PASS / ACCEPTED: user-reported focused run, 40 tests, zero failures/errors/skips |

The integration fixture helpers only create synthetic recognition inputs in temporary test directories using existing test dependencies. They are not production readers/validators. INPUT_NOT_READABLE is exhaustively covered as raw evidence by U02/U11; a permission-dependent integration fixture is deliberately not claimed.

Historical S2 source-review evidence (pre-execution, superseded by acceptance below): removing the added factory block restores FormatAssessment.java byte-for-byte to 76b676d; removing the appended tests/import and restoring the class comment restores FormatAssessmentTest.java byte-for-byte. SHA-256 comparison covers every other tracked file, including all enums, M5/M6/M7 source/tests, architecture tests, build and other documentation. Only the three authorized existing paths changed; the only new repository file is the authorized integration test. HEAD remains 76b676d; no staging or commit. git diff --check and git diff 76b676d --check passed; the complete four-file patch also passed whitespace review and git apply --check against an isolated baseline copy. These are source/Git checks, not executable Java evidence.

Historical S2 verification commands proposed during source review (not executed in the assistant environment; actual user-reported commands/results follow):

    mvn -Dtest=FormatAssessmentTest,FormatAssessmentIntegrationTest test
    mvn -Dtest=FormatDetectionServiceTest,DependencyDirectionTest,M6BoundaryTest,PackageStructureTest,PresentationBoundaryTest test
    mvn -Dtest=DicomSeriesServiceTest,DicomToNiftiServiceTest,DicomToNiftiServiceHardeningTest,ConversionValidationReportTest,Nifti1VolumeWriterTest,Nifti1VolumeWriterHardeningTest,NiftiAffineMapperTest test
    git diff --check

### Actual S2 executable verification and acceptance

On 2026-10-04 the user reports that F0-S2 executable verification passed and accepts S2. Status: **ACCEPTED — ea40616**. S1 is accepted at 76b676dfb2bfa7376839e99fb390c2579f9cc783. S2 is accepted at ea4061672a293745ac5f5fc1b0d8d240d217e64e; the checkpoint was committed and pushed. Java 21.0.12.1 / Maven 3.9.16 was already established for S1. The following are actual user-reported results, not executions or independently inspected build logs from this assistant environment. Fresh Git fetch on 2026-10-04 independently resolved origin/feature/pre-m8-foundation to ea4061672a293745ac5f5fc1b0d8d240d217e64e, confirming the supplied remote checkpoint. The assistant did not rerun the Java/Maven commands.

| Actual command | Tests | Failures | Errors | Skipped | Result |
| --- | --- | --- | --- | --- | --- |
| `mvn "-Dtest=FormatAssessmentTest,FormatAssessmentIntegrationTest" test` | 40 | 0 | 0 | 0 | BUILD SUCCESS |
| `mvn "-Dtest=FormatAssessmentTest,FormatAssessmentIntegrationTest,FormatDetectionServiceTest,DependencyDirectionTest,M6BoundaryTest,PackageStructureTest,PresentationBoundaryTest" test` | 70 | 0 | 0 | 0 | BUILD SUCCESS |
| `mvn "-Dtest=DicomSeriesServiceTest,DicomToNiftiServiceTest,DicomToNiftiServiceHardeningTest,ConversionValidationReportTest,Nifti1VolumeWriterTest,Nifti1VolumeWriterHardeningTest,NiftiAffineMapperTest" test` | 79 | 0 | 0 | 0 | BUILD SUCCESS |

The user also reports `git diff --check` passed, with only the known LF→CRLF working-copy warning on the tracker and no whitespace errors. The three run counts belong to their respective commands and are not added into a unique-test total because the runs overlap.

Historical S2 acceptance/bookkeeping context (S3 authorization below supersedes the then-pending S3 gate): this evidence covers S2 compilation, focused unit/integration execution and the specified assessment/M5/architecture and protected M6/M7 regression gates. U01/U02/U03-S2/U11/I01-I05 are accepted under the 40-test focused run. S3's new architecture coverage was not implemented or tested by these existing architecture gates. F0 remains incomplete; full F0 closure and S3 authorization remain separate pending steps. The S2 checkpoint is accepted and recorded above. This tracker-only update makes no Java source/test change and creates no commit. S3, F1-F11 and M8 remain blocked.

## F0-S3 acceptance and F0 closure evidence

Historical S3 preparation authorization: F0-S3 only. Accepted anchors: S1 76b676dfb2bfa7376839e99fb390c2579f9cc783; S2 ea4061672a293745ac5f5fc1b0d8d240d217e64e; tracker checkpoint 14207bcd494509584b9dfba3ebd3be62b73367d2. Fresh Git fetch resolved the remote branch to 14207bc; S3 was prepared in a clean isolated worktree at that checkpoint. S1/S2 acceptance and their actual user-reported results above remain unchanged.

Historical pre-execution status (superseded by verification and acceptance below): **S3 SOURCE PREPARED / EXECUTABLE VERIFICATION PENDING**. Only FoundationContractBoundaryTest.java and this tracker changed. The new test uses the existing JUnit dependency and Java reflection; no production type, dependency, port, service, adapter or separate utility is added. No compilation, test execution or red/green cycle was performed in the assistant environment. At that pre-execution review, no S3 acceptance or checkpoint was claimed; subsequent user-reported acceptance is recorded below.

| Criterion | Authored method in FoundationContractBoundaryTest | Coverage / evidence |
| --- | --- | --- |
| B01 | publicBoundaryReferencesOnlyApprovedValueTypes | Inspects all six actual compiled type declarations: record components, public fields/constructors/methods, generic parameter/return types, type parameters, declared exception types and generic supertypes. Exact allowlist limits project references to six F0 types plus DetectionResult/ImagingFormat. String/Object are permitted only in required generated record/enum methods; enum values arrays are the only approved array boundary. Unexpected types, generic forms, free-text fields and exception signatures fail. PASS / ACCEPTED: user-reported focused architecture gate, 5 tests, zero failures/errors/skips |
| B02 | readinessAndReasonMembersRemainExactlyApproved | Exact readiness and assessment-reason sets. PASS / ACCEPTED: user-reported focused architecture gate, 5 tests, zero failures/errors/skips |
| B02 | assessmentRecordAndCanonicalConstructorRemainFrozen | Actual record status, all seven ordered component names/types, exact List<AssessmentReason> generic argument and sole public canonical constructor. PASS / ACCEPTED: user-reported focused architecture gate, 5 tests, zero failures/errors/skips |
| B02 | publicDeclarationsRemainOnlyApprovedValueApis | Actual package placement and record/enum declarations; exact declared public method sets permit generated accessors/equals/hashCode/toString and enum values/valueOf. The sole explicit public factory has exact static signature fromDetection(DetectionResult) -> FormatAssessment. Extra setters, factories, readiness shortcuts, conversion/validation/I/O methods or public data fields fail. PASS / ACCEPTED: user-reported focused architecture gate, 5 tests, zero failures/errors/skips |
| B01/B02 | constructorAndFactoryFailuresUseFixedNonIdentifyingMessages | Representative null/raw-coherence, duplicate-reason, family, READY and reason-applicability failures assert exact fixed IllegalArgumentException messages, null cause and no suppressed payload. Both extension hints retain the same raw-contract message. Combined with the typed API checks, there is no path/free-text input or throwable payload channel. PASS / ACCEPTED: user-reported focused architecture gate, 5 tests, zero failures/errors/skips |

The allowlist intentionally rejects arbitrary java.* types: filesystem/path, byte/stream payload, Throwable and serializer/processing abstractions are not approved merely because they are Java types. Reflection inspects declarations, not comments. Inherited Object/Enum language scaffolding is not treated as a new explicit API; declared/generated methods are handled explicitly. These tests constrain the public value boundary, not the authenticity of later validator evidence or the internals of the approved DetectionResult leaf type.

B02 conservative factory behavior reuses the accepted S2 U11 method FormatAssessmentTest.factoryExhaustsAcceptedCombinationsAndPreservesRawEvidence, plus the other accepted S2 unit/integration tests, in the passed combined verification command recorded below. S3 does not duplicate the exhaustive mapping table or modify those tests. Protected M5/M6/M7 implementations/callers are covered by the source comparison and existing regression gates; this reflective test does not invoke Git or read source paths at runtime.

Historical pre-execution static review: all previously tracked files except this tracker are byte-identical to 14207bc, including all six F0 production types, both assessment tests, every existing architecture test, M5/M6/M7 source/tests, pom.xml, other tracked documentation. The separately extracted .roomodes/RC2/RC3 files were not touched. The only new Java file is src/test/java/org/cbihi/mrinormalizer/architecture/FoundationContractBoundaryTest.java. No production Java changed. git diff --check passed; the complete two-file patch includes the untracked new test and passes whitespace/applicability checks against an isolated 14207bc copy. HEAD remains 14207bc; no staging or commit. These are source/Git checks, not Java executable evidence.

Historical proposed local Java 21.0.12.1 / Maven 3.9.16 commands (superseded by the supplied passing results below; not executed in the assistant environment):

    mvn "-Dtest=FoundationContractBoundaryTest" test
    mvn "-Dtest=FoundationContractBoundaryTest,FormatAssessmentTest,FormatAssessmentIntegrationTest,FormatDetectionServiceTest,DependencyDirectionTest,M6BoundaryTest,PackageStructureTest,PresentationBoundaryTest" test
    mvn "-Dtest=DicomSeriesServiceTest,DicomToNiftiServiceTest,DicomToNiftiServiceHardeningTest,ConversionValidationReportTest,Nifti1VolumeWriterTest,Nifti1VolumeWriterHardeningTest,NiftiAffineMapperTest" test
    git diff --check

Historical pre-execution stop condition (superseded): compilation/test discovery, reflection assertions and regressions awaited local verification, and the source review prohibited accepting/committing S3 before those results. The passing verification below closes that execution gate. This bookkeeping update itself does not commit or push.

### Actual S3 executable verification and F0 closure regression

On 2026-10-04 the user supplies passing S3 executable verification and final F0 closure regression for the exact tree after applying the complete S3 patch. These are user-reported actual results, not commands rerun by the assistant. Environment: Java 21.0.12.1 / Maven 3.9.16; the clean build compiled production and tests under Java release 21.

| Actual gate / command | Tests | Failures | Errors | Skipped | Result |
| --- | --- | --- | --- | --- | --- |
| S3 focused architecture gate | 5 | 0 | 0 | 0 | BUILD SUCCESS |
| Combined F0/M5/architecture gate | 75 | 0 | 0 | 0 | BUILD SUCCESS |
| Protected M6/M7 regression | 79 | 0 | 0 | 0 | BUILD SUCCESS |
| Final exact-tree closure after applying complete S3 patch: `mvn clean test` | 174 | 0 | 0 | 0 | BUILD SUCCESS |

The user reports `git diff --check` passed except for the known LF→CRLF informational warning on the tracker, with no whitespace errors. No production Java changed during S3. The S3 scope remains exactly the new FoundationContractBoundaryTest.java plus tracker updates; no existing Java test, build file, dependency or protected implementation changed. Focused and subsystem run counts overlap and are not added together; 174 is the actual final full-regression count.

B01/B02 are **PASS / ACCEPTED**, and F0-A12 full regression is **PASS**. S1 remains accepted at 76b676dfb2bfa7376839e99fb390c2579f9cc783; S2 remains accepted at ea4061672a293745ac5f5fc1b0d8d240d217e64e. Current state:

    S1 ACCEPTED — 76b676d
    S2 ACCEPTED — ea40616
    S3 ACCEPTED — 8b6e714
    F0 IMPLEMENTATION COMPLETE
    F0 CLOSURE EVIDENCE PASSED
    F0 ACCEPTED
    Tested implementation checkpoint: 8b6e714216f7d3d174a81cdc9a120d88a4348f80
    Closure evidence record: a2554ab93a7ff2fe5c4e547a7fb3119b57ac367c
    F1-F11 BLOCKED
    GUI BLOCKED
    M8 BLOCKED

The user-supplied tested implementation checkpoint is 8b6e714216f7d3d174a81cdc9a120d88a4348f80; the closure evidence record is a2554ab93a7ff2fe5c4e547a7fb3119b57ac367c. Fresh Git fetch confirms the remote branch at a2554ab, and comparison with 8b6e714 shows only this tracker differs. The tested Java tree is unchanged; no Maven rerun is needed or performed for this docs-only reconciliation. Future PM8 policy implementations and their owning workstream acceptance remain deferred; passing F0 contracts does not implement F1-F11, GUI or M8 and does not authorize F1. Stop after this tracker-only update.

## Current evidence, defects and approval gate

| Date | Command/review | Exact result | Meaning |
| --- | --- | --- | --- |
| 2026-10-04 | git ls-remote for requested branch | 0b91666c03d6247a6882c164aa6b033d9b761101 | Fresh remote anchor |
| 2026-10-04 | git rev-parse HEAD before edits; git status --porcelain | Exact anchor; clean checkout | Safe isolated change target |
| 2026-10-04 | git diff d9ad7ea HEAD -- src/main src/test pom.xml | Empty | Protected imaging tree unchanged at audit anchor |
| 2026-10-04 | java -version; mvn -version | Java 17.0.20; mvn not found | Required build runtime unavailable |
| 2026-10-04 | mvn clean test attempted | Shell could not find mvn; tests never started | No test evidence generated |
| 2026-10-04 | git diff --check before setup; git diff --cached --check on setup | Exit 0 | Whitespace checks executed |
| 2026-10-04 | git commit of tracker/setup | 1147129; one tracker file | Documentation-only local checkpoint |
| 2026-10-04 | git diff --check; git diff 0b91666 --check | Exit 0 for working specification and entire baseline change | Documentation whitespace verified |
| 2026-10-04 | git diff 0b91666 --name-only | Only docs/planning/PRE-M8-FOUNDATION-TRACKER.md | All baseline source/tests/build/docs protected from change |
| 2026-10-04 | Byte comparison of three protected extracted files against ZIP members | All three byte-identical | Local untracked files preserved |
| 2026-10-04 | Review-document checks | Rule at top; all twelve requested sections present | Document structure verified; not a Java test |
| 2026-10-04 | Specification-only revision requested by user | All six corrections incorporated; enum additions READY and DETECTION_NOT_COMPLETED; constructor/factory/negative cases and future policy evidence revised | Production authorization still pending; no F1 implementation |
| 2026-10-04 | Final specification-only corrections | Readiness reduced to REQUIRES_VALIDATION/BLOCKED/READY; malformed raw UNKNOWN + NONE/INVALID_* rejected; affected constructor/factory/reason/test descriptions revised | Specification otherwise approved; final production authorization pending; budget unchanged |
| 2026-10-04 | Revision: git diff --check; git diff 0b91666 --check; git diff 0b91666 --name-only; git diff d9ad7ea -- src/main src/test pom.xml | Both whitespace checks passed; only tracker path differs from baseline; protected source/tests/build comparison empty | Documentation-only revision; no Java tests executed and no new commit/push |
| 2026-10-04 | User-reported S2 focused assessment unit/integration command (exact command above) | 40 tests; 0 failures/errors/skipped; BUILD SUCCESS | S2 executable focused evidence accepted |
| 2026-10-04 | User-reported S2 combined assessment/M5/architecture command (exact command above) | 70 tests; 0 failures/errors/skipped; BUILD SUCCESS | Existing M5/architecture gate accepted; no S3 implementation implied |
| 2026-10-04 | User-reported protected M6/M7 regression command (exact command above) | 79 tests; 0 failures/errors/skipped; BUILD SUCCESS | Protected regression evidence accepted |
| 2026-10-04 | User-reported git diff --check | Passed; known tracker LF→CRLF working-copy warning only; no whitespace errors | S2 ACCEPTED — ea40616; F0 incomplete |
| 2026-10-04 | S2 checkpoint committed/pushed (user-reported); fresh Git fetch confirms remote branch | ea4061672a293745ac5f5fc1b0d8d240d217e64e | S2 ACCEPTED — ea40616; S3 NOT AUTHORIZED; F0 INCOMPLETE |
| 2026-10-04 | User-reported S3 focused architecture gate | 5 tests; 0 failures/errors/skipped; BUILD SUCCESS | B01/B02 PASS / ACCEPTED |
| 2026-10-04 | User-reported combined F0/M5/architecture gate | 75 tests; 0 failures/errors/skipped; BUILD SUCCESS | S3 and reused F0/M5/architecture coverage accepted |
| 2026-10-04 | User-reported protected M6/M7 regression | 79 tests; 0 failures/errors/skipped; BUILD SUCCESS | Protected regression evidence passed |
| 2026-10-04 | User-reported final exact-tree mvn clean test after complete S3 patch | 174 tests; 0 failures/errors/skipped; BUILD SUCCESS; production/tests compiled under Java release 21 | F0-A12 PASS; F0 ACCEPTED; tested implementation: 8b6e714; closure evidence: a2554ab |
| 2026-10-04 | User-reported git diff --check and S3 scope | Passed; known tracker LF→CRLF informational warning only; no production Java changes; new architecture test plus tracker only | S3 ACCEPTED — 8b6e714; F1-F11, GUI and M8 blocked |

Confirmed code defects: none newly established by executable reproduction in this pass. Documentation inconsistencies and runtime limitations are recorded above. Do not treat architectural gaps or unused helpers as a freshly reproduced imaging defect; do not fix them during F0.

- [x] Repository-grounded review/specification prepared with source/caller/test inspection.
- [x] Tracker/setup committed locally, isolated from the dirty ZIP working copy.
- [x] Corrected F0 specification approved; S1 source and constructor tests prepared.
- [x] User supplies passing Java 21/Maven S1 focused and protected-regression evidence.
- [x] S1 accepted at 76b676d; remote checkpoint verified through Git.
- [x] S2 explicitly authorized; tests first and pure factory source prepared within four-path budget.
- [x] S2 static review, complete diff, protected-source comparison and whitespace checks prepared.
- [x] User reports S2 Java 21/Maven executable verification: focused 40 tests, combined assessment/M5/architecture 70 tests and protected M6/M7 79 tests; all BUILD SUCCESS with zero failures/errors/skips.
- [x] S2 ACCEPTED — ea40616; full checkpoint ea4061672a293745ac5f5fc1b0d8d240d217e64e was committed and pushed; remote ref verified through Git.
- [x] S3 explicitly authorized for exactly the dedicated B01/B02 architecture test and this tracker.
- [x] S3 reflective API/shape and fixed-message tests authored; protected-source static review completed.
- [x] S3 executable verification passed: focused 5 tests, combined F0/M5/architecture 75 tests and protected M6/M7 79 tests; zero failures/errors/skips; BUILD SUCCESS.
- [x] S3 ACCEPTED — 8b6e714. No factory declaration/stub/implementation was introduced in S1.
- [x] F0 IMPLEMENTATION COMPLETE under the accepted test matrix.
- [x] F0 CLOSURE EVIDENCE PASSED: exact-tree mvn clean test, 174 tests, zero failures/errors/skips, BUILD SUCCESS; production/tests compiled under Java release 21.
- [x] S3/F0 accepted checkpoints recorded: tested implementation 8b6e714216f7d3d174a81cdc9a120d88a4348f80; closure evidence record a2554ab93a7ff2fe5c4e547a7fb3119b57ac367c.
- [x] F0 ACCEPTED.
- [ ] F1 explicitly authorized; currently BLOCKED.

**STOP AFTER THIS TRACKER-ONLY UPDATE: S1 ACCEPTED — 76b676d; S2 ACCEPTED — ea40616; S3 ACCEPTED — 8b6e714; F0 IMPLEMENTATION COMPLETE; F0 CLOSURE EVIDENCE PASSED; F0 ACCEPTED; F1-F11 BLOCKED; GUI BLOCKED; M8 BLOCKED. No F1 authorization is implied.**
