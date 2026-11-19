# Architecture Revision 1.0 — RC3

**Project:** CS5013 MRI Volume Normalizer  
**Document:** Architecture Revision 1.0 — Release Candidate 3  
**Version:** 1.0.0-RC3  
**Status:** RELEASE CANDIDATE — requires one final read-only repository audit before approval  
**Date:** 2026-10-01  
**Primary objective:** Lock a minimal, correctness-first architecture before further implementation  
**Implementation baseline:** Committed project through M5.1 / prompt-provenance checkpoint plus uncommitted M6 reconstruction work  
**Critical path:** Architecture lock → AR-0 build repair → M6 correctness hardening → format-neutral volume refactor → DICOM→NIfTI → dataset workflow → NIfTI→DICOM → validation → GUI

---

## RC2 Technical Corrections

RC2 preserves the architectural direction of RC1 but corrects several standards-sensitive ambiguities found during a second technical review.

The principal corrections are:

1. **DICOM axis terminology is made unambiguous.** The first `ImageOrientationPatient` triplet is the DICOM *row direction cosine* but points along increasing **column index** (`x`). The second triplet is the DICOM *column direction cosine* but points along increasing **row index** (`y`).
2. **Pixel Spacing ordering is explicit.** `PixelSpacing[0]` is row spacing (`y` step); `PixelSpacing[1]` is column spacing (`x` step).
3. **Voxel-center convention is explicit.** DICOM `ImagePositionPatient` is the center of voxel `(0,0,0)`, and the NIfTI affine shall map integer voxel indices to voxel centers without an added half-voxel shift.
4. **Classic MR single-frame handling is corrected.** `NumberOfFrames` may be absent; the supported rule is “absent or equal to 1,” not “must literally be present and equal to 1.”
5. **Frame of Reference is strengthened.** For classic MR Image Storage, the Frame of Reference Module is mandatory; `FrameOfReferenceUID` is therefore required and must be consistent across the reconstructed stack.
6. **Transfer-syntax compatibility is clarified.** Every selected instance must use a supported transfer syntax, but all instances do not need to use the same supported syntax after successful decoding.
7. **Stored-value terminology is corrected.** The project preserves decoded stored **sample values**, not the original Pixel Data byte stream or unused high bits.
8. **NIfTI scaling policy is made concrete for the MVP.** DICOM stored sample values remain stored in the NIfTI array; a declared DICOM linear rescale transform is represented using NIfTI scaling fields when valid.
9. **NIfTI spatial-transform policy is tightened.** DICOM→NIfTI writes consistent scanner-anatomical `sform` and, when representable, `qform`; NIfTI→DICOM treats materially disagreeing qform/sform as ambiguous rather than silently choosing one.
10. **Reverse-conversion shear handling is explicit.** Sheared/non-orthogonal NIfTI geometry is unsupported in the MVP unless future resampling is deliberately added.
11. **Canonical slice direction is explicit.** Slices are sorted in increasing projection along the common normal; the first sorted slice is the volume origin and spacing is positive.
12. **Detection/domain migration is reconciled.** Detection result types may remain in place during AR-0, but they are application-level concepts in the target architecture.

These corrections are intentionally narrow; they do not introduce a major redesign.

---

## 1. Purpose

This document defines the proposed authoritative software architecture for the MRI Volume Normalizer.

RC2 incorporates:

- the original project proposal;
- the submitted design document;
- the current live repository structure;
- the first Architecture Revision 1.0 draft;
- the subsequent read-only Codex audit of the live VS Code repository;
- the fresh `mvn clean test` failure and the concrete source inconsistencies identified during that audit.

This document does **not** assume that either the current implementation or the previous architecture draft is automatically correct.

The purpose of RC2 is to establish a precise contract for:

- software boundaries;
- image-domain semantics;
- supported DICOM and NIfTI subsets;
- geometry correctness;
- series selection;
- metadata-first dataset discovery;
- provenance;
- validation;
- safe dataset organization;
- migration of current code;
- completion criteria for M6 and subsequent milestones.

No further major feature implementation should begin until this RC has passed one final read-only audit and is promoted to `APPROVED`.

---

# 2. Project Scope

## 2.1 Product objective

The project is a standalone Java 21 research application for controlled MRI dataset preparation and conversion between DICOM and NIfTI.

The MVP shall support:

1. content-based identification of DICOM and NIfTI files;
2. recursive dataset discovery;
3. lightweight DICOM metadata inspection and series discovery;
4. explicit selection of a DICOM series;
5. validation of a documented conventional single-frame MR subset;
6. geometrically correct 3D volume reconstruction;
7. DICOM → NIfTI-1 conversion;
8. deterministic dataset organization with provenance;
9. controlled NIfTI → DICOM MR-series reconstruction;
10. quantitative validation and supported round-trip verification;
11. later desktop GUI integration.

The principal technical contribution is a reliable medical-image data-engineering workflow rather than a replacement for mature universal converters.

---

## 2.2 Meaning of bidirectional conversion

“Bidirectional” does not imply byte-for-byte reversibility.

DICOM and NIfTI do not carry equivalent metadata models.

Therefore:

### DICOM → NIfTI

The application shall preserve, for the supported subset:

- voxel dimensions;
- stored voxel values when no explicit scaling operation is requested;
- scalar representation within the supported mapping;
- voxel spacing;
- world-space geometry;
- supported intensity-transform semantics;
- selected metadata required for later provenance.

### NIfTI → DICOM

The reverse path is controlled reconstruction.

The application shall create new valid research-use DICOM MR instances from:

- NIfTI voxel data;
- NIfTI spatial information;
- provenance when available;
- a supported metadata template;
- user-supplied metadata where required;
- documented generated/defaulted values.

It shall not claim recovery of arbitrary source DICOM metadata.

---

## 2.3 Intensity normalization scope

Intensity normalization remains recorded as a project milestone because it appears in existing project planning documents.

However:

> **Intensity normalization is not on the critical path for proving DICOM↔NIfTI conversion correctness.**

The conversion pipeline shall first preserve voxel and spatial semantics.

Optional normalization such as:

- linear rescaling;
- z-score normalization;
- percentile normalization;

may be implemented only after:

1. DICOM → NIfTI is stable;
2. NIfTI → DICOM is stable;
3. round-trip validation is working;
4. time remains.

Normalization shall not be silently performed during ordinary conversion.

---

## 2.4 Explicit non-goals

The MVP shall not attempt:

- PACS/DICOM networking;
- clinical deployment;
- arbitrary Enhanced Multi-frame DICOM;
- universal SOP Class support;
- arbitrary vendor-specific compressed codecs;
- spectroscopy or diffusion metadata reconstruction;
- registration;
- segmentation;
- multimodal fusion;
- machine-learning inference;
- distributed processing;
- universal parity with dcm2niix;
- automatic support for QUADRUPED coordinate semantics.

---

# 3. Architectural Principles

These rules are normative.

## AR-01 — Domain independence

The generic domain layer shall not depend on:

- dcm4che;
- Swing;
- FlatLaf;
- Java filesystem implementation code;
- NIfTI I/O implementation code;
- JSON libraries;
- application services.

## AR-02 — DICOM and NIfTI are external representations

DICOM and NIfTI are storage representations.

Neither shall define the generic in-memory image-volume model.

The generic volume model shall not require DICOM-only fields such as:

