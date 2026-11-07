# Domain Model

M4 establishes these format-neutral concepts:

- `InputSource`: an external input reference.
- `OutputTarget`: an external output reference.
- `ImagingFormat`: a format identity value.
- `Volume`: a conceptual volume contract.

`Volume` intentionally does not define voxel storage, numeric precision, memory layout, axis order, or coordinate convention. Spatial coordinate and orientation preservation are mandatory architectural requirements, but the MRI convention remains unresolved.

No DICOM or NIfTI behavior is implemented in M4.

## Current implemented domain model — 2026-11-07

The text above is the **historical M4 design snapshot**, not the present
implementation status. Following accepted ADR-009, ADR-010 and ADR-011,
the merged submission prototype (`main` at
`b032a51cd369b428f201c6b0dd8d23a49f195eec`) implements the following
bounded, format-neutral MRI volume model:

- **`Volume` and `ImageVolume`:** `Volume` remains a minimal marker
  interface; the conversion workflows use the `ImageVolume` record,
  containing `VolumeGeometry geometry`, `VoxelData voxels`, and
  `IntensityTransform intensityTransform`. Its constructor requires
  non-null components and matching geometry/voxel dimensions.
- **`VoxelData`, `ImmutableVoxelData`, and `ScalarType`:** the
  implemented 3D scalar profile is `UINT8`, `INT8`, `UINT16`, and
  `INT16`. `ImmutableVoxelData` uses a defensively copied `long[]` of
  stored numeric voxel values, with checked positive dimensions and
  overflow-safe element counts. Voxel indexing is **x = column,
  y = row, z = slice**, with x-fastest linear position
  `(z * height + y) * width + x`.
- **`VolumeGeometry`, `CoordinateSystem`, and `AffineMatrix4`:**
  the domain represents volume dimensions, physical spacing, origin,
  direction vectors, explicit world-coordinate convention, and a 4×4
  voxel-to-world affine. For the supported conventional MR profile,
  reconstructed DICOM patient geometry is in **LPS** and NIfTI world
  geometry is expressed in **RAS**. The origin is the centre of voxel
  `(0, 0, 0)`.
- **`IntensityTransform`:** explicit, finite linear interpretation
  metadata (`declared`, `slope`, `intercept`), with non-zero slope.
  The supported forward conversion preserves stored numeric voxels
  rather than applying rescale directly to their array values.

The application-layer `NiftiAffineMapper` performs the supported
DICOM-to-NIfTI coordinate change:

```text
A_RAS = diag(-1, -1, 1, 1) * A_LPS
```

This changes the voxel-to-world mapping, **not** the stored voxel order;
the supported path does not introduce resampling or interpolation.
`VolumeGeometry.voxelToWorldAffine()` constructs the affine using
column-index direction/spacing for x, row-index direction/spacing for y,
and slice direction/spacing for z.

**Scope boundary:** These implemented decisions resolve the original M4
questions for the project's supported **3D integer conversion profile**.
They do not establish arbitrary floating-point or 4D volumes, universal
DICOM/NIfTI compatibility, image registration, resampling, clinical
validity, or complete bidirectional round-trip fidelity. The original
M4 statement about unresolved coordinate convention remains above
as dated history, superseded for the implemented profile by ADR-011.

**Authority:** [Architecture Revision 1.0](ARCHITECTURE-REVISION-1.0.md),
[ADR-009/010/011](../../DECISIONS.md), and the
[foundation tracker](../planning/PRE-M8-FOUNDATION-TRACKER.md).
This is documentation reconciliation, not a change to the approved
architecture, domain source or formal acceptance decisions.
