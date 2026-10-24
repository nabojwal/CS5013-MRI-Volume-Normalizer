# Project PAI — F9 Submission-Scope Implementation Authorization

**Document ID:** PAI-F9-SUBMISSION-SCOPE-EXCEPTION-01
**Prepared:** 2026-10-23; **project-owner approval recorded:** 2026-10-24 (Asia/Kolkata)
**Status:** **OWNER-APPROVED FOR A LIMITED IMPLEMENTATION-START EXCEPTION; NOT YET EFFECTIVE — TRACKER PUBLICATION AND CONCRETE F9 SCOPE APPROVAL REQUIRED**
**Repository:** `nabojwal/CS5013-MRI-Volume-Normalizer`
**Branch:** `feature/pre-m8-foundation`
**Documentation-preparation baseline:** `5d9bce6a7b668c1d2361d66d88cb0cf6ff232572`

## 1. Purpose and necessity

The canonical `docs/planning/PRE-M8-FOUNDATION-TRACKER.md` currently states that F9 (headless orchestration through existing M6/M7) depends on F2–F8 and is **NOT STARTED / BLOCKED**. F8 was implemented and published at `f31821e0482f8553297b33a37f776af2cbc40e55`, with its tracker reconciliation at `bc1613d0e95bf67014bae76102d4af2e0b89a926`, but is **not formally ACCEPTED / CLOSED**. Its independent review identified 18/42 demonstrated and 24/42 partially demonstrated contractual obligations.

The reported final F8 qualification passed 795/795 Java tests on both Windows and native WSL2 Linux; 29/29 source files matched across hosts. A separate Windows real-MRI M6→M7→F7 smoke test on the supported contiguous 15-slice selection passed 2/2; this does not establish F8 formal acceptance or complete-series support. Its validation report is published at `5d9bce6a7b668c1d2361d66d88cb0cf6ff232572`.

To meet the course submission deadline without reopening accepted modules, the project owner has approved a limited exception to the F8-to-F9 implementation-start dependency; its implementation-start effect awaits publication of the canonical tracker amendment and approval of a concrete F9 implementation contract, without silently treating the F8 prerequisite as satisfied.

## 2. Exact exception requested

Under the project-owner approval dated 2026-10-24, and only after publication of a matching canonical-tracker amendment and approval of a bounded F9 implementation contract, the exception permits **F9 design finalization, implementation, Windows-first integration testing, and a clearly qualified submission demonstration** on `feature/pre-m8-foundation`, notwithstanding F8's still-pending formal acceptance.

This is an **implementation-start dependency exception only**. It does **not**:

- Declare F8 ACCEPTED / CLOSED or waive any F8-T01–T42 contractual obligation.
- Declare F9 ACCEPTED / CLOSED, production-qualified, or safe for unrestricted use.
- Waive mandatory runtime fail-closed, integrity, journal-acknowledgment, or nonreplacement behavior.
- Authorize F10, F11, GUI, M8, or global Pre-M8 acceptance.
- Approve a permanent Windows-only support claim or silently remove any previously frozen mandatory Linux acceptance gate.
- Authorize Git staging, commits, pushes, releases, tags, or changes to the source baseline without separate review and explicit release instruction.

**Dependency disposition after documentation publication and F9 scope approval:** `F9 AUTHORIZED FOR WINDOWS-FIRST IMPLEMENTATION UNDER SUBMISSION-SCOPE EXCEPTION; F8 FORMAL ACCEPTANCE PENDING; F9 FORMAL ACCEPTANCE GATED`.

## 3. Bounded F9 work

F9 shall perform the previously planned **headless orchestration** across accepted existing components (including inventory, metadata inspection, F4 candidate discovery and findings, applicable planning/COPY handling, M6 reconstruction, M7 NIfTI writing, F7 reopening and F8 verification/provenance interfaces). No standalone approved F9 implementation contract was located in the published repository at preparation time. An implementation-ready draft must identify exact APIs, path scope and test obligations; it requires explicit scope approval before F9 production work. This exception does not itself approve those APIs or paths.