- Bits Allocated;
- Bits Stored;
- High Bit;
- Pixel Representation;
- Samples Per Pixel;
- Photometric Interpretation;
- Transfer Syntax UID;
- SOP Class UID.

These shall remain DICOM-specific metadata.

## AR-03 — Application layer owns use cases

Application code shall orchestrate workflows such as:

- detection;
- dataset discovery;
- DICOM series discovery;
- DICOM volume reconstruction;
- DICOM → NIfTI conversion;
- NIfTI → DICOM conversion;
- provenance;
- validation;
- dataset organization.

Application services shall not implement raw DICOM binary parsing themselves.

## AR-04 — Infrastructure implements external I/O

Infrastructure may depend on:

- dcm4che;
- Java NIO;
- compression streams;
- NIfTI-specific readers/writers.

Infrastructure translates external storage representations into application/domain representations.

## AR-05 — Presentation is not on the current critical path

The GUI shall remain frozen except for build maintenance until the processing core is stable.

Presentation code shall not:

- parse DICOM;
- parse NIfTI;
- order slices;
- construct affines;
- perform conversion;
- decide provenance.

## AR-06 — Fail explicitly

The software shall not silently:

- select an arbitrary DICOM series;
- merge incompatible acquisitions;
- use filename order as slice order;
- use `InstanceNumber` as primary slice order;
- interpolate;
- resample;
- normalize intensities;
- convert coordinate systems without an explicit transformation;
- invent clinical metadata as though it were present in the source.

## AR-07 — Determinism and permutation invariance

Where input ordering should not affect the semantic result, the application shall be permutation-invariant.

In particular:

- the selected series result shall not depend on the order of candidate files;
- geometry validation shall use a deterministic reference orientation;
- source identities shall not depend on arbitrary input ordering.

## AR-08 — Privacy-safe provenance

Patient Name and Patient ID shall not be included in ordinary provenance/logging by default.

## AR-09 — Correctness before abstraction

The architecture shall prefer the minimum sound refactor.

Do not create interfaces or packages speculatively.

A new abstraction should be introduced only when:

- an external dependency boundary requires it;
- a use case requires it;
- independent testing materially benefits from it.

## AR-10 — Fresh verification beats stale reports

Historical Surefire reports are not evidence that the current tree passes.

Only a fresh successful build/test run from the live source tree establishes acceptance.

---

# 4. Current Repository Reality

At the time of RC3:

- Git HEAD is the prompt-provenance checkpoint;
- M6 work exists as uncommitted/untracked source;
- a fresh `mvn clean test` fails during production compilation;
- no tests execute in that fresh run.

Two current integration blockers are known.

## 4.1 Blocker B1 — Provenance constructor mismatch

`DefaultDicomSeriesService` constructs a richer provenance object than the current `ProvenanceRecord` declares.

The current record effectively exposes:

```text
inputFingerprint
softwareVersion
completedAt
successful
```

while M6 service code supplies additional fields.

This must be reconciled as one coherent application-owned provenance model.

Do **not** add an overload that silently discards information.

## 4.2 Blocker B2 — Result volume API mismatch

`DicomProcessingResult.volume()` is typed as an empty `Volume` interface.

M6 tests call:

```text
geometry()
voxels()
rescaleTransform()
```

on that result.

This cannot compile once test compilation is reached.

AR-0 shall temporarily expose the actual concrete volume or provide a meaningful volume contract.

No test-side casts shall be introduced as a workaround.

---

# 5. Target Architecture

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

## 5.1 Allowed dependency direction

```text
presentation -> application -> domain

infrastructure -> application ports
infrastructure -> domain models
```

Concrete infrastructure composition shall occur at the bootstrap boundary.

## 5.2 Prohibited dependency direction

```text
domain -> application
domain -> infrastructure
domain -> presentation

presentation -> infrastructure

application business logic -> concrete infrastructure classes
```

---

# 6. Package Strategy

The following package map is directional, not a requirement to create all future classes immediately.

```text
org.cbihi.mrinormalizer
|
+-- domain
|   +-- volume
|   +-- geometry
|   +-- intensity
|
+-- application
|   +-- port.out
|   +-- detection
|   +-- dicom
|   +-- conversion
|   +-- dataset
|   +-- provenance
|   +-- validation
|
+-- infrastructure
|   +-- detection
|   +-- dicom
|   +-- nifti
|   +-- filesystem
|
+-- presentation
|   +-- swing
|
+-- Main
```

Future classes shall be added alongside their first real use case.

Do not scaffold the entire tree in advance.

---

# 7. Generic Image Volume

## 7.1 Central concept

The conversion engine shall operate through one trusted, format-neutral image-volume representation.

The current `NativeVolume` shall evolve into this concept rather than coexist with a duplicate parallel aggregate.

Proposed name:

```text
ImageVolume
```

Conceptually:

```text
ImageVolume
+-- dimensions
+-- voxel data
+-- volume geometry
+-- intensity transform
```

## 7.2 Initial scalar subset

The first generic scalar model shall support only what the current DICOM MVP can represent faithfully.

Initial types:

```text
UINT8
INT8
UINT16
INT16
```

The generic API shall not advertise unsupported types such as:

- UINT64;
- FLOAT32;
- FLOAT64;

while storage access remains `long`.

Future scalar types may be added together with compatible storage.

## 7.3 Voxel access

Initial conceptual API:

```java
interface VoxelData {
    int width();
    int height();
    int depth();
    ScalarType scalarType();
    long rawValueAt(int x, int y, int z);
}
```

The exact Java signature remains subject to implementation review.

## 7.4 Numeric representation versus DICOM storage metadata

Example:

A signed DICOM sample with:

```text
BitsAllocated = 16
BitsStored = 12
HighBit = 11
PixelRepresentation = signed
```

may be represented generically as:

```text
INT16
```

while the exact DICOM storage fields remain available in:

```text
DicomPixelEncoding
```

for provenance and controlled reverse conversion.

## 7.5 Overflow safety

Dimension multiplication shall use checked arithmetic.

No constructor may rely on unchecked:

```text
width * height * depth
```

using 32-bit integer arithmetic.

Allocation validation shall fail explicitly on overflow or impossible dimensions.

---

# 8. Intensity Semantics

## 8.1 Stored values

Raw stored values shall remain distinct from interpreted values.

For supported DICOM:

```text
interpreted = stored * slope + intercept
```

The conversion core shall preserve the raw stored values unless a documented transformation is explicitly requested.

## 8.2 Generic transform

`RescaleTransform` shall evolve into a generic linear `IntensityTransform`.

It shall represent:

- slope;
- intercept;
- whether scaling was explicitly declared in the source when that distinction matters.

## 8.3 NIfTI scaling

Before NIfTI implementation, the project must explicitly define:

- `scl_slope`;
- `scl_inter`;
- NIfTI no-scaling semantics;
- how generic raw values map into written storage;
- how interpreted values are verified.

The writer shall not blindly copy DICOM rescale metadata into NIfTI fields without a defined mapping.

---

# 9. DICOM-Specific Instance Model

DICOM-specific metadata shall live outside the generic image-volume model.

RC2 distinguishes two semantic views even if the first implementation uses one concrete class:

```text
DicomInstanceMetadata
    identifiers
    supported-profile metadata
    geometry/acquisition metadata
    pixel-encoding metadata
    transfer syntax
    rescale metadata

DecodedDicomSlice
    DicomInstanceMetadata
    decoded stored sample values
```

