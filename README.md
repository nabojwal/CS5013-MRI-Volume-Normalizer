# CS5013 MRI Volume Normalizer

## 1. Overview

A Java research-data utility for IIT Madras CS5013: Programming with AI. The
implemented engine recognizes DICOM/NIfTI inputs and reconstructs a selected
supported DICOM MR series while retaining physical geometry and stored pixels.
Controlled DICOM-to-NIfTI and NIfTI-to-DICOM conversion, research dataset
organization and expanded provenance are planned. This is a research-use project,
not a clinical or PACS product.

## 2. Project Objectives

- Recognize formats from content rather than trusting extensions.
- Assemble compatible MR slices using physical position and explicit validation.
- Preserve voxel values, spatial meaning and separate rescale metadata.
- Support reproducible conversion and dataset organization in later stages.

Intensity normalization is optional under the approved architecture, not part of
the required conversion MVP.

## 3. Current Development Status

Reviewed 2026-10-01 at `fc1ad46` (`fix: harden voxel allocation validation`).

| Milestone | Description | Status / checkpoint |
|---|---|---|
| M1 | Java/Maven/JUnit baseline | COMPLETE — `63fff6d` |
| M2 | dcm4che integration | COMPLETE — `8e1580b` |
| M3 | FlatLaf foundation | COMPLETE — `cf9013d` |
| M4 | Initial architecture | COMPLETE — `7e50a9b`; revision governs subsequent work |
| M5 | Content-based detection | COMPLETE — `88da522` |
| M5.1 | Scoped detection hardening | COMPLETE — `1989589`; remaining gaps below |
| M6 | DICOM reconstruction | IN PROGRESS — baseline `3c3d979`, geometry `32b4bc1`, allocation `fc1ad46` |
| AR-1 | Architecture refactor | Planned; not implemented |
| M7 | DICOM-to-NIfTI | Planned under Architecture 1.0 |
| M8 | Controlled NIfTI-to-DICOM | Planned under Architecture 1.0 |
| Later | Dataset workflow, round-trip validation, GUI, demo and hardening | Planned |

Fresh checkpoint reruns and current-suite results are in
[Verified Test Results](docs/testing/TEST-RESULTS.md). Passing tests do not satisfy
all M6 criteria: output remains `NativeVolume`, not the required format-neutral
`ImageVolume`.

## 4. Supported Scope

`DefaultDicomSeriesService` accepts explicit file inputs and a selected
`SeriesInstanceUID`. `Dcm4cheInstanceReader` supports conventional MR Image
Storage, Modality MR, single-frame input (absent NumberOfFrames defaults to 1).
All supplied files are read before series filtering; an invalid unselected file
can therefore fail a request.

- Transfer syntaxes: Implicit VR Little Endian, Explicit VR Little Endian and
  Explicit VR Big Endian. Compressed/encapsulated and deflated input is unsupported.
- Pixels: MONOCHROME2, one sample, 8/16 allocated bits, signed or unsigned,
  valid BitsStored and HighBit = BitsStored - 1; stored values remain `long`.
- Geometry: IOP/IPP validation, one common normal, physical ordering, duplicate
  position/SOP rejection, spacing and whole-grid residual validation, native LPS.
- Compatibility: study, dimensions, encoding, transfer syntax, spacing and rescale.
- Rescale slope/intercept remain explicit metadata. No normalization, resampling,
  interpolation or coordinate-system conversion occurs.
- Provenance records a hash of ordered input references, versions, timestamp,
  counts and summaries; it is not a content fingerprint or full manifest.

NIfTI-1/2 single-file headers and gzip wrapping are recognized. NIfTI volume I/O
and conversion are not implemented. Detection is not full image validation.

## 5. Architecture

Current packages separate `domain.model`/`domain.port`, `application`,
`infrastructure` adapters and `presentation`. dcm4che parsing is in infrastructure.
The [approved Architecture Revision 1.0](docs/architecture/ARCHITECTURE-REVISION-1.0.md)
defines target application ports, the format-neutral volume, split reconstruction
responsibilities and later workflows. AR-1 remains pending. Draft-era implementation
snapshots in that contract are historical, not current status.

## 6. Requirements

Java 21 and Maven (verified with Maven 3.9.16). `pom.xml` pins dcm4che-core 5.33.0,
FlatLaf 3.6.1, JUnit Jupiter 5.12.2, compiler plugin 3.14.1 and Surefire 3.5.3.
Initial resolution requires Maven repositories including the configured dcm4che
repository. This audit changes no dependencies.

## 7. Build and Test

Run from the project root:

```shell
mvn clean test
mvn "-Dtest=DicomSeriesServiceTest" test
mvn "-Dtest=ImmutableVoxelDataTest" test
mvn "-Dtest=FormatDetectionServiceTest" test
git diff --check
```

There is no implemented end-user conversion workflow yet.

## 8. Test Philosophy

JUnit covers domain invariants, synthetic-file service/adapter behavior,
regressions, dependency smoke checks and selected architecture boundaries.
Confirmed bugs should receive a failing regression before a minimal fix, focused
verification and full-suite execution. See [Test Strategy](docs/testing/TEST-STRATEGY.md)
and [Test Results](docs/testing/TEST-RESULTS.md) for methodology, evidence and gaps.

## 9. AI-Assisted Development

AI assistants operate under explicit constraints and human project-owner authority.
The [AI Prompt Log](PROMPTS.md) connects preserved prompts and labeled historical
task summaries to tests, implementation, verification and Git checkpoints.
Negative prompting constrains scope. Generated diffs require review and tests
must run before checkpoints; AI does not independently approve architecture.
Missing historical prompts are identified rather than reconstructed as quotations.

## 10. Repository Documentation

- [Architecture contract](docs/architecture/ARCHITECTURE-REVISION-1.0.md)
- [Milestones](MILESTONES.md)
- [Development log](DEVELOPMENT_LOG.md)
- [AI prompt log and traceability](PROMPTS.md)
- [Original prompt registry](ai/PROMPT_REGISTRY.md)
- [Test strategy](docs/testing/TEST-STRATEGY.md)
- [Test results and executable inventory](docs/testing/TEST-RESULTS.md)

## 11. Limitations / Planned Work

M6 is IN PROGRESS. AR-1 and the format-neutral volume are unimplemented.
Single-slice spacing is currently `0.0`; an explicit policy is required before
NIfTI output. FrameOfReferenceUID/AnatomicalOrientationType checks are absent.
The detector returns INPUT_TOO_LARGE above 1 MiB; the M6 reader does not inherit
this limit, but a valid larger-file reconstruction regression is missing.
NIfTI dimension/datatype validators exist but are not invoked by detection.
Provenance identity/privacy tests need expansion. The test gap register separates
implemented behavior, missing tests and planned features.

No patient datasets, DICOM studies, NIfTI volumes, generated output, secrets or
logs belong in Git. Tests construct synthetic temporary fixtures.
