# Architecture Revision 1.0

# Architecture Revision 1.0

**Project:** CS5013 MRI Volume Normalizer  
**Document:** Architecture Revision 1.0  
**Version:** 1.0.0  
**Status:** APPROVED — authoritative architecture contract  
**Date:** 2026-10-01  
**Primary objective:** Establish the authoritative processing architecture before further feature development  
**Current implementation baseline:** Existing repository through M5.1 plus uncommitted/partially integrated M6 work

---

## 1. Purpose

This document defines the target software architecture for the MRI Volume Normalizer before further implementation proceeds.

It supersedes the earlier M4 architectural notes where they conflict with this revision, while preserving useful principles from the existing codebase:

- separation of presentation, application, domain, and infrastructure concerns;
- isolation of dcm4che from higher-level application logic;
- explicit handling of DICOM geometry and pixel semantics;
- deterministic, testable processing;
- privacy-safe provenance;
- rejection of unsupported or inconsistent data rather than silent approximation.

This revision is necessary because the project has moved beyond architectural scaffolding into real DICOM volume reconstruction, while the next stages — NIfTI writing, reverse conversion, dataset discovery, provenance, and round-trip validation — require a stable, format-neutral internal model.

The architecture is deliberately designed before further GUI work. The presentation layer is not on the critical path until the imaging engine is stable.

---

## 2. Authoritative Scope

### 2.1 Product scope

The project is a standalone Java 21 application for research MRI data preparation and controlled conversion between DICOM and NIfTI.

The minimum viable product shall support:

1. detection of DICOM and NIfTI inputs based primarily on file content;
2. recursive discovery of research imaging datasets;
3. grouping of compatible DICOM instances into coherent image series;
4. geometric and pixel-semantic validation of supported DICOM MR series;
5. reconstruction of a native 3D image volume from conventional single-frame MR DICOM instances;
6. DICOM-to-NIfTI conversion with preservation of supported voxel and spatial semantics;
7. controlled NIfTI-to-DICOM MR image-series reconstruction;
8. deterministic dataset organization and provenance tracking;
9. automated verification of geometry, voxel fidelity, and supported round-trip behavior;
10. a later desktop presentation layer for non-programming users.

### 2.2 Scope interpretation

“Bi-directional conversion” does **not** mean byte-for-byte recovery of arbitrary DICOM objects.

NIfTI does not represent the full DICOM information model. Therefore:

- DICOM -> NIfTI shall preserve the image volume and supported spatial/intensity semantics;
- metadata that cannot be represented directly in NIfTI shall be recorded separately where required;
- NIfTI -> DICOM shall create a new, valid research-use MR image series using:
  - information available from the NIfTI volume;
  - provenance metadata when available;
  - explicitly supplied metadata;
  - documented safe defaults where permitted;
- generated DICOM identifiers shall be new identifiers rather than pretending to reconstruct original objects exactly.

### 2.3 Current scope correction

Voxel-intensity normalization such as z-score or percentile normalization is **not a required MVP feature** for the conversion engine.

The current conversion path shall preserve stored voxel values where no rescaling is explicitly requested. Any future intensity-normalization operation shall be treated as an optional post-conversion processing feature and shall not be coupled to the core DICOM <-> NIfTI architecture.

### 2.4 Explicit non-goals for the MVP

The project shall not attempt to provide:

- PACS/DICOM networking;
- clinical deployment;
- support for every DICOM SOP Class;
- arbitrary vendor-specific compressed transfer syntaxes;
- arbitrary Enhanced Multi-frame DICOM;
- diffusion/spectroscopy metadata reconstruction;
- registration;
- segmentation;
- multimodal fusion;
- AI/ML inference;
- distributed processing;
- a replacement for mature converters such as dcm2niix across all vendor-specific edge cases.

---

## 3. Architecture Principles

The following rules are normative.

### AR-01 — Domain independence

The domain layer shall not depend on:

- dcm4che;
- Swing;
- FlatLaf;
- filesystem implementation classes;
- a NIfTI I/O library;
- JSON serialization libraries;
- presentation code;
- application services.

### AR-02 — Storage formats are adapters, not the domain

DICOM and NIfTI are external storage representations.

The central volume model shall be format-neutral.

The generic volume model shall not require DICOM-specific concepts such as:

- `BitsAllocated`;
- `BitsStored`;
- `HighBit`;
- `PixelRepresentation`;
- `PhotometricInterpretation`;
- `TransferSyntaxUID`.

Those fields belong to DICOM-specific metadata or adapter-level representations.

### AR-03 — Application layer owns use-case orchestration

Application services shall coordinate workflows such as:

- dataset discovery;
- DICOM series reconstruction;
- DICOM -> NIfTI conversion;
- NIfTI -> DICOM conversion;
- provenance generation;
- validation and reporting.

Application services shall not parse raw DICOM byte structures directly.

### AR-04 — Infrastructure implements external I/O

Infrastructure adapters may depend on external technologies such as:

- dcm4che;
- Java NIO;
- GZIP streams;
- a future NIfTI parser/writer implementation.

Infrastructure shall translate external representations into application/domain representations.

### AR-05 — Presentation is last

Presentation code shall invoke application use cases.

Presentation code shall not:

- parse DICOM or NIfTI;
- perform series grouping;
- compute slice order;
- construct spatial affines;
- decode pixels;
- create authoritative provenance.

GUI work remains frozen except for keeping existing code compilable.

### AR-06 — Fail explicitly

Unsupported or inconsistent medical-image structures shall fail with stable error categories.

The application shall not silently:

- choose an arbitrary DICOM series;
- reorder by filename;
- reorder by `InstanceNumber`;
- merge incompatible instances;
- resample;
- interpolate;
- normalize voxel values;
- convert coordinate systems without an explicit conversion step;
- manufacture missing clinical metadata as though it had been present in the source.

### AR-07 — Determinism

Given the same supported inputs, configuration, and software version, processing decisions shall be deterministic.

### AR-08 — Privacy-safe provenance

Patient name and patient ID shall not be written into logs or provenance by default.

### AR-09 — Testability

Core validation, geometry, affine, and volume-assembly operations shall be deterministic Java logic testable without GUI code.

### AR-10 — Small interfaces at true external boundaries

Interfaces shall be introduced for external dependencies or independently replaceable I/O boundaries.

Pure internal algorithms do not require interfaces merely for architectural appearance.

---

## 4. Target Layered Architecture

```text
                         +----------------------+
                         |     Presentation     |
                         | Swing / FlatLaf later|
                         +----------+-----------+
                                    |
                                    v
                         +----------------------+
                         |     Application      |
                         | Use cases / workflow |
                         +----------+-----------+
                                    |
                                    v
                         +----------------------+
                         |        Domain        |
                         | Volume / Geometry /  |
                         | Voxels / Intensity   |
                         +----------^-----------+
                                    |
                         application output ports
                                    |
             +----------------------+----------------------+
             |                      |                      |
             v                      v                      v
     +---------------+      +---------------+      +---------------+
     | DICOM Adapter |      | NIfTI Adapter |      | File/Manifest |
     |    dcm4che    |      | reader/writer |      |   Java NIO    |
     +---------------+      +---------------+      +---------------+
                     Infrastructure
```

### 4.1 Allowed dependency direction

The target dependency direction is:

```text
presentation -> application -> domain

infrastructure -> application ports
infrastructure -> domain
```

Infrastructure may create domain objects as part of adapter translation.

Domain must remain the innermost layer.

### 4.2 Prohibited dependencies

The following are prohibited:

```text
domain -> application
domain -> infrastructure
domain -> presentation

application -> infrastructure concrete classes

presentation -> infrastructure
presentation -> dcm4che
presentation -> NIfTI implementation
```

Composition of concrete adapters shall occur at the application bootstrap/composition boundary.

---

## 5. Target Package Structure

The exact class names may be adjusted during implementation, but package responsibilities shall follow this structure.

```text
org.cbihi.mrinormalizer
|
+-- domain
|   |
|   +-- volume
|   |   +-- ImageVolume
|   |   +-- Dimensions3D
|   |   +-- VoxelData
|   |   +-- ImmutableVoxelData
|   |   +-- ScalarType
|   |
|   +-- geometry
|   |   +-- VolumeGeometry
|   |   +-- SliceGeometry
|   |   +-- CoordinateSystem
|   |   +-- AffineMatrix4
|   |
|   +-- intensity
|       +-- IntensityTransform
|
+-- application
|   |
|   +-- port
|   |   +-- out
|   |       +-- FormatProbe
|   |       +-- DicomInstanceReader
|   |       +-- DicomSeriesWriter
|   |       +-- NiftiVolumeReader
|   |       +-- NiftiVolumeWriter
|   |       +-- DatasetScanner
|   |       +-- ManifestWriter
|   |
|   +-- detection
|   |   +-- FormatDetectionService
|   |
|   +-- dicom
|   |   +-- ReconstructDicomVolumeUseCase
|   |   +-- DicomSeriesValidator
|   |   +-- SliceGeometryValidator
|   |   +-- VolumeAssembler
|   |   +-- model
|   |       +-- DicomInstance
|   |       +-- DicomPixelEncoding
|   |       +-- DicomSeriesSelection
|   |
|   +-- conversion
|   |   +-- ConvertDicomToNiftiUseCase
|   |   +-- ConvertNiftiToDicomUseCase
|   |
|   +-- dataset
|   |   +-- DiscoverDatasetUseCase
|   |   +-- NormalizeDatasetLayoutUseCase
|   |
|   +-- provenance
|   |   +-- ProvenanceRecord
|   |   +-- ProvenanceManifest
|   |
|   +-- validation
|       +-- ValidationReport
|
+-- infrastructure
|   |
|   +-- detection
|   |   +-- DicomFormatProbe
|   |   +-- NiftiFormatProbe
|   |
|   +-- dicom
|   |   +-- dcm4che
|   |       +-- Dcm4cheInstanceReader
|   |       +-- Dcm4cheSeriesWriter
|   |
|   +-- nifti
|   |   +-- Nifti1Reader
|   |   +-- Nifti1Writer
|   |
|   +-- filesystem
|       +-- NioDatasetScanner
|       +-- JsonManifestWriter
|
+-- presentation
|   +-- swing
|
+-- Main
```

This structure intentionally distinguishes:

- generic image-domain concepts;
- DICOM-specific application models;
- external I/O adapters;
- conversion use cases.

---

## 6. Core Domain Model

### 6.1 `ImageVolume`

`ImageVolume` shall be the central format-neutral representation used between DICOM and NIfTI workflows.

Conceptually:

```text
ImageVolume
+-- Dimensions3D
+-- VoxelData
+-- VolumeGeometry
+-- IntensityTransform
```

The volume shall not expose dcm4che or NIfTI implementation types.

### 6.2 `Dimensions3D`

Required fields:

```text
width   > 0
height  > 0
depth   > 0
```

Dimension ordering shall be documented consistently.

For the Java indexing API used by this project:

```text
x = column index
y = row index
z = slice index
```

### 6.3 `VoxelData`

The generic voxel interface shall expose:

- width;
- height;
- depth;
- generic scalar type;
- stored numeric value access.

It shall not return DICOM-specific `PixelEncoding`.

Illustrative contract:

```java
public interface VoxelData {
    int width();
    int height();
    int depth();
    ScalarType scalarType();
    long rawValueAt(int x, int y, int z);
}
```

The exact API may support additional floating-point cases later if needed by NIfTI, but the current DICOM M6 path shall preserve integer stored values as `long`.

### 6.4 `ScalarType`

The generic scalar-type model shall describe the data representation required by the in-memory volume, for example:

- signed integer;
- unsigned integer;
- bit width or equivalent storage classification.

It shall be independent of DICOM tags.

### 6.5 `IntensityTransform`

The generic model shall represent the relationship between stored voxel values and interpreted values.

For the current DICOM subset:

```text
interpreted = stored * slope + intercept
```

The original raw stored values shall remain accessible.

The presence or absence of a declared transform shall be preserved.

### 6.6 DICOM-specific pixel encoding

DICOM storage metadata shall be represented separately, e.g. `DicomPixelEncoding`:

```text
bitsAllocated
bitsStored
highBit
pixelRepresentation
samplesPerPixel
photometricInterpretation
```

This metadata may be retained for provenance and reverse-conversion decisions, but it shall not define the generic `VoxelData` contract.

---

## 7. Geometry and Coordinate Model

Geometry is a first-class part of the image domain.

### 7.1 DICOM reconstruction convention

During DICOM reconstruction, geometry shall remain in native DICOM patient coordinates:

```text
LPS = Left / Posterior / Superior
```

No DICOM LPS -> NIfTI RAS conversion belongs inside the DICOM reader.

### 7.2 `SliceGeometry`

The slice representation shall explicitly contain:

- image position in patient coordinates;
- column-index direction;
- row-index direction;
- derived slice normal/direction;
- projected position along the slice normal.

Direction vectors must contain three finite values.

### 7.3 Orientation validation

The reconstruction pipeline shall validate:

- finite direction cosines;
- approximate unit magnitude;
- row/column orthogonality;
- valid non-zero cross product;
- consistency of orientation across the series.

Default M6 tolerance:

```text
direction cosine tolerance = 1e-4
```

### 7.4 Position validation

The application shall:

1. choose a validated reference orientation;
2. derive slice normal by cross product;
3. project `ImagePositionPatient` onto that normal;
4. reject unacceptable in-plane displacement;
5. sort by projected physical position;
6. reject duplicate/ambiguous positions;
7. validate regular inter-slice spacing for the supported M6 profile.

Default tolerances:

```text
position tolerance          = 1e-3 mm
pixel spacing tolerance     = 1e-3 mm
slice spacing tolerance     = 1e-3 mm
duplicate position tolerance= 1e-3 mm
```

### 7.5 Slice ordering rule

The authoritative ordering rule is physical position.

The implementation shall **not** use:

- filename;
- directory order;
- `InstanceNumber`;

as the primary ordering mechanism.

### 7.6 `VolumeGeometry`

The format-neutral geometry model shall contain enough information to derive voxel-to-world mapping:

- width;
- height;
- depth;
- row spacing;
- column spacing;
- slice spacing when defined;
- world-space origin;
- column-index direction;
- row-index direction;
- slice direction;
- coordinate-system identity.

### 7.7 `AffineMatrix4`

Before DICOM -> NIfTI writing is implemented, the architecture shall add an immutable 4x4 affine representation.

The affine shall explicitly define:

```text
voxel index -> world coordinate
```

The project shall test representative voxel/world mappings, including corners and centre positions.

### 7.8 Single-slice policy

A single-slice image does not provide inter-slice spacing from neighboring positions.

The generic geometry model shall not silently interpret a synthetic `0.0` as a physical spacing.

Before NIfTI output is implemented, one explicit policy shall be chosen:

- derive a z-extent from supported metadata such as `SpacingBetweenSlices` or `SliceThickness`, if valid; or
- represent slice spacing as unavailable and require the NIfTI writer to apply a documented supported fallback.

The policy must be documented and tested.

---

## 8. DICOM Supported Profile

### 8.1 Initial supported object class

M6 supports conventional single-frame MR Image Storage only.

Required high-level constraints:

```text
Modality = MR
SOP Class = MR Image Storage
NumberOfFrames = 1
```

Multi-frame objects are outside M6.

### 8.2 Approved transfer syntaxes

The initial supported transfer syntaxes are:

- Implicit VR Little Endian;
- Explicit VR Little Endian;
- Explicit VR Big Endian.

Compressed or encapsulated transfer syntaxes shall be rejected explicitly in the MVP unless deliberately added in a later revision.

### 8.3 Current pixel profile

The existing implementation intentionally supports a limited monochrome integer profile:

- 8-bit or 16-bit allocated samples;
- valid `BitsStored`;
- `HighBit = BitsStored - 1`;
- signed or unsigned integer representation;
- `SamplesPerPixel = 1`;
- `PhotometricInterpretation = MONOCHROME2`.

Unsupported encodings shall fail explicitly.

### 8.4 Required identifiers and compatibility fields

For an instance participating in a reconstructed series, the workflow shall validate at least:

- Study Instance UID;
- Series Instance UID;
- SOP Instance UID;
- Modality;
- SOP Class UID;
- Rows;
- Columns;
- Pixel Spacing;
- Image Orientation Patient;
- Image Position Patient;
- Photometric Interpretation;
- Samples Per Pixel;
- Bits Allocated;
- Bits Stored;
- High Bit;
- Pixel Representation;
- Rescale Slope / Intercept semantics;
- Transfer Syntax UID;
- supported object type.

Duplicate SOP Instance UIDs shall be rejected.

### 8.5 Explicit series selection in M6

The current M6 reconstruction use case requires an explicit selected `SeriesInstanceUID`.