This distinction supports metadata-first discovery without forcing pixel decoding.

The DICOM metadata representation may contain:

```text
StudyInstanceUID
SeriesInstanceUID
SOPInstanceUID
SOPClassUID
FrameOfReferenceUID
Modality
Rows
Columns
PixelSpacing
ImageOrientationPatient
ImagePositionPatient
AnatomicalOrientationType
EchoNumbers / temporal discriminator where applicable
DicomPixelEncoding
RescaleTransform
TransferSyntaxUID
```

The decoded slice payload contains **numeric sample values after DICOM stored-value interpretation** (masking unused bits and sign-extending where required).

The architecture does **not** promise preservation of:

- the original Pixel Data byte sequence;
- unused high bits outside `BitsStored`;
- file padding;
- byte ordering as a storage artifact.

Conversion fidelity is defined on decoded numeric sample values and supported spatial/intensity semantics.

# 10. DICOM Supported Profile

## 10.1 Object profile

The initial reconstruction profile supports classic conventional single-frame MR Image Storage.

Required constraints include:

```text
Modality = MR
SOP Class UID = MR Image Storage
NumberOfFrames absent OR NumberOfFrames = 1
```

A present `NumberOfFrames > 1` is unsupported.

The architecture must not require the `NumberOfFrames` attribute to be present in classic single-frame MR instances.

## 10.2 Anatomical coordinate convention

The MVP supports the conventional BIPED patient-based coordinate system.

Policy:

```text
AnatomicalOrientationType absent
    -> default human/BIPED patient coordinate convention

AnatomicalOrientationType = BIPED
    -> supported

AnatomicalOrientationType = QUADRUPED
    -> explicit unsupported-profile failure
```

The project does not implement QUADRUPED coordinate interpretation in the MVP.

## 10.3 Frame of Reference

For classic MR Image Storage, the Frame of Reference Module is part of the mandatory MR IOD profile.

Therefore, for the supported MVP:

- `FrameOfReferenceUID` is required and nonblank;
- all selected slices shall have the same `FrameOfReferenceUID`;
- incompatible or missing frame identity is a reconstruction failure.

## 10.4 Transfer syntax profile

Per-instance supported transfer syntaxes are:

- Implicit VR Little Endian;
- Explicit VR Little Endian;
- Explicit VR Big Endian.

Each selected instance must use one of these supported syntaxes.

**All selected instances do not need to use the same transfer syntax** after successful decoding. Transfer syntax is a storage encoding, not a reconstructed-volume semantic.

Compressed/encapsulated or otherwise unsupported syntaxes shall fail explicitly.

## 10.5 Pixel profile

For the supported **classic MR Image Storage input/output subset**, the DICOM-specific pixel profile is:

- `BitsAllocated = 16`;
- `1 <= BitsStored <= 16`;
- `HighBit = BitsStored - 1` for the MVP;
- signed or unsigned integer representation;
- `SamplesPerPixel = 1`;
- `PhotometricInterpretation = MONOCHROME2` as an intentional MVP restriction.

`MONOCHROME1` is valid in DICOM MR but is outside this project's initial supported subset.

Unsupported encodings fail explicitly.

The generic `ImageVolume` / NIfTI scalar model remains broader and may still support:

```text
UINT8
INT8
UINT16
INT16
```

Thus, 8-bit generic scalar support does **not** imply 8-bit classic MR DICOM support.

The generic volume preserves decoded numeric sample values. DICOM storage metadata remains available separately as `DicomPixelEncoding`.

## 10.6 Rescale profile

For the MVP:

- Rescale Slope and Rescale Intercept may both be absent, representing no declared DICOM rescale transform;
- if either is present, both must be valid under the supported profile;
- slope must be finite and nonzero;
- the transform must be consistent across all selected slices because one generic volume-level transform is used.

Per-slice varying rescale transforms are unsupported in M6.

## 10.7 Reconstruction-profile validation versus DICOM IOD conformance

M6 performs **reconstruction-profile validation**, not complete DICOM MR IOD conformance validation.

Its responsibility is to verify the subset of DICOM attributes and relationships required to safely reconstruct the supported 3D MR volume, including identifiers, supported object/profile constraints, geometry, acquisition compatibility, pixel encoding, and stored sample semantics.

Therefore:

> Acceptance by the M6 reconstruction pipeline shall **not** be interpreted as proof that the source object has been exhaustively validated against every mandatory module and attribute of the full MR Image IOD.

M8 has the stronger output obligation. The NIfTI → DICOM writer shall generate objects conforming to the project's documented supported classic MR output profile and shall perform attribute-level validation of the mandatory fields/modules promised by that profile, in addition to successful dcm4che parse-back.

## 10.8 Acquisition ambiguity

A single `SeriesInstanceUID` may still contain images that should not be merged into one 3D volume.

The M6 compatibility policy shall therefore include acquisition discriminators sufficient for the supported conventional MR subset.

At minimum, before M6 acceptance the implementation shall define behavior for:

- `EchoNumbers` when present;
- `TemporalPositionIdentifier` when present;
- inconsistent presence/value of those discriminators;
- other acquisition identifiers only where required by actual fixtures.

The MVP may reject ambiguous higher-dimensional data rather than infer or construct 4D/5D volumes.

`AcquisitionNumber` shall not be treated as a universally reliable sole discriminator.

# 11. Candidate-Series Versus Selected-Series Semantics

A DICOM reconstruction request may contain candidate files from multiple series.

This is valid.

The contract is:

```text
candidate inputs
        |
        v
inspect series identity
        |
        v
selected SeriesInstanceUID
        |
        v
only selected-series instances proceed to reconstruction
```

Instances belonging to other series shall be excluded rather than treated as `MIXED_SERIES`.

`MIXED_SERIES` is relevant only if data considered to belong to the selected reconstruction set is internally inconsistent.

The selected `SeriesInstanceUID` remains mandatory.

Failure states include:

```text
SERIES_NOT_SELECTED
SERIES_NOT_FOUND
```

No arbitrary series may be selected automatically.

---

# 12. Metadata-First DICOM Processing

The current service fully decodes all supplied DICOM inputs before series filtering.

That behavior shall not survive into dataset-scale processing.

The target two-stage behavior is:

```text
Stage 1: metadata inspection
        |
        +-- identify supported object
        +-- study/series identity
        +-- geometry metadata
        +-- acquisition metadata
        +-- transfer syntax/profile
        |
        v
series discovery / explicit selection
        |
Stage 2: pixel decode
        |
        v
selected series only
```

The exact port shape is not fixed yet.

Possible implementation choices include:

- separate metadata and pixel reader ports; or
- one reader with separate metadata/decode operations.

The selected design should be the smallest solution that prevents unnecessary decoding of unselected series.

An unsupported/corrupt file belonging to an unrelated unselected series should not automatically invalidate a valid selected series unless dataset-discovery policy explicitly requires a global-failure mode.

---

# 13. Geometry Contract

Geometry is correctness-critical.

## 13.1 Coordinate and indexing terminology

To avoid DICOM terminology/indexing confusion, RC3 uses the following names:

```text
x = column index in the pixel array
y = row index in the pixel array
z = slice index in the reconstructed stack
```

For DICOM `ImageOrientationPatient (0020,0037)`:

```text
IOP[0..2] = DICOM row direction cosine
          = direction of increasing x / column index
          = xDirection

IOP[3..5] = DICOM column direction cosine
          = direction of increasing y / row index
          = yDirection
```

