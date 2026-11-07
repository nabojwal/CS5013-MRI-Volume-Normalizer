# M4 Architecture

M4 establishes package boundaries and contracts without implementing format detection, DICOM processing, NIfTI processing, normalization, conversion, or GUI screens.

The dependency direction is:

```text
presentation -> application -> domain
infrastructure -> domain
```

The domain is independent of Swing, FlatLaf, dcm4che, and filesystem implementations.

## Current architecture guide — 2026-11-07

**Historical scope:** The M4 text above records the initial architectural
scaffolding, **not the current implementation baseline**. The working
DICOM → NIfTI, bounded independent NIfTI → derived MR DICOM, forward/reverse
batch controllers and Swing/FlatLaf GUI were subsequently implemented and
merged through [PR #1](https://github.com/nabojwal/CS5013-MRI-Volume-Normalizer/pull/1).
The source checkpoint used for this guide is `main` at
[`b032a51cd369b428f201c6b0dd8d23a49f195eec`](https://github.com/nabojwal/CS5013-MRI-Volume-Normalizer/commit/b032a51cd369b428f201c6b0dd8d23a49f195eec).

### Reading order and authority

| Resource | Purpose and status |
| --- | --- |
| [Architecture Revision 1.0](ARCHITECTURE-REVISION-1.0.md) | **Approved authoritative architecture contract**; supersedes conflicting M4 notes. Its M5/M6 baseline, GUI freeze and original status sections are *historical implementation context*, not a description of the integrated November source. Its dependency rules remain binding unless formally reconciled. |
| [Architecture decisions](../../DECISIONS.md) | Accepted decisions including the format-neutral `ImageVolume` model (ADR-009), generic integer scalar model (ADR-010) and DICOM LPS → NIfTI RAS affine (ADR-011); later decisions qualify earlier unresolved M4 questions without rewriting their history. |
| [M8 MR DICOM conformance profile](M8-DICOM-CONFORMANCE-PROFILE.md) | **M8-MR-1 implementation candidate**, defining the restricted derived MR Image Storage output, geometry, required metadata and verification/publication limits; **not** an approved universal interoperability or formal DICOM conformance claim. |
| [Domain model](domain-model.md), [package responsibilities](package-responsibilities.md), [service boundaries](service-boundaries.md) | Preserve original M4 statements and provide **dated, implementation-grounded reconciliations** for `ImageVolume`, NIfTI adapters, F9/M8, FBO/EBO and GUI workflows. |
| [Unresolved decisions](unresolved-decisions.md) | Current qualification questions and the **confirmed source-versus-AR-05 GUI presentation-boundary discrepancy**; it grants neither an architecture exception nor a milestone acceptance. |
| [Foundation tracker](../planning/PRE-M8-FOUNDATION-TRACKER.md), [test strategy](../testing/TEST-STRATEGY.md), [test results](../testing/TEST-RESULTS.md) | Source checkpoints, bounded executable evidence, historical verification and **separate formal acceptance** obligations. |

### Integrated GUI architecture — implementation versus target

The working desktop entry point is
[`GuiI1App`](../../src/main/java/org/cbihi/mrinormalizer/presentation/gui/GuiI1App.java);
[`GuiI1Window`](../../src/main/java/org/cbihi/mrinormalizer/presentation/gui/GuiI1Window.java)
provides the Swing interface, while
[`GuiI1Scanner`](../../src/main/java/org/cbihi/mrinormalizer/presentation/gui/GuiI1Scanner.java)
and [`GuiI1Execution`](../../src/main/java/org/cbihi/mrinormalizer/presentation/gui/GuiI1Execution.java)
coordinate candidate inspection and dispatch. The GUI routes supported
single-series DICOM work through F9, multiple eligible series through
FBO, and bounded NIfTI reverse conversion through EBO/M8. Backend
verification and publication are authoritative; preview alone is
not an acceptance decision.

**Architecture reconciliation required:** The approved AR-05 / §19
presentation boundary prohibits presentation-layer DICOM/NIfTI
parsing, DICOM series grouping and direct concrete infrastructure
dependencies. The merged `GuiI1DicomDiscovery` performs dcm4che
metadata inspection and candidate grouping; `GuiI1Scanner` reads
NIfTI header fields; and `GuiI1Execution` directly invokes
F9 infrastructure composition and FBO/EBO bootstrap commands.
These **confirmed layering deviations** remain open for a separately
reviewed implementation correction or approved exception.
GUI functionality and its tests do **not** waive Architecture Revision 1.0.

**Status boundaries:** M10 GUI integration is **implemented**; **F10**
foundation boundary reconciliation is a different workstream and has
no recorded formal closure. PR #1 reports **961/961 passing native Windows
tests** from a user-run elevated, fixture-provisioned workstation;
this guide neither reruns those tests nor closes outstanding
F8/F9/M8 acceptance, F10/F11 or the global Pre-M8 gate. No arbitrary
DICOM/NIfTI profile, full round-trip, broad filesystem or clinical
interoperability is claimed.

This README is a **navigation and status guide**, not an amendment
to the approved architecture or M8 conformance profile.