The submission demonstration may use a **single explicitly selected geometrically compatible MRI series or slab**. Inputs that violate existing M6 compatibility rules must fail clearly; no missing-slice interpolation, gap filling, geometry relaxation, automatic merging of incompatible series, or unsupported-format handling is authorized.

F9 must not promote an output to VERIFIED/PUBLISHED/SUCCESS solely because M7 writing completed, or when reopened-content verification, digest continuity, source/plan binding, or required provenance acknowledgment is failed, missing, or uncertain. Ambiguous publication or journal state must remain non-successful and must not be continued through as a verified operation.

## 4. Scope freeze and stop conditions

- **Freeze F0–F7 and M6/M7**: no production modifications or compatibility-policy changes within this exception.
- **Freeze the published F8 production and separately authorized compatibility source bytes**. F9 may call approved F8 interfaces, but may not silently repair, bypass, or weaken F8 behavior.
- No additional dependencies, architecture rewrite, tracker edits beyond the explicit authorization reconciliation, or speculative robustness features.
- If F9 needs predecessor-source changes, new F8 behavior, relaxed safety checks, a new F9 contract, or an F8 failure affects truthful output status, **stop that specific change**, document the obstacle, and seek separate authorization.
- Protect all medical data. Never commit DICOM/NIfTI payloads, patient identifiers, DICOM UIDs, private operational paths, or uncontrolled medical metadata to the public repository.

## 5. Verification and permitted claims

**Primary submission development environment:** Windows 11, Java 21, Maven/JUnit 5 and qualified local NTFS. Use normal-user qualification for user-facing behavior; do not mischaracterize elevated fixture results as normal-user tests. Preserve focused, affected, and relevant native-negative evidence according to the approved F9 contract.

Additional Linux test cycles may be **deferred during F9 development for submission**, but any frozen cross-platform gate remains binding for unqualified formal acceptance unless separately amended. Record NOT RUN where applicable.

A submission demonstration must distinguish: (a) a headless F9 end-to-end pass on its supported input, (b) controlled rejection of incompatible input, and (c) any unsupported or incompletely qualified F8-verified-publication claims. Do not infer that a passing M6→M7→F7 smoke test establishes F9 or F8 end-to-end acceptance.

**F9 engineering exit criterion:** a working, repeatable Windows headless orchestration path for a supported MRI input with meaningful output, logging/reporting and explicit fail-closed handling; preserved source/test/evidence inventory; and a bounded disclosure of unverified F8/F9 acceptance obligations. This is *not* formal F9 acceptance.

## 6. Governance and release control

1. **APPROVED 2026-10-24:** the project owner approved the limited Windows-first F9 implementation-start exception and explicitly authorized preparation of this documentation and canonical-tracker amendment; this is not permission to stage, commit or push.
2. Before any F9 production implementation on the governed branch, publish the documentation-only changes to this file and the **canonical** `docs/planning/PRE-M8-FOUNDATION-TRACKER.md` to record this exception and its approval, changing only the F9 implementation-start authorization; preserve the F8 pending-acceptance record and all other gates. Publish the documentation amendment as a separate reviewed unit.
3. Approve the concrete F9 implementation contract and exact paths before F9 production changes. Stage/commit/push implementation only under explicit separate direction. No extra development subphases are created by this documentation decision.
4. Formal F8 and F9 acceptance require their own contractual evidence and decisions. This exception must not be cited as evidence that either was accepted.

## 7. Approval record

- **Project-owner decision:** APPROVED — 2026-10-24, instruction in this conversation.
- **Approval scope:** Windows-first F9 development and clearly qualified demonstration; F8/F9 formal acceptance remain gated.
- **Authorized documentation changes:** ADD this file; MODIFY only `docs/planning/PRE-M8-FOUNDATION-TRACKER.md`.
- **Canonical tracker amendment commit:** NOT YET CREATED / NOT PUBLISHED.
- **F9 concrete implementation contract:** Draft being prepared separately; NOT YET APPROVED.
- **F9 implementation-start exception effective:** **NO** until this documentation checkpoint is published and the concrete F9 implementation scope is approved.
- **Staging, commit, push:** **NOT AUTHORIZED** by this decision.
- **F8/F9 formal acceptance:** PENDING; no contractual safety or regression requirement waived.