For DICOM `PixelSpacing (0028,0030)`:

```text
PixelSpacing[0] = row spacing
                = physical step for increasing y

PixelSpacing[1] = column spacing
                = physical step for increasing x
```

This naming is normative for the project and must be reflected in tests and code comments.

## 13.2 DICOM coordinate convention

DICOM reconstruction shall remain in the native BIPED patient-based LPS coordinate system.

No LPS→RAS conversion occurs in the DICOM reader.

The DICOM patient coordinate system is right-handed for the supported BIPED convention.

## 13.3 Voxel-center convention

`ImagePositionPatient` identifies the center of the first transmitted pixel.

For the reconstructed volume:

```text
ImagePositionPatient of the first sorted slice
    = world coordinate of voxel center (x=0, y=0, z=0)
```

NIfTI qform/sform also map voxel indices to voxel coordinates.

Therefore the DICOM→NIfTI mapping shall **not add a ±0.5 voxel shift**.

## 13.4 Orientation validation

Each selected slice orientation shall be checked for:

- exactly six required orientation values;
- finite values;
- nonzero vectors;
- approximately unit vectors;
- xDirection/yDirection orthogonality;
- valid nonzero cross product;
- component-wise orientation compatibility with the reference orientation.

Simultaneously reversing both in-plane axes shall not be silently treated as equivalent because it changes voxel-index orientation.

Default direction-cosine tolerance:

```text
1e-4
```

`GeometryValidationPolicy` shall reject:

- negative tolerances;
- NaN;
- infinity.

## 13.5 Deterministic reference orientation

Reference orientation shall be independent of arbitrary request ordering.

For M6, use a deterministic selected-instance ordering before choosing the reference. A practical default is lexical `SOPInstanceUID` ordering after UID validation.

The chosen rule shall be documented and covered by permutation-invariance tests.

## 13.6 One common projection normal

After orientation validation:

```text
referenceNormal = normalize(cross(referenceXDirection, referenceYDirection))
```

Every selected `ImagePositionPatient` shall be projected onto this **single reference normal**.

Per-slice normals shall not be used as independent projection axes for ordering.

## 13.7 In-plane consistency

Relative to the reference basis, each selected position shall be decomposed into:

- x in-plane displacement;
- y in-plane displacement;
- z projection along the common normal.

For the supported regular-stack profile, in-plane displacement beyond the configured position tolerance is rejected.

Default position tolerance:

```text
1e-3 mm
```

## 13.8 Canonical slice direction and order

Slices shall be sorted by **increasing projection** on `referenceNormal`.

Then:

```text
sliceDirection = referenceNormal
sliceSpacing   > 0
origin         = ImagePositionPatient of first sorted slice
```

Primary ordering shall not use:

- filename;
- directory order;
- `InstanceNumber`.

Tests shall include contradictory `InstanceNumber` values.

## 13.9 Duplicate-position detection

Projected positions within duplicate-position tolerance shall be rejected.

Default:

```text
1e-3 mm
```

## 13.10 Regular spacing and whole-grid residual

Adjacent-gap checking alone is insufficient.

For depth >= 2, the validator shall:

1. sort projected positions;
2. determine a deterministic proposed positive spacing;
3. define the expected grid from the first sorted position;
4. compare every actual projected position with its expected grid position;
5. reject accumulated residuals beyond tolerance.

The method used to estimate proposed spacing (for example, first gap or a robust deterministic estimate) shall be documented and tested. Whatever method is chosen, the final whole-grid residual check is authoritative.

Default spacing/position tolerance:

```text
1e-3 mm
```

## 13.11 Final-grid geometry

The `ImageVolume` geometry shall describe the same regular grid that passed validation.

The software shall not validate one geometry and publish another.

## 13.12 Model invariants

`SliceGeometry`, `VolumeGeometry`, and related geometry values shall enforce:

- finite coordinates;
- finite directions;
- valid normalized/orthogonal basis within policy where required;
- finite positive in-plane spacing;
- finite positive slice spacing when defined;
- valid dimensions;
- valid coordinate-system identity.

Trusted domain objects shall not depend solely on adapter-side validation.

# 14. Single-Slice Geometry Policy

Single-slice data does not define physical inter-slice spacing from neighboring positions.

Therefore:

- zero shall not be silently treated as a physical z-spacing;
- unknown inter-slice spacing shall be represented explicitly until export policy resolves it.

Before NIfTI writing, the project shall choose one documented policy.

Possible supported sources include:

- valid `SpacingBetweenSlices`;
- valid `SliceThickness` only under a documented assumption;
- explicit export default recorded in provenance.

Any fallback shall be:

- deterministic;
- documented;
- represented in provenance;
- covered by tests.

---

# 15. Affine Architecture

Before NIfTI writing, introduce one small immutable affine value object:

```text
AffineMatrix4
```

It represents:

```text
integer voxel index (x,y,z) -> world coordinate of that voxel center
```

No general-purpose matrix dependency is required.

## 15.1 DICOM LPS affine

Using the RC2 indexing terminology:

```text
xDirection = IOP[0..2]   // DICOM row direction cosine
yDirection = IOP[3..5]   // DICOM column direction cosine

columnSpacing = PixelSpacing[1]
rowSpacing    = PixelSpacing[0]
sliceDirection = common reference normal
sliceSpacing   = validated positive stack spacing
origin         = first sorted ImagePositionPatient
```

Then the homogeneous affine columns are:

```text
A_LPS =
[ xDirection * columnSpacing,
  yDirection * rowSpacing,
  sliceDirection * sliceSpacing,
  origin ]
```

with homogeneous final row:

```text
0 0 0 1
```

This formula maps `(x,y,z) = (0,0,0)` directly to the DICOM voxel-center origin; no half-voxel translation is added.

## 15.2 Authoritative representation

Structured geometry fields are authoritative during M6 validation.

The affine is derived from the validated structured geometry.

The project shall not store two independently editable geometry descriptions.

Tests shall verify that affine point transformation agrees with the structured representation.

## 15.3 LPS to RAS

For unchanged voxel indexing:

```text
A_RAS = diag(-1, -1, 1, 1) * A_LPS
```

This changes world-coordinate basis only; it does not reorder voxel data.

The DICOM adapter remains LPS-native.

## 15.4 Handedness / qfac

For the canonical M6 ordering, the basis is expected to remain orthogonal and right-handed after LPS→RAS.

The NIfTI writer shall nevertheless determine handedness from the final 3×3 affine rather than hard-code `qfac`.

If the qform representation cannot encode the validated affine without loss, the writer shall not fabricate a qform.

# 16. M6 Reconstruction Responsibilities

The existing `DefaultDicomSeriesService` contains substantial reusable behavior.

It shall not be discarded.

It shall eventually be decomposed into testable responsibilities.

Conceptually:

```text
ReconstructDicomVolumeUseCase
        |
        +-- metadata/pixel reader boundary
        +-- selected-series compatibility validation
        +-- geometry validation/order
        +-- volume assembly
        +-- provenance/result creation
```

Concrete helper classes are sufficient.

Interfaces are not mandatory for internal deterministic algorithms.

## 16.1 Compatibility validation

Selected-series validation shall cover at least:

- Study Instance UID;
- selected Series Instance UID;
- SOP uniqueness;
- SOP Class;
- Modality;
- Frame of Reference;
- anatomical orientation convention;
- Rows;
- Columns;
- Pixel Spacing;
- Image Orientation Patient;
- Image Position Patient;
- DICOM pixel encoding;
- transfer syntax;
- rescale semantics;
- supported acquisition compatibility.

## 16.2 Volume assembly

Assembly shall:

- consume physically sorted slices;
- use checked dimension arithmetic;
- preserve stored integer voxel values;
- construct generic scalar type;
- preserve generic intensity transform;
- construct validated geometry;
- produce one `ImageVolume`.

No file parsing belongs in the assembler.

---

# 17. Application Ports

Current external ports shall move out of the generic domain and into the application boundary.

Immediate:

```text
FormatProbe
DicomInstanceReader
```

Future ports shall be added only with their first real use case.

Expected later capabilities include:

```text
NiftiVolumeReader
NiftiVolumeWriter
DicomSeriesWriter
DatasetScanner
ManifestWriter
```

The architecture does not require all of these to exist immediately.

---

# 18. Error Architecture

Errors exposed at the application boundary shall be stable.

Top-level categories should distinguish:

```text
INPUT
FORMAT
PARSE
RESOURCE_LIMIT
UNSUPPORTED_PROFILE
SERIES_SELECTION
SERIES_COMPATIBILITY
GEOMETRY
PIXEL_DATA
OUTPUT
VALIDATION
INTERNAL
```

Important principle:

> An unexpected internal programming/runtime defect must not automatically be reported as `DICOM_PARSE_FAILED`.

Technical adapter exceptions should be translated deliberately.

---

# 19. Format Detection Contract

Detection is identification from bounded structural evidence.

Detection is **not** full semantic loading and shall not decode image pixels.

## 19.1 File size versus read budget

A bounded inspection budget is a limit on bytes inspected, not a maximum supported file size.

Large valid DICOM or NIfTI files must not be rejected merely because their total file size exceeds the detection budget.

## 19.2 Probe continuation

A probe that lacks sufficient evidence shall not prevent unrelated probes from running.

The detection model shall distinguish at least the semantics of:

```text
NOT_THIS_FORMAT
INSUFFICIENT_EVIDENCE
RESOURCE_BUDGET
CORRUPT
UNSUPPORTED
```

The exact enum names may differ.

`CORRUPT` shall mean evidence of structural invalidity, not merely “larger than our budget.”

## 19.3 DICOM detection

The `DICM` prefix at byte offset 128 is a strong Part 10 signal but is not the sole recognition mechanism.

Parser-assisted fallback may inspect metadata up to (but not decode) Pixel Data.

## 19.4 NIfTI detection

For NIfTI-1 single-file detection:

- header size;
- magic;
- dimensions;
- datatype/bitpix consistency;

shall be validated using correct specification mappings before a file is called valid.

Existing unused helper methods shall be corrected before activation.

## 19.5 GZIP

For `.nii.gz` candidates, detection shall decompress only enough bytes to inspect the required NIfTI header evidence.

A decompression read budget shall not require draining the full image stream and shall not be classified as corruption.

Full gzip integrity and payload-size verification belongs to NIfTI loading, not format detection.

# 20. Provenance Architecture

Provenance is application-owned.

RC3 requires reproducibility identities stronger than path hashing.

## 20.1 Source identity

For a conversion/reconstruction operation, source identity shall be based on selected source **content**.

Recommended MVP strategy:

1. compute a streaming SHA-256 digest for each selected source file;
2. associate it with a stable instance identity such as `SOPInstanceUID`;
3. sort the `(stableIdentity, contentDigest)` entries deterministically;
4. hash the canonical ordered representation to produce the operation source identity.

Multiplicity shall be preserved; identical duplicate inputs must not disappear merely because their digests match.

This identity is designed to remain stable under:

- candidate input reordering;
- source file relocation;

while changing when selected source content changes.

Dataset discovery need not hash all files eagerly; content hashing may occur for selected conversion inputs.

## 20.2 Configuration identity

Configuration identity shall cover at least:

- selected `SeriesInstanceUID`;
- geometry-validation tolerances;
- supported-profile settings;
- conversion/scaling policy;
- software options that can change output.

Canonical serialization shall be deterministic before hashing.

## 20.3 Software identity

Software identity shall include:

- application version;
- relevant adapter/library versions.

Versions shall be obtained from one controlled source rather than duplicated hard-coded literals.

## 20.4 Operation record

A minimal provenance operation record shall contain:

```text
schemaVersion
operation
completedAt
outcome
sourceIdentity
configurationIdentity
softwareIdentity
inputCount
selectedCount
rejectedCount
selectedSeriesIdentity (privacy-safe)
geometryDecision
pixelDecision
intensityTransform
diagnostics
outputIdentity (when applicable)
```

Geometry and pixel decisions should be structured values rather than semicolon-delimited strings.

## 20.5 Deterministic testing

Use an injected `Clock` or equivalent controlled time source for deterministic tests.

## 20.6 Privacy

Default provenance excludes:

- Patient Name;
- Patient ID;
- arbitrary free-text PHI-bearing DICOM fields.

Cryptographic content digests do not themselves authorize copying identifying metadata into the manifest.

# 21. Dataset Discovery Architecture

Dataset discovery is distinct from volume reconstruction.

## 21.1 Traversal

Recursive discovery shall define:

- deterministic traversal ordering;
- unreadable-path behavior;
- permission failures;
- symbolic link policy;
- cycle handling;
- duplicate-file handling;
- partial-success behavior.

## 21.2 Lightweight DICOM discovery

Discovery shall use metadata inspection without decoding full pixel payloads.

It may record:

- source path;
- Study Instance UID;
- Series Instance UID;
- SOP Class;
- Modality;
- geometry/acquisition summary;
- supported/unsupported status.

## 21.3 Series discovery versus reconstruction

Discovery identifies candidate series.

Reconstruction validates one explicitly selected series.

These are different use cases.

---

# 22. Directory Normalization and Safe Output

Dataset organization must not blindly flatten inputs.

## 22.1 Planning

The organizer shall first produce a deterministic destination plan.

## 22.2 Collision policy

No file shall be silently overwritten.

Collision handling shall use deterministic identifiers/suffixes.

## 22.3 Safe publication

For write/copy/move workflows:

1. prepare output in a staging location where appropriate;
2. validate successful output creation;
3. publish/finalize destination;
4. only then remove a source if move semantics are explicitly selected.

Source data shall not be deleted before destination success is established.

## 22.4 Partial failure

The workflow shall preserve successful work while reporting failed/skipped entries in the manifest.

Retry semantics shall be deterministic.

## 22.5 Copy versus move

The MVP should default to copy semantics unless the final requirement explicitly demands move.

If move is supported, it must be explicit.

---

# 23. NIfTI MVP Contract

The first NIfTI implementation shall be deliberately narrow.

## 23.1 Format and dimensionality

MVP:

```text
NIfTI-1
single-file .nii
gzip-wrapped .nii.gz
3D single-volume images only
```

NIfTI-2 detection may remain if already implemented, but NIfTI-2 conversion is outside the MVP unless separately approved.

Inputs with active higher dimensions (`dim[0] > 3` with dimension sizes > 1) are unsupported for the initial reverse-conversion path.

## 23.2 Initial scalar types

Initial supported scalar mapping:

```text
UINT8
INT8
UINT16
INT16
```

Other NIfTI datatypes are explicit unsupported-profile cases until storage and validation are extended.

