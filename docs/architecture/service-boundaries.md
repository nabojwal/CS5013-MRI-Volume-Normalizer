# Service Boundaries

## Historical M4 service-boundary snapshot (preserved)

The following paragraphs describe the **original M4 contract**, not the
complete service architecture of the merged application. The original
`ProcessingApplication` interface still exists, but subsequent milestones
introduced additional concrete application services and orchestration
boundaries. The M4 paragraphs are retained without alteration.

`ProcessingApplication` is the synchronous application boundary. It accepts a `ProcessingRequest` and returns a `ProcessingResult`.

The contract does not implement format detection, metadata reading, series grouping, volume construction, normalization, conversion, or output validation. Those capabilities belong to later milestones and must be introduced behind explicit contracts when their requirements are defined.

Presentation code will invoke application contracts. It will not call dcm4che, NIfTI adapters, filesystem APIs, or processing code directly.

## Current merged service boundaries — 2026-11-07

**Source baseline:** `main` at
[`b032a51cd369b428f201c6b0dd8d23a49f195eec`](https://github.com/nabojwal/CS5013-MRI-Volume-Normalizer/commit/b032a51cd369b428f201c6b0dd8d23a49f195eec),
integrated by [PR #1](https://github.com/nabojwal/CS5013-MRI-Volume-Normalizer/pull/1).
These are **implemented source entry points and responsibilities**, not a
retroactive change to [Architecture Revision 1.0](ARCHITECTURE-REVISION-1.0.md).

| Implemented boundary | Scope and representative production components |
| --- | --- |
| M6 DICOM reconstruction | `DefaultDicomSeriesService` implements `DicomSeriesService` and validates a selected, supported conventional MR series before constructing format-neutral `ImageVolume` |
| F3/F4 metadata discovery and grouping | `DicomMetadataInspectionService` and `DicomSeriesDiscoveryService` inspect/organize candidate metadata; F4's conservative Study+Series grouping does **not** replace definitive M6 validation |
| M7 DICOM → NIfTI | `DefaultDicomToNiftiService` coordinates the forward conversion use case with approved affine and NIfTI-1 output semantics |
| F8 conversion evidence | `VerifiedConversionEvidenceService`, `NiftiOutputComparator` and application output ports express reopened-output comparison, acknowledgment and fail-closed verification/publication semantics |
| F9 selected-series headless orchestration | `F9HeadlessConversionService.executeOne(F9HeadlessRequest)` returns `F9HeadlessResult`; `F9WindowsComposition` constructs its concrete adapters at an infrastructure composition boundary |
| **M8 NIfTI → derived MR DICOM** | `M8ConversionService.execute(M8Request)` returns `M8Result`; the single-use service coordinates bounded NIfTI reading, geometry policy, DICOM slice writing/verification and workspace/journal operations |
| **FBO forward batch** | `FboBatchController` coordinates bounded sequential selected-series jobs; `FboCommand`, `F9ForwardBatchRunner` and corresponding preflight/report adapters provide entry and I/O integration |
| **EBO reverse evaluation batch** | `EboBatchController` coordinates bounded sequential M8 jobs; `EboCommand` and EBO input/report adapters provide evaluation-mode command composition |
| **M10 desktop GUI / GUI-I1** | `GuiI1App` and `GuiI1Window` provide the working Swing/FlatLaf interface; `GuiI1Scanner` and `GuiI1Execution` perform candidate inspection and dispatch to the authoritative F9/FBO/EBO backends |

### Unresolved presentation-layer boundary

The **target** rules in Architecture Revision 1.0 (AR-05, §4 and §19)
require presentation to call application use cases, keep parsing/series
grouping outside the presentation layer and avoid direct dependencies
on infrastructure. The merged GUI does **not** fully conform to that
literal dependency direction:

- `presentation.gui.GuiI1DicomDiscovery` uses dcm4che to inspect
  DICOM metadata and groups candidate instances by Study/Series.
- `presentation.gui.GuiI1Scanner` inspects preliminary NIfTI-1
  header fields.
- `presentation.gui.GuiI1Execution` calls
  `infrastructure.headless.F9WindowsComposition` and the
  `bootstrap.FboCommand`/`bootstrap.EboCommand` entry points.

These are **confirmed source-versus-approved-architecture differences**,
not evidence that conversion output is incorrect, nor an approved
exception to AR-05. Resolve them in an independently reviewed
architecture/implementation reconciliation; see
[unresolved decisions](unresolved-decisions.md). This document does
not rewrite the approved contract or claim completion of F10 foundation
boundary reconciliation. **M10 GUI integration and F10 foundation
reconciliation are distinct milestones.**

### Verification and scope boundary

PR #1 records a **user-reported native Windows 961/961 passing test
regression**, executed elevated with provisioned short-path filesystem
fixtures. This documentation update does not rerun Maven, grant
formal F8/F9/M8 acceptance, close the global Pre-M8 gate or establish
broad platform/round-trip/clinical validation. Both conversion
directions and batch/GUI workflows exist within their bounded
profiles; authoritative acceptance and qualification remain recorded
in the [Pre-M8 foundation tracker](../planning/PRE-M8-FOUNDATION-TRACKER.md)
and [test results](../testing/TEST-RESULTS.md).
