# Pre-M8 Foundation Completion Tracker

> **A workstream is not complete because its classes exist. It is complete only when its behavioral contracts are implemented, negative cases are tested, integration evidence passes, and the tracker records the verified checkpoint.**

## Program control

- Date: 2026-10-05 (Asia/Calcutta).
- Branch: feature/pre-m8-foundation.
- Canonical baseline: 0b91666c03d6247a6882c164aa6b033d9b761101.
- M6 protected checkpoint: cb7769e; protected tag: pre-demo-m6-2026-10-02.
- M7 protected checkpoint: d9ad7ea; protected tag: pre-demo-m7-2026-10-03.
- Current authorization: tracker-only F1-S3 acceptance reconciliation in docs/planning/PRE-M8-FOUNDATION-TRACKER.md. F1-S3 ACCEPTED — 03d598a1606121ecc7918279ec9a5f20f622ee93. F1-S4 NOT YET IMPLEMENTED; S4 requires separate explicit authorization after this bookkeeping checkpoint. S5A/S5B/S6 remain NOT AUTHORIZED; F1 INCOMPLETE; F2-F11, GUI and M8 BLOCKED. No commit or push is authorized.
- F0: S1 ACCEPTED at 76b676d; S2 ACCEPTED — ea40616 using user-supplied Java 21/Maven results. S2 was committed and pushed; the accepted remote checkpoint is ea4061672a293745ac5f5fc1b0d8d240d217e64e. S3 ACCEPTED — 8b6e714; F0 IMPLEMENTATION COMPLETE; F0 CLOSURE EVIDENCE PASSED; F0 ACCEPTED / CLOSED.
- F1: S0 ACCEPTED; F1-S1A ACCEPTED — b7a327d55ac26265474378fec00ae9a03ba7784e; F1-S1B ACCEPTED — 8b57b48e2a677a08a1d2f11f9eb760b0d3b8f00c; F1 INCOMPLETE. S2A ACCEPTED — 99dcd08d3c1e4d8653be1ef89b521422294be274; S2B ACCEPTED — 935fc29ad9a40a53b497a5fe008db5d6d7d485ac; F1-S3 ACCEPTED — 03d598a1606121ecc7918279ec9a5f20f622ee93; F1-S4 NOT YET IMPLEMENTED; S4 requires separate explicit authorization after this bookkeeping checkpoint; S5A/S5B/S6 remain NOT AUTHORIZED.
- F2-F11: NOT STARTED / BLOCKED by sequential review and acceptance.
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
| F0 | Architectural contracts and policies | Baseline audit and design approval | **ACCEPTED / CLOSED**; implementation complete; closure evidence passed; S1/S2/S3 accepted | S1: `76b676d`; S2: `ea4061672a293745ac5f5fc1b0d8d240d217e64e`; S3 tested implementation: `8b6e714216f7d3d174a81cdc9a120d88a4348f80`; closure evidence: `a2554ab93a7ff2fe5c4e547a7fb3119b57ac367c` |
| F1 | Persistent provenance/reporting | Accepted F0 | **IN PROGRESS / INCOMPLETE**. S0, S1A, S1B, S2A, S2B, S3, S4, S5A and **S5B ACCEPTED**. S6 **NOT YET IMPLEMENTED / next bounded closure slice**. | S1A: `b7a327d55ac26265474378fec00ae9a03ba7784e`; S1B: `8b57b48e2a677a08a1d2f11f9eb760b0d3b8f00c`; S2A: `99dcd08d3c1e4d8653be1ef89b521422294be274`; S2B: `935fc29ad9a40a53b497a5fe008db5d6d7d485ac`; S3: `03d598a1606121ecc7918279ec9a5f20f622ee93`; S4: `233c334023f035eba4c6122a262f9961e08e9b07`; S5A: `26f41054449335fba6d05e5e4e77bad51c3ea641`; S5B tested implementation: `ca25625264ebe96c0d777c3da833e03efc03e7aa`; S5B acceptance reconciliation: `fc4de75b18783df45d5757c11c51f1924ea9d535` |
| F2 | Deterministic recursive inventory | F0/F1 | **NOT STARTED / BLOCKED pending F1 closure** | None |
| F3 | Metadata-only DICOM inspection | F0/F1 | **NOT STARTED / BLOCKED pending F1 closure** | None |
| F4 | Candidate series discovery | F2/F3 | **NOT STARTED / BLOCKED** | None |
| F5 | Immutable deterministic organization plan | F1/F2/F4 | **NOT STARTED / BLOCKED** | None |
| F6 | Verified collision-safe COPY execution | F1/F5 | **NOT STARTED / BLOCKED** | None |
| F7 | Supported project-owned NIfTI-1 reading | F0 and protected generic volume | **NOT STARTED / BLOCKED by current sequential Pre-M8 gate** | None |
| F8 | Reopen, compare and persist conversion evidence | F1/F7/M7 | **NOT STARTED / BLOCKED** | None |
| F9 | Headless orchestration through existing M6/M7 | F2-F8 | **NOT STARTED / BLOCKED** | None |
| F10 | Minimal boundary reconciliation | Working F9 | **NOT STARTED / BLOCKED** | None |
| F11 | Synthetic then stakeholder acceptance | F0-F10 | **NOT STARTED / BLOCKED** | None |

The matrix records architectural dependencies and the current sequential authorization state. It does not authorize parallel work or bypass predecessor acceptance gates. **F1 must close through S6 before F2-F11 work is authorized. GUI and M8 remain blocked until the complete Pre-M8 acceptance gate is satisfied.**

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
- [x] F1-S0 specification/repository audit and final corrections accepted by the user before explicit S1A authorization; historical review preparation retained below.
- [x] F1-S1A implementation explicitly authorized for the six listed paths only; executable verification passed and ACCEPTED at b7a327d55ac26265474378fec00ae9a03ba7784e.
- [x] F1-S1B explicitly authorized for exactly the six listed paths; source prepared and user-supplied executable verification passed: focused 27, combined value/plan 44 and full regression 218 tests, zero failures/errors/skips; BUILD SUCCESS. ACCEPTED — 8b57b48e2a677a08a1d2f11f9eb760b0d3b8f00c.
- [x] F1-S1B ACCEPTED — 8b57b48e2a677a08a1d2f11f9eb760b0d3b8f00c; supplied passing focused/combined/full results retained below.
- [x] F1-S2A explicitly authorized for exactly the seven listed paths; tests-first source prepared and user-supplied executable verification passed: focused 27, combined manifest contracts 71 and full clean regression 245 tests, zero failures/errors/skips; BUILD SUCCESS. ACCEPTED — 99dcd08d3c1e4d8653be1ef89b521422294be274. S2B authorization is recorded below.
- [x] F1-S2B authorized only for the three listed paths; tests-first source and static review prepared for L01-L05, 30 authored test methods.
- [x] F1-S2B user-supplied executable verification PASSED: focused 30, combined manifest/replay 101 and full clean regression 275 tests, zero failures/errors/skips; BUILD SUCCESS. git diff --check passed with no whitespace errors.
- [x] F1-S2B ACCEPTED — 935fc29ad9a40a53b497a5fe008db5d6d7d485ac; actual focused 30, combined 101 and clean-full 275 passing tests retained below.
- [x] F1-S3 explicitly authorized for exactly five paths; J01-J04 implementation and executable gates PASSED: focused 25, combined F1 126, clean-full 300 tests; zero failures/errors/skips. Python standard-library interoperability PASS; git diff --check PASS.
- [x] F1-S3 ACCEPTED — 03d598a1606121ecc7918279ec9a5f20f622ee93 (implementation checkpoint). This later tracker-only bookkeeping checkpoint has no commit SHA yet. S4 is not yet implemented and requires separate explicit authorization after this bookkeeping checkpoint.

**Historical F0 reconciliation stop (superseded only by the F1-S0 audit authorization below): S1 ACCEPTED — 76b676d; S2 ACCEPTED — ea40616; S3 ACCEPTED — 8b6e714; F0 IMPLEMENTATION COMPLETE; F0 CLOSURE EVIDENCE PASSED; F0 ACCEPTED; F1-F11 BLOCKED; GUI BLOCKED; M8 BLOCKED. No F1 authorization is implied.**

## Accepted F1-S0 specification — historical final-review preparation

Historical S0 preparation status on 2026-10-04: **FINAL REVIEW PREPARED; IMPLEMENTATION NOT AUTHORIZED AT THAT TIME**. Superseded by the user's acceptance of S0 and explicit S1A-only authorization recorded in F1-S1A below; other slices remain unauthorized. Repository audit baseline remains **0757d7290ee8443062960392658ff1702817dfc5**. The original rejected F1-S0 proposal and intermediate revision are superseded by this section. The latest review approved the other S0 design choices, including the immutable plan/delta journal, narrow codec, publication scope, slice decomposition and 33-path ceiling; the final corrections below await final architectural review, not implementation authorization. F0 remains accepted and closed. Only this tracker changes. No Java, test, dependency, schema, helper type or commit has been implemented. F1-S1A and all later implementation work remain unauthorized.

Corrections incorporated: supplied ContentDigest evidence only; one immutable job/plan manifest plus factual hash-linked records; schema-specific canonical decoding; one authoritative location for each original M5/M6/M7 diagnostic; explicit F5 logical-plan/F6 digest/F6-F9 execution-evidence composition sequence; restart replay without an external receipt; normalized SHA-256 identifiers; explicit identifying-path privacy qualification; responsibility-neutral persistence exception; smaller model slices; separate persistence contract/replay/adapter slices; recalculated total and per-slice budgets.

### F1-S0.1 Repository findings and reuse classification

Paths are relative to src/main/java/org/cbihi/mrinormalizer unless stated otherwise. EXISTING — verified means source inspected at the exact baseline, not executed in this assistant environment.

| Symbol / document | Actual inspected contract | Reuse or limitation |
| --- | --- | --- |
| EXISTING — verified: application/provenance/ProvenanceRecord | Record: inputFingerprint, softwareVersion, dcm4cheVersion, completedAt, successful, inputCount, acceptedSlices, geometry, pixels, errors; only errors is defensively copied | Preserve unchanged. It is an M6 processing summary, not a validated manifest. Arbitrary geometry/pixels strings must not be serialized automatically; constructor permits fabricated strings and incompletely validated fields |
| EXISTING — verified: application/provenance/SelectedSourceFingerprint.sha256(List<Source>) | Sorts by SOPInstanceUID; rejects missing/duplicate UIDs; hashes each file through an 8192-byte buffer; aggregates framed big-endian UID byte lengths, UID bytes and binary file digests | Preserve unchanged. A selected-series identity is NOT a file content hash. It reads files and wraps causes; do not use it as the F1 per-file hashing port or expose its causes |
| EXISTING — verified: application/validation/ConversionValidationReport | Typed output target, dimensions, scalar type, voxel count, spacing, affine, intensity transform and preservation/resampling/interpolation/order flags; checks finite spacing and dimension/count arithmetic | Reuse facts through an explicit projection, excluding its OutputTarget.reference. It records M7 runtime invariants after writing; it never establishes reopened-file verification |
| EXISTING — verified: application/result/DicomProcessingResult and DicomToNiftiResult | Reconstruction success is volume present and no errors. Conversion success requires output and report with matching targets and no errors; failed conversion cannot carry report | Pure projection consumes these real result contracts. M6 provenance.successful alone is not conversion or job success |
| EXISTING — verified: DefaultDicomSeriesService / DefaultDicomToNiftiService | M6 constructs geometry/pixels summary strings and wall-clock timestamps; M7 projects reconstruction provenance and creates report only after writer success | Inspect only; no callers or protected algorithms changed, wrapped into new execution services or wired to persistence in F1 |
| EXISTING — verified: application/port/out/NiftiVolumeWriter.write(ImageVolume, AffineMatrix4, OutputTarget) | Only current application output-port file; serializes volume, not manifests | No reuse as manifest writer. EXISTING domain.port.FormatProbe and DicomInstanceReader remain where they are; no migration |
| EXISTING — verified: domain/model/InputSource and OutputTarget | Bare String reference records with no intrinsic canonicalization/validation | OutputTarget may carry a runtime export destination only. Do not persist its reference or assume it establishes containment |
| EXISTING — verified: FormatAssessment and five F0 enums | Immutable conservative assessment, accepted constructor/factory rules and raw DetectionResult preserved | Direct reuse in restricted source records; serialization preserves all seven components and raw extensionMismatch, without inference or READY upgrades |
| EXISTING — verified: DetectionDiagnostic, DicomProcessingError, DicomToNiftiError | Existing phase-specific enums, including distinctions between detection failures, geometry/selection errors and output errors | Preserve DetectionDiagnostic only in FormatAssessment.initialDetection, DicomProcessingError only in ProcessingEvidence.reconstructionErrors, and DicomToNiftiError only in ProcessingEvidence.conversionErrors; no duplicate original-enum fields in ManifestFailure |
| EXISTING — verified: AffineMatrix4, ScalarType, IntensityTransform | Finite immutable affine with get(row,column), four integer scalar types, finite nonzero intensity slope | Reuse typed M7 facts; no matrix comparison, voxel access or reader added |
| EXISTING — verified: infrastructure/DependencyContainer | Simple register/get/contains Map<Class<?>,Object>; no actual production registration/use found; Main only prints application and Java version | No container/Main edit or artificial bootstrapping. F1 adapters are constructor-injected in tests; F9 owns eventual composition |
| EXISTING — verified: architecture tests | DependencyDirectionTest inspects selected domain types; M6BoundaryTest selected M6 boundaries; PresentationBoundaryTest one signature; PackageStructureTest selected packages; FoundationContractBoundaryTest freezes exactly the six F0 types and generated APIs | Preserve all tests unchanged. New dedicated F1 coverage must inspect its actual declarations and generics; old gates do not automatically cover new packages |
| EXISTING — verified: DECISIONS.md ADR-014 | Accepted UTF-8 JSON for future manifests, application-owned semantics, M7 in-memory report sufficient, no M7 sidecar or implied reopening | Governs F1 serialization. No new ADR or change to M7 required for the proposed separate dataset manifest |
| EXISTING — verified: ARCHITECTURE-REVISION-1.0.md | AR-01/03/04/08/10, sections 10/16/17/18/20/26: application semantics, infrastructure I/O, small real ports, privacy and machine-readable traceability | Proposed ManifestWriter/ProvenanceManifest/JsonManifestWriter names in architecture are target concepts, not existing implementations. Historical M6 API mismatch and unresolved JSON choice have been superseded by accepted M6/M7/ADR-014; do not repair protected components based on stale prose |
| EXISTING — verified: pom.xml | Java release 21; dcm4che-core 5.33.0, FlatLaf 3.6.1, test-scoped JUnit 5.12.2; compiler 3.14.1 and Surefire 3.5.3; no declared JSON library | No dependency/version/build change proposed. Do not rely on accidental transitive JSON libraries |
| EXISTING — verified: accepted PM8-D08/D09/D15 | Versioned UTF-8 JSON, controlled facts, planned/actual separation, checkpointing, stop on persistence failure; mandatory restricted relative source-to-destination mapping with per-file hashes; public export may omit sensitive fields; recovery must reconcile filesystem | Inherited normative constraints. F1 defines the persistence capability and supplied-evidence model; F6/F9 own mutation sequencing and filesystem reconciliation |

Additional read-only context: AGENTS.md, PROJECT.md, MILESTONES.md and latest DEVELOPMENT_LOG.md entries. MILESTONES still names M8 next; accepted tracker controls the Pre-M8 sequence. These files are protected from this audit's edits.

### F1-S0.2 Revised behavioral contract and ownership

All new symbols, signatures, schemas, test names, strategies and budgets below are **PROPOSED**, not implementation or acceptance evidence.

F1 owns immutable reporting values, explicit projection of supplied M6/M7 facts, a restricted plan/journal persistence boundary, deterministic replay, a limited public export and canonical UTF-8 JSON. It does not perform imaging-file hashing, discovery, metadata reading, parsing, grouping, destination planning, copying, conversion, reopened-output verification or filesystem recovery execution.

- F5 supplies the accepted immutable logical organization layout: source identities/references, operation IDs and destinations. F5 performs no hashing. F6 pre-execution verifies current sources and computes/revalidates per-file ContentDigest evidence without changing F5's approved destinations, operation IDs or layout semantics.
- F6/F9 composes the F1 ProvenanceManifest from that accepted F5 logical plan plus supplied F6 digest evidence, then publishes it through the F1 store. ProvenanceManifest is the immutable execution-evidence plan; it need not be the same Java object/type as the eventual F5 logical planning model. Composition preserves every approved F5 mapping/key and adds supplied evidence; it does not replan.
- Required order: F5 logical plan -> F6 pre-execution source verification/digests -> F6/F9 execution-evidence plan composition -> F1 publication of plan.json -> known plan receipt acknowledged -> first imaging mutation permitted only after its own acknowledged IN_PROGRESS intent. An ambiguous/failed publication cannot authorize mutation. Missing digests are permissible only for deliberately nonexecuting sources/operations; later evidence that changes the published plan requires a new owner-approved plan/job.
- F1 validates supplied evidence shape/coherence only; it never hashes an imaging file, reads a SOURCE imaging file, calls SelectedSourceFingerprint or introduces a digest I/O boundary. SHA-256 of serialized plan/checkpoint bytes remains F1's journal-integrity mechanism, distinct from imaging-file content hashing.
- The plan is published once and immutable. Subsequent checkpoint records contain one operation observation or one job-state observation, never the full plan/current manifest. Replay joins these facts with the plan to produce a current in-memory report on request. No full-manifest snapshot is persisted after each operation.
- Every relocated COPY source must remain individually traceable through its immutable SOURCE relative key, expected supplied digest, planned OUTPUT destination and completed/identical-existing observation with matching output digest. Distinct source paths are never merged because content matches. Conversion has explicit many-source mapping and WRITTEN_UNVERIFIED observations; F8 remains the owner of reopened-output verification and any future schema extension permitting verified conversion success.
- Recognition does not establish validity/support/readiness. F1 preserves all seven F0 components and raw DetectionResult, including extensionMismatch; it never emits new recognition-derived evidence or bypasses M6/M7.
- Public export is a separate allowlisted aggregate value, not a redacted privileged manifest. Restricted source/destination paths remain mandatory. No dedicated UID/patient metadata fields or raw exception/text payloads are persisted, but a mandatory restricted relative path may itself contain a name, patient identifier or UID-like text. Public export omits all such paths.
- Persist plan before executing; persist IN_PROGRESS intent before mutation; persist actual outcome after the operation; require known acknowledgment before the next mutation. This sequencing is a later F6/F9 obligation tested with controlled owner fixtures, not an F1 executor.
- Publication uncertainty, corruption, invalid chain, unsupported schema, unsafe access/containment or exhausted reporting budget stops continuation. Never overwrite earlier accepted evidence, infer success from intent or silently choose an older successful prefix.

Supported: initial empty/nonempty plans; unavailable/corrupt/unrecognized source assessments; supplied digests and missing digests on nonexecuting records; blocked/skip/partial/failure jobs; COPY completion or identical-existing with supplied matching digests; factual M6/M7 result projection; written-unverified conversion; idempotent plan/append requests; bounded replay and public summaries.

Rejected: invalid/null/duplicate/unresolved values; unsafe relative reference; executable operation without every expected source digest; invalid state/phase/chronology; mutable plan changes; fabricated successful operation from a plan or M7 report; invalid/noncanonical/unsupported journal; stale receipt or different same-sequence bytes; unsafe publication/access; excessive resource use; arbitrary metadata/message/throwable payloads. Contract misuse uses fixed non-identifying argument errors; external persistence failures use typed fixed-message errors. F1 does not assert that supplied digest or phase evidence is truthful; the owning F6/M6/M7/F8 contracts establish that evidence.

### F1-S0.3 Immutable restricted job/plan schema

PROPOSED fixed execution-evidence plan schema: org.cbihi.mrinormalizer.provenance-plan; schemaVersion integer 1. Composed by F6/F9 from accepted F5 logical mappings plus F6 pre-execution evidence. Published once as plan.json inside the private job directory. Its canonical byte SHA-256 is the immutable plan anchor and receipt sequence 0; it is not embedded in its own bytes.

| Canonical key order | Exact value |
| --- | --- |
| schema, schemaVersion | Fixed identifier and integer 1 |
| jobId, createdAt | Supplied canonical lowercase UUID and UTC Instant.toString timestamp; not generated by codec |
| roots | Fixed ordered logical descriptors SOURCE then OUTPUT; no absolute location, account, hostname or device |
| sources | Canonically ordered SourceFileRecord objects: source, digest, assessment, failures |
| operations | Canonically ordered ManifestOperation objects: operationId, kind, sources, destination |

source={root:SOURCE,path:canonical relative path}; destination={root:OUTPUT,path:canonical relative path}. digest is null or {sizeBytes,sha256}, supplied by F6/pre-execution. assessment is the complete unchanged F0 value, including its raw detection and reasons. failures is a copied ordered list of workflow-only {phase,code} objects, not a text field or second original-diagnostic location. COPY requires exactly one source; CONVERT_DICOM_TO_NIFTI one or more. All source keys must resolve. An operation missing expected source digests can only be reported as nonexecuting/blocked/skipped under this plan. Filling or changing a missing/known plan digest later requires a new owner-approved plan/job, not mutation of the accepted plan.