## 23.3 Units

The MVP spatial world geometry is expressed in millimetres.

When reading NIfTI:

- millimetres are accepted directly;
- other declared spatial units require explicit conversion or rejection;
- unknown units are not silently assumed to be millimetres for NIfTI→DICOM.

## 23.4 Data scaling policy

NIfTI scaling semantics are:

```text
if scl_slope != 0:
    interpreted = stored * scl_slope + scl_inter
else:
    no scaling is applied
```

For DICOM→NIfTI MVP:

- the NIfTI data array stores the decoded DICOM stored sample values unchanged numerically;
- if DICOM declares a valid non-identity rescale transform, encode that linear transform using `scl_slope/scl_inter`;
- if DICOM declares no rescale transform or the effective transform is identity, write the NIfTI no-scaling convention (`scl_slope = 0`, `scl_inter = 0`);
- provenance records whether scaling was source-declared versus absent/identity.

For NIfTI→DICOM:

- `scl_slope = 0` means no declared NIfTI scaling;
- nonzero finite slope/intercept are represented as the generic `IntensityTransform`;
- unsupported transforms or nonfinite values fail explicitly.

## 23.5 Spatial-transform read precedence

For NIfTI→DICOM:

1. if `sform_code > 0`, the sform is the preferred full affine;
2. else if `qform_code > 0`, use the qform;
3. else trusted world geometry is unavailable and reverse conversion must fail unless explicit geometry is supplied.

If both qform and sform are present and differ beyond configured affine tolerance, the input is spatially ambiguous for the MVP and shall fail rather than silently selecting one.

## 23.6 Spatial-transform write policy

For a validated DICOM regular orthogonal stack:

- construct one `A_RAS`;
- write `sform_code = NIFTI_XFORM_SCANNER_ANAT` with `A_RAS`;
- when the same affine is representable by qform without loss, also write `qform_code = NIFTI_XFORM_SCANNER_ANAT` describing the same transform;
- populate `pixdim` consistently with voxel spacing;
- compute qfac from affine handedness rather than assuming it.

## 23.7 Shear / non-orthogonal geometry

The initial DICOM→NIfTI path produces an orthogonal regular stack.

For NIfTI→DICOM MVP:

- an affine containing material shear or non-orthogonal voxel axes is unsupported;
- the software shall not silently orthogonalize or resample it;
- future resampling support, if ever added, requires a new architecture decision.

## 23.8 Missing transforms

A NIfTI file with neither usable sform nor qform does not contain trusted scanner/world geometry for this project.

Reverse DICOM conversion shall reject it unless an explicit external geometry source is supplied.

# 24. DICOM → NIfTI Pipeline

```text
candidate DICOM inputs
        |
        v
metadata inspection
        |
        v
selected SeriesInstanceUID
        |
        v
selected-series pixel decode
        |
        v
compatibility validation
        |
        v
common-normal geometry validation
        |
        v
final-grid validation
        |
        v
ImageVolume in DICOM LPS
        |
        v
derive A_LPS
        |
        v
LPS -> RAS
        |
        v
NIfTI-1 writer
        |
        v
output + provenance + validation report
```

## 24.1 DICOM → NIfTI invariants

For the integer-preserving MVP path:

```text
shape_out == shape_reconstructed
decoded_stored_samples_out == decoded_stored_samples_reconstructed
```

The equality above concerns **numeric sample values**, not original DICOM Pixel Data bytes.

If an intensity transform exists, interpreted-value equivalence shall also be checked using the declared scaling semantics.

Geometry shall match within defined tolerances.

No interpolation, reorientation of the voxel array, or resampling occurs.

## 24.2 Independent interoperability validation

The first NIfTI writer milestone shall not rely only on the project’s own reader for validation.

At least one independent validation path shall be used, for example:

- a trusted external library/tool;
- a reference test fixture with independently known header/affine interpretation.

This guards against reader/writer sharing the same bug.

---

# 25. NIfTI → DICOM Pipeline

```text
NIfTI input
     |
     v
NIfTI reader
     |
     v
validated ImageVolume
     |
     v
RAS/world -> DICOM LPS mapping
     |
     v
supported MR metadata template
     |
     +-- source/provenance metadata
     +-- user-supplied values
     +-- generated values
     +-- documented defaults
     |
     v
new Study/Series/SOP UIDs
     |
     v
DICOM MR series writer
     |
     v
attribute-level validation + parse-back validation
```

## 25.1 Metadata provenance classes

Generated attributes shall be classifiable as:

```text
SOURCE
PROVENANCE_MANIFEST
USER_SUPPLIED
GENERATED
DEFAULTED
```

## 25.2 Validity versus parseability

Successfully reopening output with dcm4che is necessary but not sufficient.

Before M8 implementation, define a documented classic MR Image Storage output profile containing the mandatory modules/attributes the project promises to generate, including at least valid patient/study placeholders or supplied values, series identity, Frame of Reference, equipment/general acquisition fields required by the chosen profile, Image Plane, Image Pixel, MR Image, and SOP Common information.

The reverse converter shall validate its generated output against that project profile in addition to dcm4che parse-back.

The metadata template shall explicitly classify every required field as source/provenance-derived, user-supplied, generated, or defaulted.

---

# 26. Testing Architecture

Testing is part of the implementation contract.

## 26.1 Current test status

Current source contains 35 `@Test` methods across 9 classes.

At RC3 creation time:

```text
fresh tests executed = 0
```

because production compilation fails first.

Historical passing Surefire output shall not be treated as current acceptance evidence.

## 26.2 Existing M6 tests to preserve

Existing synthetic DICOM tests provide useful coverage for:

- unsigned values;
- signed 12-bit values;
- explicit series selection;
- unknown series;
- selected series among multiple candidates;
- mixed study;
- duplicate SOP UID;
- dimension mismatch;
- spacing/orientation mismatch;
- duplicate positions;
- irregular spacing;
- missing spatial metadata;
- unsupported object/pixel profile;
- three supported transfer syntaxes;
- unsupported compressed/deflated syntax.

These tests should be preserved and refactored rather than discarded.

## 26.3 Required new M6 tests

Add tests for:

- oblique stacks;
- nonzero origins;
- non-square images;
- unequal row/column spacing;
- contradictory `InstanceNumber`;
- shuffled-input permutation invariance;
- common-normal projection;
- cumulative grid residual;
- tolerance boundaries;
- NaN/infinite geometry;
- invalid `GeometryValidationPolicy`;
- `FrameOfReferenceUID` mismatch;
- explicit QUADRUPED rejection;
- acquisition ambiguity;
- 8-bit signed/unsigned samples;
- unused high bits;
- truncated/missing pixel payload;
- immutable-model dimension overflow;
- single-slice spacing policy;
- complete voxel-array comparisons.

## 26.4 Detection tests

Add:

- valid DICOM > 1 MiB;
- valid NIfTI > 1 MiB;
- gzip volume exceeding old 16 MiB decompression threshold;
- insufficient-evidence continuation to later probes;
- valid/invalid NIfTI datatype-bitpix combinations;
- corrupt versus budget-limited distinction.

## 26.5 Provenance tests

Add:

- same selected content in different input order -> same source identity;
- same content at relocated paths -> same source identity where intended;
- changed content -> changed source identity;
- selected series change -> changed configuration identity;
- validation policy change -> changed configuration identity;
- privacy-sensitive fields absent by default;
- deterministic time through injected clock.

