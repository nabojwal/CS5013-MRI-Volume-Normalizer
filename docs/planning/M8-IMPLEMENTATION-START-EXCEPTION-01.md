# Project PAI — M8 Bounded Implementation-Start Exception

**Identifier:** PAI-M8-IMPLEMENTATION-START-EXCEPTION-01
**Owner approval:** 28 October 2026 (Asia/Kolkata), explicitly granted in the Project PAI conversation
**Design authority:** PAI-M8-D1-01, Revision 2; M8-R1 independent design-review disposition APPROVED
**Audited baseline:** `3daf9d829a7b24d826b96faf35d45b7860a986d9`
**Branch:** `feature/pre-m8-foundation`
**Status:** G1/G2/G3 OWNER-APPROVED. GOVERNANCE DOCUMENTATION PREPARATION ONLY; M8-I1 NOT AUTHORIZED.

## 1. Purpose and exact exception

The original Pre-M8 foundation gate remains incomplete, but the standalone NIfTI-to-DICOM capability is a mandatory project deliverable. The project owner explicitly approves a **bounded implementation-start exception (G1)** as the authorization route for a subsequent, separately authorized M8-I1 engineering cycle. This is not a finding that the Pre-M8 gate has passed, and is not unconditional permission to implement.

The exception becomes an eligible basis for a later M8-I1 authorization only **after** this documentation-only governance checkpoint is independently reviewed and separately authorized for publication, and its parent/source identity has been verified. Merely creating or publishing this exception does not authorize M8-I1. A new, explicit owner instruction must authorize M8-I1 after governance publication.

## 2. Approved design and owner decisions

- **G1 — Exception approved:** Permit the owner to authorize a bounded M8 implementation cycle ahead of global Pre-M8 closure, without treating any predecessor obligation as satisfied.
- **G2 — M8-owned provenance and publication approved:** Use the contract's versioned `PAI-M8-JOURNAL-1` checkpoint protocol, verified slice inventory, multi-instance no-replace publication, and completion predicate. No F1 reverse-operation kind is invented; F1/F8 schemas, services, safety and journal semantics remain unchanged.
- **G3 — Standard Extended MR profile approved:** Emit derived conventional MR Image Storage instances under the explicitly declared M8-MR-1 Standard Extended profile, with standard Rescale Slope, Rescale Intercept and Rescale Type attributes. Document in `docs/architecture/M8-DICOM-CONFORMANCE-PROFILE.md` that external viewers may ignore this extension; do not claim universal viewer or clinical interoperability.

The full technical authority is the approved Revision 2 contract in `docs/planning/M8-IMPLEMENTATION-CONTRACT-1.0.md`, original source SHA-256 `5d16f97055ae4b88b979fdf434177d8c8e07e62e06f6d08eaccc847eb5967d9c`; governance-copy SHA-256 `bd0e8d986fdde2044080d4144eb9342bd1bf16e0319b2399f2fe2abef6457051`. The governance copy differs only by removal of five trailing Markdown hard-break spaces in its header to avoid repository whitespace errors; all words, requirements, signatures, tables and line numbers are preserved. Its historical pre-approval status sentences remain part of the original design deliverable; the later M8-R1 approval and this subsequent owner decision supersede those status sentences **only as governance records**, not as amendments to the technical requirements.

The focused M8-R1 review closed B01 (terminal receipt AND verified completion marker AND complete matching published inventory) and B02 (noncollapsing, signed and spacing-consistent serialized adjacent slice positions). The corrected rules and six affected acceptance rows are already incorporated into Revision 2. No further general design phase is required.

## 3. Immutable scope and predecessor boundaries

- **New use case:** One supported NIfTI-1 input file to a newly generated, independently verified derived DICOM MR series in a job-specific destination child. Geometry and raw voxel semantics are preserved within the frozen supported profile; required metadata is supplied, generated or explicitly classified as non-recoverable.
- **Independent directions:** The existing DICOM-to-NIfTI functionality remains a separately usable path. M8 must not call M6/M7/F9 or run DICOM-to-NIfTI-to-DICOM processing.
- **Excluded:** Round-trip acceptance, GUI implementation, automatic dataset selection, multi-file NIfTI input, resampling, interpolation, unsupported 4D/float/NIfTI-2 inputs, arbitrary metadata restoration, PACS and clinical claims.
- **Frozen:** All existing F0-F9, M6/M7/F7/F8/F9 production and test paths, schema paths, `pom.xml`, and accepted compatibility contracts. No automatic predecessor repair or scope expansion.
- **M8-I1 path ceiling, subject to later authorization:** Exactly **29 ADD / 0 MODIFY / 0 DELETE** paths enumerated in contract §8 (16 production Java, 9 test Java, 1 conformance document and 3 qualification scripts); no new Maven dependency. A changed path budget requires a separately approved bounded amendment.
- **Acceptance:** A finite, risk-based **32-obligation** matrix (M8-01–M8-32) in contract §9. Require the contract's explicit Windows NTFS and non-root Linux ext4 qualification, relevant regression and independent reverse-conversion demo. Test execution results must be observed, not assumed; platform-specific limitations must be labelled.