Operation IDs are owner-supplied unique lowercase 64-hex stable plan keys. F1 does not generate layouts, series identities, naming counters or operation IDs. Repeated destinations are permitted only for explicitly supplied COPY mappings with identical known expected source digests, preserving every distinct source; different/unknown digests or conversion overlaps reject. Case/provider alias collision decisions remain F5/F6 obligations.

Schema documents are PROPOSED Draft 2020-12 documents with additionalProperties:false on every object. They document wire shape; executable model/codec/replay validation is authoritative. No runtime JSON-schema library or remote schema lookup is introduced.

### F1-S0.4 Append-only factual checkpoint schema

PROPOSED fixed schema: org.cbihi.mrinormalizer.provenance-checkpoint; schemaVersion integer 1. Each record describes exactly one operation observation or one job observation, not a full snapshot, changed source inventory or repeated plan.

| Canonical key order | Exact value |
| --- | --- |
| schema, schemaVersion | Fixed checkpoint identifier and integer 1 |
| jobId, sequence | Same job UUID; positive signed long sequence, strictly consecutive from 1 |
| previousRecordSha256 | Sequence 1 references the plan-byte hash; later records reference immediately preceding record-byte hash |
| recordedAt, kind | Supplied nondecreasing UTC Instant; kind=OPERATION_OBSERVED or JOB_OBSERVED |
| operationId, observation, jobState | OPERATION_OBSERVED has planned ID plus Observation and jobState=null; JOB_OBSERVED has operationId=null, observation=null and explicit jobState |
| failures | Workflow-only {phase,code} list for this observation, empty only when coherent; no original M5/M6/M7 enum fields or identifying message |

Observation keys, in order: state, startedAt, finishedAt, outputDigest, matchedExpectedSourceCount, disposition, processingEvidence. This small record refers to plan source/destination data by operationId. matchedExpectedSourceCount is supplied F6 observation, not an F1 verification: IN_PROGRESS/COMPLETED/IDENTICAL_EXISTING/WRITTEN_UNVERIFIED must equal the immutable planned source count; other states use 0 unless retaining prior uncertain facts. It means the owner established every expected source digest match. F1 must not treat a caller-supplied number as independently verified bytes.

processingEvidence is null or the explicit restricted M6/M7 projection below. Source/path/destination inventories are never copied into the record. One immutable plan plus its valid record chain therefore preserves the mandatory restricted mapping. Absence of a completed observation cannot imply relocation occurred.

Every object has fixed keys/order; optional values use explicit JSON null, lists use arrays. Enum wire names are exact names, not ordinals. Missing/extra/duplicate keys, unsupported version/enum or implicit defaults reject. Schema 1 never reports reopened-output verification: conversion facts include the fixed literal postWriteValidation=NOT_PERFORMED. F8 must review/version a later verified-output schema before validated conversion publication/job-success claims.

### F1-S0.5 Processing evidence and authoritative error location

Schema 1 has exactly one authoritative restricted home for each original diagnostic:

| Original diagnostic type | Sole persisted evidence location |
| --- | --- |
| DetectionDiagnostic | SourceFileRecord.assessment.initialDetection().diagnostic(), inside the unchanged FormatAssessment raw DetectionResult |
| DicomProcessingError | ProcessingEvidence.reconstructionErrors |
| DicomToNiftiError | ProcessingEvidence.conversionErrors |

ProcessingEvidence has phase scope, phaseSuccessful, optional SourceSummary, one reconstructionErrors list, one conversionErrors list and optional ConversionFacts. SourceSummary has no errors member. Existing ProvenanceRecord.errors is cross-checked against result.errors where supplied; mismatch rejects projection rather than silently choose conflicting evidence. Projection persists the actual result's original errors once in the appropriate ProcessingEvidence list. Neither source.failures, checkpoint.failures nor jobFailures may duplicate/recode those original facts. ManifestFailure has only workflow phase/code and cannot store an original enum. A genuinely new F6 hashing/execution or F1 persistence failure remains a separate owner-supplied fact, not a translation of M5/M6/M7 evidence. M5 INPUT_TOO_LARGE retains its F0 inconclusive meaning.

SourceSummary fields: optional selectedSourceFingerprint, softwareVersion, dcm4cheVersion, completedAt, provenanceSuccessful, inputCount, acceptedSlices. Fingerprint is explicitly tagged SELECTED_SERIES_SHA256 by a fixed serialization literal and never treated as a per-file digest. Drop geometry and pixels strings unconditionally; do not parse or sanitize arbitrary legacy free text. Build version strings are bounded safe tokens from trusted build configuration. SourceSummary.provenanceSuccessful preserves the exact existing ProvenanceRecord.successful flag, which M7 output failure can set false; it does not independently assert M6 reconstruction failure. ProcessingEvidence.phaseSuccessful comes from the actual result for its scope. Neither is overall job success.

ConversionFacts contains the typed report's dimensions, scalarType, voxelCount, finite spacing, row-major 4x4 RAS affine, intensityTransform and preservation/resampling/interpolation/order flags. It excludes OutputTarget/reference. Pure conversion projection uses DicomToNiftiResult.successful() and validates report/target consistency without persisting the runtime path. Failed phase evidence forbids conversion facts. A completed M7 phase may remain recorded in an operation that subsequently fails in another phase; do not erase prior factual completion or turn it into overall success.

No UID/patient metadata field is projected or persisted. That prohibition does not promise the mandatory relative paths contain no identifying/UID-like text.

### F1-S0.6 Restricted/public privacy contract

| Field class | Restricted plan/journal | Public export |
| --- | --- | --- |
| Source/destination relative paths and mandatory mapping | Required; may contain identifying or UID-like text | Omitted entirely |
| Supplied file hashes and selected-series aggregate | Controlled technical evidence, still linkable/sensitive | Omitted |
| Absolute roots, runtime OutputTarget references | Not persisted | Not persisted |
| UID/patient metadata fields, free-text metadata, geometry/pixels summary strings, exception text/stacks | Not persisted as fields/payloads | Not persisted |
| UUID/operation IDs, exact timestamps/byte sizes, geometry/intensity/source facts and versions | Restricted necessary controlled facts only | Omitted |
| Job state and operation/failure aggregate counts | Derived from valid replay | Only allowlisted aggregate summary |

Public report has its own schema org.cbihi.mrinormalizer.public-job-report, schemaVersion 1; canonical keys: schema, schemaVersion, state, sourceCount, operationCounts, failureCounts. Operation count objects have fixed kind/state/count keys. Failure count objects have exactly namespace/code/count keys: namespace is the controlled FailureNamespace enum, and code is an exact enum-name token validated against that namespace's closed code set. They contain no optional original diagnostic/error payloads. These are derived aggregates, never additional restricted diagnostic facts. No path, identifier, digest, timestamp, version string, geometry, arbitrary text or metadata map is a PublicJobReport component. It never replaces the restricted plan/journal.

Public counts derive from each operation's current observation, source facts and current job failures, not every historical transition: a failure retained across recovery observations is not double-counted. Detection counts derive once per distinct source from assessment.initialDetection().diagnostic(), excluding NONE; reconstruction/conversion counts derive once per current operation from their respective ProcessingEvidence lists. HASHING/EXECUTION/POST_WRITE_VALIDATION/PERSISTENCE counts derive from the workflow-only source/current-operation/current-job failure lists. Namespace preserves the distinction even when different enums share a code spelling. Do not infer invalidity from a diagnostic aggregate or recode original diagnostics as workflow failures. Operation counts include all 18 kind/state combinations, including zero counts. SourceCount counts distinct source keys, even with identical bytes. Aggregate numbers do not establish cohort anonymization.

Restricted store access must be established before plan/record/lock/stage visibility. Qualification remains owner-only POSIX permissions (directories 0700, files 0600), or effective Windows ACL allowing owner/required system administrators without broad-user inherited access. Unsupported restriction returns ACCESS_CONTROL_UNAVAILABLE; no public staging fallback. F1 does not change arbitrary parent ACLs/accounts or elevate privileges. Public export uses its already limited value projection and cannot write into the restricted namespace or SOURCE. Mandatory path privacy is enforced through restricted access, not removal of operational traceability.

### F1-S0.7 Deterministic ordering and relative paths

- RelativePath remains an immutable logical SOURCE/OUTPUT value, not java.nio.file.Path, URI, storage capability or proof of containment. Its constructor requires canonical slash-separated representation and rejects rather than normalizes away unsafe evidence.
- Portable schema-1 path subset rejects empty/absolute/leading/trailing/repeated separators, dot/dot-dot/empty segments, backslash/colon/drive/UNC forms, NUL/control/unpaired surrogate, trailing dot/space and Windows-reserved device bases including extensions. Preserve permitted Unicode scalar values, case, interior spaces and UID-like text exactly. No case-folding, Unicode normalization, percent decoding or anonymization inference. Nonrepresentable names cannot be relocated under this contract; the owning inventory must report a blocked outcome rather than drop/rename the source identity.
- F2/F5 supply relative references from canonical configured roots and retain exact source spelling. F1 does not walk SOURCE paths or inspect imaging files. F1 store/export adapters canonicalize only configured roots and their own persistence/export resources for containment and overlap checks; configured roots may have symlink ancestors. Do not follow discovered/final symlink resources by default or ban every configured ancestor. Fail closed if safe containment/access cannot be established.
- Sources and source-reference arrays sort by logical role then unsigned lexicographic UTF-8 path bytes. Operations sort by operationId ASCII. Workflow failures sort by Phase then Code declared wire order; duplicate phase/code pairs reject. Original diagnostic enums occur only in their authoritative assessment/processing fields. Processing error lists use declared enum wire order. Affines are row-major. Public count arrays use fixed enum order and positive failure counts sorted by FailureNamespace declared order then their namespace-specific enum declaration order; duplicate namespace/code aggregate keys reject.
- Canonical object order is exactly each schema table and nested record component order, with explicit fixed literal fields. Lists are copied/ordered/immutable; duplicate keys/references reject. Journal order is chronological sequence; never sort records by timestamps, filesystem listing or operation ID.
- Same supplied plan/record/replayed public value yields identical bytes. Caller-supplied UUIDs/times/observations may differ across executions. No codec clock, locale formatting or automatic evidence upgrade. Plan/record integrity hashes cover exact canonical UTF-8 bytes including final LF; they are not authenticated signatures.

### F1-S0.8 ContentDigest is supplied evidence only

ContentDigest(sizeBytes,sha256) is an immutable value: nonnegative checked size and lowercase 64-hex SHA-256 of the full original stored bytes of one regular imaging file, from byte zero to EOF. F6/pre-execution computes/supplies that evidence and re-hashes/verifies at execution; F1 does not compute it. Empty-content and compressed-wrapper digests are representable without asserting recognition validity. No sampled-prefix/voxel hash, decompression/recompression, path, UID or timestamp contributes to that digest contract.

No F1 filesystem hashing method, adapter, port, test, stream, SOURCE-file read or resource-binding algorithm is proposed. Missing digest means absent/unperformed evidence unless a real owner failure is supplied. Nonexecuting blocked/skip records need an applicable disposition/assessment/failure, not a fabricated hash failure. Actual-source/destination stability, safe open handles, mutation races and destination byte verification are F6 acceptance obligations; an F1 value cannot bypass them.

The persistence codec/store computes SHA-256 only for serialized restricted plan/checkpoint bytes and compares canonical public bytes for idempotence. Those journal checksums do not constitute per-file imaging digest execution.

### F1-S0.9 Observation and job state rules

Operation State: NOT_STARTED, IN_PROGRESS, COMPLETED, IDENTICAL_EXISTING, WRITTEN_UNVERIFIED, SKIPPED_POLICY, BLOCKED, FAILED, RECOVERY_REQUIRED. Disposition: NOT_REQUESTED_BY_POLICY, VALIDATION_REQUIRED, UNSUPPORTED_INPUT, INPUT_UNAVAILABLE, RECOVERY_RECONCILIATION_REQUIRED. These do not change F0 readiness enum/state semantics.

| Operation observation | Required / forbidden facts |
| --- | --- |
| NOT_STARTED | Derived initial state only; no record may invent a second initial state. No times/digest/disposition/phase evidence; source-match count 0 |
| IN_PROGRESS | startedAt present, finishedAt absent; every plan source has expected digest and supplied match count equals cardinality; no output digest/disposition/failures/processingEvidence |
| COMPLETED | COPY only; both chronological times; supplied output digest equals sole expected source digest; count matches sources; no failures/disposition/processingEvidence |
| IDENTICAL_EXISTING | COPY only; same digest/count/timing rules, supplied independent existing-target observation; no failures/disposition/processingEvidence |
| WRITTEN_UNVERIFIED | Conversion only; both times/output digest/source-match count; successful CONVERSION evidence with M7 facts, NOT_PERFORMED reopening; no failures/disposition |
| SKIPPED_POLICY | finishedAt; optional startedAt <=  finishedAt; disposition NOT_REQUESTED_BY_POLICY; no output digest/failures/processingEvidence; count 0 |
| BLOCKED | finishedAt; optional startedAt <=  finishedAt; disposition VALIDATION_REQUIRED, UNSUPPORTED_INPUT or INPUT_UNAVAILABLE; applicable plan assessment/failure required; no completed-output claim |
| FAILED | finishedAt; optional startedAt <=  finishedAt; actual failure evidence in checkpoint failures or processing error list required; no disposition/outputDigest; completed phase facts may remain without overall success |
| RECOVERY_REQUIRED | disposition RECOVERY_RECONCILIATION_REQUIRED and typed recovery failure; retained prior known times/digest/match count/phase facts cannot be erased or promoted to success; unfinished facts remain explicit |

Times are supplied observations, not inferred. An absent successful output digest does not assert no partial/staged output physically exists. COPY observations never carry ProcessingEvidence. For conversion, phase evidence must be coherent, but a successful earlier M6/M7 phase cannot force operation success after a later failure.

Allowed transitions: NOT_STARTED -> IN_PROGRESS, BLOCKED or SKIPPED_POLICY; IN_PROGRESS -> COMPLETED, IDENTICAL_EXISTING, WRITTEN_UNVERIFIED, FAILED or RECOVERY_REQUIRED; any previously observed state -> RECOVERY_REQUIRED only on explicit owner-supplied uncertainty, preserving earlier facts/failures. No RECOVERY_REQUIRED -> success, terminal retry, completed downgrade-to-failed, unblocking/replanning or return-to-NOT_STARTED is automatic or authorized under schema 1. Reconciliation/retry semantics belong to F6/F9 and may require a new plan/job or a separately reviewed future schema.

JobState: PLANNED, RUNNING, COMPLETED, FAILED, RECOVERY_REQUIRED. At plan anchor sequence 0, every operation is NOT_STARTED and job PLANNED. A JOB_OBSERVED RUNNING record is required before operation observations. COMPLETED requires a nonempty plan with all operations COPY COMPLETED/IDENTICAL_EXISTING or SKIPPED_POLICY, at least one successful COPY, no source/operation/job failures, and no conversion WRITTEN_UNVERIFIED. Empty plans remain PLANNED; no synthetic success. FAILED requires actual failure evidence and no active IN_PROGRESS operation; if outcome is uncertain, use RECOVERY_REQUIRED. RECOVERY_REQUIRED requires explicit recovery facts and cannot be resumed automatically. RUNNING -> COMPLETED/FAILED/RECOVERY_REQUIRED; PLANNED -> RUNNING or explicit FAILED/RECOVERY_REQUIRED; terminal jobs may only acquire explicit RECOVERY_REQUIRED uncertainty, retaining prior facts. Operation observations after terminal job states reject except corresponding uncertainty records while RECOVERY_REQUIRED. Repeat identical job-state records with no new fact reject; retries of already committed identical bytes remain storage-idempotent.

### F1-S0.10 Replay/current-state and deterministic recovery

ManifestStore.replay(Optional.empty()) replays the complete valid existing plan/journal without an externally supplied acknowledged rollback floor; this is the normal restart case. ManifestStore.replay(Optional.of(receipt)) performs the same full replay and additionally proves that receipt belongs to this job/plan and still exists unchanged in the valid chain, whether it identifies the plan anchor, an earlier record or the head. A later valid head is allowed. Neither mode permits ignoring corrupt committed evidence. With no committed plan and no orphan committed records, return Optional.empty(); a supplied receipt in that case proves acknowledged loss and must fail. A committed record without its plan is corruption, never an empty job. With a plan present, an empty input argument returns Optional.of(currentState), even with no records.

Replay is a pure application reducer. Validate plan once; seed every planned operation as NOT_STARTED. Validate each consecutive record, job/version/previous hash, nondecreasing recordedAt >= plan.createdAt, phase applicability and legal transition. Fold one referenced operation or job observation. Never alter plan sources/mappings/digests. Keep original typed failures and prior known facts when recording uncertainty. Current-state/public summaries are derived views, not persisted snapshots.

The reducer builds source/operation indexes and eligibility facts once in O(S + O + Rf), where Rf is the total number of planned source references. Each subsequent single-operation observation validates/updates its indexed state and aggregate counters without copying/sorting the complete manifest. Job completion uses counters, not an O(O) traversal after every append. Creating an explicitly requested immutable ManifestState/current public view is O(S + O), not performed by append. Mutable reducer internals are confined to this reporting algorithm; its returned values remain immutable.

Storage integrity is checked independently against actual canonical bytes; reducer checksum arguments are supplied by the persistence adapter. Hash chains detect mismatched links/content and gaps with surviving later records, not malicious re-signing. A complete terminal suffix deleted with no later link and no retained receipt cannot be detected by a plain chain alone. A caller-supplied minimum expected receipt detects missing/changed acknowledged evidence; absence of that receipt is not a claim of rollback/tamper protection.

| Restart/crash/corruption condition | Deterministic result |
| --- | --- |
| No plan/committed records and no minimum receipt | Optional.empty() result; staging alone is not a committed plan |
| Existing complete plan/journal, empty minimumExpectedHead argument | Replay entire valid chain and return current state; normal restart without external rollback detection |
| Existing plan/journal, supplied minimum receipt | Replay entire valid chain plus prove unchanged acknowledged anchor/record; head may be later than receipt |
| Only complete plan, no records | Optional.of(PLANNED current state), including with empty minimumExpectedHead argument; no operation executed inferred |
| Complete valid contiguous prefix and no conflicting final entries | Fold exactly that prefix; return factual state and head receipt, never stronger filesystem claims |
| Intent record exists; output work finished but outcome record absent/staged only | IN_PROGRESS remains factual; F6/F9 must reconcile actual files/hashes before continuation. Stage bytes never imply success |
| Outcome final published but acknowledgment lost | Replay recognizes that exact record; identical retry returns same receipt. Caller still reconciles any interrupted workflow before further imaging mutation |
| Only an owned uncommitted staging artifact for next sequence | Ignore it as authoritative evidence; no sequence reservation consumed; after explicit ownership check it may be removed by store cleanup, never replayed/promoted |
| Final record n+2 without n+1, missing plan, wrong job/name/bucket/sequence, invalid checksum/link/time/model | CORRUPT_CHECKPOINT and stop. Do not return an older prefix as usable current state |
| Unsupported latest or earlier plan/record version; noncanonical valid JSON | UNSUPPORTED_SCHEMA or CORRUPT_CHECKPOINT and stop; no silent migration/fallback |
| Last acknowledged head absent/changed relative to supplied minimum receipt | CHECKPOINT_CONFLICT/CORRUPT_CHECKPOINT and stop; no rollback to older success |
| Uncertain physical publication that cannot be classified | Persistence exception with UNKNOWN and RECOVERY_REQUIRED; no authoritative new receipt, no automatic append retry |

Replay does not inspect SOURCE/output imaging files, delete outputs, reopen NIfTI or execute any recovery. Clean prefix validity is not permission to restart an interrupted operation. F9 owns reconciliation, explicit workflow stop and subsequent owner-authorized continuation. Corrupt journals are retained for controlled investigation; F1 has no repair/skip/reset API.

### F1-S0.11 Resource/storage/I/O bounds and sequence arithmetic

PROPOSED independent hard limits: plan <= 64 MiB canonical bytes; sources <= 100000; operations <= 100000; total planned source references <= 200000; path <= 4096 UTF-8 bytes; trusted version token <= 64 ASCII chars; per-checkpoint canonical bytes <= 16 KiB; failures <= 64 per source/observation; checkpoint count <= 300001; cumulative checkpoint bytes <= 256 MiB. Public export <= 16 MiB. Schema nesting is fixed and at most 32 levels. Values exceeding any limit reject; maxima need not be simultaneously realizable. No truncation, dropped source mapping or silent compaction.

Logical persisted bytes are bounded by 64 MiB +256 MiB per job, plus small fixed lock/staging metadata; at most one current stage <= 16 KiB (plan staging up to 64 MiB during creation). Plan is written once. A successful COPY emits intent plus outcome records, each bounded below1 KiB for this fixed schema without phase/failure payload; 100000 such operations use < 200 MiB checkpoint payload plus a few job records. Longer conversion/failure histories use their actual encoded sizes and may hit the 256 MiB bound earlier. Such plans/histories stop explicitly on capacity exhaustion; the count bound is not an unlimited storage guarantee. No quadratic complete-plan write/read traffic is hidden behind checkpoints.

