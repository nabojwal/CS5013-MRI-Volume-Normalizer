# CS5013 MRI Volume Normalizer

## 1. Overview

A Java 21 research-data utility for IIT Madras CS5013: Programming with AI.

The implemented engine detects DICOM/NIfTI inputs, reconstructs an explicitly
selected supported conventional MR DICOM series into a format-neutral
`ImageVolume`, and converts that volume to NIfTI-1 `.nii` or `.nii.gz` while
preserving the supported stored voxel values and spatial semantics.

Controlled NIfTI-to-DICOM reconstruction, dataset-level organization and the
final desktop workflow remain later milestones. This is a research-use project,
not a clinical or PACS product.

## 2. Core Principles

- detect formats from content rather than trusting extensions;
- reconstruct DICOM slices using validated physical geometry;
- preserve raw stored voxel values;
- preserve rescale slope/intercept as explicit intensity metadata;
- keep DICOM geometry in patient LPS until the conversion boundary;
- perform explicit DICOM LPS to NIfTI RAS affine conversion;
- do not resample, interpolate, flip or reorder voxel arrays in the supported
  DICOM-to-NIfTI path;
- fail explicitly on unsupported data or unsafe output conditions.

Intensity normalization is optional and is not part of the required conversion
MVP.

## 3. Current Development Status

Reviewed 2026-10-02 on `feature/m7-dicom-to-nifti`.

| Milestone | Description | Status / checkpoint |
|---|---|---|
| M1 | Java/Maven/JUnit baseline | COMPLETE — `63fff6d` |
| M2 | dcm4che integration | COMPLETE — `8e1580b` |
| M3 | FlatLaf foundation | COMPLETE — `cf9013d` |
| M4 | Initial architecture | COMPLETE — `7e50a9b` |
| M5 | Content-based detection | COMPLETE — `88da522` |
| M5.1 | Detection hardening | COMPLETE — `1989589` |
| M6 | DICOM reconstruction | COMPLETE — protected checkpoint `cb7769e` |
| M7-P01 | Format-neutral `ImageVolume` | COMPLETE — `1dd31fc` |
| M7-P02 | DICOM LPS to NIfTI RAS affine | COMPLETE — `8fb2831` |
| M7-P03 | NIfTI-1 writer | COMPLETE — `6924dd5` |
| M7-P04 | End-to-end DICOM to NIfTI | COMPLETE — `a495699` |
| M7-P05A | Safe output and structured failures | COMPLETE — `2bf1927` |
| M7-P05B | Independent NIfTI interoperability validation | VERIFIED |
| M7 | DICOM-to-NIfTI acceptance | IN PROGRESS — validation-report criterion remains |
| M8 | Controlled NIfTI-to-DICOM | NOT STARTED |
| M10 | GUI integration | NOT STARTED |

The latest Java regression baseline contains 123 passing tests with zero
failures, errors or skips.

## 4. Supported DICOM Profile

`DefaultDicomSeriesService` accepts explicit file inputs and an explicitly
selected `SeriesInstanceUID`.

Supported initial profile:

- conventional single-frame MR Image Storage;
- Modality `MR`;
- Implicit VR Little Endian;
- Explicit VR Little Endian;
- Explicit VR Big Endian;
- MONOCHROME2;
- one sample per pixel;
- 8-bit or 16-bit allocated integer samples;
- signed and unsigned representations;
- validated BitsStored / HighBit;
- explicit Image Orientation Patient / Image Position Patient geometry;
- validated Pixel Spacing;
- validated FrameOfReferenceUID;
- BIPED patient-coordinate profile;
- validated regular slice spacing.

Physical slice position controls ordering. Filename order and `InstanceNumber`
do not control reconstruction.

Single-slice reconstruction requires valid `SpacingBetweenSlices` or falls back
to valid `SliceThickness`.

## 5. Generic Volume and Spatial Model

`ImageVolume` is the authoritative in-memory image representation.

The generic scalar profile currently supports:

- `UINT8`;
- `INT8`;
- `UINT16`;
- `INT16`.

The established voxel-index convention is:

- x = column index;
- y = row index;
- z = slice index.

DICOM reconstruction remains in patient LPS coordinates.

