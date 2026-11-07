# CS5013 MRI Volume Normalizer

## Purpose

Desktop Java application for importing, validating, normalizing, and converting MRI volume data.

## Primary Goal

Provide a robust workflow for:

```text
DICOM / NIfTI input
        -> format detection
        -> validation
        -> volume loading
        -> normalization
        -> conversion
        -> output validation
        -> provenance reporting
```

## Implemented submission profile versus planned normalization

The **Primary Goal** above describes the broader research project
roadmap; its `normalization` stage must not be read as a claim that
general MRI intensity normalization, bias-field correction, histogram
standardization, spatial registration or resampling algorithms are
implemented and independently validated.

At the merged [PR #1](https://github.com/nabojwal/CS5013-MRI-Volume-Normalizer/pull/1)
checkpoint (`main` `b032a51cd369b428f201c6b0dd8d23a49f195eec`),
the demonstrated submission prototype implements:

- Supported conventional single-frame MR DICOM-series reconstruction,
  DICOM → NIfTI-1 conversion and bounded **independent** NIfTI-1 →
  newly derived MR DICOM conversion (3D integer `INT8`, `UINT8`,
  `INT16`, `UINT16` profile).
- Selected-series validation, explicit spatial LPS/RAS mapping,
  preservation of supported stored voxel and intensity-scaling
  semantics, reopened output checks and controlled provenance/reporting.
  **Preserving intensity scaling metadata is not applying a
  normalization algorithm.**
- Qualified sequential FBO/EBO batch dispatch and a working
  Swing/FlatLaf desktop GUI for supported bidirectional conversion.

These directions are **not** a general DICOM → NIfTI → DICOM round-trip
guarantee, original scanner-metadata recovery, unrestricted DICOM SOP
class or transfer syntax support, floating-point/4D reverse conversion,
or a clinical product. EBO evaluation metadata are declared, not
reconstructed acquisition truth.

The reported native-Windows **961/961** test regression was run on an
elevated workstation with provisioned short-path fixtures; it is
not GitHub CI or ordinary-user/cross-platform acceptance. The
[foundation tracker](docs/planning/PRE-M8-FOUNDATION-TRACKER.md)
distinguishes implemented M10 GUI functionality from the separate,
unclosed F10/F11 and formal F8/F9/M8 acceptance obligations.
The approved architecture's presentation-layer boundary discrepancy
is documented in [unresolved decisions](docs/architecture/unresolved-decisions.md).

## Technology

- Java 21 LTS
- Maven
- Swing
- FlatLaf
- dcm4che
- JUnit 5

## Architectural Principles

- Separation of UI and processing
- Modular services
- Test-driven development
- Deterministic processing
- Explicit validation
- Provenance tracking
- No dependence on filename extensions for format identification

## Out of Scope

- Cloud deployment
- Patient-data hosting
- Deep-learning-based segmentation
- PACS integration unless explicitly added to scope
- Clinical diagnosis