## 26.6 Architecture tests

After AR-1, tests shall verify:

- no dcm4che dependency from domain;
- no dcm4che type in application public API;
- no Swing/FlatLaf dependency from domain/application core;
- presentation does not depend on infrastructure;
- moved ports reside in application;
- generic `VoxelData` does not expose DICOM encoding;
- DICOM-specific metadata does not leak into generic `ImageVolume`.

Architecture tests shall not claim repository-wide enforcement if they only inspect selected reflected signatures.

---

# 27. Migration Table

| Current component | RC3 decision | Notes |
|---|---|---|
| `Main` | KEEP | Minimal bootstrap |
| `Volume` | REMOVE/EVOLVE | Replace empty marker with meaningful volume API |
| `NativeVolume` | EVOLVE | Becomes the single generic `ImageVolume` concept |
| `VoxelData` | MODIFY | Generic scalar type, no DICOM encoding |
| `ImmutableVoxelData` | KEEP/MODIFY | Preserve indexing/storage; add checked arithmetic |
| `PixelEncoding` | SPLIT/MOVE | DICOM-specific encoding metadata |
| `PixelValueType` | EVOLVE | Generic `ScalarType` |
| `RescaleTransform` | EVOLVE | Generic `IntensityTransform` |
| `CoordinateSystem` | MODIFY | Explicit supported LPS/RAS conventions |
| `SliceGeometry` | KEEP/MODIFY | Stronger finite/consistency invariants |
| `VolumeGeometry` | KEEP/MODIFY | Stronger invariants + affine consistency |
| `DicomInstance` | MOVE/MODIFY | DICOM-specific application model |
| `SeriesIdentity` | REMOVE unless real consumer appears | Avoid duplicate selection models |
| `GeometryValidationPolicy` | KEEP/MODIFY | Validate tolerances |
| `DicomProcessingError` | MOVE | Application DICOM error model |
| `DicomProcessingException` | MOVE/MODIFY | Controlled translation only |
| `domain.port.FormatProbe` | MOVE | `application.port.out` |
| `domain.port.DicomInstanceReader` | MOVE | `application.port.out` |
| `FormatDetectionService` | KEEP/MODIFY | Fix direct I/O/budget semantics |
| `DicomSeriesRequest` | KEEP/MODIFY | Candidate inputs + explicit selected series |
| `DicomProcessingResult` | FIX NOW, then MODIFY | Must expose usable volume contract |
| `DicomSeriesService` | KEEP | Rename optional, not mandatory |
| `DefaultDicomSeriesService` | PRESERVE/SPLIT LATER | Refactor only after baseline is green |
| `ProvenanceRecord` | FIX NOW / REDESIGN | Application-owned |
| `DicomFormatProbe` | KEEP/MODIFY | Replace size rejection with read budget |
| `NiftiFormatProbe` | KEEP/MODIFY | Correct validation mapping/budget semantics |
| `Dcm4cheInstanceReader` | KEEP/MODIFY | Preserve substantial implementation |
| `DependencyContainer` | REMOVE LATER | Unused service locator |
| `ProcessingApplication` | REMOVE LATER | Placeholder |
| `ProcessingRequest` | REMOVE LATER | Placeholder |
| `ProcessingResult` | REMOVE LATER | Placeholder |
| `PresentationBoundary` | REMOVE LATER | Obsolete placeholder |
| `InputSource` | MOVE/MODIFY | Application resource concept |
| `OutputTarget` | MOVE/MODIFY | Application resource concept |
| `DetectionDiagnostic` | KEEP in AR-0; MOVE in AR-1 | Target: application detection model |
| `DetectionOutcome` | KEEP in AR-0; MOVE in AR-1 | Detection is an application concern, not image domain |
| `DetectionResult` | KEEP/MODIFY then MOVE | Clarify legal states; target application detection model |
| `ImagingFormat` | REMOVE if unused | Avoid duplicate enum |
| `AffineMatrix4` | ADD before NIfTI writing | Small immutable value object |
| dataset discovery classes | ADD with first use case | Do not scaffold early |
| NIfTI reader/writer ports | ADD with M7/M8 | Do not scaffold early |
| DICOM writer port | ADD with M8 | Do not scaffold early |

---

# 28. AR-0 — Build Repair Only

AR-0 restores a coherent baseline.

It is **not** an architecture refactor.

## 28.1 AR-0A — Provenance mismatch

Reconcile:

```text
ProvenanceRecord
DefaultDicomSeriesService
DicomProcessingResult
```

using one coherent temporary/current schema.

Do not silently discard fields.

## 28.2 AR-0B — Volume result mismatch

Fix the result API so the current M6 tests can compile.

Preferred smallest repair:

```text
DicomProcessingResult.volume()
    -> NativeVolume
```

temporarily, if that is the least invasive repair.

The later AR-1 refactor may evolve `NativeVolume` to `ImageVolume`.

Do not perform the generic-volume refactor inside AR-0.

## 28.3 AR-0C — Fresh verification

Run:

```bash
mvn clean test
```

Then record:

- Java version;
- Maven version;
- Git HEAD;
- production compile status;
- test count;
- failures;
- errors;
- skips.

Fix only newly exposed integration failures required to establish a coherent pre-refactor baseline.

No new feature implementation.

## 28.4 AR-0 exit criteria

AR-0 is complete when:

- production compiles;
- tests compile;
- fresh test suite runs;
- baseline outcome is recorded;
- no stale report is presented as current evidence;
- a clean checkpoint is created.

---

# 29. Correctness Hardening Before Structural Refactor

After AR-0 and before package churn:

1. add common-normal projection regression test;
2. add final-grid residual regression test;
3. add checked voxel-allocation test;
4. add >1 MiB detection regression tests;
5. add provenance identity tests;
6. add frame/orientation-profile tests;
7. implement the smallest correctness fixes needed for those tests.

Rationale:

> Separate correctness changes from structural refactoring so failures remain diagnosable.

---

# 30. AR-1 — Architecture Refactor

Only after a verified baseline and correctness hardening:

1. move external ports to application;
2. move DICOM-specific models out of generic domain;
3. separate DICOM pixel encoding from generic scalar representation;
4. evolve `NativeVolume` to one `ImageVolume` concept;
5. strengthen domain invariants;
6. split M6 service responsibilities where useful;
7. remove unused generic placeholders/container;
8. update architecture tests.

No NIfTI conversion shall be implemented during the refactor.

---

# 31. M6 Completion Contract

M6 is complete only when all of the following are true.

## Build

1. clean checkout compiles;
2. `mvn clean test` passes;
3. current test results are recorded.

## Series behavior

4. selected `SeriesInstanceUID` is mandatory;
5. unknown series fails explicitly;
6. requests may contain multiple candidate series;
7. only selected-series instances are reconstructed;
8. unrelated unselected-series corruption follows the documented discovery/selection policy rather than automatically poisoning selected reconstruction.

## DICOM profile

9. conventional single-frame MR only;
10. BIPED convention only;
11. supported transfer-syntax allowlist enforced;
12. nonblank `FrameOfReferenceUID` required and compatibility enforced;
13. supported acquisition compatibility enforced;
14. duplicate SOP UIDs rejected.

## Geometry

