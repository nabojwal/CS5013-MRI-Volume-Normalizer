# Package Responsibilities

## `domain`

Owns format-neutral concepts, invariants, and contracts. It must not depend on application, infrastructure, presentation, Swing, FlatLaf, dcm4che, or filesystem implementation classes.

## `application`

Owns synchronous use-case contracts, requests, results, and application-boundary provenance. It coordinates domain contracts without implementing file-format processing.

## `infrastructure`

Reserved for adapters to dcm4che, future NIfTI support, filesystem access, and provenance persistence. No concrete processing adapters are introduced in M4.

## `presentation`

Owns presentation-facing contracts. Future Swing and FlatLaf code belongs here and communicates with application contracts rather than infrastructure implementations.

## Current merged implementation — 2026-11-07

The responsibilities above record the **historical M4 architecture baseline**.
They are preserved as written; statements that NIfTI support and Swing
adapters were *future* work do **not** describe the merged application.
The current source checkpoint is `main` at
[`b032a51cd369b428f201c6b0dd8d23a49f195eec`](https://github.com/nabojwal/CS5013-MRI-Volume-Normalizer/commit/b032a51cd369b428f201c6b0dd8d23a49f195eec),
integrated by [PR #1](https://github.com/nabojwal/CS5013-MRI-Volume-Normalizer/pull/1).

| Package | Implemented responsibility and representative types |
| --- | --- |
| `domain.model`, `domain.error`, `domain.port` | Format-neutral `ImageVolume`, `VoxelData`, `ScalarType`, `VolumeGeometry`, `AffineMatrix4`, `IntensityTransform`, domain errors and selected legacy ports; no GUI or dcm4che implementation ownership |
| `application.service`, `application.conversion`, `application.validation` | M6 reconstruction, DICOM → NIfTI conversion, F9 verified orchestration, M8 NIfTI → derived MR DICOM service, LPS-to-RAS affine mapping, reopened-output comparison and conversion evidence |
| `application.dataset.*`, `application.provenance.*`, `application.port.out` | Dataset assessment, metadata catalog and conservative F4 Study+Series grouping, NIfTI read result/limits, organization/COPY models, versioned provenance contracts and application-owned output ports |
| `application.fbo`, `application.ebo`, `application.m8` | Bounded sequential forward/reverse batch controllers, requests/results, M8 limits, metadata, plans and controlled fault classifications |
| `infrastructure.dicom`, `infrastructure.nifti` | Concrete dcm4che readers, metadata adapters, derived MR DICOM slice writer/verifier, project-owned NIfTI-1 reader/writer, header and gzip handling |
| `infrastructure.filesystem`, `infrastructure.provenance`, `infrastructure.detection` | Dataset scanning, qualified destination admission, no-replace evidence/publication workspaces, JSON manifest/report I/O, M8 journaling and bounded content probes |
| `infrastructure.fbo`, `infrastructure.ebo`, `infrastructure.headless`, `bootstrap` | Forward batch preflight/runner/reports, reverse input discovery/reports, F9 Windows composition and M8/FBO/EBO command composition/entry points |
| `presentation.cli`, `presentation.gui` | Headless CLI entry point and an **implemented Swing/FlatLaf desktop GUI** (`GuiI1App`, `GuiI1Window`, `GuiI1Scanner`, `GuiI1Execution`) for inspecting and running both conversion directions within their bounded profiles |

### Presentation-boundary reconciliation remains open

The table describes **source ownership, not architectural conformance
certification**. [Architecture Revision 1.0, AR-05 and §19](ARCHITECTURE-REVISION-1.0.md)
require presentation to invoke application use cases rather than parse
image formats, group DICOM series or depend on concrete infrastructure.
In the merged implementation, `presentation.gui.GuiI1DicomDiscovery`
directly uses dcm4che for metadata-only parsing and candidate grouping;
`GuiI1Scanner` inspects NIfTI-1 header fields; and
`GuiI1Execution` invokes `F9WindowsComposition`, `FboCommand` and
`EboCommand` directly. **These are confirmed source/contract boundary
deviations**, even though backend validation remains authoritative.
They require a separately reviewed implementation reconciliation or
approved architectural exception. This file neither authorizes such
an exception nor rewrites the frozen AR-05 rule; see
[unresolved decisions](unresolved-decisions.md).

### Evidence and scope

Both DICOM → NIfTI and independent NIfTI → derived DICOM workflows,
FBO/EBO sequential orchestration and **M10 desktop GUI integration**
are implemented. The recorded PR #1 Windows regression reports
**961 passing tests, zero failures/errors/skips**, under an elevated,
fixture-provisioned environment—not new verification performed by
this documentation update. **M10 GUI integration is distinct from
F10 foundation boundary reconciliation.** Outstanding F8/F9/M8
formal acceptance, F10/F11 and the global Pre-M8 gate remain
separate from source implementation; consult the
[foundation tracker](../planning/PRE-M8-FOUNDATION-TRACKER.md).
This overview does not expand the supported imaging profiles or
establish unrestricted destination compatibility, cross-direction
round-trip fidelity or clinical validation.