Failure states shall include at minimum:

```text
SERIES_NOT_SELECTED
SERIES_NOT_FOUND
```

The service shall never silently choose a series.

Dataset-level series discovery will be introduced separately and shall not change this M6 invariant.

---

## 9. DICOM Reconstruction Application Design

The current `DefaultDicomSeriesService` contains useful working logic but owns too many responsibilities.

It shall be decomposed conceptually as follows:

```text
ReconstructDicomVolumeUseCase
        |
        +-- DicomInstanceReader         [external port]
        +-- DicomSeriesValidator        [deterministic application/domain logic]
        +-- SliceGeometryValidator      [deterministic geometry logic]
        +-- VolumeAssembler             [deterministic assembly]
        +-- provenance/result creation  [application boundary]
```

### 9.1 `DicomInstanceReader`

Responsibility:

- inspect one input resource;
- parse supported DICOM;
- validate low-level object/encoding constraints;
- decode one frame of raw stored pixel values;
- extract DICOM-specific metadata;
- create an application-level DICOM instance representation.

It shall be an **application output port**, implemented by dcm4che infrastructure.

### 9.2 `DicomSeriesValidator`

Responsibility:

- duplicate SOP detection;
- study consistency;
- series consistency;
- modality/SOP consistency;
- dimensions;
- pixel spacing;
- DICOM pixel-storage compatibility;
- rescale compatibility;
- transfer-syntax/profile compatibility where required.

### 9.3 `SliceGeometryValidator`

Responsibility:

- orientation validation;
- physical-position consistency;
- projected-position calculation/verification;
- physical sorting;
- duplicate slice detection;
- regular-spacing validation for the supported profile.

### 9.4 `VolumeAssembler`

Responsibility:

- combine ordered one-slice voxel arrays;
- construct generic volume dimensions;
- construct generic geometry;
- preserve raw voxel values;
- preserve intensity-transform semantics.

It shall not parse files.

### 9.5 Use-case result

The reconstruction use case shall return an explicit result containing:

- success/failure;
- reconstructed generic `ImageVolume` when successful;
- structured error(s) when unsuccessful;
- application-owned provenance.

The result shall not use dcm4che types.

---

## 10. Application Ports

The following ports are expected.

### 10.1 Current/immediate ports

```text
FormatProbe
DicomInstanceReader
```

These shall move from `domain.port` to an application port package.

### 10.2 Required future ports

```text
NiftiVolumeReader
NiftiVolumeWriter
DicomSeriesWriter
DatasetScanner
ManifestWriter
```

### 10.3 Port rule

Ports describe capabilities required by application use cases.

They shall not expose:

- dcm4che types;
- Swing types;
- concrete infrastructure classes.

Use `Path` only where deliberately accepted as an application-level resource boundary; otherwise use a small stable resource reference type.

---

## 11. NIfTI Supported Profile

The MVP shall support NIfTI-1 single-file volumes:

```text
.nii
.nii.gz
```

Support for NIfTI-2 detection may remain if already implemented, but NIfTI-2 conversion shall not automatically become an MVP requirement unless explicitly approved.

### 11.1 NIfTI reader responsibilities

The NIfTI reader shall:

- parse the required header fields;
- validate dimensions and datatype;
- load supported voxel data;
- interpret `sform` and/or `qform` according to an explicit precedence policy;
- reconstruct voxel-to-world geometry;
- distinguish storage representation from generic `ImageVolume`.

### 11.2 NIfTI writer responsibilities

The NIfTI writer shall:

- write dimensions;
- write datatype/bit depth supported by the generic volume;
- write voxel spacing;
- write raw voxel values without unintended numerical change;
- construct spatial affine metadata;
- write qform/sform consistently;
- support `.nii`;
- support `.nii.gz` through compression wrapping.

### 11.3 NIfTI affine policy

Before implementation begins, M7 shall define and test:

- DICOM LPS -> NIfTI RAS conversion;
- voxel-index axis ordering;
- qform/sform precedence and codes;
- numeric tolerances;
- behavior for geometry that cannot be represented reliably.

---

## 12. DICOM -> NIfTI Conversion Pipeline

The target path is:

```text
explicit DICOM inputs
        |
        v
Dcm4cheInstanceReader
        |
        v
validated DICOM instances
        |
        v
series validation
        |
        v
physical geometry ordering
        |
        v
generic ImageVolume in DICOM LPS
        |
        v
explicit LPS -> RAS / NIfTI affine mapping
        |
        v
NiftiVolumeWriter
        |
        v
NIfTI-1 output + provenance/validation report
```

### 12.1 Invariants

For the supported lossless integer-preserving path:

```text
output dimensions == reconstructed dimensions
stored voxel array == source stored voxel array
```

unless a specifically documented transformation is requested.

The conversion use case shall not perform interpolation or resampling.

### 12.2 Validation

DICOM -> NIfTI tests shall compare:

- dimensions;
- datatype;
- voxel spacing;
- affine mapping;
- representative voxel-to-world points;
- complete voxel array where practical.

---

## 13. NIfTI -> DICOM Conversion Pipeline

The reverse path is controlled reconstruction:

```text
NIfTI input
     |
     v
NiftiVolumeReader
     |
     v
generic ImageVolume
     |
     v
RAS/world -> DICOM LPS geometry mapping
     |
     v
metadata template / provenance / safe defaults
     |
     v
new DICOM Study/Series/SOP identifiers
     |
     v
DicomSeriesWriter
     |
     v
supported conventional MR DICOM instances
```

### 13.1 Identifier policy

Generated output shall use new valid UIDs for:

- Study Instance UID;
- Series Instance UID;
- SOP Instance UID.