Physical storage includes provider allocation/inode/ACL overhead: for illustration, 300001 <= 16-KiB records each occupying one 4-KiB block would consume about 1.15 GiB before metadata and records needing additional blocks; this is not a provider guarantee. Logical byte caps alone do not prove sufficient free disk. S4 qualification must measure/record allocation and directory/lock/publication behavior; disk-full remains explicit failure. No full snapshots are retained as an alternative.

Store uses records/<six-digit bucket>/<20-digit sequence>.json, bucket=floor((sequence-1)/1000), to limit a bucket to 1000 records. Names are technical store metadata, not organization paths. No per-operation counter changes dataset naming. Maximum 301 record buckets at the declared count bound. Unknown/conflicting final names or unexpected directory types fail; storage traversal is confined to this private job journal, not recursive dataset discovery.

A store instance owns one job's exclusive lease until close. It reads/validates the plan/chain once on open/first access, then holds a private reporting reducer/head and appends one bounded record at a time. Append does not reread the entire prefix or build an immutable current-state snapshot. Reopen intentionally performs one O(plan bytes + journal bytes + entries) integrity/replay pass, streaming records without retaining history. Live memory is plan/index/current observations plus one bounded record and an optional requested current view, not all historical records. Resource caps and checked references prevent unbounded count growth; actual peak heap/time need executable S4 qualification. Short-lived reopening before every operation is outside the intended performance contract and F9 must retain the job-scoped lease for its sequential workflow.

Before an IN_PROGRESS record is admitted, preserve budget for its maximum-sized outcome record plus a maximum-sized job control/failure record (32 KiB total reserve). Keep one 16-KiB job control reserve while nonterminal work exists; accept reserved terminal/control observations instead of using reserved capacity for another operation. If capacity cannot accommodate intent+reserve, return RESOURCE_LIMIT before that operation can mutate data. The reducer's active-operation count is <= 1 in schema 1. Disk-space failure can still prevent persistence despite logical reservations; owning workflow stops and reconciles.

Sequence uses Math.addExact(headSequence,1); first checkpoint=1 and plan anchor=0. Reject nonpositive, duplicate/different, skipped or overflowing sequences before stage creation. Count cap is normally reached far earlier than Long.MAX_VALUE; both limits are independently tested. Idempotent same-byte replay does not increment sequence/count or consume capacity. No wrap, reuse, slot reservation before publication or tombstone gap. Crash stage cleanup does not alter the next sequence; final gaps are corruption.

### F1-S0.12 Failure taxonomy and neutral exception

ManifestFailure is the workflow/reporting-only immutable pair (Phase phase, Code code). Its exact Phase set is HASHING, EXECUTION, POST_WRITE_VALIDATION, PERSISTENCE. It has no DETECTION/RECONSTRUCTION/CONVERSION phase, no original diagnostic/error fields and no free-text/message/cause payload. Original M5/M6/M7 enums remain only in the authoritative locations in F1-S0.5; there is no EXISTING_DIAGNOSTIC code. HASHING/EXECUTION facts are supplied by F6, not executed by F1. POST_WRITE_VALIDATION evidence belongs to F8; schema 1 can retain a supplied failure but cannot claim reopened-output success.

Complete Code set: INVALID_MANIFEST, INVALID_REFERENCE, CONTAINMENT_UNPROVEN, INPUT_UNAVAILABLE, SOURCE_CHANGED, HASH_FAILED, CHECKPOINT_CONFLICT, WRITE_FAILED, PUBLICATION_UNAVAILABLE, READ_FAILED, UNSUPPORTED_SCHEMA, CORRUPT_CHECKPOINT, ACCESS_CONTROL_UNAVAILABLE, RESOURCE_LIMIT, CLEANUP_FAILED, RECOVERY_REQUIRED, OUTPUT_CONFLICT, VERIFICATION_FAILED.

- SOURCE_CHANGED/HASH_FAILED are supplied HASHING owner failures. INVALID_REFERENCE/INPUT_UNAVAILABLE/CONTAINMENT_UNPROVEN describe new owning-workflow or persistence-resource failures, never a copied/reclassified M5 diagnostic.
- Invalid plan/model, chain/schema corruption, CAS conflict, encoding/bounds, access, read/write/publication and recovery ambiguity are PERSISTENCE failures. RESOURCE_LIMIT may additionally represent supplied HASHING resource failure. OUTPUT_CONFLICT records EXECUTION or PERSISTENCE export conflicts; VERIFICATION_FAILED records supplied EXECUTION/POST_WRITE_VALIDATION failure evidence. The applicability matrix below is normative.
- CLEANUP_FAILED is separate from primary failure and cannot erase it. States SKIPPED_POLICY/BLOCKED/WRITTEN_UNVERIFIED are not fabricated processing errors; their dispositions/evidence remain explicit.
- Constructor requires nonnull phase/code and an allowed pair, with fixed non-identifying rejection messages. Containing lists copy/order pairs and reject duplicates. Canonical restricted wire object is exactly {phase,code} in that key order, with additionalProperties:false; legacy original-enum fields/codes/phases reject rather than migrate.

Exact allowed Code/Phase matrix (all other pairs reject):

| Code | Allowed Phase |
| --- | --- |
| INVALID_MANIFEST | PERSISTENCE |
| INVALID_REFERENCE, INPUT_UNAVAILABLE | HASHING, EXECUTION, PERSISTENCE |
| CONTAINMENT_UNPROVEN | HASHING, EXECUTION, PERSISTENCE |
| SOURCE_CHANGED, HASH_FAILED | HASHING (supplied F6 evidence only) |
| CHECKPOINT_CONFLICT, WRITE_FAILED, PUBLICATION_UNAVAILABLE, READ_FAILED, UNSUPPORTED_SCHEMA, CORRUPT_CHECKPOINT, ACCESS_CONTROL_UNAVAILABLE | PERSISTENCE |
| RESOURCE_LIMIT | HASHING, PERSISTENCE |
| CLEANUP_FAILED, RECOVERY_REQUIRED | EXECUTION, PERSISTENCE |
| OUTPUT_CONFLICT | EXECUTION, PERSISTENCE |
| VERIFICATION_FAILED | EXECUTION, POST_WRITE_VALIDATION |

PROPOSED neutral exception: **ProvenancePersistenceException**. This neutral reporting/persistence exception is introduced only with its owning persistence contract slice. It has a copied typed failure list, PublicationOutcome NOT_PUBLISHED/PUBLISHED/UNKNOWN and optional known ManifestReceipt. Its own failures must be PERSISTENCE-phase facts. Fixed category message, null cause, suppressed payload disabled; no arbitrary message/cause constructor. Known published plan/checkpoint needs receipt; public export may be PUBLISHED without job receipt. UNKNOWN forbids a new receipt. Receipt identifies known publication bytes, not universal power-loss durability. Programmer misuse remains fixed argument error; no blanket exception swallowing.

### F1-S0.13 Canonical UTF-8 codec: schema-specific decoder only

No new dependency or pom.xml edit. Package-private JsonManifestCodec emits the fixed schemas and reads **only canonical internal plan/checkpoint bytes** through schema-specific typed cursor routines. Plan decoding is the anchor case of journal decoding. There is no reusable JSON tokenizer, full-grammar parser, AST, generic map/list value parser, DOM, reflection serializer, polymorphic dispatch or public JSON framework API.

Decoder entry points explicitly expect fixed schema literal, key order, punctuation and known nested shapes. Primitive routines accept only emitted UTF-8 strings/escapes, exact decimal integer tokens and finite Double.toString tokens required by typed conversion fields. Each field is decoded directly to its known type/canonical constructor. Known-length/bounded arrays hold only their expected typed source/operation/error/affine values. No parse-any-value or unknown-field skipping method. Exact re-encoding must equal all input bytes including final LF; semantically equivalent valid JSON with reordered keys, alternate whitespace/escapes/number forms or unknown fields may and must be rejected.

Emitter: UTF-8 without BOM; no insignificant whitespace; one final LF; quote/backslash escapes where necessary; permitted Unicode scalar values emitted losslessly; reject controls/unpaired surrogates in schema-controlled strings. Integers are exact signed long/int decimal tokens, never double. Finite doubles use Double.toString; NaN/infinity reject. No locale clock/default values. Optional fields have explicit null. Unsupported schema/version and unexpected canonical keys/tokens fail before accepting partial state. Public export encoding is introduced only in its owning slice; no public-report decoder exists.

Independent Python standard-library json parsing of generated canonical fixtures remains required alongside fixed golden bytes and typed round trips. Python must verify supplementary Unicode, exact integers above 2^53, fixed fields and semantic values. This is local interoperability evidence, not a new Maven/runtime dependency. Reader/writer agreement alone is insufficient. No Java/Python execution is claimed in this revision.

### F1-S0.14 Persistence and publication strategy

Immutable plan.json plus one immutable file per factual checkpoint; no mutable latest pointer and no full-snapshot generation. Plan bytes anchor the chain; sequence0 receipt has planSha256=headSha256. For checkpoint n, receipt carries jobId, sequence n, planSha256 and headSha256 of that record. Compare-and-set uses expected head receipt plus record.previousRecordSha256.

Job-private directory uses canonical UUID under the configured restricted OUTPUT manifest directory. Acquire restrictive permissions/effective ACL before directory/lock/stage visibility, and one exclusive cooperative job lease held until store.close(). Reads/replay require the same lease. Unsupported/concurrent/overlapping lease fails immediately; no indefinite wait. No guarantee against an owner-privileged process ignoring permissions/lock. Root/ancestor safety follows accepted PM8-D02 without blanket ancestor rejection. F1 accesses only its own persistence/export resources and never a supplied source imaging file.

Create: bound/encode immutable plan, publish once; exact same existing plan may return same sequence0 receipt, but differing plan is conflict. Appending records before plan rejects. Establish/replay the existing prefix once to initialize the private reducer. Append: verify exact expected head/sequence/link/job/time and transition using validateNext without state mutation; encode one bounded record and enforce cumulative limits/reserves before touching staging.

Publication: create exclusive restrictive stage on same filesystem, write complete canonical bytes, calculate journal hash, force/close, verify stage. Initial proposed no-replace mechanism remains qualified hard-link creation of final plan/record entry to the complete staged inode, followed by removal of only the owned staging link. This optional provider mechanism must pass actual Windows/POSIX no-replace, complete-visibility, lease and access tests. If unsupported/unproved, fail PUBLICATION_UNAVAILABLE; no direct-final write/copy/non-atomic fallback. Files.move ATOMIC_MOVE is not assumed to guarantee refusal of existing targets.

Only after known publication accept the record into reducer and return receipt. Interrupted publication is classified under the held lease by comparing the expected final bytes: NOT_PUBLISHED, PUBLISHED with receipt, or UNKNOWN with recovery failure. On ambiguous append, poison the live store against more mutations; close/reopen and explicit owner reconciliation are required. A post-publication cleanup/close failure records its own failure and known-publication outcome, never deletes committed evidence. Retry of the exact current record returns its same receipt; stale receipt with new/different bytes conflicts. Existing earlier record identical retry may return its known receipt only without advancing head; caller must fetch current receipt before later append.

One journal replay streams all final entries in sequence and rejects gaps/name/type/version/hash/model errors. Owned incomplete stages are not final evidence and do not consume a sequence. No deleting/correcting corrupt committed records. Safe owned-stage cleanup remains private implementation detail; no SOURCE/output imaging deletion. A failed stage cleanup can stop the workflow even when no journal record published.

Process-interruption complete-file visibility is the initial proposed guarantee on qualified local providers. File force does not establish portable directory-entry/power-loss durability or remote-storage guarantees. Directory durability expectations require separate platform evidence/approval before acceptance; receipts do not claim universal hardware durability. Network/remote filesystem support is not silently assumed.

Public export is separately derived/encoded in its own slice, staged within OUTPUT outside restricted journal namespace and published through the same qualified no-replace behavior. Runtime OutputTarget.reference is never serialized. Identical existing canonical public bytes are an explicit idempotent observation; different bytes are OUTPUT_CONFLICT. Public export cannot modify plan/journal or overwrite imaging output. Publication and cleanup failures use the same neutral persistence exception without pretending to have a checkpoint receipt.