For NIfTI output:

`A_RAS = diag(-1, -1, 1, 1) * A_LPS`

This changes the world-coordinate convention only. It does not reorder the
stored voxel array.

## 6. NIfTI-1 Output Profile

The project-owned writer supports:

- `.nii`;
- `.nii.gz`;
- NIfTI-1 single-file `n+1` format;
- little-endian header and payload;
- UINT8, INT8, UINT16 and INT16;
- millimetre spatial units;
- explicit intensity scaling metadata;
- sform as the authoritative full affine;
- qform when the affine is representable by the NIfTI quaternion model;
- `qform_code = 0` when exact quaternion representation is not supported;
- no silent overwrite of existing outputs;
- cleanup of newly created partial output after serialization failure.

The writer uses no third-party NIfTI runtime dependency.

## 7. DICOM-to-NIfTI Workflow

The implemented application path is:

```text
explicit DICOM inputs
        |
        v
Dcm4cheInstanceReader
        |
        v
validated DICOM series
        |
        v
ImageVolume in DICOM patient LPS
        |
        v
NiftiAffineMapper
        |
        v
voxel-to-world RAS affine
        |
        v
Nifti1VolumeWriter
        |
        +--> .nii
        |
        +--> .nii.gz
```

Output failures are represented separately from DICOM reconstruction errors.

## 8. Verification

Primary verification:

```shell
mvn clean test
```

Latest verified Java result:

```text
123 tests passed
0 failures
0 errors
0 skipped
BUILD SUCCESS
```

Useful focused commands:

```shell
mvn "-Dtest=DicomSeriesServiceTest" test
mvn "-Dtest=Nifti1VolumeWriterTest,Nifti1VolumeWriterHardeningTest" test
mvn "-Dtest=DicomToNiftiServiceTest,DicomToNiftiServiceHardeningTest" test
git diff --check
```

Independent M7 interoperability validation was performed with NiBabel 5.4.2
against Java-generated `.nii` and `.nii.gz` files.

The external reader confirmed:

- shape `(2, 2, 2)`;
- datatype `uint16`;
- voxel spacing `(0.5, 0.75, 2.0)`;
- exact raw voxel-array preservation;
- qform code `1`;
- sform code `1`;
- qform/sform affine geometry within absolute tolerance `1e-5`;
- declared slope `2.5` and intercept `-100`;
- gzip-wrapped NIfTI interoperability;
- expected DICOM-LPS to NIfTI-RAS world mapping.

NiBabel is an external acceptance-validation tool only. It is not a project
runtime dependency.

## 9. Provenance

Successful DICOM reconstruction records a canonical SHA-256 fingerprint derived
from selected SOP Instance UIDs and selected source content, independent of path
and input ordering.

The DICOM-to-NIfTI use case carries reconstruction provenance forward. NIfTI
output failures are represented as failed overall conversion outcomes.

An explicit conversion validation-report model remains the final M7 acceptance
item.

## 10. Architecture

The authoritative design contract is:

[Architecture Revision 1.0](docs/architecture/ARCHITECTURE-REVISION-1.0.md)

Current implementation decisions are recorded in:

[Architecture Decision Records](DECISIONS.md)

## 11. Repository Documentation

- [Architecture contract](docs/architecture/ARCHITECTURE-REVISION-1.0.md)
- [Milestones](MILESTONES.md)
- [Architecture decisions](DECISIONS.md)
- [Development log](DEVELOPMENT_LOG.md)
- [AI prompt log and traceability](PROMPTS.md)
- [Test strategy](docs/testing/TEST-STRATEGY.md)
- [Verified test results](docs/testing/TEST-RESULTS.md)

## 12. Remaining Work

Immediate:

- finish the M7 conversion validation-report acceptance criterion;
- perform the final M7 acceptance review and checkpoint.

Later:

- controlled NIfTI-to-DICOM reconstruction;
- dataset discovery and organization workflow;
- broader round-trip validation;
- Swing/FlatLaf user workflow;
- stakeholder demo hardening.

No patient datasets, DICOM studies, generated imaging outputs, secrets or
runtime logs belong in Git. Tests construct synthetic temporary fixtures.