Original identifiers shall not be fabricated if they are unavailable.

### 13.2 Metadata provenance policy

Every generated DICOM field used by the controlled reconstruction shall be classifiable as one of:

```text
SOURCE
PROVENANCE_MANIFEST
USER_SUPPLIED
GENERATED
DEFAULTED
```

This classification should be represented in provenance rather than only displayed in the GUI.

### 13.3 Reverse-conversion limitation

The application shall document that arbitrary source DICOM metadata cannot be recovered from NIfTI alone.

---

## 14. Dataset Discovery and Directory Normalization

This capability exists in the original project scope and must not be lost while focusing on conversion.

### 14.1 Discovery

`DatasetScanner` shall recursively discover candidate files using Java NIO without loading whole datasets into memory.

Discovery shall produce structured entries including:

- original source path;
- detected format/outcome;
- diagnostic information;
- lightweight series identifiers where available;
- scan failures.

### 14.2 DICOM series discovery

Dataset discovery may group candidate DICOM instances by identifiers such as:

- Study Instance UID;
- Series Instance UID;
- modality/SOP type;
- relevant acquisition attributes.

Series discovery and M6 series reconstruction are separate responsibilities.

Discovery finds candidate series.

Reconstruction validates one selected series rigorously.

### 14.3 Directory normalization

Dataset-layout normalization shall occur only after format/series interpretation.

Files shall not be blindly flattened first.

The organizer shall:

- use deterministic destinations;
- avoid silent overwrite;
- resolve collisions deterministically;
- record source -> destination mapping;
- preserve failure/skipped states in a manifest.

### 14.4 Naming

No patient-identifying information is required in normalized directory names.

A privacy-safe study/series identifier or stable application identifier is preferred.

---

## 15. Format Detection Architecture

The current format-detection foundation is retained.

### 15.1 Detection goals

Detection shall prioritize content evidence over filename extensions.

Possible outcomes currently include:

```text
DICOM
NIFTI
NIFTI_GZ
UNKNOWN
CORRUPT
```

### 15.2 DICOM detection

The `DICM` prefix at offset 128 is a useful signal but not the sole definition of DICOM.

Files without the prefix may be passed to parser-based validation where supported.

### 15.3 Detection budget

The existing M5 1 MiB DICOM inspection limit is a bounded **format-detection policy**, not a final statement that larger DICOM objects are unsupported.

Full M6 parsing shall not inherit this 1 MiB restriction.

### 15.4 NIfTI detection hardening

Existing NIfTI field-validation helpers that are present but not currently used in the detection path shall either:

- be integrated into detection; or
- be removed if superseded by a clearer validation implementation.

Dead validation logic shall not remain as apparent-but-unused protection.

---

## 16. Provenance Architecture

Provenance is owned by the application boundary.

### 16.1 Immediate source inconsistency

The current M6 working tree contains an API mismatch between `DefaultDicomSeriesService` and `ProvenanceRecord`.

This must be repaired before M6 can be considered build-consistent.

### 16.2 Provenance goals

A processing provenance record may contain:

- input fingerprint(s);
- software version;
- relevant adapter/library version;
- processing timestamp;
- operation name;
- success/failure;
- input count;
- accepted/rejected counts;
- selected series identity in privacy-safe form;
- geometry decisions;
- pixel interpretation decisions;
- error categories;
- output fingerprint/path where appropriate.

### 16.3 Privacy

By default provenance shall exclude:

- Patient Name;
- Patient ID;
- free-text identifying DICOM fields.

### 16.4 Manifest

Dataset/conversion workflows shall support a machine-readable manifest.

The exact serialization format remains an implementation decision, with JSON a likely choice, but format choice is not part of the domain model.

---

## 17. Error Architecture

Errors shall be stable at the application boundary.

### 17.1 Error categories

The architecture shall distinguish at least:

```text
INPUT
FORMAT/PARSE
UNSUPPORTED_PROFILE
SERIES_SELECTION
SERIES_COMPATIBILITY
GEOMETRY
PIXEL_DATA
OUTPUT
VALIDATION
INTERNAL
```

Existing M6 categories such as:

```text
SERIES_NOT_SELECTED
SERIES_NOT_FOUND
DUPLICATE_SOP_INSTANCE
MIXED_STUDY
MIXED_SERIES
INCOMPATIBLE_INSTANCE
INVALID_ORIENTATION
INVALID_POSITION
DUPLICATE_SLICE
IRREGULAR_SPACING
UNSUPPORTED_OBJECT_TYPE
UNSUPPORTED_TRANSFER_SYNTAX
UNSUPPORTED_PIXEL_REPRESENTATION
PIXEL_DATA_UNAVAILABLE
MISSING_REQUIRED_METADATA
DICOM_PARSE_FAILED
```

should be retained where useful.

### 17.2 Exception policy

Infrastructure exceptions shall not escape directly into presentation.

Adapters shall translate technical failures to stable application errors while retaining controlled diagnostic causes for logs/tests.

---

## 18. Memory and Performance Strategy

The architecture shall remain series-scoped.

### 18.1 Dataset level

Do not retain decoded pixels for an entire multi-patient dataset.

Use:

- streaming discovery;
- lightweight metadata inspection;
- series-by-series reconstruction.

### 18.2 Volume level

The current MVP may materialize one supported 3D volume in memory.

Allocation shall use overflow-safe dimension arithmetic.

### 18.3 Future optimization

Slice streaming or bounded buffers may be introduced later if required by measured memory behavior.

Performance optimization shall not precede correctness of geometry and voxel semantics.

---

## 19. Presentation Boundary

Presentation is not the current focus.

The existing FlatLaf dependency and presentation package may remain, but no further GUI workflow implementation should be prioritized until:

