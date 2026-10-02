# Architecture Decision Records

## ADR-001 - Java 21

Decision:
Use Java 21 LTS.

Reason:
Stable LTS baseline and project compatibility.

Status:
Accepted

---

## ADR-008 - Bounded Detection Budget

Decision:
Format detection performs bounded inspection. DICOM inputs larger than 1 MiB return `UNKNOWN` with `INPUT_TOO_LARGE`.

Reason:
Detection must avoid traversing large DICOM objects or series. This is a resource budget, not a statement that large DICOM input is invalid or unsupported; full parsing is deferred to M6 workflows.

Status:
Accepted

---

## ADR-006 - Content-Based Format Detection

Decision:
Detect supported formats from bounded content inspection; treat filename extensions as hints and report mismatches separately.

Reason:
Extensions can be missing or incorrect, while format recognition must be based on file structure.

Status:
Accepted

---

## ADR-007 - Standard Library NIfTI Detection

Decision:
Use Java standard-library byte and gzip APIs for M5 NIfTI header recognition rather than adding a NIfTI dependency.

Reason:
M5 requires header recognition only. A complete NIfTI reader is outside scope and belongs to M7.

Status:
Accepted

---

## ADR-004 - Layered Architecture

Decision:
Use the dependency direction `presentation -> application -> domain`, with infrastructure depending on domain contracts.

Reason:
Separates user interaction, use-case orchestration, domain concepts, and external adapters so processing remains testable and independent of UI frameworks.

Status:
Accepted

---

## ADR-005 - Spatial Coordinate Preservation

Decision:
Every future volume-processing workflow must preserve spatial coordinate and orientation information explicitly.

Reason:
Spatial meaning is essential to MRI volume correctness and must not be lost during loading, normalization, or conversion.

Status:
Accepted requirement; coordinate convention unresolved

---

## ADR-002 - dcm4che

Decision:
Use dcm4che for DICOM handling.

Reason:
Provides established DICOM information-object and file-handling capabilities rather than implementing DICOM parsing ourselves.

Status:
Accepted

---

## ADR-003 - Swing + FlatLaf

Decision:
Use Swing with FlatLaf for the desktop UI.

Reason:
Matches the project requirements while providing a modern cross-platform Swing appearance.

Status:
Accepted

---

## ADR-009 - Format-Neutral ImageVolume

Decision:
Use `ImageVolume` as the authoritative in-memory 3D image representation shared by conversion workflows.

Contract:
- `VolumeGeometry geometry`
- `VoxelData voxels`
- `IntensityTransform intensityTransform`

Invariants:
- all components are non-null;
- geometry and voxel dimensions match;
- x is column index, y is row index, z is slice index;
- voxel values remain stored numeric values;
- spatial geometry is explicit;
- no DICOM or NIfTI implementation type is exposed.

`NativeVolume` is superseded by `ImageVolume` rather than retained as a parallel aggregate.

Reason:
DICOM-to-NIfTI conversion requires one format-neutral volume boundary. The previous aggregate was structurally suitable but its voxel contract leaked DICOM `PixelEncoding`.

Status:
Accepted

---

## ADR-010 - Generic Scalar Type

Decision:
The initial generic scalar model supports `UINT8`, `INT8`, `UINT16`, and `INT16`.
`VoxelData` exposes `ScalarType` instead of DICOM `PixelEncoding`. The initial backing implementation continues to store decoded numeric values in `long[]` with x-fastest indexing.

DICOM-specific storage metadata such as Bits Allocated, Bits Stored, High Bit, Pixel Representation, Samples per Pixel, and Photometric Interpretation remains on DICOM metadata objects and does not define the generic voxel contract.

Mapping:
- 8-bit unsigned DICOM storage -> `UINT8`
- 8-bit signed DICOM storage -> `INT8`
- 16-bit unsigned DICOM storage -> `UINT16`
- 16-bit signed DICOM storage -> `INT16`

A signed 12-bit sample stored in a 16-bit DICOM container maps to `INT16`; the original Bits Stored value remains DICOM-specific metadata.

Reason:
The NIfTI writer needs a generic scalar storage representation, not DICOM tag semantics.

Status:
Accepted

---

## ADR-011 - DICOM LPS to NIfTI RAS Affine

Decision:
The generic voxel-to-world affine uses voxel axes `(x, y, z)` where x is the
column index, y is the row index, and z is the slice index. No voxel-array
reordering, interpolation, or resampling is performed for DICOM-to-NIfTI
conversion.

For `VolumeGeometry` in DICOM patient LPS coordinates:

- affine column 0 = `columnIndexDirection * columnSpacingMm`;
- affine column 1 = `rowIndexDirection * rowSpacingMm`;
- affine column 2 = `sliceDirection * sliceSpacing`;
- affine column 3 = `origin`, the centre of voxel `(0, 0, 0)`.

The NIfTI RAS affine is obtained by left-multiplication:

`A_RAS = diag(-1, -1, 1, 1) * A_LPS`.

This changes the world-coordinate convention only. It does not flip or reorder
the stored voxel array.

For NIfTI output, sform is the authoritative full affine. qform shall represent
the same transform when the affine is representable by the NIfTI quaternion
model; otherwise qform shall not silently approximate unsupported geometry.
Scanner-anatomical transform codes are used for geometry originating from the
supported DICOM MR profile.

For later NIfTI reading, a valid sform is preferred, qform is the fallback, and
material disagreement between simultaneously valid transforms is rejected rather
than silently choosing one.

For the M7 NIfTI-1 writer, quaternion representability uses an absolute tolerance
of `1e-5` when checking normalized direction-vector lengths, orthogonality and
rotation determinant. qform/sform equivalence and independent serialized-affine
validation use an absolute affine-element tolerance of `1e-5`. Geometry outside
the quaternion-representable profile is preserved exactly in sform and receives
`qform_code = 0`; it is never silently approximated.

No change to voxel values is permitted.

Reason:
DICOM patient geometry is LPS while NIfTI commonly expresses anatomical world
coordinates as RAS. Making the conversion explicit and testable prevents hidden
axis flips and preserves the established x/y/z voxel-index convention.

Status:
Accepted

---

## ADR-012 - Fail-Safe NIfTI Output Collision Policy

Decision:
DICOM-to-NIfTI conversion shall never silently overwrite an existing output
file. The NIfTI writer creates new `.nii` and `.nii.gz` outputs using
create-new semantics.

If the requested output already exists, conversion fails explicitly with
`OUTPUT_ALREADY_EXISTS` and the existing file remains unchanged.

If a new output file is created but serialization subsequently fails, the writer
attempts to remove the partial output before propagating the failure.

Invalid output targets and general write failures are exposed through structured
application-level conversion errors rather than being treated as successful
conversion results.

Reason:
Research-data conversion must not silently destroy an existing dataset or leave a
partial volume that can be mistaken for a valid result.

Status:
Accepted

---

## ADR-013 - Project-Owned Minimal NIfTI-1 Writer and Independent Validation

Decision:
The M7 supported profile uses the project-owned minimal NIfTI-1 writer rather
than adding a runtime NIfTI library dependency.

The writer supports the explicitly approved integer NIfTI-1 single-file profile
and `.nii.gz` compression wrapper. External libraries may be used outside the
Java application as independent acceptance-validation tools.

For M7 acceptance, NiBabel is used only as an independent validation
implementation. It is not a Maven dependency and is not part of the deployed
application.

Reason:
The supported NIfTI output profile is deliberately narrow and deterministic.
Keeping serialization project-owned preserves the existing dependency boundary,
while external validation reduces the risk of testing only against our own
interpretation of the format.

Status:
Accepted