## 4. Binding output integrity and publication boundaries

M8 must validate a private source snapshot through the existing F7 reader; plan all generated identifiers and geometry; acknowledge intent; write and close staged DICOM instances; independently parse and compare every instance; acknowledge staged verification; then use the M8-owned journal, no-replace publication and completion-marker protocol.

`ARTIFACT_COMPLETE` requires **J AND M AND I** (valid terminal COMPLETED receipt, verified completion marker, matching complete public inventory). `EXECUTION_SUCCESS` additionally requires successful resource release and no faults. A partial/uncertain publication or acknowledgment cannot be successful. Multi-file visibility is not atomic; failure and recovery semantics are exactly those frozen in the contract. No F1/F8 protocol is misrepresented as a reverse conversion.

The declared Standard Extended MR scaling is a **limited producer conformance profile**, not proof that every consumer will interpret the added scaling attributes. Generated UIDs and omitted historical metadata must never be presented as original acquisition data. No patient images or identifying metadata are authorized for Git publication.

## 5. Prerequisites, approval order and release controls

1. **Completed:** M8-D1 Revision 2 delivered; M8-R1 independent design review approved; owner explicitly approved G1, G2, G3 and preparation of this exact governance-only patch.
2. **Current authorization:** Prepare the three-path patch below and return the full unified diff for review. **No repository edit, project test, staging, commit, push, tag or publication** is authorized by this preparation instruction.
3. **Later governance approval:** Obtain independent exact-diff review, then separate owner authorization for any local documentation application, staging/commit and publication. GitHub pushes remain under the owner's control.
4. **After governance publication:** Verify the resulting SHA, exact 2 ADD/1 MODIFY governance scope, and unchanged predecessor/source hashes against `3daf9d829a7b24d826b96faf35d45b7860a986d9` (apart from the separately approved governance-only changes).
5. **Later M8-I1 authorization:** Obtain a **new explicit owner instruction** authorizing the contract's tests-first complete implementation cycle. Without that instruction, M8-I1 remains NOT AUTHORIZED.
6. **M8-P1:** Independent implementation/evidence acceptance and subsequent publication/tracker reconciliation require their own review and release authorization.

## 6. Three-path governance checkpoint budget

| Operation | Path |
|---|---|
| ADD | `docs/planning/M8-IMPLEMENTATION-CONTRACT-1.0.md` |
| ADD | `docs/planning/M8-IMPLEMENTATION-START-EXCEPTION-01.md` |
| MODIFY | `docs/planning/PRE-M8-FOUNDATION-TRACKER.md` — only M8/scope governance records |

No Java, tests, dependencies, README, milestone, accepted predecessor or unrelated tracked/untracked files may be changed in this governance unit.

## 7. Retained acceptance obligations and negative permissions

- **F8:** IMPLEMENTED / PUBLISHED / CROSS-PLATFORM QUALIFIED; formal acceptance PENDING (18/42 demonstrated, 24/42 partially demonstrated). No F8 obligation waived.
- **F9:** Windows-qualified submission implementation PUBLISHED; formal acceptance PENDING. Elevated Windows tests do not establish ordinary-user Windows or native Linux qualification.
- **F10/F11:** Remain NOT STARTED / BLOCKED under their existing dependencies and sequential authorization rules.
- **GUI:** Remains BLOCKED; no GUI implementation authorized.
- **Global Pre-M8:** Every acceptance checkbox remains unchecked until supported by its own evidence and approval. This exception is not global Pre-M8 completion.
- **Round-trip:** Not part of current independent M8 acceptance and not reported as completed; any pre-existing higher-level round-trip plan remains deferred.

**STOP:** This governance document grants no implementation, repository write, Git staging, local commit, remote push or publication permission. M8-I1 requires a later explicit authorization after the governance checkpoint is published.