15. finite valid orientation;
16. unit/orthogonal direction validation;
17. deterministic reference orientation;
18. one common slice normal;
19. all positions projected on that common normal;
20. filename/InstanceNumber not used as primary order;
21. duplicate physical positions rejected;
22. final regular grid validated against every accepted slice;
23. in-plane displacement checked;
24. single-slice policy explicit;
25. trusted geometry objects reject invalid internal state.

## Voxels

26. signed/unsigned decoding correct;
27. supported 8/16-bit profile correct;
28. decoded stored numeric sample values preserved;
29. generic scalar representation independent of DICOM storage metadata;
30. dimension arithmetic overflow-safe;
31. intensity transform explicit.

## Architecture

32. output is one trusted format-neutral `ImageVolume`;
33. dcm4che remains in infrastructure;
34. application/domain public API exposes no dcm4che types;
35. architecture tests reflect the actual revised package rules.

## Provenance

36. provenance schema coherent;
37. source/configuration/software identities defined;
38. privacy-safe defaults;
39. diagnostics structured and stable.

---

# 32. M7 — DICOM → NIfTI Plan

M7 begins only after M6 is frozen.

Order:

1. finalize NIfTI MVP contract;
2. add `AffineMatrix4`;
3. implement and test LPS affine;
4. implement and test LPS→RAS conversion;
5. define qform/sform read/write rules;
6. define NIfTI scaling rules;
7. implement NIfTI-1 writer;
8. implement `.nii.gz`;
9. validate with an independent implementation;
10. produce conversion provenance/report.

A CLI or test harness is sufficient for first demonstration.

GUI is not required.

---

# 33. Dataset Workflow Plan

After the first DICOM→NIfTI path works:

1. recursive scan;
2. bounded format detection;
3. lightweight DICOM metadata discovery;
4. deterministic series grouping;
5. safe dataset-layout planning;
6. collision-safe copy/publication;
7. manifest generation;
8. per-series conversion;
9. partial-failure reporting.

---

# 34. M8 — NIfTI → DICOM Plan

Before implementation, define:

- supported NIfTI geometry subset;
- supported scalar subset;
- scaling behavior;
- metadata template;
- required MR IOD attribute subset;
- generated/defaulted field policy;
- UID generation;
- provenance classification.

Then implement:

```text
NIfTI
 -> ImageVolume
 -> DICOM LPS geometry
 -> metadata template
 -> DICOM series
 -> attribute validation
 -> dcm4che parse-back
```

---

# 35. Round-Trip Validation

For supported fixtures:

```text
DICOM
 -> NIfTI
 -> DICOM
```

Validate:

- voxel array;
- dimensions;
- spacing;
- orientation/world geometry;
- supported scalar semantics;
- DICOM-series consistency;
- provenance;
- documented metadata loss.

Do not claim arbitrary metadata identity.

---

# 36. Optional Intensity-Normalization Milestone

Only after bidirectional conversion and fidelity validation are stable:

- decide whether intensity normalization remains necessary for the course deliverable;
- choose one explicit strategy;
- implement it as an optional processing use case;
- never make it an implicit part of conversion.

Existing planning documents that name normalization shall be updated only after the scope decision is formally recorded.

---

# 37. GUI Plan

GUI work resumes only after:

1. M6 is accepted;
2. DICOM→NIfTI works;
3. core application use cases are stable.

The GUI shall orchestrate existing services rather than introduce new processing logic.

---

# 38. Immediate Development Sequence

The authoritative next sequence is:

```text
RC3 architecture review
        |
        v
final read-only Codex check
        |
        v
approve Architecture Revision 1.0
        |
        v
AR-0A provenance repair
        |
        v
AR-0B result-volume repair
        |
        v
fresh mvn clean test
        |
        v
verified baseline checkpoint
        |
        v
correctness regression tests
        |
        v
geometry / detection / provenance fixes
        |
        v
AR-1 structural refactor
        |
        v
M6 acceptance
        |
        v
M7 affine + DICOM->NIfTI
```

Do not jump directly to NIfTI implementation.

---

# 39. Final Read-Only Audit Contract

Before approving RC3, Codex shall perform a narrow follow-up audit.

It shall **not** repeat the entire first audit.

Required task:

> Check whether all BLOCKER and REQUIRED corrections from the previous live-repository audit, plus the standards-critical corrections recorded in RC2, have been correctly incorporated into `ARCHITECTURE-REVISION-1.0-RC3.md`. Do not modify files. Report only remaining contradictions, omissions, overengineering, or statements inconsistent with the current live repository. Classify each as BLOCKER, REQUIRED, OPTIONAL, or NONE. End with exactly one verdict: `READY TO APPROVE` or `REVISE AGAIN`.

The follow-up should specifically verify:

- both AR-0 blockers;
- candidate-series semantics;
- metadata-first selected-series decoding;
- deterministic common-normal geometry;
- cumulative/final-grid residual validation;
- frame/anatomical orientation scope;
- scalar subset;
- overflow;
- NIfTI scaling/affine contract;
- detection budget behavior;
- provenance identities;
- reverse-DICOM validity;
- dataset failure/safe-output semantics;
- normalization milestone reconciliation;
- non-speculative class creation;
- DICOM IOP / PixelSpacing axis convention;
- voxel-center/no-half-voxel convention;
- classic MR `NumberOfFrames` absent-or-1 rule;
- mandatory MR `FrameOfReferenceUID`;
- per-instance transfer syntax support without unnecessary same-syntax requirement;
- decoded-sample versus original-byte fidelity;
- concrete NIfTI scaling and qform/sform policies.

---

# 40. Promotion to Approved Architecture

If the final Codex check reports no unresolved BLOCKER or REQUIRED issue, rename/promote this document to:

```text
ARCHITECTURE-REVISION-1.0.md
Version: 1.0.0
Status: APPROVED
```

Then treat it as the authoritative architecture contract for subsequent implementation.

---

# 41. Standards-Critical Conventions Used by RC2

The following conventions are intentionally fixed because small mistakes here can produce spatially incorrect medical images.

## 41.1 DICOM Image Plane convention

RC3 follows DICOM PS3.3 Image Plane semantics:

- `ImageOrientationPatient` gives direction cosines of the first row followed by the first column;
- the first triplet points along increasing pixel-column index;
- the second triplet points along increasing pixel-row index;
- `PixelSpacing` is encoded as row spacing followed by column spacing;
- `ImagePositionPatient` is the center of the first transmitted pixel.

## 41.2 Frame of Reference

For classic MR Image Storage, RC3 treats `FrameOfReferenceUID` as required and constant within the reconstructed stack, consistent with the mandatory Frame of Reference module of the MR Image IOD.

## 41.3 BIPED default

If `AnatomicalOrientationType` is absent, RC3 uses the default human/BIPED patient-based coordinate convention. Explicit QUADRUPED input is outside the MVP.

## 41.4 NIfTI world axes

RC3 uses NIfTI scanner anatomical coordinates in RAS+ orientation for DICOM-derived world geometry:

```text
+x = Right
+y = Anterior
+z = Superior
```

## 41.5 NIfTI scaling

RC3 follows the NIfTI-1 convention that `scl_slope = 0` means scaling is not applied; otherwise interpreted values are `stored * scl_slope + scl_inter`.

These conventions shall be reflected directly in tests.

---

# 42. Guiding Principle

> **First reconstruct one trustworthy, format-neutral MRI volume with mathematically valid geometry and explicit voxel semantics. Then build conversion, dataset organization, provenance, validation, and UI around that stable core.**

This principle governs the remainder of the project.