1. architecture revision is complete;
2. M6 reconstruction passes clean tests;
3. DICOM -> NIfTI works end-to-end.

The eventual GUI shall call application services only.

---

## 20. Testing Architecture

Testing is part of the architecture, not a final-stage activity.

### 20.1 Unit tests

Unit tests shall cover:

- scalar/pixel interpretation;
- geometry vector validation;
- slice projection/order;
- duplicate detection;
- spacing validation;
- affine construction;
- coordinate conversion;
- provenance construction;
- deterministic naming/collision rules.

### 20.2 Synthetic DICOM tests

Programmatic DICOM Part 10 fixtures shall cover at least:

- valid multi-slice series;
- shuffled filenames/input order;
- signed pixel values;
- unsigned pixel values;
- Implicit VR Little Endian;
- Explicit VR Little Endian;
- Explicit VR Big Endian;
- missing orientation;
- invalid orientation;
- inconsistent orientation;
- duplicate positions;
- irregular spacing;
- mixed studies;
- mixed/incompatible series;
- duplicate SOP Instance UID;
- incompatible dimensions;
- incompatible pixel spacing;
- missing pixel data;
- unsupported object type;
- unsupported transfer syntax.

No patient data shall be required for these tests.

### 20.3 Architecture tests

Architecture tests shall verify:

- domain has no dcm4che dependency;
- domain has no Swing/FlatLaf dependency;
- application public APIs expose no dcm4che types;
- presentation does not depend on infrastructure;
- dcm4che implementation stays in infrastructure DICOM packages;
- NIfTI implementation stays in infrastructure NIfTI packages.

### 20.4 Integration tests

Required integration chains include:

```text
DICOM file -> dcm4che adapter -> reconstruction -> ImageVolume
```

and later:

```text
ImageVolume -> NIfTI writer -> NIfTI reader -> ImageVolume
```

### 20.5 End-to-end tests

Final supported round trip:

```text
DICOM
  -> ImageVolume
  -> NIfTI
  -> ImageVolume
  -> DICOM
  -> validation
```

### 20.6 Quantitative acceptance

For supported integer-preserving paths:

```text
shape_out == shape_ref
max(abs(voxel_out - voxel_ref)) == 0
```

where no rescaling is requested.

Spacing and affine/world coordinates shall be compared using explicitly documented numerical tolerances.

---

## 21. Migration From Current Codebase

The following table is the starting migration contract.

| Current component | Decision | Target / rationale |
|---|---|---|
| `Main` | KEEP | Minimal composition/entry point only |
| `DetectionDiagnostic` | KEEP / MODIFY | Stable detection diagnostics |
| `DetectionOutcome` | MODIFY | Keep format outcome semantics clear from container/compression |
| `DetectionResult` | KEEP / MODIFY | Retain value-object role |
| `ImagingFormat` | MODIFY | Clarify supported format/version semantics |
| `InputSource` | MOVE / MODIFY | Application resource reference rather than MRI domain concept |
| `OutputTarget` | MOVE / MODIFY | Application resource reference rather than MRI domain concept |
| `Volume` | REPLACE / EVOLVE | Introduce meaningful format-neutral `ImageVolume` |
| `VoxelData` | MODIFY | Replace DICOM `PixelEncoding` dependency with generic scalar type |
| `ImmutableVoxelData` | KEEP / MODIFY | Preserve implementation; add overflow-safe validation |
| `PixelValueType` | KEEP / EVOLVE | Can contribute to generic scalar semantics |
| `PixelEncoding` | SPLIT / MOVE | Become DICOM-specific `DicomPixelEncoding`; generic scalar metadata moves to domain volume |
| `RescaleTransform` | KEEP / RENAME | Evolve toward generic `IntensityTransform` |
| `CoordinateSystem` | MODIFY | Add explicit world-coordinate conventions needed by NIfTI |
| `SliceGeometry` | KEEP / MODIFY | Preserve geometry role and validation |
| `VolumeGeometry` | KEEP / MODIFY | Add explicit affine/world mapping semantics |
| `NativeVolume` | EVOLVE | Become or back the generic `ImageVolume` |
| `SeriesIdentity` | REVIEW | Integrate consistently or remove |
| `DicomInstance` | MOVE / MODIFY | DICOM-specific application model, not generic domain |
| `GeometryValidationPolicy` | KEEP | Explicit tolerance policy |
| `DicomProcessingError` | MOVE / MODIFY | Stable application error model |
| `DicomProcessingException` | MODIFY | Prevent domain/infrastructure exception leakage |
| `domain.port.FormatProbe` | MOVE | `application.port.out.FormatProbe` |
| `domain.port.DicomInstanceReader` | MOVE | `application.port.out.DicomInstanceReader` |
| `FormatDetectionService` | KEEP / MODIFY | Keep use case; reduce direct infrastructure responsibilities |
| `DicomSeriesRequest` | MODIFY | Strong immutable request; explicit selected series |
| `DicomProcessingResult` | MODIFY | Return explicit generic `ImageVolume` and structured errors |
| `DicomSeriesService` | RENAME / KEEP | Evolve to `ReconstructDicomVolumeUseCase` |
| `DefaultDicomSeriesService` | SPLIT | Preserve behavior; extract validation/assembly responsibilities |
| `ProvenanceRecord` | REDESIGN NOW | Repair M6 API mismatch and define one application-owned model |
| `ProcessingRequest` | REMOVE | Generic placeholder no longer useful |
| `ProcessingResult` | REMOVE | Superseded by explicit use-case results |
| `ProcessingApplication` | REMOVE | Replace with named application use cases |
| `PresentationBoundary` | REMOVE / DEFER | Replace later with explicit GUI controllers/use-case calls |
| `DicomFormatProbe` | KEEP / MODIFY | Detection only; do not impose M5 1 MiB budget on M6 |
| `NiftiFormatProbe` | KEEP / MODIFY | Integrate or remove unused validation paths |
| `Dcm4cheInstanceReader` | KEEP / REFACTOR | Preserve substantial parsing/decoding logic; remain infrastructure |
| `DependencyContainer` | REMOVE | Avoid unused service-locator abstraction; compose explicitly |
| Existing architecture tests | KEEP / EXPAND | Update to revised dependency rules |
| `DicomSeriesServiceTest` | KEEP / REFACTOR | Preserve synthetic M6 behavior coverage |
| `M6BoundaryTest` | KEEP / EXPAND | Update package rules |
| `DatasetScanner` | ADD | Original proposal requirement |
| `SeriesDiscovery` | ADD | Separate discovery from reconstruction |
| `NormalizeDatasetLayoutUseCase` | ADD | Original proposal requirement |
| `ProvenanceManifest` | ADD | Source/output traceability |
| `NiftiVolumeReader` | ADD | Required for reverse path and validation |
| `NiftiVolumeWriter` | ADD | Required for DICOM -> NIfTI |
| `AffineMatrix4` | ADD | Required for explicit spatial conversion |
| `ConvertDicomToNiftiUseCase` | ADD | Core deliverable |
| `ConvertNiftiToDicomUseCase` | ADD | Core deliverable |
| `DicomSeriesWriter` | ADD | Controlled reverse conversion |
| `MetadataTemplate` | ADD LATER | Required inputs/defaults for NIfTI -> DICOM |
| `ValidationReport` | ADD | Machine-readable validation results |
| `RoundTripValidator` | ADD | Final acceptance workflow |

