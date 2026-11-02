# Project Milestones

## Current integration state — 2026-11-02

**Verified integration baseline:** `b7739fd5db9a8e83e12f3a95b7f03bad31bd7a89` on `integration/post-ebo1-r2`.
**Frozen release checkpoint:** `checkpoint/ebo-1-r2-windows-evidenced` at the
same SHA. No merge into `main` is represented by this documentation update.

| Scope | State as of 2026-11-02 | Qualification / qualification limit |
| --- | --- | --- |
| M6/M7 and F0-F7 | ACCEPTED / CLOSED in recorded supported profiles | Historic evidence and protected checkpoints below and in canonical tracker |
| F8 | IMPLEMENTED; FORMAL ACCEPTANCE NOT ESTABLISHED | Tracker records cross-platform full regressions 795/795 each and independent audit 18/42 demonstrated, 24/42 partial; do not infer closure from later EBO regression |
| F9 | WINDOWS-QUALIFIED SUBMISSION CHECKPOINT; FORMAL ACCEPTANCE PENDING | Focused 20/20, full 815/815; real-MRI 15-image slab and independent NIfTI comparison passed, incompatible 20-image selection rejected; ordinary-user Windows and native Linux not established in tracker |
| M8 | INDEPENDENT NIfTI-1 TO DERIVED MR DICOM IMPLEMENTED | Native Windows evidence present, separate formal M8 acceptance not asserted; requires explicit MR/BIPED and sequence metadata |
| EBO-1 R2 | ACCEPTED / CLOSED FOR NON-CLINICAL EVALUATION SCOPE | T01-T27 evidenced; five closure tests 5/5; F6 targeted 1/1; native Windows full clean regression 888/888, zero failures/errors/skips. R1 BraTS demonstration retained; R2 BraTS not rerun |
| F10 and F11 | NOT STARTED / GATED | Independent contracts and acceptance remain necessary |
| Desktop GUI | NOT IMPLEMENTED / NOT AUTHORIZED by existing tracker | FlatLaf dependency and reserved Swing package are not a functional UI |
| Global Pre-M8 | NOT FORMALLY CLOSED | Canonical checklist remains unchecked; M8 implementation exception does not waive it |

The original October 3 M7 results below are retained verbatim as historical
acceptance evidence. In particular, `129 tests passed` and `M8 - NOT STARTED`
apply only to that snapshot, not to the November 2 integration baseline.

## Historical M7 status record — 2026-10-03 (preserved)

Last updated: 2026-10-03

## CURRENT STATE

Most recently completed milestone:

`M7 - DICOM-to-NIfTI`

Next milestone:

`M8 - Controlled NIfTI-to-DICOM`

Latest completed implementation checkpoint:

`0976788` - `feat: add conversion validation report`

Latest verified Java build:

```text
mvn clean test
```

Result:

```text
129 tests passed
0 failures
0 errors
0 skipped
BUILD SUCCESS
```

Independent M7 interoperability validation:

```text
NiBabel 5.4.2

.nii: PASS
.nii.gz: PASS
shape: PASS
datatype: PASS
spacing: PASS
raw voxel values: PASS
qform/sform geometry: PASS
intensity transform: PASS
DICOM LPS -> NIfTI RAS mapping: PASS
```

M7 is COMPLETE.

The final M7 acceptance item was satisfied by the application-level
`ConversionValidationReport`, implemented at `0976788` under ADR-014.

The report is produced only after successful NIfTI writing and records factual
conversion invariants. It does not claim that the written output was reopened,
reparsed or independently validated at runtime.

Persisted provenance/validation sidecars are not part of M7. ADR-014 records
UTF-8 JSON as the canonical serialization format for future persisted
provenance/validation manifests.

## M1 - Development Environment

Status: COMPLETE

Completed: 2026-08-26

Git checkpoint: `63fff6d`

## M2 - dcm4che Integration

Status: COMPLETE

Completed: 2026-08-26

Git checkpoint: `8e1580b`

## M3 - FlatLaf Integration

Status: COMPLETE

Completed: 2026-08-26

Git checkpoint: `cf9013d`

## M4 - Core Architecture

Status: COMPLETE

Completed: 2026-08-26

Git checkpoint: `7e50a9b`

Architecture Revision 1.0 governs subsequent implementation.

## M5 - Format Detection

Status: COMPLETE

Git checkpoint: `88da522`

M5.1 hardening checkpoint: `1989589`

## M6 - DICOM Processing

Status: COMPLETE

Frozen: 2026-10-02

Protected checkpoint:

`cb7769e` - `fix: harden selected-source provenance identity`

Protected tag:

`pre-demo-m6-2026-10-02`

The supported conventional single-frame MR reconstruction path validates:

- explicit series selection;
- supported MR object/transfer-syntax profile;
- image geometry;
- direction cosines;
- physical slice ordering;
- duplicate positions;
- regular spacing;
- compatibility;
- signed/unsigned stored pixels;
- raw voxel preservation;
- rescale semantics;
- format-neutral `ImageVolume`;
- single-slice spacing;
- selected-source provenance identity.

## M7 - DICOM-to-NIfTI

Status: COMPLETE

Completed: 2026-10-03

Architecture/implementation checkpoints:

- M7-P01 format-neutral `ImageVolume`: `1dd31fc`
- M7-P02 LPS-to-RAS affine: `8fb2831`
- M7-P03 NIfTI-1 writer: `6924dd5`
- M7-P04 end-to-end DICOM-to-NIfTI implementation: `a495699`
- M7-P04 governance cleanup: `c38ff6d`
- M7-P05A safe output and structured failures: `2bf1927`
- M7-P05B independent NiBabel validation: PASS
- ADR-014 structured validation report and future JSON manifest: `b6bab40`
- M7-P05C conversion validation report: `0976788`

Final Java regression:

```text
129 tests
0 failures
0 errors
0 skipped
BUILD SUCCESS
```

M7 acceptance criteria:

- [x] output parsed by an independent NIfTI implementation;
- [x] output shape matches;
- [x] supported integer voxel array matches exactly;
- [x] spacing matches within tolerance;
- [x] world-coordinate mapping matches within tolerance;
- [x] qform/sform are internally consistent;
- [x] conversion produces explicit provenance/validation output.

The final conversion validation report records:

- output target;
- dimensions;
- scalar type;
- voxel count;
- voxel spacing;
- NIfTI-RAS affine;
- intensity transform;
- stored-voxel preservation;
- resampling state;
- interpolation state;
- voxel-order state.

For the supported M7 path:

- stored voxel values are preserved;
- resampling is false;
- interpolation is false;
- voxel ordering is unchanged.

The report is created only after the NIfTI writer completes successfully.
Failure results do not carry a validation report.

GUI integration was not required for M7 completion.

## M8 - Controlled NIfTI-to-DICOM

Status: NOT STARTED

## M9 - Validation

Status: NOT STARTED

Broader round-trip and dataset-level validation remains separate from the focused
M7 NIfTI acceptance validation already completed.

## M10 - GUI Integration

Status: NOT STARTED

## M11 - Stakeholder Demo

Status: NOT STARTED

## M12 - Final Hardening

Status: NOT STARTED