Primary references from the audited proposal remain [Java SE 21 Files](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html), [FileChannel.force](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html) and [RFC8259](https://www.rfc-editor.org/rfc/rfc8259). Reading documentation is not executable publication/durability evidence.

### F1-S0.15 Exact proposed types and API, by owning slice

Every declaration below is PROPOSED NEW; referenced Java standard types and audited F0/domain/result types are EXISTING. Models use java.util.List/Optional/UUID and java.time.Instant, not Path/stream/JSON nodes. Records expose canonical validated constructors and generated value methods only. No placeholders/forward store dependencies are introduced in earlier slices.

Application package org.cbihi.mrinormalizer.application.provenance.manifest, **S1A**:

```java
public record RelativePath(Root root, String path) {
    public enum Root { SOURCE, OUTPUT }
}
public record ContentDigest(long sizeBytes, String sha256) { }
public record SourceFileRecord(
    RelativePath source, Optional<ContentDigest> digest,
    FormatAssessment assessment, List<ManifestFailure> failures) { }
public record ManifestFailure(Phase phase, Code code) {
    public enum Phase { HASHING, EXECUTION, POST_WRITE_VALIDATION, PERSISTENCE }
    public enum Code {
        INVALID_MANIFEST, INVALID_REFERENCE,
        CONTAINMENT_UNPROVEN, INPUT_UNAVAILABLE, SOURCE_CHANGED, HASH_FAILED,
        CHECKPOINT_CONFLICT, WRITE_FAILED, PUBLICATION_UNAVAILABLE,
        READ_FAILED, UNSUPPORTED_SCHEMA, CORRUPT_CHECKPOINT,
        ACCESS_CONTROL_UNAVAILABLE, RESOURCE_LIMIT, CLEANUP_FAILED,
        RECOVERY_REQUIRED, OUTPUT_CONFLICT, VERIFICATION_FAILED
    }
}
```

Same application package, **S1B**:

```java
public record ProcessingEvidence(
    Scope scope, boolean phaseSuccessful, Optional<SourceSummary> sourceSummary,
    List<DicomProcessingError> reconstructionErrors,
    List<DicomToNiftiError> conversionErrors,
    Optional<ConversionFacts> conversionFacts) {
    public enum Scope { RECONSTRUCTION, CONVERSION }
    public record SourceSummary(
        Optional<String> selectedSourceFingerprint, String softwareVersion,
        String dcm4cheVersion, Instant completedAt, boolean provenanceSuccessful,
        int inputCount, int acceptedSlices) { }
    public record ConversionFacts(
        int width, int height, int depth, ScalarType scalarType, long voxelCount,
        double rowSpacingMm, double columnSpacingMm, double sliceSpacingMm,
        AffineMatrix4 niftiRasVoxelToWorld, IntensityTransform intensityTransform,
        boolean storedVoxelValuesPreserved, boolean resampled,
        boolean interpolated, boolean voxelOrderChanged) { }
}
public record ManifestOperation(
    String operationId, Kind kind, List<RelativePath> sources,
    RelativePath destination) {
    public enum Kind { COPY, CONVERT_DICOM_TO_NIFTI }
}
public record ProvenanceManifest(
    int schemaVersion, UUID jobId, Instant createdAt,
    List<SourceFileRecord> sources, List<ManifestOperation> operations) { }
```

Constructors copy/order lists and enforce nonnull/unique/count/type/digest/phase/finiteness rules. SourceSummary has no original-error list. Failed phase evidence requires appropriate original phase errors and forbids conversion facts; successful conversion phase requires facts/no errors; reconstruction forbids conversion fields. Conversion fact invariants reuse actual report meaning without modifying protected report constructors. Immutable plan validates references/destination compatibility and complete eligible mappings but has no store receipt/state/exception dependency.

Same application package, **S2A persistence protocol**:

```java
public record CheckpointRecord(
    int schemaVersion, UUID jobId, long sequence, String previousRecordSha256,
    Instant recordedAt, Kind kind, Optional<String> operationId,
    Optional<Observation> observation, Optional<ManifestState.JobState> jobState,
    List<ManifestFailure> failures) {
    public enum Kind { OPERATION_OBSERVED, JOB_OBSERVED }
    public enum State {
        NOT_STARTED, IN_PROGRESS, COMPLETED, IDENTICAL_EXISTING,
        WRITTEN_UNVERIFIED, SKIPPED_POLICY, BLOCKED, FAILED, RECOVERY_REQUIRED
    }
    public enum Disposition {
        NOT_REQUESTED_BY_POLICY, VALIDATION_REQUIRED, UNSUPPORTED_INPUT,
        INPUT_UNAVAILABLE, RECOVERY_RECONCILIATION_REQUIRED
    }
    public record Observation(
        State state, Optional<Instant> startedAt, Optional<Instant> finishedAt,
        Optional<ContentDigest> outputDigest, int matchedExpectedSourceCount,
        Optional<Disposition> disposition,
        Optional<ProcessingEvidence> processingEvidence) { }
}
public record ManifestReceipt(
    UUID jobId, long sequence, String planSha256, String headSha256) { }
public record ManifestState(
    ProvenanceManifest plan, ManifestReceipt receipt, JobState state,
    Instant recordedAt, List<OperationState> operations,
    List<ManifestFailure> jobFailures) {
    public enum JobState { PLANNED, RUNNING, COMPLETED, FAILED, RECOVERY_REQUIRED }
    public record OperationState(
        String operationId, CheckpointRecord.Observation observation,
        List<ManifestFailure> failures) { }
}
public final class ProvenancePersistenceException extends RuntimeException {
    public enum PublicationOutcome { NOT_PUBLISHED, PUBLISHED, UNKNOWN }
    public ProvenancePersistenceException(
        List<ManifestFailure> failures, PublicationOutcome outcome,
        Optional<ManifestReceipt> knownPublication);
    public List<ManifestFailure> failures();
    public PublicationOutcome outcome();
    public Optional<ManifestReceipt> knownPublication();
}
```

Application package org.cbihi.mrinormalizer.application.port.out, **S2A**:

```java
public interface ManifestStore extends AutoCloseable {
    ManifestReceipt create(ProvenanceManifest plan);
    ManifestReceipt append(CheckpointRecord record, ManifestReceipt expectedHead);
    Optional<ManifestState> replay(Optional<ManifestReceipt> minimumExpectedHead);
    @Override void close();
}
```

Job-scoped store capability, no checked filesystem/Throwable signature or adapter/path API. An empty minimumExpectedHead argument is valid with an existing plan/journal: perform full replay with no external rollback floor. A present receipt adds proof of unchanged acknowledged plan/record evidence; it never limits replay to that prefix. An empty result denotes no committed plan and no orphan committed records, provided no supplied receipt claims acknowledged evidence; orphan records or acknowledged loss fail. Minimum receipt checks job/plan/sequence and the acknowledged anchor/record hash, even when its sequence precedes current head; it is not an external authentication token. During an initial streamed replay compare that record when encountered; during a live lease an explicitly requested older minimum receipt may require reading only its bounded record, never a prefix reread before every append. No fake store implementation in S2A.

Application manifest package, **S2B pure replay**:

```java
public final class ManifestReplay {
    public ManifestReplay(ProvenanceManifest plan, String planSha256);
    public void validateNext(CheckpointRecord record);
    public ManifestReceipt accept(CheckpointRecord record, String recordSha256);
    public ManifestState current();
}
```

This deliberately stateful reducer has private bounded indexes/counters. validateNext is side-effect free. accept validates and atomically advances only its in-memory reporting state; supplied hashes must have canonical shape and record link must match current receipt. It performs no hashing/I/O/execution. current returns a defensively copied immutable view on request. Persistence validates before publication and accepts only after known committed bytes. No public transition helper/ready shortcut or file utility is introduced.

Infrastructure package org.cbihi.mrinormalizer.infrastructure.filesystem, **S3 canonical codec**:

```java
final class JsonManifestCodec {
    JsonManifestCodec();
    byte[] encode(ProvenanceManifest plan);
    ProvenanceManifest decodePlan(byte[] canonicalUtf8);
    byte[] encode(CheckpointRecord checkpoint);
    CheckpointRecord decodeCheckpoint(byte[] canonicalUtf8);
}
```

These are package-private schema-specific cursor/encoding APIs; no AST/parser framework or public export reference/stub is present in S3. Individual bounded byte arrays and typed decoding follow the fixed schemas; no arbitrary input value parser.

Infrastructure filesystem package, **S4 store publication adapter**:

```java
public final class JsonManifestStore implements ManifestStore {
    public JsonManifestStore(
        Path sourceRoot, Path outputRoot, Path manifestDirectory, UUID jobId);
    public ManifestReceipt create(ProvenanceManifest plan);
    public ManifestReceipt append(CheckpointRecord record, ManifestReceipt expectedHead);
    public Optional<ManifestState> replay(Optional<ManifestReceipt> minimumExpectedHead);
    @Override public void close();
}
```

Path is allowed only on concrete infrastructure construction. Constructor establishes one job-scoped lease or fails explicitly. Private/package-private publication/cursor/fault seams remain in the budgeted adapter/codec, without added helper files/interfaces. No imaging-file digest API. Resource-scope close must release its lease; failure is fixed typed, never silently ignored as accepted workflow success.

Application manifest package, **S5A projection/export value**:

```java
public record PublicJobReport(
    int schemaVersion, ManifestState.JobState state, long sourceCount,
    List<OperationCount> operationCounts, List<FailureCount> failureCounts) {
    public record OperationCount(
        ManifestOperation.Kind kind, CheckpointRecord.State state, long count) { }
    public enum FailureNamespace {
        DETECTION, RECONSTRUCTION, CONVERSION, HASHING,
        EXECUTION, POST_WRITE_VALIDATION, PERSISTENCE
    }
    public record FailureCount(
        FailureNamespace namespace, String code, long count) { }
}
public final class ManifestProjection {
    private ManifestProjection();
    public static ProcessingEvidence reconstruction(DicomProcessingResult result);
    public static ProcessingEvidence conversion(DicomToNiftiResult result);
    public static PublicJobReport publicReport(ManifestState current);
}
```

FailureCount accepts only positive counts and exact controlled enum-name code tokens: DETECTION uses DetectionDiagnostic excluding NONE; RECONSTRUCTION uses DicomProcessingError; CONVERSION uses DicomToNiftiError; the four workflow namespaces use only ManifestFailure.Code values allowed for that corresponding ManifestFailure.Phase. No arbitrary code text, optional raw enum fields or message fields. Its public wire shape is exactly {namespace,code,count}; schema alternatives enumerate each namespace's closed allowed codes. PublicJobReport constructor validates aggregate shape/order/uniqueness; projection establishes counts from coherent replay and the authoritative locations in F1-S0.5. No original enum is duplicated in restricted failure storage. No constructor can authenticate caller-supplied fact origin. No public-report value is declared before S5A.

Application output-port package and infrastructure filesystem package respectively, **S5B export I/O**:

```java
public interface PublicReportWriter {
    void write(PublicJobReport report, OutputTarget target);
}
public final class JsonPublicReportWriter implements PublicReportWriter {
    public JsonPublicReportWriter(Path sourceRoot, Path outputRoot, Path manifestDirectory);
    public void write(PublicJobReport report, OutputTarget target);
}
```

Only in S5B add package-private byte[] JsonManifestCodec.encode(PublicJobReport report). No such method/ref/stub in S3; no public-report decoder. No new concrete composition/use-case call is proposed. Existing DependencyContainer/Main/M6/M7 remain unchanged; F9 owns wiring.

### F1-S0.16 Dedicated test matrix, revised responsibilities

Every method below is PROPOSED, not authored/executed. No imaging-file hashing test is part of F1. Parameterized rows exhaust the named enum/field/state combinations; real provider qualification is separate from controlled fault mocks.

| ID | Proposed test method(s) | Required evidence |
| --- | --- | --- |
| V01 | ManifestValueTest.rejectsNullMalformedAndOversizedValues | Required fields/Optional/list entries; supplied digest shape/size; fixed messages; every core field boundary |
| V02 | ManifestValueTest.relativePathsPreserveAllowedTextAndRejectUnsafeForms | All portable path rejection classes; exact permitted Unicode/case/spaces and synthetic identifying/UID-like path retained restricted, never treated as anonymized |
| V03 | ManifestValueTest.suppliedDigestDoesNotReadOrValidateFiles | Construct digest/source values with synthetic nonexistent SOURCE path; no I/O/probe/hash/readiness inference; empty-content or gzip-wrapper digest remains supplied bytes evidence |
| V04 | ManifestValueTest.workflowFailuresHaveOnlyApprovedPhaseCodePairs | Exact two components/four phases/18 codes; exhaustive allowed/rejected Phase/Code pairs; null/fixed-message/copy/order cases; no original-enum/source/path/message/Throwable payload; supplied F6 hash failure kept without execution |
| V05 | ManifestValueTest.collectionsAreCopiedCanonicalAndUnique | Every supplied list mutation/unmodifiable/duplicate case and small fixture permutation |
| M01 | ManifestPlanTest.plansResolveAllReferencesAndMappings | Kind/source cardinality, root role, duplicate IDs/references, unknown keys, total references bound, shared equal-digest COPY destination with distinct sources; conflicts rejected |
| M02 | ManifestPlanTest.frozenPlanSeparatesExpectedEvidenceFromExecution | Synthetic accepted F5 layout plus supplied F6 digests compose immutable execution-evidence plan with unchanged source/operation/destination semantics; absent hash only on nonexecuting input, published digest cannot be filled/replaced, no hashing/receipt/state/actual observation in plan, complete plan not operation success |
| M03 | ManifestPlanTest.assessmentPreservesAcceptedF0Combinations | All 14 accepted raw pairs × both extension hints; full F0 fields/reason order unchanged; no factory promotion |
| M04 | ProcessingEvidenceTest.originalErrorsHaveOneAuthoritativeHome | Exact reconstructionErrors/conversionErrors components once, no SourceSummary.errors or original-enum ManifestFailure fields; defensive order/copy; M5 raw diagnostics solely in assessment; phase errors not duplicated/reclassified into workflow failure payload |
| M05 | ProcessingEvidenceTest.validatesPhaseAndTypedConversionFacts | Both scopes success/failure, dimension/count/finite affine/spacing/intensity, source count/version/fingerprint, conversion fact presence/absence; no reopening implication |
| C01 | PersistenceContractTest.recordKindAndObservationMatrixIsExhaustive | Both record kinds, all9 states, nullability/times/digest/source-count/disposition/phase error variants; NOT_STARTED derived-only; conversion WRITTEN_UNVERIFIED |
| C02 | PersistenceContractTest.receiptsAndNeutralFailuresAreFrozen | Plan sequence0 vs positive heads, hash shapes, all 3 publication outcomes/receipt applicability; neutral exception fixed message/null cause/no suppressed payload |
| C03 | PersistenceContractTest.currentViewCopiesAndRetainsExactPlan | Returned immutable state joins distinct mappings; no plan rewrite/repeated snapshots; phase diagnostics authoritative |
| L01 | ManifestReplayTest.initialPlanAndConsecutiveRecordsReplayExactly | Source/operation seed, JOB RUNNING, intent/outcome, counters/current receipt; sequence/time/hash/job/version validation |
| L02 | ManifestReplayTest.transitionPairsAndJobStatesAreExhaustive | Every 9×9 operation pair and 5×5 job pair with predicates; <= 1 active op, copy completion/identical/skip/failure, conversions never completed verified jobs |
| L03 | ManifestReplayTest.uncertaintyPreservesFactsWithoutRecoveryExecution | Intent/outcome crash gap, recovery observations retain known facts/failures; no automatic success/retry/unblock/mutation or SOURCE inspection |
| L04 | ManifestReplayTest.validateNextIsPureAndAcceptAdvancesOnlyOnce | Failed validation leaves reducer/current receipt unchanged; accept idempotence is controlled by store, no double fold; current only snapshots on request |
| L05 | ManifestReplayTest.limitsAndCheckedSequenceNeverWrapOrDropFacts | Count/reference/failure limits, Long.MAX_VALUE overflow and gap despite smaller normal cap; state/counters remain unchanged on rejection |
| J01 | JsonManifestCodecTest.planAndCheckpointGoldenBytesAreCanonical | Exact literal/key/record ordering, UTF-8 noBOM, oneLF, explicitnull, quoted supplementary Unicode, both schemas and every observation kind |
| J02 | JsonManifestCodecTest.onlyFixedCanonicalSchemaFormsDecode | Direct typed cursor forms only; valid JSON reordered/spaced/alternate-escaped/alternate-number forms, generic structures/unknown/missing/duplicate keys/trailing tokens rejected; removed original-enum ManifestFailure keys, old phases/EXISTING_DIAGNOSTIC and space-corrupted digest/hash identifiers reject; no general parser/AST dependency |
| J03 | JsonManifestCodecTest.numbersUnicodeAndBoundsRemainExact | Integer > 2^53/long boundaries, finite doubles, malformed UTF-8/unpaired/control, fixed nesting, plan/record/string/count bounds; no unbounded allocation |
| J04 | JsonManifestCodecTest.roundTripsEverySupportedInternalRecord | Exact encode/decode/re-encode equality; typed constructors enforce invalid model; unknown version/enum fail, no default/migration |
| P01 | ManifestProjectionTest.projectsRealM6AndM7WithoutDuplicatedErrors | Actual result fixtures/provenance cross-check; original M6/M7 codes only in their respective ProcessingEvidence lists, no optional original-enum workflow fields or SourceSummary.errors; legacy provenance flag distinct from actual phase success, WRITTEN_UNVERIFIED/NOT_PERFORMED only |
| P02 | ManifestProjectionTest.restrictedPathsRemainSensitivePublicFieldsDoNot | Synthetic name/patient/UID-like relative paths remain exact restricted; legacy free text/runtime absolute targets omitted; public fields/bytes omit all paths/hashes/IDs/times/metadata |
| P03 | ManifestProjectionTest.countsCurrentObservationsNotRepeatedHistory | Repeated recovery fact not double-counted; each non-NONE raw M5 diagnostic counted once per source from assessment; M6/M7 errors once per current observation from authoritative lists; workflow pairs only from their own lists, no recoded original failure; exact namespace/code/count shape and allowlist/rejected arbitrary tokens, zero failures omitted, canonical order/duplicates/checked positive aggregate sums; distinct equal-byte sources counted separately |
| S01 | ManifestStoreTest.onePlanAndSmallRecordsPublishWithExactReceipts | Plan published once; consecutive hash-linked records and exact receipt checksums; no full-plan serialization after each operation; supported prefix facts; controlled F6/F9 owner fixture requires acknowledged plan receipt and IN_PROGRESS intent before first simulated mutation, without changing logical layout or hashing in F1 |
| S02 | ManifestStoreTest.planAndAppendIdempotenceNeverOverwrite | Identical plan/current/earlierrecord retries, stale/new/different same-sequence request, wrong expected head/job/hash, absent plan; no head advancement on an old retry |
| S03 | ManifestStoreTest.restartWithoutReceiptAndMinimumReceiptReplayExactly; ManifestStoreTest.corruptionUnsupportedAndAcknowledgedLossNeverFallBack | Empty input with plan only/existing full journal returns current state; no plan/records returns empty result; matching anchor/older/head minimum receipts allow full replay; absent/changed/wrong-job/wrong-plan receipt rejects, including no-plan acknowledged loss; gaps, wrong bucket/name/type/version, canonical/link/digest/model corruption, final symlink, missing/changed minimum receipt; Fail closed; no older success returned as usable current state |
| S04 | ManifestStoreTest.intentOutcomeAndAcknowledgmentCrashBoundariesAreFactual | Inject before/after staging, forcing, publication and acknowledgment; incomplete stages ignored as authoritative evidence; absent outcome retains IN_PROGRESS; known published retry; UNKNOWN blocks further appends |
| S05 | ManifestStoreTest.primaryCleanupAndCloseFailuresRemainExplicit | NOT_PUBLISHED/PUBLISHED/UNKNOWN including cleanup; retained committedbytes; lease release/closefailure; no source/output deletion or swallowed failure |
| S06 | ManifestStoreTest.privateCreationAndJobLeaseAreQualified | Effective POSIX/Windows stage/final/lockaccess, configuredrootcanonicalcontainment, forbidden entrylinks, concurrent, overlapping and unsupported leases; no blanket configured-ancestor ban |
| S07 | ManifestStoreTest.budgetReservationsSequenceAndBucketsAreBounded | Exact byte/count/reference bounds; reserved outcome/control capacity; overflow/cap rejection before staging; bucket boundaries; staging consumes no sequence; disk-full stop |
| S08 | ManifestStoreTest.appendDoesNotReencodeOrRereplayFullPlan | Instrument actual adapter/codec boundaries: plan encoded once; prefix read once per lease; one bounded record per append; no complete current view per append; many-operation fixture linear work and retained-history absence |
| S09 | ManifestStoreTest.supportedLocalProviderPublicationIsExecutable | Actual Windows local NTFS / supported POSIX: hard-link no-replace, complete visibility, lease and access guarantees, allocation and startup/replay measurements; unsupported fails; no accepted evidence from skipped tests |
| E01 | PublicReportWriterTest.publicGoldenBytesAndTargetsAreLimited | Public schema golden bytes, no privileged decoder, no sensitive identifiers, OUTPUT containment/namespace protection; identical existing bytes are idempotent, different bytes conflict |
| E02 | PublicReportWriterTest.exportFailuresPreserveRestrictedEvidence | Stage/publication/cleanup faults; neutral failure outcomes; unchanged plan/journal/imaging bytes; no manufactured checkpoint receipt |
| B01 | ProvenanceContractBoundaryTest.actualApisGenericsAndResponsibilitiesAreConfined | Actual/nested constructors/records/methods/generics/enumsets; no application Path/stream/bytes/JSON/outerlib/Throwable payload, no digest I/O capability; explicitly scoped replay reducer |
| B02 | ProvenanceContractBoundaryTest.phaseErrorsPrivacyAndOwnedSliceShapeAreFrozen | Sole original diagnostic homes: assessment.initialDetection, reconstructionErrors, conversionErrors; SourceSummary has no errors; exact workflow-only ManifestFailure components/phase/code sets and public aggregate namespace allowlist; immutable execution-evidence plan and delta-only checkpoint shape; no imaging processing/readiness shortcuts or protected API change |
| R01 | Independent local Python JSON interoperability | Generated plan/checkpoint/public fixtures parsed by Python json, exact Unicode/integers/semantics/golden expectations; actual commands recorded, no dependency |
| R02 | Protected regression/source comparison | Existing F0/M5/architecture and M6/M7 unchanged; exact baseline protected diff; no added production caller/build dependency |
| R03 | Final exact-tree closure | Actual Java 21/Maven clean test, zero failures/errors/skips, actual tested tree/count; criterion mappings, provider evidence and whitespace checks; stop before F2 |

No test/parser/schema is authored now. Fault seams remain private/package-private inside the budgeted adapter/codec, not extra helper types/files/ports. Bounds/performance claims require executable measurements when their owning slice is authorized. Corpus interoperability remains independent of the canonical decoder.

### F1-S0.17 Revised small-slice plan and exact per-slice budgets

Budgets count distinct paths within that slice, including the tracker. All ADDs are proposed later work; only S0 tracker edits are authorized now. A later MODIFY applies only to explicitly named files added in earlier accepted F1 slices. No placeholder/class/interface/method for a later slice is introduced early.

| Slice | Tests first and limited ownership | ADD paths | MODIFY paths | Slice total |
| --- | --- | --- | --- | --- |
| S0 final correction | Repository/specification only; final architectural review | 0 | tracker1 | 1 |
| S1A core evidence values | RelativePath, ContentDigest, SourceFileRecord, ManifestFailure; ManifestValueTest | 4 production +1 test | tracker1 | 6 |
| S1B immutable plan/phase values | ProcessingEvidence, ManifestOperation, ProvenanceManifest; ManifestPlanTest and ProcessingEvidenceTest | 3 production +2 tests | tracker1 | 6 |
| S2A persistence protocol contracts | CheckpointRecord, ManifestReceipt, ManifestState, ProvenancePersistenceException and ManifestStore port; PersistenceContractTest. No adapter/replay/codec | 5 production +1 test | tracker1 | 7 |
| S2B pure replay | ManifestReplay reducer and ManifestReplayTest; no filesystem or persisted current snapshot | 1 production +1 test | tracker1 | 3 |
| S3 internal canonical codec | JsonManifestCodec and its test; plan/checkpoint schema documents; independent JSON fixtures | 1 production +1 test +2 schema docs | tracker1 | 5 |
| S4 restricted store adapter/publication | JsonManifestStore and ManifestStoreTest only; actual job lease, delta journal, limits/private access/qualification; no export | 1 production +1 test | tracker1 | 3 |
| S5A privacy projection/export model | PublicJobReport, ManifestProjection and ManifestProjectionTest; public schema document. No export adapter/port/codec reference added | 2 production +1 test +1 schema doc | tracker1 | 5 |
| S5B public export boundary/adapter | PublicReportWriter, JsonPublicReportWriter and its test; add only owned public encode method/tests to accepted codec | 2 production +1 test | JsonManifestCodec1 +JsonManifestCodecTest1 +tracker1 | 6 |
| S6 architecture/closure | Dedicated ProvenanceContractBoundaryTest and fullclosure mapping/evidence; no refactor/wiring | 1 test | tracker1 | 2 |

Each slice stops for its own executable/protected regression review and acceptance. S4 specifically blocks on unproved Windows/private-access/publication/resource behavior; do not weaken the contract or merge slices to hide that gap. S1A/S1B introduce no receipts, persistence exception, store state/protocol or public-report types. PublicJobReport first appears in S5A. Persistence contracts and adapter are separately reviewable. F1-S1A remains unauthorized; none of the proposed slices is started by this revision.

### F1-S0.18 Recalculated exact full F1 file budget

**33 distinct paths: ADD 32 / MODIFY1 =19 new production files +10 new tests +3 new schema documents +1 existing tracker.** Schema documents are proposals only; none exists yet. Relative to the rejected proposal: remove 2 filesystem digest production files and 1 digest test; add 3 reporting journal/replay/current-state production files, 4 smaller protocol/model/replay tests, and 1 separate checkpoint schema. This increase comes from explicit smaller slices and delta-journal semantics, not hidden hashing/utility work. The latest S0 re-review approved this 33-path full F1 ceiling and the owning-slice allocations. These final corrections change no path or slice budget; they add only a nested public aggregate namespace within the already-budgeted PublicJobReport, not a file/helper/placeholder. The ceiling does not authorize a single 33-path change set or any implementation.

| Action | Exact path | First owning slice |
| --- | --- | --- |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/RelativePath.java | S1A |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ContentDigest.java | S1A |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/SourceFileRecord.java | S1A |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ManifestFailure.java | S1A |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ProcessingEvidence.java | S1B |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ManifestOperation.java | S1B |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ProvenanceManifest.java | S1B |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/CheckpointRecord.java | S2A |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ManifestReceipt.java | S2A |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ManifestState.java | S2A |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ProvenancePersistenceException.java | S2A |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/port/out/ManifestStore.java | S2A |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ManifestReplay.java | S2B |
| ADD | src/main/java/org/cbihi/mrinormalizer/infrastructure/filesystem/JsonManifestCodec.java | S3 |
| ADD | src/main/java/org/cbihi/mrinormalizer/infrastructure/filesystem/JsonManifestStore.java | S4 |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/PublicJobReport.java | S5A |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ManifestProjection.java | S5A |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/port/out/PublicReportWriter.java | S5B |
| ADD | src/main/java/org/cbihi/mrinormalizer/infrastructure/filesystem/JsonPublicReportWriter.java | S5B |
| ADD | src/test/java/org/cbihi/mrinormalizer/ManifestValueTest.java | S1A |
| ADD | src/test/java/org/cbihi/mrinormalizer/ManifestPlanTest.java | S1B |
| ADD | src/test/java/org/cbihi/mrinormalizer/ProcessingEvidenceTest.java | S1B |
| ADD | src/test/java/org/cbihi/mrinormalizer/PersistenceContractTest.java | S2A |
| ADD | src/test/java/org/cbihi/mrinormalizer/ManifestReplayTest.java | S2B |
| ADD | src/test/java/org/cbihi/mrinormalizer/infrastructure/filesystem/JsonManifestCodecTest.java | S3 |
| ADD | src/test/java/org/cbihi/mrinormalizer/infrastructure/filesystem/ManifestStoreTest.java | S4 |
| ADD | src/test/java/org/cbihi/mrinormalizer/ManifestProjectionTest.java | S5A |
| ADD | src/test/java/org/cbihi/mrinormalizer/infrastructure/filesystem/PublicReportWriterTest.java | S5B |
| ADD | src/test/java/org/cbihi/mrinormalizer/architecture/ProvenanceContractBoundaryTest.java | S6 |
| ADD | docs/schemas/provenance-plan-v1.schema.json | S3 |
| ADD | docs/schemas/provenance-checkpoint-v1.schema.json | S3 |
| ADD | docs/schemas/public-job-report-v1.schema.json | S5A |
| MODIFY | docs/planning/PRE-M8-FOUNDATION-TRACKER.md | S0 and specifically authorized slices |

No helpers, additional adapters/ports, generic parser/AST, source hashing execution, new dependency, fixtures/scripts/resource files or composition change is hidden outside this budget. Nested records/enums remain in their owning files. Golden/fault fixtures live in listed tests; controlled seams stay in listed adapters/codec. S5B's named codec/test modifications reuse already counted paths. No slice may add a later-owned API placeholder or unbudgeted helper type.

### F1-S0.19 Architecture/protected files and unresolved gates

Application owns supplied values, phase/error semantics, plan/journal/replay/current-state interpretation, privacy projection and two real persistence/export output ports. Infrastructure owns canonical persistence bytes, its own journal integrity hashes, restricted access/lease/containment/publication and export. Domain gets no new dependency/type. Path exists only on infrastructure construction; application APIs expose no concrete adapter, NIO stream/byte/JSON AST or framework payload. Explicit ManifestReplay is a bounded reporting reducer, not imaging validation/orchestration. Existing architecture tests remain unchanged; dedicated actual declaration/generic checks are added only in S6.

Protected unchanged: every F0 file/test, FoundationContractBoundaryTest, M5/M6/M7 source/tests including ProvenanceRecord/SelectedSourceFingerprint/ConversionValidationReport, existing ports/domain models, existing architecture tests, Main, DependencyContainer, pom.xml, AGENTS.md, PROJECT.md, README/MILESTONES/DEVELOPMENT_LOG/DECISIONS, all architecture documents and .roomodes/RC2/RC3. No patient data enters Git. F2/F3/F5/F6/F8/F9 ownership work, GUI and M8 remain blocked.

Final specification review concerns the corrected identifiers, logical-plan/evidence composition sequence, receipt-free restart and sole diagnostic authority with revised failure/count signatures and schema descriptions. The remaining S0 design choices, 33-path ceiling and small-slice allocations are approved at specification level. Later executable acceptance gates remain: capacity/lease/startup/heap/physical-allocation qualification; hard-link/private-access/containment support on actual Windows/POSIX; directory durability/power-loss expectations. The decoder's narrow accepted language must stay explicit and independently JSON-interoperable. If any approved budget/contract cannot be implemented, stop/revise rather than add a dependency/helper or weaken evidence.

F8 must separately version extended reopened-output verification; schema 1 cannot mark a validated conversion job complete. F5 owns the immutable logical organization layout/keys/destinations without hashing. F6 owns pre-execution digest computation/revalidation and later supplied match observations. F6/F9 composes the immutable F1 execution-evidence plan without altering F5 semantics, obtains the F1 plan publication receipt before any imaging mutation, then acknowledges operation intent. F9 owns recovery and sequential lifetime-store composition. An interrupted/corrupt log cannot auto-authorize continuation. No production caller/container wiring is part of F1.

### F1-S0.20 Historical revision evidence and stop (superseded for S1A only)

Current work is specification only. Complete final-correction review diff is generated against the tracker at the start of this final-review request; the repository Git diff also captures the cumulative tracker change from 0757d72. Actual change this revision must be exactly docs/planning/PRE-M8-FOUNDATION-TRACKER.md; all other existing files, including any pre-existing untracked review copy, remain unchanged. No Java/test/schema/dependency/helper implementation, commit or executable Java verification is claimed. Whitespace/protected-file/budget checks are Git/source documentation evidence only.

    F0 ACCEPTED / CLOSED
    F1-S0 ORIGINAL REVIEW — REVISE
    F1-S0 FINAL REVIEW PREPARED — final architectural review pending
    F1-S1A / F1-S1B NOT AUTHORIZED
    F1 IMPLEMENTATION NOT STARTED
    F2-F11 BLOCKED
    GUI BLOCKED
    M8 BLOCKED

**STOP FOR FINAL ARCHITECTURAL REVIEW. Modify only the tracker; do not implement any proposed Java, test, dependency or schema, introduce placeholders or commit.**


## F1-S1A accepted — b7a327d55ac26265474378fec00ae9a03ba7784e

Authorization on 2026-10-04: the user explicitly accepts the immediately preceding S0 specification and authorizes **S1A only**, tests first for V01-V05, with exactly six paths. No S1B placeholder or later-owned type/method is authorized. The earlier S0 planning/stop statements above are historical: the accepted specification remains the contract, while this section and program control record current authorization/evidence. S1B and all later slices remain blocked.

### Baseline and exact changed-file budget

The available local Git Java baseline is 0757d7290ee8443062960392658ff1702817dfc5, with all accepted F0 Java/test files. The exact accepted S0 tracker from the preceding final-correction review was retained before S1A editing. The user identifies an immediately preceding docs-only approval checkpoint but supplies no hash for it; this assistant does not invent or claim verification of that hash. Work is isolated from the pre-existing dirty worktrees. The deliverable patch is incremental against the accepted S0 tracker and the unchanged Java baseline, rather than repeating S0 specification additions. No commit or staging is performed.

| Action | Exact repository path |
| --- | --- |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/RelativePath.java |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ContentDigest.java |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/SourceFileRecord.java |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ManifestFailure.java |
| ADD | src/test/java/org/cbihi/mrinormalizer/ManifestValueTest.java |
| MODIFY | docs/planning/PRE-M8-FOUNDATION-TRACKER.md |

**S1A: exactly 6 paths = 4 production additions +1 test addition +1 tracker modification.** Full F1 ceiling remains 33 distinct paths; every later path retains its approved owning slice. No additional path/dependency is required or authorized.

### Source behavior and invariant-to-test mapping

All four records retain exactly the approved components and generated record APIs plus canonical constructors. RelativePath has only the approved Root enum; ManifestFailure has only its approved Phase/Code enums. Production code has no explicit public factory/convenience/upgrade/I/O API or utility/helper type.

ManifestValueTest was authored and source-reviewed before any of the four production files existed; a source snapshot records that order. This is tests-first authorship, **not an executed red/green cycle**. There are 17 authored @Test methods. Historical source preparation was STATIC REVIEW ONLY; that pending-execution status is superseded by the user-reported 17/17 focused pass and 191-test full regression below. V01-V05 and the mapped test methods are PASS under those executable gates; no accepted checkpoint is implied.

| Criterion / constructor invariant | Authored ManifestValueTest method(s) |
| --- | --- |
| V01: every required record component, Optional/list and list entry is nonnull; fixed non-identifying argument errors with null cause/no suppressed payload | rejectsNullCoreComponentsWithFixedMessages; shared reject helper used across negative cases |
| V01: ContentDigest size is nonnegative through Long.MAX_VALUE; exactly 64 lowercase hex characters, preserved as supplied | suppliedDigestRejectsMalformedHashesAndNegativeSizes; suppliedDigestAcceptsExactSizeAndHexBoundaries |
| V01: source uses SOURCE root; failures bounded at 64 before copying; no truncation | sourceRecordRequiresSourceRootAndBoundsFailures; allApplicableFailuresFitTheSourceLimitWithoutLoss |
| V01/V04: exact four record shapes, generic Optional<ContentDigest>/List<ManifestFailure> signatures, sole canonical public constructors, exact enum sets, no explicit public methods | approvedRecordAndEnumShapesHaveNoExplicitPublicMethods |
| V02: reject empty/absolute/drive/UNC/URI/backslash/colon references, empty/dot/dot-dot segments, trailing dot/space, controls/NUL and unpaired surrogates | relativePathsRejectUnsafeReferenceForms |
| V02: reserved device bases rejected case-insensitively, including extensions and nested segments; numeric/superscript COM/LPT device suffixes and console device names | relativePathsRejectReservedDeviceBasesIncludingExtensions |
| V02: preserve allowed Unicode scalar spelling, case, spaces and synthetic identifying/UID-like paths; no normalization, case folding, percent decoding or anonymization inference | relativePathsPreserveAllowedTextAndIdentityExactly |
| V02: path maximum 4096 UTF-8 bytes, counted without byte arrays; exact 1/2/3/4-byte scalar widths and one-byte-over rejection | relativePathUtf8LimitCountsScalarWidthsExactly |
| V03: supplied digests/source references perform no file read/hash/probe; keep original assessment/raw detection/extensionMismatch and conservative readiness unchanged | suppliedDigestAndSyntheticSourceDoNotUpgradeAssessment |
| V03: absent digest does not fabricate HASH_FAILED; supplied empty-content/wrapper digest does not assert validity/readiness; unavailable assessment and empty workflow failures are representable | absentDigestAndSuppliedEmptyOrWrapperDigestDoNotInventFailures |
| V03/V04: new owner-supplied workflow fact does not translate or replace original M5 evidence; INPUT_TOO_LARGE remains inconclusive | suppliedWorkflowFailureDoesNotTranslateRawDetectionEvidence |
| V04: exact approved four phases/eighteen codes and exhaustive 72-pair matrix; 29 permitted/43 rejected pairs; no original diagnostic fields or free-text failure payload | workflowFailuresHaveOnlyApprovedPhaseCodePairs; approvedRecordAndEnumShapesHaveNoExplicitPublicMethods |
| V05: defensively copy failures, sort by phase then code declaration order, return unmodifiable list, reject repeated equal phase/code pairs | collectionsAreCopiedCanonicalAndUnique |
| V05: all six permutations of a three-failure fixture produce equal values/hashCode/canonical order | allFailurePermutationsProduceTheSameValue |
| V05: same code in distinct applicable phases is distinct evidence, ordered deterministically | equalCodesInDifferentAllowedPhasesRemainDistinct |
| V05: preserve all 29 distinct applicable pairs without loss; cap 64 does not permit duplicates (no valid 64-distinct-pair list exists in this frozen matrix) | allApplicableFailuresFitTheSourceLimitWithoutLoss; sourceRecordRequiresSourceRootAndBoundsFailures |

Null/malformed/coherence failures are fixed IllegalArgumentException messages. No input path, hash, source, enum-value rendering, underlying exception text or other supplied content is interpolated. RelativePath validates a logical string only and establishes no actual containment. ContentDigest validates supplied shape only; original imaging bytes are not hashed. SourceFileRecord accepts optional digest and any already-valid F0 assessment without inferring evidence authenticity or processing success. Canonical failures use phase/code declaration order, matching the frozen wire-order proposal, and value equality for duplicate detection.

### Static review and actual user-supplied executable evidence

Static review checks the six-path budget, tests-first source ordering, constructor matrix and fixed messages, dependency/public shapes, lack of I/O/hash/probe/future-symbol APIs, and protected existing-file byte equality. git diff --check passes; the complete incremental patch includes all five new files as well as tracker edits. Git diff does not show untracked additions unless explicitly included, so the review patch includes them rather than omitting their source.

No existing F0/M5/M6/M7 Java/test, architecture test, output port, composition file, build/dependency file, schema or architecture document changed. The four new production files import only Java language/library value/collection types plus the existing FormatAssessment. No Path, stream, byte-array, JSON or SelectedSourceFingerprint API/call is added. No S1B-or-later model, parser/serializer/schema/store/receipt/checkpoint/replay/projection/public-report symbol is implemented, referenced or stubbed in S1A Java.

Historical assistant source-preparation environment: OpenJDK 17.0.20; Maven unavailable. The assistant performed static review only and did not compile or execute Java/JUnit. The earlier pending-execution requirement is superseded by the actual user-supplied results recorded below; these are user-reported executable evidence, not assistant-run tests.

**F1-S1A ACCEPTED — b7a327d55ac26265474378fec00ae9a03ba7784e**

User-reported verification environment:

- OpenJDK Temurin 21.0.12.1 LTS.
- Maven 3.9.16.
- Windows 11 amd64.

| Gate | Actual command | Tests | Failures | Errors | Skipped | Result |
| --- | --- | --- | --- | --- | --- | --- |
| Focused S1A / V01-V05 | mvn "-Dtest=ManifestValueTest" test | 17 | 0 | 0 | 0 | BUILD SUCCESS |
| Full regression | mvn clean test | 191 | 0 | 0 | 0 | BUILD SUCCESS |

The full clean regression compiled **63 production source files, release 21**, and **22 test source files, release 21**. The user also reports **git diff --check passed**. The tracker-only bookkeeping update additionally passes the assistant's local git diff --check; no Maven rerun is claimed for this documentation edit.

Exact S1A scope remains four production additions (RelativePath, ContentDigest, SourceFileRecord, ManifestFailure), ManifestValueTest and this tracker. No existing Java/test/dependency/build file changed. This evidence update modifies only docs/planning/PRE-M8-FOUNDATION-TRACKER.md; all five S1A Java/test additions and every other repository file remain byte-identical to their pre-update state. No schema, pom.xml, later-slice implementation or dependency change is made.

Historical S1A evidence-only stop: no commit SHA existed when the user supplied the 17-test focused and 191-test full results, so acceptance was then pending. Superseded by **F1-S1A ACCEPTED — b7a327d55ac26265474378fec00ae9a03ba7784e** in the explicit S1B authorization. The actual accepted parent was fetched and checked out; S1A production/test files remain unchanged. No S1B acceptance or commit is implied.

    F0 ACCEPTED / CLOSED
    F1-S0 ACCEPTED
    F1-S1A ACCEPTED — b7a327d55ac26265474378fec00ae9a03ba7784e
    F1-S1A ACCEPTED CHECKPOINT — b7a327d55ac26265474378fec00ae9a03ba7784e
    F1 INCOMPLETE
    HISTORICAL S1A STOP — S1B WAS NOT AUTHORIZED AT THAT TIME
    F2-F11 BLOCKED
    GUI BLOCKED
    M8 BLOCKED

**Historical S1A evidence-only stop (superseded by S1A checkpoint acceptance and S1B-only authorization below): no commit/push or S1B implementation was authorized by that bookkeeping request.**


## F1-S1B authorization and source review

First bookkeeping action: **F1-S1A ACCEPTED — b7a327d55ac26265474378fec00ae9a03ba7784e**. This exact parent commit was fetched and checked out before S1B work. The user explicitly authorizes S1B only: ProcessingEvidence, ManifestOperation, ProvenanceManifest, ManifestPlanTest, ProcessingEvidenceTest and this tracker; tests first for M01-M05. Source preparation and static evidence are recorded below. No commit or push; S2A and later slices remain NOT AUTHORIZED.


### S1B prepared behavior and protected boundaries

The three production files implement only the accepted record components, approved nested records/enums, canonical constructors and private constructor-support routines. ProcessingEvidence owns only supplied typed M6/M7 phase facts: RECONSTRUCTION forbids conversion errors/facts; successful phase evidence has no errors; successful CONVERSION requires facts; failed conversion requires original reconstruction/conversion errors and forbids successful conversion facts. SourceSummary retains its independent provenanceSuccessful flag and has no errors field. The two authoritative original-error lists are copied, sorted in enum declaration order and reject null/duplicate entries. No original diagnostic is copied into a workflow failure value.

SourceSummary validates optional lowercase 64-hex aggregate fingerprint, nonnull timestamp, nonnegative counts with acceptedSlices <= inputCount, and nonempty ASCII build-version tokens up to 64 characters using letters/digits/dot/underscore/plus/hyphen. These are supplied values, not authenticated build provenance. It does not call SelectedSourceFingerprint or hash any bytes. ConversionFacts reuses the existing immutable AffineMatrix4/IntensityTransform/ScalarType guarantees; its own constructor checks nonnull references, positive dimensions, checked exact long voxel count and the existing report's finite spacing contract (row/column positive, slice nonnegative). Supplied conversion flags are preserved without imposing new M7 geometry semantics or claiming reopened verification. Overflow becomes a fixed argument error with no propagated cause.

ManifestOperation contains only stable supplied 64-lowercase-hex operationId, kind, unique nonempty SOURCE references and OUTPUT destination; COPY has exactly one source, conversion one or more. It neither generates IDs/layout nor executes operations. ProvenanceManifest is immutable schemaVersion 1 execution-evidence inventory/layout, composed later from accepted F5 logical semantics plus F6 supplied digests. It checks source/operation key uniqueness, reference resolution, exact destination compatibility and lexical file/ancestor destination conflicts. Shared destinations require COPY on both sides with equal known ContentDigest values, preserving distinct source mappings. Case/provider/Unicode alias resolution remains with F5/F6; there is no filesystem lookup or name normalization. Missing digests are allowed on plans for later nonexecuting/blocked decisions and never imply execution eligibility or success.

Source count and operation count are each bounded at 100000; total planned source references at 200000; each operation's unique source references cannot exceed the source inventory cap. Constructors copy/order lists: source keys/reference arrays follow unsigned lexicographic UTF-8 order through scalar comparison, including supplementary Unicode; operation arrays use ASCII operationId order. Temporary indexes exist only during immutable construction and do not create a replay/state/store API. The 64-MiB canonical plan-byte bound remains owned by the later S3 codec/S4 publication boundary; no serializer is introduced here to estimate or enforce encoded bytes.

### Exact S1B six-path budget

| Action | Exact repository path |
| --- | --- |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ProcessingEvidence.java |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ManifestOperation.java |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ProvenanceManifest.java |
| ADD | src/test/java/org/cbihi/mrinormalizer/ManifestPlanTest.java |
| ADD | src/test/java/org/cbihi/mrinormalizer/ProcessingEvidenceTest.java |
| MODIFY | docs/planning/PRE-M8-FOUNDATION-TRACKER.md |

**Exactly 6 paths: 3 production additions +2 test additions +1 tracker modification.** The full 33-path F1 ceiling and later owning-slice budgets remain unchanged. Every S1A file and all existing F0/M5/M6/M7 Java/tests, architecture tests, output ports, schemas, pom.xml, dependencies, composition and architecture docs remain unchanged. No S2A placeholder/stub, extra production type, factory, evidence projector, hashing/filesystem/JSON API, receipt/checkpoint/replay/persistence exception/store/public-report symbol or F2/F5/F6 implementation is added.

### M01-M05 authored test mapping

Both test files were authored before any of the three S1B production files existed; source snapshots preserve that order. This is tests-first authorship, not a claimed executed red/green cycle. There are **27 authored @Test methods: 16 ManifestPlanTest +11 ProcessingEvidenceTest**. The initial source/static-only status is historical and superseded by the user-supplied 27-test focused executable PASS below, covering M01-M05. No accepted S1B checkpoint is claimed.

| Criterion | Authored method(s) | Cases |
| --- | --- | --- |
| M01 required components/version/ID/kind/source cardinality/root | ManifestPlanTest.requiredComponentsAndVersionRejectWithFixedMessages; operationIdsAndKindsRemainExactlyApproved; operationCardinalityRootsNullsAndDuplicatesReject | Nulls, schema version 1 only, exact kind set, 64-hex ID, COPY one source/conversion nonempty, SOURCE/OUTPUT roles, null/duplicate references |
| M01 references/key uniqueness/destination compatibility | ManifestPlanTest.plansResolveAllReferencesAndRejectDuplicateKeys; equalDigestCopyMappingsRetainDistinctSources; incompatibleAndConversionDestinationSharingRejects; lexicalAncestorDestinationsConflictWithoutFilesystemInference | Unknown source key, duplicate source/operation ID, equal-digest distinct-source shared COPY, missing/different size/hash, conversion overlaps, exact lexical ancestors; no provider/case folding |
| M01 count/reference bounds | ManifestPlanTest.countLimitsRejectBeforeDuplicateProcessing; exactSourceAndReferenceLimitsAreRepresentable; exactOperationLimitIsRepresentable | Over-cap before duplicate work, valid 100000 sources/operations, exact 200000 references and one over; no truncation |
| M02 immutable supplied execution-evidence plan | ManifestPlanTest.frozenPlanSeparatesExpectedEvidenceFromExecution; listsAreCopiedOrderedAndUnmodifiable; canonicalOrderingUsesUnsignedUtf8RatherThanUtf16; emptyAndNonexecutingInventoriesRemainPlansOnly; operationAndManifestRecordShapesRemainFrozen | Same accepted layout/ID/destination with supplied digest, missing-digest inventory remains immutable, copied canonical unmodifiable arrays, BMP/supplementary UTF-8 ordering, empty plans, exact record/generic shapes |
| M03 preserve F0 evidence | ManifestPlanTest.assessmentPreservesAcceptedF0Combinations | All 14 accepted raw pairs times both extensionMismatch values, same assessment/raw instances and unchanged meaning |
| M04 sole original error authority/copy/order | ProcessingEvidenceTest.originalErrorsHaveOneAuthoritativeHome; phaseErrorsAreCopiedCanonicalUniqueAndUnmodifiable; errorPermutationsHaveEqualValuesWithoutCrossNamespaceStorage | Exact outer/nested record shape/generics, no SourceSummary error list, all original enum members copied once, canonical lists, duplicates/nulls/unmodifiable order |
| M05 scope/success/error/fact/summary matrix | ProcessingEvidenceTest.requiredEvidenceComponentsAndErrorEntriesRejectWithFixedMessages; phaseSuccessErrorAndFactPresenceMatrixIsExhaustive; provenanceFlagIsIndependentOfPhaseAndOverallExecution | Both scopes times success/error/fact/summary-presence combinations (64), coherent success/failure evidence, provenance flag independent, fixed null/error/coherence messages |
| M05 summary fingerprint/version/count/time | ProcessingEvidenceTest.sourceSummaryNullsFingerprintAndVersionTokensReject; sourceSummaryCountsAndSuppliedFieldsRemainExact | Nonnulls, exact hash shape, 64-character ASCII token bounds, nonnegative counts/accepted<=input, max integer counts and exact supplied values |
| M05 typed conversion fact invariants | ProcessingEvidenceTest.conversionFactNullReferencesAndDimensionArithmeticReject; conversionSpacingMatchesExistingReportContract; typedAffineIntensityScalarAndFlagsAreSuppliedFactsOnly | Required types, positive dimensions/exact count/overflow with fixed message and null cause, finite spacing, all scalar types and 16 flag combinations, exact reused immutable affine/intensity and no output target/reopening field |

### Historical static review and proposed executable verification

The following pre-execution record is retained as historical evidence. Its pending-verification statements are superseded by the actual user-supplied results below; the assistant did not run Maven locally.

Static/source review only: exact public/nested record shapes and enum sets, approved constructor invariants, immutable/canonical lists, fixed non-identifying messages, no input-derived path/hash/error/cause payload, no forbidden imports/APIs or later-owned types, exact six-path budget and protected-file byte equality. git diff --check passes. The complete patch includes all five untracked Java/test additions and the tracker delta relative to **b7a327d55ac26265474378fec00ae9a03ba7784e**; no existing file other than the tracker changes. No commit/staging/push is performed.

Assistant runtime remains OpenJDK 17.0.20 without Maven. **No compilation or Java/JUnit execution is claimed for S1B.** The large boundary fixtures, generic/record reflection assertions, phase matrix, Unicode order, checked arithmetic and all 27 authored tests require actual Java 21/Maven verification on the exact applied tree. Proposed local commands:

    mvn "-Dtest=ManifestPlanTest,ProcessingEvidenceTest" test
    mvn "-Dtest=ManifestValueTest,ManifestPlanTest,ProcessingEvidenceTest" test
    mvn clean test
    git diff --check

The full clean regression must include unchanged F0/M5/M6/M7/architecture gates and actual compilation/test counts; no expected count is recorded as an executed pass. The synthetic bound fixtures allocate 100000 model values/references to exercise declared limits; their actual resource behavior also awaits execution. Stop and report any executable contract defect instead of changing accepted S1A/protected types or adding a dependency/helper path.

    F0 ACCEPTED / CLOSED
    F1-S0 ACCEPTED
    F1-S1A ACCEPTED — b7a327d55ac26265474378fec00ae9a03ba7784e
    HISTORICAL F1-S1B SOURCE PREPARED / EXECUTABLE VERIFICATION PENDING
    F1-S1B CHECKPOINT NOT CREATED / NOT ACCEPTED
    F1 INCOMPLETE
    F1-S2A AND LATER SLICES NOT AUTHORIZED
    F2-F11 BLOCKED
    GUI BLOCKED
    M8 BLOCKED

**Historical source-preparation stop, superseded by the executable evidence below: no commit or push was authorized; S2A and later work remained blocked.**

### F1-S1B actual executable evidence supplied by the user (historical checkpoint-pending record)

**F1-S1B EXECUTABLE VERIFICATION PASSED — checkpoint pending**

The historical checkpoint-pending statements in this section are superseded by **F1-S1B ACCEPTED — 8b57b48e2a677a08a1d2f11f9eb760b0d3b8f00c** and the S2A-only authorization below. The user supplied the following actual executable results. These are user-reported Java release 21/Maven evidence, not an assistant-local rerun. No S1B commit SHA exists yet, so no accepted S1B checkpoint is claimed.

| Gate | Exact command | Tests | Failures | Errors | Skipped | Result |
| --- | --- | --- | --- | --- | --- | --- |
| Focused S1B / M01-M05 | mvn "-Dtest=ManifestPlanTest,ProcessingEvidenceTest" test | 27 | 0 | 0 | 0 | BUILD SUCCESS |
| Combined S1A/S1B values and plan | mvn "-Dtest=ManifestValueTest,ManifestPlanTest,ProcessingEvidenceTest" test | 44 | 0 | 0 | 0 | BUILD SUCCESS |
| Full clean regression | mvn clean test | 218 | 0 | 0 | 0 | BUILD SUCCESS |

The full clean regression compiled **66 production source files with release 21** and **24 test source files with release 21**. The user reports **git diff --check passed**, with only the known LF→CRLF informational warning on the tracker and no whitespace errors. The tracker-only evidence update additionally passes the assistant's local git diff --check; no Maven rerun is claimed for this documentation edit.

Exact S1B scope remains **3 production additions +2 test additions +tracker**: ProcessingEvidence, ManifestOperation, ProvenanceManifest, ManifestPlanTest, ProcessingEvidenceTest and this tracker. No existing S1A/F0/M5/M6/M7 Java/tests/build/dependency files changed. This evidence update modifies only docs/planning/PRE-M8-FOUNDATION-TRACKER.md; all five S1B Java/test additions and every other repository file remain byte-identical to their pre-update state. No schema, pom.xml, dependency or later-slice implementation changed.

    F0 ACCEPTED / CLOSED
    F1-S0 ACCEPTED
    F1-S1A ACCEPTED — b7a327d55ac26265474378fec00ae9a03ba7784e
    F1-S1B EXECUTABLE VERIFICATION PASSED — checkpoint pending
    F1-S1B ACCEPTED CHECKPOINT NOT CLAIMED — no commit SHA exists yet
    F1 INCOMPLETE
    F1-S2A AND LATER SLICES NOT AUTHORIZED
    F2-F11 BLOCKED
    GUI BLOCKED
    M8 BLOCKED

**STOP AFTER TRACKER-ONLY EVIDENCE UPDATE. No commit or push; no S2A or later work is authorized.**


## F1-S2A authorization and source preparation (historical; now accepted)

The following S2A source-preparation authorization/stop conditions are historical and superseded by the S2A executable evidence and accepted checkpoint 99dcd08d3c1e4d8653be1ef89b521422294be274, then the S2B-only authorization below.

First tracker update: **F1-S1B ACCEPTED — 8b57b48e2a677a08a1d2f11f9eb760b0d3b8f00c**. The accepted parent was fetched and checked out in an isolated clean worktree before S2A edits. User-reported S1B executable evidence remains focused 27/27, combined S1A/S1B 44/44 and clean full regression 218/218, zero failures/errors/skips, BUILD SUCCESS; 66 production and 24 test sources compiled under Java release 21. No standalone bookkeeping commit is made.

F1-S2A is explicitly authorized for CheckpointRecord, ManifestReceipt, ManifestState, ProvenancePersistenceException, ManifestStore, PersistenceContractTest and this tracker only. Tests first for C01-C03; source/static review only, no compilation, commit or push. S2B and all later slices remain NOT AUTHORIZED; F1 INCOMPLETE; F2-F11, GUI and M8 BLOCKED.


### S2A protocol values and ownership

The five production additions preserve the exact accepted S0 record/nested-enum components and ManifestStore method signatures. All added model behavior is canonical-constructor validation or private constructor support; no factory/upgrade/transition or other public convenience API is introduced.

CheckpointRecord is one supplied record value: schemaVersion 1, nonnull components, positive long sequence, exact lowercase 64-hex previous hash/operation ID, exclusive OPERATION_OBSERVED versus JOB_OBSERVED fields. Initial NOT_STARTED/PLANNED states are derived only and cannot be checkpoint records. Observation checks its local state/fact matrix, nonnegative source-match count bounded by the 100000-source plan cap, time ordering and successful CONVERSION facts for WRITTEN_UNVERIFIED. A record's supplied start/finish/SourceSummary completion cannot exceed recordedAt; a supplied phase summary cannot complete after the observation's finishedAt. These are comparisons of supplied values, not clock calls or proof of actual execution.

Failure lists are nonnull, copied, unique, canonical by Phase/Code declaration order and bounded at 64 before copying. Operation successes/progress/policy skip forbid failures; FAILED requires a new workflow failure or errors in the already-authoritative ProcessingEvidence lists; RECOVERY_REQUIRED requires an applicable typed recovery failure. BLOCKED can be locally represented without a new failure because its applicable plan assessment is checked by S2B. RUNNING/FAILED job records can retain supplied failures; actual prior-operation failure and legal job progression remain S2B responsibilities. COMPLETED job records forbid their own failure list, and RECOVERY_REQUIRED requires explicit typed recovery evidence. Original M5/M6/M7 enum evidence is not recoded or duplicated.

ManifestReceipt checks nonnull identity/hash components, nonnegative sequence and exact hash spelling. At sequence zero, headSha256 equals planSha256. Positive sequences through Long.MAX_VALUE remain representable; no sequence increment, checksum computation or chain-membership assertion occurs. Checkpoint count/cap, consecutive sequence/overflow arithmetic, link equality, prior observation preservation, legal transitions, source eligibility/count/digest matching, COPY/conversion kind checks and job completion eligibility belong to S2B/S4. No isolated model performs these global checks.

ManifestState is an immutable complete current-view value, retaining the exact plan instance. Its receipt must refer to the same job; source mappings/digests are never rebuilt or changed. Its operation list must have exactly the plan's unique operation keys and is copied/sorted by ASCII ID; operation/job failure lists are canonical immutable copies. Local operation failure coherence and supplied view chronology are checked. A sequence-zero anchor is PLANNED at plan.createdAt with every operation NOT_STARTED and no job failure. Positive-receipt job states do not constitute proof of a legal journal history; that proof is the owning reducer/store's task. No mutable accumulator, history, reducer, snapshot generation or current-view factory is added.

ProvenancePersistenceException is final, has the sole approved typed constructor/accessors, requires a nonempty canonical unique bounded PERSISTENCE-phase failure list and uses the fixed message `Provenance persistence failed`. Its cause is initialized to null, suppression disabled and stack trace nonwritable; no arbitrary message/cause constructor or raw exception field exists. A known receipt is permitted only with PUBLISHED; NOT_PUBLISHED/UNKNOWN forbid it. PUBLISHED without a job receipt remains the accepted later public-export failure case. Actual plan/checkpoint publication requires its known receipt through the eventual store protocol, not an S2A execution claim.

ManifestStore is only the application output port, extending AutoCloseable with abstract create/append/replay/close and no checked exception signature, default body or implementation. Its documented replay contract permits Optional.empty() with an existing complete valid plan/journal for ordinary restart without an external rollback floor. A supplied receipt adds proof of unchanged acknowledged evidence in the complete valid chain, including when a later head exists. No plan and no orphan evidence may yield an empty result only without a supplied receipt claiming acknowledged evidence; corrupt/orphan/acknowledged-loss evidence must fail rather than return a usable older prefix. These are interface semantics only; no fake store or replay implementation is added.

### Exact S2A seven-path budget

| Action | Exact repository path |
| --- | --- |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/CheckpointRecord.java |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ManifestReceipt.java |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ManifestState.java |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ProvenancePersistenceException.java |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/port/out/ManifestStore.java |
| ADD | src/test/java/org/cbihi/mrinormalizer/PersistenceContractTest.java |
| MODIFY | docs/planning/PRE-M8-FOUNDATION-TRACKER.md |

Exactly **7 paths: 5 production additions +1 test addition +tracker**; the accepted full 33-path F1 ceiling and slice allocations are unchanged. S1A/S1B and every existing F0/M5/M6/M7 source/test remain unchanged, as do pom.xml, dependencies, build files, architecture tests/docs and composition. No replay, codec, adapter, filesystem/Path, hard-link/ACL/publication implementation, hashing, schema, projection, public export, dependency or later-slice placeholder/stub is introduced.

### C01-C03 authored test mapping

PersistenceContractTest was authored before any of the five S2A production paths existed, with a retained source snapshot. This is tests-first source preparation, not an executed red/green cycle. Static review then refined only this new test: RUNNING job facts remain locally representable because global job validation belongs to S2B; all accepted failure pairs and complete reflection types/generics were added. No accepted test was modified. Final test source contains **27 authored @Test methods**. The initial source/static-only status is historical and superseded by the user-supplied 27-test focused executable PASS below, covering C01-C03. No accepted S2A checkpoint is claimed.

| Criterion / invariant | Authored PersistenceContractTest method(s) | Coverage |
| --- | --- | --- |
| C01 local observation matrix | recordKindAndObservationMatrixIsExhaustive; observationNullabilityAndCountBoundsReject | All 9 states × presence of start/finish/digest × 0/1/100000 matched counts × all dispositions/absence × absent/successful/failed reconstruction/conversion evidence; required Optionals and count limits |
| C01 record components and exclusivity | recordKindsRequireExclusiveFields; checkpointRequiredComponentsVersionSequenceAndHashesReject; initialStatesAreDerivedAndNeverJournalRecords | Both kinds × 8 presence combinations; nulls, schema 1, positive sequence, exact hash/ID shape, NOT_STARTED and PLANNED derived-only |
| C01 supplied chronology and phase facts | suppliedChronologyRejectsReversedAndFutureFacts; writtenUnverifiedRequiresSuccessfulConversionFacts; originalPhaseErrorsSatisfyFailureWithoutDuplication | Equality/reversed/future times including SourceSummary; M7 written-unverified remains distinct; either original error list satisfies a failed observation without recoding; successful earlier phase retained with a later workflow failure |
| C01 failures/recovery | checkpointFailureApplicabilityIsExhaustive; checkpointFailuresAreCopiedCanonicalUniqueAndBounded; recoveryRetainsSuppliedFactsWithoutPromotingOrExecuting; everyApprovedWorkflowFailureRemainsCanonicalWithoutTruncation | Every observable operation/job state with empty/generic/recovery failure; null/duplicate/cap/copy/order; explicit uncertainty retains known supplied facts; all 29 approved workflow pairs and persistence-only subset copied without truncation |
| C01 no global reducer | localRecordsDoNotValidateGlobalSequenceLinksOrEligibility | Positive long including Long.MAX_VALUE and sequence beyond store count cap are local values; no prior link, transition, plan/kind/eligibility or hash computation |
| C02 receipt shape and exception boundary | receiptsAndNeutralFailuresAreFrozen; receiptSequenceAndHashShapeAreExact | Exact receipt components/types; sequence-zero equal hashes, positive heads, null/negative/malformed hashes; final neutral exception's sole typed constructor/accessors and generic signatures |
| C02 outcome/receipt and payload policy | publicationOutcomeAndKnownReceiptMatrixIsExhaustive; persistenceExceptionIsFixedAndCannotCarryCauseOrSuppressedPayload; persistenceExceptionFailuresAreRequiredPersistenceOnlyAndCanonical | All 3 outcomes × receipt presence; fixed message/null nonreplaceable cause, disabled suppression/nonwritable stack; nonempty/nonnull/unique/PERSISTENCE-only/bounded canonical failures; PUBLISHED export-without-receipt case |
| C02 application port | storeIsOnlyTheFrozenApplicationOutputPort | Exact 4 abstract methods, generics, AutoCloseable, no defaults or checked Throwable signature; no fake store; restart meaning documented on actual port |
| C03 immutable exact plan/current arrays | currentViewCopiesAndRetainsExactPlan; currentViewRequiresNonNullComponentsAndMatchingJob; currentViewRequiresUniqueCompletePlannedOperationKeys | Same immutable plan, distinct equal-byte source mappings retained, copied/unmodifiable/sorted operation/job lists, all nulls, job mismatch, complete unique planned key set and bounds |
| C03 supplied current chronology/anchor | currentViewChronologyAndPlanAnchorAreCoherent | View time >= plan creation and observed facts; exact sequence-zero PLANNED time/initial operations/empty failure shape; empty plan anchor allowed |
| C03 failure lists/original authority | operationStateFailuresAreImmutableAndStateApplicable; jobFailureListsAreCanonicalUniqueBoundedAndCopied; currentViewRetainsOriginalEvidenceAndDoesNotImplementReducer | Operation nulls/ID/copy/order/dup/cap/all state failure pairs; job failure null/dup/cap; original phase instance/errors remain only in ProcessingEvidence; no global job eligibility/reducer in a supplied positive-receipt view |
| C01-C03 frozen public shapes | allProtocolRecordShapesEnumsAndGenericsRemainFrozen | Exact 5 record/nested component lists/types, all Kind/State/Disposition/JobState members, relevant Optional/List generic types, canonical constructors and generated record methods only |

### Historical S2A source/static evidence and proposed executable verification

The following pre-execution record is retained as historical evidence. Its pending-verification statements are superseded by the actual user-supplied results below; no assistant-local Maven run is claimed.

Static review covers actual declarations, private constructor support, state/fact/failure applicability and chrono guards, controlled fixed messages, immutable canonical copies, sole original diagnostic authority and dependency direction. Parent/source comparison confirms all existing files except the tracker are byte-identical to **8b57b48e2a677a08a1d2f11f9eb760b0d3b8f00c**, including S1A/S1B/F0/M5/M6/M7 Java/tests/build/dependencies. git diff --check passes; the complete seven-path patch also passes git apply --check --whitespace=error-all against the parent tracker and absent new paths. No staging, commit or push occurs.

**No compilation or Java/JUnit execution is performed for S2A**, as explicitly requested. Constructor/reflection compilation, all 27 authored tests (including the exhaustive observation matrix), exception inherited-payload behavior, port generics and protected/full regressions require local Java 21/Maven verification on the exact applied tree. Proposed commands:

    mvn "-Dtest=PersistenceContractTest" test
    mvn "-Dtest=ManifestValueTest,ManifestPlanTest,ProcessingEvidenceTest,PersistenceContractTest" test
    mvn clean test
    git diff --check

No expected compilation/test total is recorded as an actual result. Stop and report an executable contract defect rather than modify accepted S1A/S1B/protected files or introduce a dependency/helper path.

    F0 ACCEPTED / CLOSED
    F1-S0 ACCEPTED
    F1-S1A ACCEPTED — b7a327d55ac26265474378fec00ae9a03ba7784e
    F1-S1B ACCEPTED — 8b57b48e2a677a08a1d2f11f9eb760b0d3b8f00c
    HISTORICAL F1-S2A SOURCE PREPARED / EXECUTABLE VERIFICATION PENDING
    F1-S2A CHECKPOINT NOT CREATED / NOT ACCEPTED
    F1 INCOMPLETE
    F1-S2B AND LATER SLICES NOT AUTHORIZED
    F2-F11 BLOCKED
    GUI BLOCKED
    M8 BLOCKED

**Historical source-preparation stop, superseded by the executable evidence below: no compilation, commit or push was authorized; S2B and later work remained blocked.**


### F1-S2A actual executable evidence supplied by the user (historical checkpoint-pending record)

**F1-S2A EXECUTABLE VERIFICATION PASSED — checkpoint pending**

The following checkpoint-pending record is historical, superseded by F1-S2A ACCEPTED — 99dcd08d3c1e4d8653be1ef89b521422294be274 and the S2B-only authorization below. The user supplied the following actual executable results; these are user-reported Java release 21/Maven evidence, not an assistant-local rerun.

| Gate | Exact command | Tests | Failures | Errors | Skipped | Result |
| --- | --- | --- | --- | --- | --- | --- |
| Focused S2A / C01-C03 | mvn "-Dtest=PersistenceContractTest" test | 27 | 0 | 0 | 0 | BUILD SUCCESS |
| Combined S1A/S1B/S2A manifest contracts | mvn "-Dtest=ManifestValueTest,ManifestPlanTest,ProcessingEvidenceTest,PersistenceContractTest" test | 71 | 0 | 0 | 0 | BUILD SUCCESS |
| Full clean regression | mvn clean test | 245 | 0 | 0 | 0 | BUILD SUCCESS |

The full clean regression compiled **71 production source files with Java release 21** and **25 test source files with Java release 21**. The user reports **git diff --check passed**, with only the known LF→CRLF informational warning on the tracker and no whitespace errors. The tracker-only evidence update additionally passes the assistant's local git diff --check; no Maven rerun is claimed for this documentation edit.

Exact S2A scope remains **five production additions +PersistenceContractTest +tracker**: CheckpointRecord, ManifestReceipt, ManifestState, ProvenancePersistenceException, ManifestStore, PersistenceContractTest and this tracker. All S1A/S1B and existing F0/M5/M6/M7 Java/tests/build/dependency files remain unchanged. This evidence update modifies only docs/planning/PRE-M8-FOUNDATION-TRACKER.md; all six S2A Java/test additions and every other repository file remain byte-identical to their pre-update state. No Java/test/build/schema/pom.xml/dependency file or later-slice implementation changed.

    F0 ACCEPTED / CLOSED
    F1-S0 ACCEPTED
    F1-S1A ACCEPTED — b7a327d55ac26265474378fec00ae9a03ba7784e
    F1-S1B ACCEPTED — 8b57b48e2a677a08a1d2f11f9eb760b0d3b8f00c
    F1-S2A EXECUTABLE VERIFICATION PASSED — checkpoint pending
    F1-S2A ACCEPTED CHECKPOINT NOT CLAIMED — no commit SHA exists yet
    F1 INCOMPLETE
    F1-S2B AND LATER SLICES NOT AUTHORIZED
    F2-F11 BLOCKED
    GUI BLOCKED
    M8 BLOCKED

**STOP AFTER TRACKER-ONLY EVIDENCE UPDATE. No commit or push; no S2B or later work is authorized.**


## F1-S2B authorization and source preparation (historical; now accepted)

First tracker change: **F1-S2A ACCEPTED — 99dcd08d3c1e4d8653be1ef89b521422294be274**. This accepted parent was fetched and checked out in an isolated clean worktree before S2B source edits. S2A executable evidence remains focused 27/27, combined 71/71 and clean full regression 245/245, zero failures/errors/skips and BUILD SUCCESS, with 71 production and 25 test source files compiled under Java release 21. No standalone bookkeeping commit is made.

The user explicitly authorizes S2B only: ManifestReplay, ManifestReplayTest and this tracker, tests first for L01-L05, source/static review only. No compilation, commit or push. S3/S4/S5 and all later slices remain NOT AUTHORIZED; F1 INCOMPLETE; F2-F11, GUI and M8 BLOCKED.


### S2B reducer contract and static review

Historical pre-execution status, superseded by the user-supplied passing executable evidence below: **F1-S2B SOURCE PREPARED / EXECUTABLE VERIFICATION PENDING.** The public API is exactly the accepted final-class constructor plus validateNext(CheckpointRecord), accept(CheckpointRecord,String) returning ManifestReceipt, and current() returning ManifestState. No other public method, helper type, port or owning-slice placeholder was added. ManifestStore and all accepted values remain unchanged.

The constructor retains the immutable plan, seeds its sequence-zero receipt/PLANNED view and derives NOT_STARTED observations. It indexes operations and supplied source eligibility once, with one current observation per planned operation. Each validation checks schema/job, checked consecutive sequence, the 300001-checkpoint count cap, previous hash and nondecreasing recordedAt against the current anchor/head time. Canonical supplied hashes are checked for lowercase 64-hex shape; no checksum is computed or authenticated here.

The existing S0 operation/job transition tables remain unchanged. Indexed validation enforces RUNNING before normal observations, planned operation resolution, one active IN_PROGRESS operation, exact source-match cardinality and supplied digest availability, source workflow failure exclusion from executing intent, COPY output-digest equality, COPY/conversion evidence separation and applicable blocked evidence. Recognition alone is not converted into validated/supported/READY evidence; successful COPY reporting does not upgrade F0 or assert imaging validity. Actual blocked source assessment or supplied workflow/processing errors can establish failure evidence; pending validation alone cannot.

Job completion uses bounded counters and requires at least one successful COPY, all operations COPY successes or policy skips, no active/uncertain operation and no source/operation/job failure. WRITTEN_UNVERIFIED never completes a schema-1 job. FAILED needs actual failure evidence and no active operation or uncertainty. Explicit RECOVERY_REQUIRED failures cannot be mislabeled as ordinary RUNNING/BLOCKED/FAILED facts; source uncertainty prevents normal work/certain-failure classification. Terminal jobs reject operation records until explicit job recovery, and only corresponding uncertainty records for previously observed operations are then permitted. No repeat, retry, unblocking or recovery-to-success transition is introduced.

Uncertainty retains every previously supplied known time, digest, source-match count, processing-evidence value and failure. Previously absent facts may be supplied later while remaining RECOVERY_REQUIRED; recorded evidence is never promoted to verified output or overall success. Known match counts, including zero, cannot be rewritten. Repeated uncertainty observations require a new supplied fact/failure; timestamp alone is insufficient. Original M5/M6/M7 diagnostic authority remains in the already accepted assessment/processing values.

validateNext makes no reducer assignments. accept validates the supplied hash/record and prepares the receipt, immutable replacement observation and all counter/job values before its assignment-only commit point. Rejected input cannot consume a sequence, change a counter/timestamp or expose partial advancement. The three methods synchronize access to one reducer; there is no asynchronous execution. current constructs an immutable copied view only when requested. No persisted/current-view cache, checkpoint history, filesystem, Path, JSON, serialization, hashing, imaging access, store, publication, export or recovery execution is present.

Initialization is O(S + O + Rf); individual records use the indexed operation and bounded evidence lists, and job predicates use counters rather than full-plan traversal. Explicit current() builds the immutable view from the current observation array. Byte-size caps/reserves, canonical-byte integrity, storage idempotence, lease/publication and platform qualification remain S3/S4 responsibilities, not invented reducer behavior.

### S2B exact changed paths

| Action | Repository path |
| --- | --- |
| ADD | src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ManifestReplay.java |
| ADD | src/test/java/org/cbihi/mrinormalizer/ManifestReplayTest.java |
| MODIFY | docs/planning/PRE-M8-FOUNDATION-TRACKER.md |

Budget: exactly **1 production addition +1 test addition + tracker = 3 paths**. No S1A/S1B/S2A or existing F0/M5/M6/M7 Java/test/build/dependency file changed; all 122 other parent-tracked files were checked byte-identical. No staged changes, commit or push. Parent remains 99dcd08d3c1e4d8653be1ef89b521422294be274.

### S2B tests-first mapping (historical authorship; executable PASS below)

ManifestReplayTest's 30 test methods were authored before ManifestReplay.java existed; a retained source snapshot records this order. Static review subsequently refined only this new test's uncertainty/API fixtures. This records tests-first source authorship, not an assistant-executed red/green cycle. The initial source/static-only status is historical and superseded by the user-supplied 30-test focused PASS below, covering L01-L05. No accepted S2B checkpoint is claimed.

| Matrix | Authored @Test methods |
| --- | --- |
| L01 initial/consecutive replay (5) | initialPlanAndConsecutiveRecordsReplayExactly; emptyPlanIsFactualPlannedStateWithoutSyntheticSuccess; constructorRejectsNullAndNoncanonicalPlanHashWithFixedMessages; inconsistentJobSequenceLinkAndTimeRejectWithoutChanges; sharedCopyDestinationRetainsEveryDistinctSourceMapping |
| L02 operation/job transitions and predicates (11) | transitionPairsAndJobStatesAreExhaustive; runningRecordIsRequiredBeforeNormalOperationObservations; unknownOperationsAndSecondActiveOperationRejectAtomically; kindSpecificSuccessAndProcessingEvidenceAreEnforced; matchingCountAndExpectedCopyDigestAreRequired; everyConversionSourceMustHaveSuppliedDigestAndExactMatchedCount; missingDigestAndSourceFailuresAreNonexecutingFacts; blockedRequiresApplicableSuppliedAssessmentOrFailure; completedRequiresActualCopyAndEveryOperationTerminalWithoutFailures; sourceAndJobFailuresCannotBecomeCompleted; failureRequiresRealEvidenceAndRetainsOriginalPhaseAuthority |
| L03 uncertainty/retained facts (6) | uncertaintyPreservesFactsWithoutRecoveryExecution; recoveryCannotEraseOrRewriteKnownTimeDigestCountOrPhaseFacts; uncertaintyRetainsFailuresAndRejectsRepeatedRecordsWithoutNewFacts; interruptedIntentRemainsInProgressAndDoesNotAuthorizeContinuation; terminalJobsPermitOnlyCorrespondingUncertaintyRecords; operationUncertaintyStopsNormalWorkAndCannotBecomeOrdinaryFailure |
| L04 validation purity/atomic acceptance/API (5) | validateNextIsPureAndAcceptAdvancesOnlyOnce; everyInvalidSuppliedHashAndNullRecordLeavesAllStateUnchanged; rejectedAttemptsDoNotChangeCountersOrConsumeSequences; currentViewsAreImmutableSnapshotsWithoutExposingIndexesOrHistory; replayPublicApiIsExactlyFrozen |
| L05 limits/checked sequence/bounded current storage (3) | limitsAndCheckedSequenceNeverWrapOrDropFacts; inheritedCountReferenceAndFailureBoundsNeverTruncateThePlan; reducerKeepsOneCurrentObservationRatherThanJournalHistory |

The transition test covers all 9x9 operation and 5x5 job pairs using factual public-API prefixes and coherent kind/failure fixtures; initial-state record rejection still belongs to accepted S2A constructors. Positive and rejected digest/count/kind/job predicates supplement those pair tables. Rejection helpers compare complete immutable views and subsequent valid acceptance checks counters/sequence remain usable. The count-cap/Long.MAX_VALUE tests use isolated reflection on the existing private receipt field, because the real count cap makes arithmetic overflow unreachable through an otherwise valid chain; no production test hook is added.

Static review checked the exact public declarations, accepted constructor/type/enum references, fixed literal non-identifying argument messages with no raw cause, delimiter structure, transition predicates, preparation before mutation, immutable-view copying and absence of forbidden responsibility imports/APIs. git diff --check passed with no whitespace errors in this worktree. These are source/static checks only.

### Historical proposed Java 21/Maven verification (now user-verified)

```text
mvn "-Dtest=ManifestReplayTest" test
mvn "-Dtest=ManifestValueTest,ManifestPlanTest,ProcessingEvidenceTest,PersistenceContractTest,ManifestReplayTest" test
mvn clean test
git diff --check
```

The earlier pending compilation/JUnit/full-regression status is historical and superseded by the user-supplied passing focused, combined and clean-full results below. These commands were not rerun in the assistant environment. Actual runtime/resource qualification of persistence remains later-slice work. No accepted S2B checkpoint exists or is claimed.

    F0 ACCEPTED / CLOSED
    F1-S0 ACCEPTED
    F1-S1A ACCEPTED — b7a327d55ac26265474378fec00ae9a03ba7784e
    F1-S1B ACCEPTED — 8b57b48e2a677a08a1d2f11f9eb760b0d3b8f00c
    F1-S2A ACCEPTED — 99dcd08d3c1e4d8653be1ef89b521422294be274
    HISTORICAL F1-S2B SOURCE PREPARED / EXECUTABLE VERIFICATION PENDING
    F1 INCOMPLETE
    F1-S3 AND LATER SLICES NOT AUTHORIZED
    F2-F11 BLOCKED
    GUI BLOCKED
    M8 BLOCKED

**Historical source-preparation stop, superseded by the executable evidence below. No assistant compilation, commit or push; no S3 or later work.**


## F1-S2B executable evidence supplied by the user (historical checkpoint-pending record)

The checkpoint-pending statements in this section are historical, superseded by **F1-S2B ACCEPTED — 935fc29ad9a40a53b497a5fe008db5d6d7d485ac** and the S3-only authorization below.

**F1-S2B EXECUTABLE VERIFICATION PASSED — checkpoint pending.** The following are actual user-supplied executable results, not an assistant-local rerun. No commit SHA exists yet; verification does not claim an accepted S2B checkpoint.

| Gate | Exact command | Tests | Failures | Errors | Skipped | Result |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Focused S2B | mvn "-Dtest=ManifestReplayTest" test | 30 | 0 | 0 | 0 | BUILD SUCCESS |
| Combined S1A/S1B/S2A/S2B | mvn "-Dtest=ManifestValueTest,ManifestPlanTest,ProcessingEvidenceTest,PersistenceContractTest,ManifestReplayTest" test | 101 | 0 | 0 | 0 | BUILD SUCCESS |
| Full clean regression | mvn clean test | 275 | 0 | 0 | 0 | BUILD SUCCESS |

User-supplied git diff --check PASSED with no whitespace errors. L01-L05 and the 30 mapped ManifestReplayTest methods are PASS under the focused gate; the combined and clean full regression results are recorded exactly as supplied. No additional compilation counts or environment/version details are inferred from these results.

Exact S2B implementation scope remains **ManifestReplay.java + ManifestReplayTest.java + this tracker only (3 paths)**. This evidence update changes only docs/planning/PRE-M8-FOUNDATION-TRACKER.md. All S1A/S1B/S2A and protected F0/M5/M6/M7 Java/tests/build/dependency files remain unchanged. No Java/test/build/schema file is modified. No commit or push is performed.

    F0 ACCEPTED / CLOSED
    F1-S0 ACCEPTED
    F1-S1A ACCEPTED — b7a327d55ac26265474378fec00ae9a03ba7784e
    F1-S1B ACCEPTED — 8b57b48e2a677a08a1d2f11f9eb760b0d3b8f00c
    F1-S2A ACCEPTED — 99dcd08d3c1e4d8653be1ef89b521422294be274
    F1-S2B EXECUTABLE VERIFICATION PASSED — checkpoint pending
    F1-S2B ACCEPTED CHECKPOINT NOT CLAIMED — no commit SHA exists yet
    F1 INCOMPLETE
    F1-S3 AND LATER SLICES NOT AUTHORIZED
    F2-F11 BLOCKED
    GUI BLOCKED
    M8 BLOCKED

**STOP AFTER TRACKER-ONLY EVIDENCE UPDATE. No commit or push; no S3 or later work is authorized.**


## F1-S3 authorization and source preparation

Historical preparation and pre-checkpoint evidence below are superseded, for current acceptance status only, by the F1-S3 acceptance reconciliation recorded at the end of this section.

First tracker change: **F1-S2B ACCEPTED — 935fc29ad9a40a53b497a5fe008db5d6d7d485ac**. Accepted parent checked out in an isolated clean worktree. S3 only is authorized for JsonManifestCodec.java, JsonManifestCodecTest.java, provenance-plan-v1.schema.json, provenance-checkpoint-v1.schema.json and this tracker. Tests first J01-J04, then focused/combined/full Maven gates, independent Python standard-library interoperability and git diff --check. No commit/push; S4 and later NOT AUTHORIZED; F1 INCOMPLETE; F2-F11, GUI and M8 BLOCKED.


### S3 canonical contract implemented

Package-private final JsonManifestCodec has exactly its zero-argument constructor and four approved package-private overload/decoder methods. No public API, dependency, port, serializer framework, arbitrary JSON-value parser/AST, reflection serializer, store, publication/export method, hashing, imaging parsing or filesystem access is introduced. All four production methods use only the approved supplied immutable values and bounded internal UTF-8 bytes. S1A/S1B/S2A/S2B and protected F0/M5/M6/M7 code/tests remain unchanged.

Both v1 documents are self-contained Draft 2020-12 schemas with all declared object shapes closed by additionalProperties:false and all their fields required, including explicit null alternatives. The fixed roots wire value is ["SOURCE","OUTPUT"]. A nonnull selectedSourceFingerprint is exactly {"kind":"SELECTED_SERIES_SHA256","sha256":<supplied aggregate hash>}; it remains distinct from a per-file ContentDigest. Conversion facts encode a row-major array of four arrays of four doubles and fixed postWriteValidation="NOT_PERFORMED". No UID/patient metadata fields, absolute roots, runtime output references or original-diagnostic duplication are added. Restricted relative paths remain required and may themselves be identifying.

Encoding uses fixed literal property order, exact enum names, exact decimal long/int tokens and finite Java 21 Double.toString tokens. UTF-8 has no BOM, no insignificant whitespace and exactly one final LF. Paths/Unicode are preserved without case folding/normalization; quote/backslash use the necessary emitted escapes. Reasons, failures, sources, operations and original error lists inherit the accepted models' canonical ordering.

The closed typed decoder consumes only those literal shapes. It rejects noncanonical UTF-8/tokens/escapes/numbers/timestamps/UUIDs, altered key order, unknown/missing/duplicate fields, trailing content, unsupported schema/version/enum, legacy fields and incompatible model facts. It never parses arbitrary maps/values or skips unknown fields. Existing constructors own local evidence/state coherence; the accepted reducer owns global journal transitions. No recognition/validity/support/READY or reopened-output claim is inferred. Successful decoding additionally requires exact encode/decode/re-encode byte identity, so model sorting cannot silently accept noncanonical list order. Failures expose one fixed non-identifying argument message and no raw cause.

Plan input/output bytes are capped at 64 MiB and checkpoint bytes at 16 KiB before unbounded growth/decoding. Strings and all typed arrays have explicit owner bounds: path 4096 UTF-8 bytes, safe/version/enum/hash tokens bounded, sources/operations/source references per operation <=100000, cumulative source references <=200000, failures <=64, reasons <=8 and original error lists bounded by their closed enum sets. Numeric token lengths are bounded before conversion. Affine shape is exactly 4x4; schema nesting is statically fixed, not recursively supplied. Positive maxima and first-over-limit cases are executable tests; no truncation. These are serialization/model bounds, not S4 physical-storage/heap/publication qualification.

### S3 tests-first and J01-J04 mapping

The initial **23 @Test methods were authored before the codec and both schemas existed**, with a retained source snapshot. A configured Maven tests-first run reached test compilation and failed because JsonManifestCodec was absent; no placeholder/stub was used. Coverage review subsequently refined only the new test file and added two source/operation/reference boundary methods; the final executable suite contains **25 @Test methods**. Initial runtime provisioning/resolution attempts preceded test execution; their environment issues were resolved outside the repository. No accepted test or dependency/build file was changed.

| Matrix | Final test methods | Result |
| --- | --- | --- |
| J01 canonical golden bytes (4) | planAndCheckpointGoldenBytesAreCanonical; fullSourceGoldenBytesPreserveUnicodeAndDetection; allNestedEvidenceFieldsHaveFixedOrderAndExplicitNulls; independentFixtureExportUsesActualCodecBytes | PASS |
| J02 closed canonical language (7) | onlyFixedCanonicalSchemaFormsDecode; alternateNumbersAndEscapesAreRejected; extraMissingDuplicateAndRemovedDiagnosticFieldsReject; modelContradictionsCannotBeHiddenInCanonicalJson; schemasVersionsEnumsAndRequiredLiteralsAreClosed; unorderedOrDuplicateModelListsAreRejected; nullWrongScalarTypesAndTruncatedFormsReject | PASS |
| J03 numbers/Unicode/resource bounds (9) | numbersUnicodeAndBoundsRemainExact; malformedUtf8AndControlCharactersReject; integerOverflowAndNonfiniteNoncanonicalDoublesReject; planAndCheckpointByteCapsRejectBeforeDecoding; boundedTypedArraysAndStringsReject; encoderEnforcesPlanBudget; exactSourceAndReferenceLimitsDecodeWithoutTruncation; exactOperationLimitDecodesAndNextEntryRejects; nullInputsAndDecoderFailuresUseFixedNonidentifyingMessages | PASS |
| J04 typed round trips/schema/API (5) | roundTripsEverySupportedInternalRecord; processingEvidenceRoundTripsWithoutDuplicateAuthority; assessmentVariantsAndOrderedReasonsRoundTrip; codecApiAndDependencyBoundaryRemainPackagePrivate; schemaDocumentsDescribeExactlyTheRestrictedShapes | PASS |

Coverage includes both record kinds, all journalable operation/job states, nullable digests/phase summaries/fingerprints, every allowed workflow failure pair, authoritative original M6/M7 error lists, recognized/unavailable/inconclusive/corrupt assessments and supported/unsupported variants. Long.MAX_VALUE and voxel counts above 2^53 remain exact; finite double extrema, signed zero, supplementary Unicode ordering, extended/fractional Instant values and required literal fields round-trip exactly.

### S3 actual executable evidence

**F1-S3 EXECUTABLE VERIFICATION PASSED; ACCEPTED — 03d598a1606121ecc7918279ec9a5f20f622ee93.** All results below were executed in the assistant environment against the final S3 Java/test/schema tree, not inferred from static review or supplied by a different runtime.

Environment: OpenJDK Temurin 21.0.12.1+1 LTS; Apache Maven 3.9.16; Linux amd64. The official JDK SHA-256 and Maven SHA-512 were checked before extraction outside the repository. Runtime-only proxy settings/trust configuration enabled dependency resolution; no pom.xml/build/dependency or repository helper was added.

| Gate | Command (mvn uses external runtime settings) | Tests | Failures | Errors | Skipped | Result |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Focused S3 | mvn -s <runtime-settings> "-Dtest=JsonManifestCodecTest" -Dprovenance.fixture.directory=<scratch-fixtures> test | 25 | 0 | 0 | 0 | BUILD SUCCESS |
| Combined F1 | mvn -s <runtime-settings> "-Dtest=ManifestValueTest,ManifestPlanTest,ProcessingEvidenceTest,PersistenceContractTest,ManifestReplayTest,JsonManifestCodecTest" test | 126 | 0 | 0 | 0 | BUILD SUCCESS |
| Clean full regression | mvn -s <runtime-settings> clean test | 300 | 0 | 0 | 0 | BUILD SUCCESS |
| Protected M6/M7 subset within that clean full run | Actual XML totals across the seven accepted protected suites; not a separate command | 79 | 0 | 0 | 0 | PASS |

The clean full build compiled **73 production source files and 27 test source files with release 21**. Surefire XML independently confirms all 300 totals and the 79-test protected subset. The final codec/test/schema bytes were unchanged after those gates; subsequent changes record tracker evidence only. No Maven rerun is claimed for this documentation-only recording.

Python standard-library json interoperability: **PASS** on six fixtures generated by the actual Java codec. Verified strict UTF-8/no BOM/one LF, supplementary Unicode and escaped quotes, exact integer types/values above 2^53 (Long.MAX_VALUE file size and 9008298766368768 voxels), fixed schema identifiers/versions/key order, source-to-destination references, unchanged raw detection, selected-series fingerprint tag, nullable/job fields, typed M7 dimensions/affine/finite extrema/signed zero/flags, NOT_PERFORMED reopening, workflow failure and recovery facts. Both schema documents were parsed and checked for closed required object shapes and resolved local references. This check used only Python's standard library; no Python/project runtime dependency was added.

### S3 exact five-path budget and final source review

| Action | Repository path |
| --- | --- |
| ADD | src/main/java/org/cbihi/mrinormalizer/infrastructure/filesystem/JsonManifestCodec.java |
| ADD | src/test/java/org/cbihi/mrinormalizer/infrastructure/filesystem/JsonManifestCodecTest.java |
| ADD | docs/schemas/provenance-plan-v1.schema.json |
| ADD | docs/schemas/provenance-checkpoint-v1.schema.json |
| MODIFY | docs/planning/PRE-M8-FOUNDATION-TRACKER.md |

Exactly **1 production addition +1 test addition +2 schema documents + tracker = 5 paths**. All 124 other accepted-parent tracked files were verified byte-identical, including the complete S1A/S1B/S2A/S2B, protected F0/M5/M6/M7 Java/tests, pom.xml and architecture/composition files. No accepted public model/API or enum changed. No extra repository path/dependency was required. git diff --check PASSED with no whitespace errors. No files staged; no commit or push.

    F0 ACCEPTED / CLOSED
    F1-S0 ACCEPTED
    F1-S1A ACCEPTED — b7a327d55ac26265474378fec00ae9a03ba7784e
    F1-S1B ACCEPTED — 8b57b48e2a677a08a1d2f11f9eb760b0d3b8f00c
    F1-S2A ACCEPTED — 99dcd08d3c1e4d8653be1ef89b521422294be274
    F1-S2B ACCEPTED — 935fc29ad9a40a53b497a5fe008db5d6d7d485ac
    F1-S3 ACCEPTED — 03d598a1606121ecc7918279ec9a5f20f622ee93
    F1 INCOMPLETE
    F1-S4 NOT YET IMPLEMENTED
    S4 REQUIRES SEPARATE EXPLICIT AUTHORIZATION AFTER THIS BOOKKEEPING CHECKPOINT
    F1-S5A/S5B/S6 NOT AUTHORIZED
    F2-F11 BLOCKED
    GUI BLOCKED
    M8 BLOCKED

**F1-S3 acceptance reconciliation (2026-10-05):** the accepted implementation checkpoint is 03d598a1606121ecc7918279ec9a5f20f622ee93. This later tracker-only bookkeeping checkpoint has no commit SHA yet; it changes no implementation, tests, schemas or build files and claims no Maven rerun. The earlier checkpoint-pending and pre-commit statements above remain historical and are superseded by this acceptance record.

**STOP AFTER TRACKER-ONLY RECONCILIATION. No commit or push. S4 requires separate explicit authorization after this bookkeeping checkpoint; no S4 implementation or acceptance is claimed.**

## F1-S4 bounded source preparation — 2026-10-05

**F1-S3 ACCEPTED — 03d598a1606121ecc7918279ec9a5f20f622ee93.**
- Accepted S4 parent: 9e7007ba40c483967874206da2097a7bab1fb86a; target branch: feature/pre-m8-foundation.
- The two intervening tracker-only hops through 7a2e57da37341a1907c55502b05a19fe21dbe7a6 were verified; direct-parent requirement was explicitly superseded. No history was changed.
- This latest S4 record supersedes earlier current-authorization/status statements; historical S3 executable evidence is unchanged.
- F1-S4 INITIAL EXECUTION FAILED; ONE CORRECTIVE SOURCE PASS PREPARED, NOT ACCEPTED pending the authoritative focused rerun; no S4 commit SHA exists.
- Exact scope: ADD src/main/java/org/cbihi/mrinormalizer/infrastructure/filesystem/JsonManifestStore.java; ADD src/test/java/org/cbihi/mrinormalizer/infrastructure/filesystem/ManifestStoreTest.java; MODIFY this tracker. No fourth path.
- Tests-first: initial 29-test S01-S09 file preceded the adapter; stage-close coverage brought the initial candidate to 30 tests. This one authorized corrective pass added two real-provider regressions before correcting the adapter: nullable-key/private/canonical hard-link publication and replacement-safe stage cleanup; 32 @Test methods total. Existing broad-ACL, symlink, no-overwrite and unsupported-provider coverage remains; no assumptions, disabled tests or skips added.
- S01: canonical plan/record receipts and acknowledged owner-fixture intent before simulated mutation.
- S02: exact/earlier retries, CAS, identity/hash conflicts, no overwrite and invalid/closed use.
- S03: restart/plan-only/minimum receipts, acknowledged loss, corruption/schema/layout/link/type failures; no fallback or repair.
- S04: stage/write/force/publication/acceptance faults; absent outcome remains IN_PROGRESS; PUBLISHED retries and UNKNOWN poisoning.
- S05: independent primary/cleanup/stage-close/lease-close failures, retained final evidence and explicit close failures.
- S06: canonical containment, configured ancestor aliases, restricted stage/final/lease resources and immediate exclusive job leases.
- S07: accepted caps, checked sequence, outcome/control reserves, real 999/1000/1001 buckets and controlled disk-full.
- S08: adapter counters cover one open replay pass, bounded append traffic, no per-append plan encoding/current view and compact hash indexing without checkpoint history.
- S09: executable real-provider complete visibility/no-replace races, lease/access, startup, allocation block size/logical bytes/usable-space measurements; actual foreign JRT provider must reject. No assumptions, disabled tests or OS skips.
- Candidate: same-filesystem private staging, force/close/byte verification and exclusive hard-link final publication; no mutable snapshots or overwrite fallback. Only canonical persistence bytes are hashed.
- Windows candidate is restricted to default-provider NTFS on SystemDrive; UNC/mapped/other volumes fail closed pending separate local-volume qualification. Effective owner-only ACLs must verify; no unrestricted fallback.
- Initial workstation evidence: Windows 11 amd64, Temurin 21.0.12.1, Maven 3.9.16, SystemDrive C:, default Windows NIO provider, NTFS; Java release-21 compilation PASS (74 production / 28 test sources); all 30 ManifestStoreTest methods discovered. Focused run: 30 tests / 1 failure / 27 errors / 0 skipped, BUILD FAILURE. Common constructor failure: PERSISTENCE CONTAINMENT_UNPROVEN, NOT_PUBLISHED.
- Confirmed cause: initial candidate incorrectly required non-null BasicFileAttributes.fileKey(). Actual Windows/NTFS probes supplied null keys for canonical normal directories and regular files; ACL view exists, roots are disjoint and namespace contained; the real regular-file/hard-link probe returned Files.isSameFile() == true with both sizes 5.
- One corrective source pass: provider-aware observations retain supplied stable keys and reject missing POSIX keys; qualified Windows null keys use birth-time substitution checks plus canonical/no-follow/type/owner/private-ACL/qualified-FileStore/lease revalidation, without claiming metadata/pathname identity. Stage/final identity uses real Files.isSameFile plus exact bytes. Instance-created unique stage/probe ownership uses overlapping Windows JDK no-delete-sharing handles where keys are absent, with access/observation/peer checks before releasing handles for cleanup; uncertain or substituted artifacts are retained with CLEANUP_FAILED. No overwrite/public API/dependency/accepted-contract changes.
- Separate workstation limitation: configuredSymlinkAncestorIsCanonicalizedRatherThanBlanketRejected could not create its fixture symlink because Windows reported a required privilege was not held. The real test remains unchanged in substance; the user will rerun with symlink creation privilege available.
- Corrective execution NOT RUN here: runtime remains Java 17 and Maven unavailable; no compilation, Maven or toolchain provisioning attempted. Authoritative next command: mvn "-Dtest=ManifestStoreTest" test on the user's Windows Java-21/Maven workstation with symlink privilege. Corrected Windows qualification pending; POSIX NOT RUN; no corrected provider PASS claimed. Exact per-file allocation may be unavailable; directory/power-loss/network durability remains unproved, as does resistance to deliberate owner-privileged races ignoring the cooperative lease (including handle release/unlink or POSIX check/unlink).
- git diff --check: PASS; static scope/public API review: exactly the three budgeted paths and frozen public constructor/four methods; accepted Java/tests/schemas/build/architecture unchanged.
- F0 ACCEPTED / CLOSED; F1-S0/S1A/S1B/S2A/S2B/S3 ACCEPTED; F1 INCOMPLETE.
- S5A/S5B/S6 NOT AUTHORIZED; F2-F11 BLOCKED; GUI BLOCKED; M8 BLOCKED.
- STOP after source preparation/static review. No commit, push, patch/ZIP/archive, public export or S5A work.

## F1-S5A bounded source preparation — 2026-10-06

**F1-S4 ACCEPTED — 233c334023f035eba4c6122a262f9961e08e9b07.**
- This latest record supersedes earlier current S4 acceptance/S5A authorization statements; historical evidence above remains unchanged. S4 user-workstation gates: focused 32/32, combined F1 158/158, clean regression 332/332 PASS, zero failures/errors/skips; clean Java release-21 compilation, actual Windows 11/default Windows NIO/NTFS S09 and git diff --check PASS. Power-loss/directory-entry durability remains unproved; no POSIX evidence is fabricated.
- S5A explicitly authorized as one bounded cycle. Canonical GitHub branch fetched directly without altering origin; new isolated s5a-work verified clean at accepted parent 233c334023f035eba4c6122a262f9961e08e9b07, direct parent 9e7007ba40c483967874206da2097a7bab1fb86a. Obsolete s4-work retained untouched; no history changes.
- Exact five-path scope: ADD src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/PublicJobReport.java; ADD src/main/java/org/cbihi/mrinormalizer/application/provenance/manifest/ManifestProjection.java; ADD src/test/java/org/cbihi/mrinormalizer/ManifestProjectionTest.java; ADD docs/schemas/public-job-report-v1.schema.json; MODIFY this tracker. No sixth path, helper/resource file, dependency or wiring change.
- Tests first: all 20 ManifestProjectionTest @Test methods authored before both production classes. P01 has 7 tests: projectsRealM6AndM7WithoutDuplicatedErrors plus exhaustive original M6/M7 enums, provenance/result agreement and independent success flags, optional safe source summary, all typed conversion facts and invalid-result/privacy failures. P02 has 3 tests: restrictedPathsRemainSensitivePublicFieldsDoNot, frozen public/nested record and projection surfaces, and closed schema field/code structure. P03 has 10 tests: countsCurrentObservationsNotRepeatedHistory through real replay recovery, all 18 ordered kind/state bins including zeros, same-kind aggregates, distinct equal-digest sources/repeated references, every original diagnostic and valid workflow pair, namespace separation, rejection/canonical order/immutability and checked count/bound cases. No disabled tests, assumptions or OS skips.
- Frozen PublicJobReport record: schemaVersion, JobState state, long sourceCount, List<OperationCount>, List<FailureCount>; nested OperationCount(kind,state,count), seven-name FailureNamespace and FailureCount(namespace,code,count). Version 1 only; source/operation bounds 100000; all 18 canonical bins required; positive failures only, exact closed source-enum/phase-code relationships, checked sums, defensive immutable lists and fixed non-identifying validation errors. No extra public helper/factory or sensitive field.
- Frozen ManifestProjection API: reconstruction(DicomProcessingResult), conversion(DicomToNiftiResult) -> ProcessingEvidence; publicReport(ManifestState) -> PublicJobReport; private constructor, no other public method. Pure supplied-value projection; actual result success contracts and target/report consistency preserved. Reconstruction errors cross-check supplied provenance errors as unique enum facts, independent of list ordering, and are retained once; conversion errors retain their own list. No original diagnostic is recoded into ManifestFailure. Safe SourceSummary/ConversionFacts are restricted evidence; no legacy geometry/pixels text or runtime OutputTarget is retained, and no reopening claim is made.
- Public privacy boundary: restricted identifying-looking SOURCE/OUTPUT text, supplied digests/fingerprints and typed technical evidence remain exact upstream. PublicJobReport omits all paths/mappings, IDs/UUIDs, hashes/fingerprints/digests, exact times, sizes, targets/references, geometry/intensity facts, software versions, patient/UID/free text and metadata. Counts consume only the supplied current ManifestState, never journal history, filesystem data or imaging bytes.
- Draft-2020-12 schema DOCUMENT parses with Python standard-library json: six required ordered top-level keys (schema/schemaVersion/state/sourceCount/operationCounts/failureCounts), fixed schema literal org.cbihi.mrinormalizer.public-job-report, exact enums, 18 operation entries, nine closed object shapes and seven closed failure branches covering 63 namespace/code pairs. Workflow branches were compared to the accepted ManifestFailure declaration. Java owns exact combination/order/duplicate/sum enforcement. No report encoder/decoder, output port/writer or S5B type/reference was introduced.
- Bounded source/static review: frozen signatures, pure application dependencies, current-only counts, source enum/phase-code closure and new-file whitespace reviewed. git diff --check PASS; final status/name review is limited to exactly the five paths above. JsonManifestCodec, S4, accepted S1-S3 models/tests/schemas, F0/M5/M6/M7, architecture, pom/build/dependencies and Main/DependencyContainer remain unchanged.
- Executable verification DEFERRED: java -version reports OpenJDK 17.0.20; mvn -version reports command not found. No compilation/Maven installation or execution attempted. Focused ManifestProjectionTest, combined F1 and clean full regression NOT RUN here; authoritative executable results must come from the user's Java-21/Maven workstation. No expected totals are presented as executed evidence, no new Windows/POSIX qualification claimed, and no corrective implementation pass was consumed by setup.
- F1-S5A SOURCE PREPARED / NOT ACCEPTED pending authoritative executable gates and explicit user acceptance; no S5A checkpoint SHA exists. F0 ACCEPTED / CLOSED; F1-S0/S1A/S1B/S2A/S2B/S3/S4 ACCEPTED; F1 INCOMPLETE. S5B/S6 NOT AUTHORIZED; F2-F11 BLOCKED; GUI BLOCKED; M8 BLOCKED.
- STOP after the bounded S5A report. No staging, commit, push, S5B, S6 or F2 work.

## F1-S5B bounded source preparation — 2026-10-06

**F1-S5A ACCEPTED — 26f41054449335fba6d05e5e4e77bad51c3ea641.**
- This latest record supersedes earlier current S5A checkpoint-pending/S5B authorization statements; historical evidence remains unchanged. Accepted S5A workstation evidence: focused 20/20, combined F1 178/178 on both runs, clean regression 352/352 PASS, zero failures/errors/skips; 76 production + 29 test sources under Java release 21; git diff --check PASS. Accepted S4 Windows/NTFS qualification passed again, with power-loss/directory-entry durability still unproved.
- S5B explicitly authorized in chat as one bounded implementation/verification cycle. Canonical GitHub branch fetched directly without changing origin; verified parent 26f41054449335fba6d05e5e4e77bad51c3ea641, direct parent 233c334023f035eba4c6122a262f9961e08e9b07 and subject feat(provenance): add privacy-safe public report projection. NEW isolated s5b-work verified exactly clean before source preparation; obsolete s4-work and prior s5a-work untouched. No history rewrite.
- Exact six-path budget: ADD src/main/java/org/cbihi/mrinormalizer/application/port/out/PublicReportWriter.java; ADD src/main/java/org/cbihi/mrinormalizer/infrastructure/filesystem/JsonPublicReportWriter.java; ADD src/test/java/org/cbihi/mrinormalizer/infrastructure/filesystem/PublicReportWriterTest.java; MODIFY src/main/java/org/cbihi/mrinormalizer/infrastructure/filesystem/JsonManifestCodec.java; MODIFY src/test/java/org/cbihi/mrinormalizer/infrastructure/filesystem/JsonManifestCodecTest.java; MODIFY this tracker. No seventh path, helper production file, new dependency, schema, receipt or wiring.
- Tests first: 25 PublicReportWriterTest methods and six public-encoding JsonManifestCodecTest methods authored before production changes. E01: 13 writer tests, anchored by publicGoldenBytesAndTargetsAreLimited; real accepted projection with identifying-looking upstream paths, digest/IDs/times/version/runtime target, independent golden bytes, frozen APIs, immutable retries/conflicts, canonical containment and safe aliases, final/dangling symlink refusal, namespace reservation without directory creation, fixed argument errors, private access/unchanged parent access and actual-provider hard-link identity including Windows null keys. E02: 12 writer tests, anchored by exportFailuresPreserveRestrictedEvidence; before/after creation, partial write, force, stage close, before/after publication, lost acknowledgment, classification, cleanup, post-unpin substitution, concurrent equal/different final entries, final symlink substitution, qualification/probe cleanup and parent substitution. No disabled tests, assumptions or OS skips.
- Restricted preservation assertions are authored for plan.json, checkpoint record, lease and journal-stage sentinels, SOURCE imaging and unrelated OUTPUT imaging bytes, plus exact restricted namespace membership after export/fault attempts. These assertions have NOT RUN here; no executed preservation evidence is claimed.
- Codec: package-private byte[] encode(PublicJobReport) is the only S5B API delta; minimum typed emission in the existing bounded writer, 16 MiB including the final LF, fixed schema org.cbihi.mrinormalizer.public-job-report, six ordered top-level keys, 18 ordered operation bins (kind/state/count), positive ordered failures (namespace/code/count), UTF-8/no BOM/no whitespace, decimal integers/.name() enums/exactly one LF. Six added tests cover golden bytes, all job states, Long.MAX_VALUE failure count, locale independence, determinism/LF/BOM/privacy, fixed null error, no decoder and all 63 closed failure pairs within the explicit budget. The accepted 25 S3 tests/fixtures are preserved; only their API guard count/allowance expands for this authorized encoder (31 codec tests now authored). Plan/checkpoint encoding/decoding unchanged; no public-report decoder or generic serializer.
- Frozen APIs: PublicReportWriter has only void write(PublicJobReport, OutputTarget). JsonPublicReportWriter has only public constructor (Path sourceRoot, Path outputRoot, Path manifestDirectory) and public void write(PublicJobReport, OutputTarget); deterministic package-private hooks remain inside the same file. No application Path/bytes/stream/JSON dependency and no orchestration.
- Target policy: SOURCE/OUTPUT canonically disjoint; safely resolvable configured aliases remain valid; explicit absolute runtime target requires an EXISTING canonical parent inside OUTPUT and outside the restricted namespace. Final symlinks and journal resource names fail closed. No directory tree creation or parent permission/ownership changes. Owner, FileStore, canonical/no-follow/type and directory substitution checks remain; private stages use POSIX 0600 or owner-only Windows ACLs.
- Publication: encode/bound first; unique private CREATE_NEW stage and qualification probes in the target parent, outside journal, on the qualified same filesystem; complete write/force/close/byte verification; reverify after the publication fault boundary; provider-qualified hard-link no-replace publication and Files.isSameFile(stage, final)/exact final bytes. Exact existing canonical report is idempotent with no rewrite; different bytes produce OUTPUT_CONFLICT / NOT_PUBLISHED. No replace, direct-final write, copy/move fallback or final cleanup deletion.
- Windows safety: qualified default local NTFS accepts null fileKey with overlapping ExtendedOpenOption.NOSHARE_DELETE ownership handles through stage creation/closed-writer verification. Creation time is a substitution check, not fabricated identity; POSIX stable keys remain required. Probe pins are verified/released together before hard-link cleanup. Cleanup revalidates ownership, access, containment and peer identity after hook/unpin boundaries; uncertain or substituted artifacts are retained with CLEANUP_FAILED. Deliberate owner-privileged races between final checks and unlink remain outside the cooperative contract; unsupported qualification fails PUBLICATION_UNAVAILABLE without downgrade.
- Failure/outcome contract: fixed neutral ProvenancePersistenceException only for external failures; accepted PERSISTENCE-phase codes only. Known absent/different final -> NOT_PUBLISHED; exact known final -> PUBLISHED; unclassifiable final -> UNKNOWN + RECOVERY_REQUIRED with uncertain stage retained. Primary and independent CLEANUP_FAILED facts coexist without erasing publication outcome. Every export exception uses Optional.empty() for knownPublication, even PUBLISHED: no ManifestReceipt is ever manufactured and no checkpoint/plan is written. Null required arguments use a fixed non-identifying argument error; no raw filesystem path/message/cause/stack escapes.
- Bounded static review: frozen surfaces, codec API delta, unchanged S3 fixtures, explicit public byte budget, ownership/cleanup/outcome paths, restricted namespace separation and saved-file whitespace checked. git diff --check PASS; status plus tracked name review confirms exactly six authorized paths. PublicJobReport, ManifestProjection, ManifestProjectionTest, public schema, JsonManifestStore, ManifestStoreTest, accepted replay/models, F0/M5/M6/M7, build/dependencies, Main/DependencyContainer and architecture files/tests unchanged.
F1-S5B ACCEPTED —
fc4de75b18783df45d5757c11c51f1924ea9d535

Tested implementation checkpoint:
ca25625264ebe96c0d777c3da833e03efc03e7aa

Implementation parent:
26f41054449335fba6d05e5e4e77bad51c3ea641

Executable evidence:
- focused S5B: 56/56 PASS
  - JsonManifestCodecTest: 31/31
  - PublicReportWriterTest: 25/25
- combined F1: 209/209 PASS
- full clean regression: 383/383 PASS
- failures: 0
- errors: 0
- skipped: 0
- Java release 21 compilation:
  78 production sources + 30 test sources
- Windows 11 / default Windows NIO / NTFS execution: PASS
- git diff --check: PASS
- no S5B corrective source pass consumed

Scope:
- S5B implementation checkpoint ca25625... contains exactly the five
  production/test changes.
- tracker reconciliation follows separately and changes documentation only.
- no S5A/S4/M5/M6/M7/build/wiring/architecture change.
- no public report decoder.
- no ManifestReceipt manufactured by public export.

Qualification:
- power-loss durability UNPROVED
- directory-entry durability UNPROVED
- network filesystem durability/support UNPROVED
- POSIX S5B provider qualification not claimed from Windows evidence

F1-S5B ACCEPTED.
F1 remains INCOMPLETE.
F1-S6 is the only remaining F1 slice.
F2-F11 remain BLOCKED.
GUI remains BLOCKED.
M8 remains BLOCKED.