---

## 22. Immediate Repair Phase — AR-0

Before architectural refactoring, the repository shall be restored to a coherent build state.

### AR-0 exit criteria

1. Resolve the current `ProvenanceRecord` / M6 service constructor mismatch.
2. Ensure source files contain no accidental encoding/NUL corruption.
3. Normalize unintended line-ending-only changes where practical.
4. Run from the project root:

```bash
mvn clean test
```

5. Record:
   - Java version;
   - Maven version;
   - Git commit;
   - test count;
   - failures/errors/skips.
6. Do not claim current M6 completion from stale Surefire reports.
7. Create a clean checkpoint before structural refactoring.

---

## 23. Architecture Refactor Phase — AR-1

AR-1 changes structure while preserving tested behavior.

### AR-1 tasks

1. Move external ports from `domain.port` to `application.port.out`.
2. Introduce generic `ImageVolume` / generic scalar semantics.
3. Separate DICOM storage metadata from generic voxel representation.
4. Resolve provenance ownership/API.
5. Split `DefaultDicomSeriesService` responsibilities without changing M6 behavior.
6. Remove:
   - `DependencyContainer`;
   - generic `ProcessingApplication`;
   - generic `ProcessingRequest`;
   - generic `ProcessingResult`;
   - obsolete placeholder presentation boundary if no longer needed.
7. Add/update architecture tests.
8. Run all behavioral tests after each coherent refactor.

### AR-1 non-goals

Do not implement NIfTI conversion during the structural refactor.

Do not redesign the GUI.

---

## 24. M6 Completion Contract

M6 is complete only when all of the following are true.

1. Clean checkout builds successfully.
2. `mvn clean test` passes.
3. Explicit selected `SeriesInstanceUID` is mandatory.
4. Unknown selected series fails explicitly.
5. Only the agreed conventional single-frame MR subset is accepted.
6. Only approved transfer syntaxes are accepted.
7. Required DICOM geometry is validated.
8. Row/column directions are validated for finite, unit, orthogonal geometry.
9. Slice normal is derived geometrically.
10. Slice order is based on projected physical position.
11. Filename order and `InstanceNumber` do not control reconstruction.
12. Duplicate slice positions are rejected.
13. Irregular spacing is rejected for the supported M6 profile.
14. Mixed study/series or incompatible dimensions are rejected.
15. Duplicate SOP Instance UIDs are rejected.
16. Signed/unsigned stored pixels are decoded correctly.
17. Original stored voxel values are preserved.
18. Rescale slope/intercept semantics remain explicit and separate from stored values.
19. Output is a validated format-neutral `ImageVolume`.
20. dcm4che is confined to infrastructure.
21. Application/domain public APIs expose no dcm4che types.
22. Synthetic/adversarial M6 fixtures pass.
23. Provenance is internally consistent and privacy-safe.
24. The M6 implementation has a Git checkpoint after clean verification.

---

## 25. M7 — DICOM to NIfTI Plan

M7 begins only after M6 is frozen.

### M7.1 NIfTI architecture

Implement:

- generic affine support;
- NIfTI-1 header model;
- `.nii` writer;
- `.nii.gz` wrapper;
- supported scalar datatype mapping;
- qform/sform generation;
- LPS -> RAS conversion.

### M7.2 End-to-end output

Required workflow:

```text
supported DICOM series
        ->
validated ImageVolume
        ->
NIfTI-1
```

### M7.3 M7 exit criteria

- output can be parsed by the project NIfTI reader or an independent validation path;
- shape matches;
- supported integer voxel array matches exactly;
- spacing matches within tolerance;
- world-coordinate mapping matches within tolerance;
- qform/sform are internally consistent;
- conversion produces provenance/validation output.

A CLI/test harness is sufficient for the first demonstrable M7 pipeline.

GUI integration is not required for M7 completion.

---

## 26. Dataset Workflow Plan

After the core DICOM -> NIfTI path is working:

1. implement recursive dataset scanning;
2. attach detection results;
3. discover DICOM series candidates;
4. expose deterministic organization plan;
5. apply copy/move policy safely;
6. produce source-to-destination manifest;
7. invoke conversion series-by-series where requested.

The dataset workflow shall reuse the imaging engine rather than duplicate conversion logic.

---

## 27. M8 — Controlled NIfTI to DICOM Plan

Implement:

- NIfTI volume reader;
- affine/geometry interpretation;
- explicit RAS -> DICOM LPS mapping;
- metadata template model;
- UID generation;
- DICOM MR instance writer;
- provenance classification of inferred/generated/defaulted metadata;
- parse-back validation using dcm4che.

### M8 exit criteria

Generated instances shall be:

- parseable;
- internally UID-consistent;
- geometrically consistent;
- dimensionally consistent;
- pixel-consistent for the supported profile;
- explicit about non-recoverable metadata.

---

## 28. Round-Trip Validation Plan

For supported fixtures:

```text
DICOM
 -> NIfTI
 -> DICOM
```

Validation shall not claim arbitrary metadata identity.

It shall validate what the supported contract promises:

- voxel array;
- dimensions;
- spacing;
- orientation/world geometry;
- generated DICOM consistency;
- selected preserved metadata/provenance;
- documented loss/non-recoverability.

---

## 29. Architecture Decision Log Required Before Implementation

The following decisions must be explicitly resolved and recorded as ADRs or a revision of this document before their implementation:

1. exact `ImageVolume` Java API;
2. exact generic scalar-type model;
3. single-slice z-spacing policy;
4. NIfTI qform/sform precedence and codes;
5. explicit LPS <-> RAS affine convention;
6. NIfTI library choice versus project-owned minimal reader/writer;
7. provenance serialization format;
8. output collision policy;
9. directory-normalization copy versus move semantics;
10. metadata template/default policy for NIfTI -> DICOM.

These decisions must not be silently selected by an implementation agent.

---

## 30. Definition of Architectural Stability

Architecture Revision 1.0 shall be considered locked when:

- this document is reviewed and approved;
- Codex performs a read-only discrepancy audit against the live repository;
- identified contradictions are resolved;
- AR-0 produces a clean test baseline;
- AR-1 refactor passes all retained behavioral and architecture tests;
- package responsibilities no longer depend on unresolved placeholder abstractions;
- the generic volume representation is no longer DICOM-shaped.

After that point, new implementation should conform to this architecture unless a documented architecture revision is approved.

---

## 31. Codex Review Contract

Before Codex modifies the repository, it should be asked to perform a **read-only audit**.

Recommended instruction:

> Read `docs/architecture/ARCHITECTURE-REVISION-1.0.md` and audit the current repository against it. Do not modify files. Produce a discrepancy report with exact file paths and line numbers. Classify each discrepancy as BLOCKER, REQUIRED REFACTOR, OPTIONAL IMPROVEMENT, or DOCUMENTATION ISSUE. Verify the current build state and identify any source/API inconsistency. Do not implement NIfTI conversion or GUI changes during this audit.

Only after the discrepancy report is reviewed should Codex be given an implementation task.

---

## 32. First Implementation Contract After Approval

After AR-0 and the read-only discrepancy review, the first implementation prompt should require:

1. behavior-preserving architectural refactor only;
2. small coherent commits;
3. tests after each refactor;
4. no new GUI features;
5. no NIfTI conversion yet;
6. no new dependencies without approval;
7. no silent change to supported DICOM profile;
8. final `mvn clean test`;
9. summary of files changed and architecture rules satisfied.

---

## 33. Current Project Status Under This Architecture

At the time this draft was written:

### Substantially established

- Java/Maven project foundation;
- Java 21 target;
- dcm4che dependency and DICOM adapter direction;
- FlatLaf/UI foundation;
- content-based format-detection foundation;
- M5 detection tests;
- DICOM M6 synthetic-test work;
- M6 DICOM metadata parsing;
- supported transfer-syntax checks;
- raw pixel decoding for the intended integer profile;
- explicit series selection;
- compatibility validation;
- physical slice ordering;
- regular-spacing checks;
- native volume assembly logic;
- architecture-boundary testing concept;
- versioned AI prompt provenance.

### Incomplete or requiring revision

- coherent current M6 build;
- provenance API;
- format-neutral volume model;
- placement of application ports;
- separation of DICOM-specific pixel metadata;
- splitting M6 service responsibilities;
- explicit affine model;
- single-slice spacing policy;
- complete dataset scanning/normalization workflow;
- NIfTI I/O;
- DICOM -> NIfTI;
- NIfTI -> DICOM;
- provenance manifest;
- quantitative conversion report;
- round-trip validator;
- final GUI orchestration.

---

## 34. Guiding Principle

The central architecture rule for the remainder of the project is:

> **Reconstruct and validate one trustworthy, format-neutral image volume first. Conversion formats, dataset organization, provenance, and the GUI shall be built around that stable core rather than embedding their concerns inside it.**

This principle is the basis for completing the project without repeated architectural redesign.

---

## 35. Approval

This document is a first draft.

Before implementation, review the following high-impact decisions:

- generic `ImageVolume` shape;
- DICOM-specific versus generic scalar metadata split;
- application-port relocation;
- `DefaultDicomSeriesService` decomposition;
- provenance redesign;
- single-slice spacing handling;
- NIfTI affine convention;
- removal of intensity normalization from the required MVP path.

Once these are approved and the read-only Codex audit reports no unresolved blocker, the document should be versioned as:

```text
ARCHITECTURE-REVISION-1.0.md
Version: 1.0.0
Status: APPROVED
```

and treated as the authoritative architecture contract for subsequent milestones.
