# Architecture Decision Status: M4 Snapshot and Integrated Prototype

**Reconciliation date:** 2026-11-07.
**Source baseline:** `main` at
`b032a51cd369b428f201c6b0dd8d23a49f195eec`
(GUI integration PR [#1](https://github.com/nabojwal/CS5013-MRI-Volume-Normalizer/pull/1)).
This document originally listed seven **open M4 design questions**. Those
questions were valid at M4 but should not be described as presently
unresolved merely because this historical list was not refreshed.
The original approved [Architecture Revision 1.0](ARCHITECTURE-REVISION-1.0.md),
subsequent contracts and [decision records](../../DECISIONS.md) retain
their authority; this file is a navigation/status reconciliation, not an
architectural amendment or an acceptance decision.

## Disposition of the original M4 open items

| Original M4 question | Integrated source/decision evidence | Present qualification |
| --- | --- | --- |
| MRI spatial coordinates and orientation | DICOM patient **LPS** reconstruction and explicit LPS→RAS NIfTI affine conversion (`NiftiAffineMapper`, `ImageVolume`) | **Implemented** for the supported geometry/profile; broader registration, resampling and round-trip fidelity are not inferred |
| `Volume` voxel representation, precision and layout | Format-neutral `ImageVolume`; x=column, y=row, z=slice; `INT8`, `UINT8`, `INT16`, `UINT16` stored integer profile | **Implemented, bounded**; not a general floating-point or 4D reader/converter |
| DICOM series grouping rules | Explicit `SeriesInstanceUID` selection with validated conventional MR compatibility; `GuiI1DicomDiscovery` provides metadata-only preview | **Implemented, bounded**; GUI candidate discovery is not final backend acceptance |
| NIfTI library selection | Project-owned `Nifti1VolumeReader` and `Nifti1VolumeWriter`, with external NiBabel used for historical interoperability evidence | **Resolved for the supported NIfTI-1 profile**; NiBabel is not a runtime dependency |
| Provenance serialization format | F1 canonical JSON plan/checkpoint codecs and versioned schemas under `docs/schemas`; F9 restricted persistence and M8's separate journal | **Implemented for their defined contracts**; not a universal clinical audit trail |
| Structured error representation | Domain/application error and result models; F9 reason codes; M8/EBO/FBO and GUI `REJECTED`, `FAILED` and `RECOVERY_REQUIRED` outcomes | **Implemented per workflow**, not a claim of one globally normalized error taxonomy |
| UI concurrency and progress | Swing `GuiI1Window` executes input inspection and conversion using `SwingWorker` and publishes status/activity events | **Implemented**; no deterministic percentage progress or unrestricted cancellation semantics are claimed |

## Remaining open qualification and integration questions

These are current **scope/qualification gaps**, not the original unresolved
M4 design questions:

- Demonstrate reliable destination admission, filesystem privacy, hard-link
  publication and recoverability on each claimed OS, local/removable filesystem,
  privilege mode and path-length class. Windows code includes a native
  NTFS/local-volume qualification layer, but this does not establish generic
  USB, FAT32/exFAT, network or cloud compatibility.
- Reconcile any presentation-to-infrastructure coupling against the approved
  [architecture presentation boundary](ARCHITECTURE-REVISION-1.0.md);
  the GUI currently delegates to concrete F9/FBO/EBO adapters via its
  execution layer. This is a review item, not proof of a contract violation.
- Complete outstanding formal F8/F9/M8 acceptance decisions and the
  independently gated F10/F11 and global Pre-M8 obligations. Implemented
  GUI functionality and 961 user-reported passing Windows tests are not
  equivalent to automatic formal closure.
- Independently evaluate broader bidirectional round-trip invariants,
  supported profiles and appropriate clinical/interoperability limits
  before making stronger claims. Evaluation-generated DICOM metadata must
  never be misrepresented as recovered scanner/acquisition metadata.

Historical M4 scope is documented in [M4 architecture notes](README.md).
Keep new decisions traceable to dated, approved source/contract/ADR records;
do not retroactively edit historical governance or invent acceptance.
